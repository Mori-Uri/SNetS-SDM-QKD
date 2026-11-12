package qkd.control;

import java.util.IdentityHashMap;
import java.util.Map;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.integrated.IntegratedRMLSAAlgorithmInterface;
import gprmcsa.modulation.ModulationSelectionAlgorithmInterface;
import gprmcsa.powerAssignment.PowerAssignmentAlgorithmInterface;
import gprmcsa.reallocation.ReallocationAlgorithmInterface;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.RoutingAlgorithmInterface;
import gprmcsa.spectrumAssignment.SpectrumAssignmentAlgorithmInterface;
import gprmcsa.trafficGrooming.TrafficGroomingAlgorithmInterface;
import network.Circuit;
import network.ControlPlane;
import qkd.network.QKDMesh;
import qkd.metrics.QKDMetricsSnapshot;
import qkd.metrics.BlockingDetailRecorder;
import qkd.physical.QKDSpanPhysicalResult;
import request.RequestForConnection;

/**
 * SNetS control plane extended with QKD lifecycle and coexistence checks.
 * Classical-only requests keep the legacy path. For QKD-protected requests,
 * the selected classical candidate is checked together with tentative QKD
 * resources before the classical circuit is committed. Active QKD sessions
 * are also protected from harmful new classical candidates.
 *
 * <p>Refer\u00eancias: [SNETS17] A. Fontinele et al., "An Efficient IA-RMLSA Algorithm for Transparent Elastic Optical Networks", Comput. Netw. 118, 2017 (SNetS) (plano de controle original); [MORI26] U. P. Mori, qualifica\u00e7\u00e3o "Planejamento de N\u00f3s Confi\u00e1veis para QKD em SDM-EONs: An\u00e1lise da Coexist\u00eancia Qu\u00e2ntico-Cl\u00e1ssica", UFPI, 2026 (admiss\u00e3o QKD antes do compromisso cl\u00e1ssico e prote\u00e7\u00e3o das sess\u00f5es ativas).</p>
 */
public class QKDControlPlane extends ControlPlane {

	private static final long serialVersionUID = 1L;
	private static final boolean TRACE = Boolean.getBoolean("snets.qkd.trace");

	private final QKDProvisioningService provisioningService;
	private final QKDSessionRegistry sessionRegistry;
	private final QKDExistingSessionImpactEvaluator existingSessionImpactEvaluator;
	private final Map<Circuit, QKDActiveConnection> pendingQkdAdmissions =
			new IdentityHashMap<Circuit, QKDActiveConnection>();

	private long totalRequestAttempts;
	private long qkdProtectedAttempts;
	private long qkdAccepted;
	private long qkdRejected;
	private long qkdReleased;
	private long qkdExistingSessionImpactChecks;
	private long qkdClassicalRejectionsByExistingSessionImpact;
	private long qkdActiveSessionRefreshes;
	private long qkdPrecommitAdmissionChecks;
	private long qkdPrecommitAdmissionRejections;
	private String lastQkdPrecommitFailureReason;

	private QKDExistingSessionImpactResult lastExistingSessionImpactResult;

	private double qkdAcceptedDemandBpsSum;
	private double qkdAdmissionRawSkrBpsSum;
	private double qkdAdmissionRawSkrBpsMin = Double.POSITIVE_INFINITY;
	private double qkdAdmissionQberSum;
	private double qkdAdmissionQberMax;
	private double qkdAdmissionPxtWMax;

	/** Parte 44: SpRS on the spans of admitted sessions (multi-band layer). */
	private double qkdAdmissionRamanPSum, qkdAdmissionRamanPMax, qkdAdmissionRamanWMax;
	private long qkdAdmissionRamanSpans;
	private double qkdAdmissionNliPSum, qkdAdmissionNliPMax;
	private final boolean nliEnabled;

	/** Parte 45: cost of the QKD admission steps (activeMetrics.AlgorithmCost), or null. */
	private transient simulator.AlgorithmCostRecorder costRecorder;

	/** Parte 45: called by Simulation when activeMetrics.AlgorithmCost is on. */
	public void setCostRecorder(simulator.AlgorithmCostRecorder recorder) {
		this.costRecorder = recorder;
	}

	private final qkd.key.QKDTemporalKeyInventory keyInventory;

	/** Null means historical instantaneous-rate semantics with no key stock. */
	public qkd.key.QKDTemporalKeyInventory getKeyInventory() { return keyInventory; }

	private void advanceInventory(double timeHours) {
		if (keyInventory == null) return;
		if (!Double.isFinite(timeHours) || timeHours < 0.0)
			throw new IllegalArgumentException("Invalid simulation event time for QKD stock");
		keyInventory.advance(timeHours * 3600.0,
				provisioningService.getResourceManager().snapshotChannels());
	}

	private boolean canOpenInventory(QKDActiveConnection c) {
		return keyInventory == null || keyInventory.canOpen(c.getRequest(), c.getQkdSession());
	}
	private void openInventory(QKDActiveConnection c) {
		if (keyInventory != null) keyInventory.open(c.getRequest(), c.getQkdSession());
	}
	private void rollbackInventory(request.RequestForConnection r) {
		if (keyInventory != null) keyInventory.rollbackOpen(r);
	}

	private final boolean multibandEnabled;
	private final double warmupArrivals;

	// Always recompute affected QKD sessions when classical sources change.
	// Checking only IC-XT misses same-core Raman/NLI and ASE/leakage coupling.
	private long arrivalsSeen;
	private boolean warmupDone;
	private long occupancySamples;
	private double activeQchSampleSum;
	private double activeQtrxSampleSum;
	private double activeSessionSampleSum;

	/** Part 23: QKD blocking by cause and per pair, admission details. */
	private long qkdBlockedClassical;

	/** Part 25: QKD cause of the last candidate rejected by the addon (null = none). */
	private String lastQkdRejectCause;
	private final qkd.metrics.BlockingDetailRecorder blockingDetails;
	private long qkdBlockedSpectrum;
	private long qkdBlockedSkr;
	private long qkdBlockedPich;
	private long qkdBlockedQtrx;
	private long qkdBlockedOther;
	private long qkdAlternativeRouteAccepted;
	private double qkdAdmittedQTrxSum;
	private double qkdAdmittedHopsSum;
	private double qkdAdmittedRouteKmSum;
	private final java.util.Map<String, long[]> qkdPerPair = new java.util.TreeMap<String, long[]>();

	public QKDControlPlane(QKDMesh mesh, int rmlsaType,
			TrafficGroomingAlgorithmInterface trafficGroomingAlgorithm,
			IntegratedRMLSAAlgorithmInterface integratedRMLSAAlgorithm,
			RoutingAlgorithmInterface routingAlgorithm,
			KRoutingAlgorithmInterface kRoutingAlgorithm,
			SpectrumAssignmentAlgorithmInterface spectrumAssignmentAlgorithm,
			ModulationSelectionAlgorithmInterface modulationSelection,
			CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment,
			ReallocationAlgorithmInterface reallocation,
			PowerAssignmentAlgorithmInterface powerAssignment) {

		super(mesh, rmlsaType,
				trafficGroomingAlgorithm,
				integratedRMLSAAlgorithm,
				routingAlgorithm,
				kRoutingAlgorithm,
				spectrumAssignmentAlgorithm,
				modulationSelection,
				coreAndSpectrumAssignment,
				reallocation,
				powerAssignment);

		this.provisioningService = new QKDProvisioningService(mesh);
		this.keyInventory = mesh.getKeyInventoryConfig().isStock()
				? new qkd.key.QKDTemporalKeyInventory(mesh.getKeyInventoryConfig(),
					mesh.getQkdNetworkParameters().isUndirectedChannels()) : null;
		this.sessionRegistry = new QKDSessionRegistry();
		this.existingSessionImpactEvaluator =
				new QKDExistingSessionImpactEvaluator(mesh);
		this.warmupArrivals = mesh.getQkdWarmupArrivals();
		this.warmupDone = warmupArrivals <= 0.0;
		this.multibandEnabled = mesh.getQkdPhysicalParameters().getMultiband() != null;
		this.nliEnabled = multibandEnabled && mesh.getQkdPhysicalParameters().getMultiband().hasNli();
		this.blockingDetails = new qkd.metrics.BlockingDetailRecorder(warmupArrivals, this);
	}

	/**
     * Applies the same transient rule as the SNetS classical metrics: once the
     * configured number of arrivals has been generated, every cumulative QKD
     * counter restarts. Then samples the QKD occupancy seen by this arrival;
     * by PASTA (Poisson arrivals) the arrival average estimates the
     * time-average occupancy.
     */
	private void observeArrival() {
		if (!warmupDone && arrivalsSeen >= warmupArrivals) {
			warmupDone = true;
			resetQkdStatistics();
		}

		arrivalsSeen++;

		if (warmupDone) {
			qkd.capacity.QKDSharedResourceManager rm = provisioningService.getResourceManager();
			occupancySamples++;
			activeQchSampleSum += rm.getActiveQuantumChannelCount();
			activeQtrxSampleSum += rm.getActiveQTrxPairs();
			activeSessionSampleSum += sessionRegistry.size();
			rm.sampleEquipment();

			if (provisioningService.getPichPool() != null) provisioningService.getPichPool().sample();
		}
	}

	private void resetQkdStatistics() {
		totalRequestAttempts = 0; qkdProtectedAttempts = 0; qkdAccepted = 0;
		qkdRejected = 0; qkdReleased = 0; qkdExistingSessionImpactChecks = 0;
		qkdClassicalRejectionsByExistingSessionImpact = 0; qkdActiveSessionRefreshes = 0;
		qkdPrecommitAdmissionChecks = 0; qkdPrecommitAdmissionRejections = 0;
		qkdAcceptedDemandBpsSum = 0; qkdAdmissionRawSkrBpsSum = 0;
		qkdAdmissionRawSkrBpsMin = Double.POSITIVE_INFINITY; qkdAdmissionQberSum = 0;
		qkdAdmissionQberMax = 0; qkdAdmissionPxtWMax = 0;
		qkdAdmissionRamanPSum = 0; qkdAdmissionRamanPMax = 0; qkdAdmissionRamanWMax = 0; qkdAdmissionRamanSpans = 0;
		qkdAdmissionNliPSum = 0; qkdAdmissionNliPMax = 0;
		occupancySamples = 0; activeQchSampleSum = 0; activeQtrxSampleSum = 0;
		activeSessionSampleSum = 0;
		qkdBlockedClassical = 0; qkdBlockedSpectrum = 0; qkdBlockedSkr = 0; qkdBlockedPich = 0; qkdBlockedQtrx = 0; qkdBlockedOther = 0;
		qkdAlternativeRouteAccepted = 0; qkdAdmittedQTrxSum = 0; qkdAdmittedHopsSum = 0; qkdAdmittedRouteKmSum = 0;
		qkdPerPair.clear();
		provisioningService.resetStatistics();
		if (keyInventory != null) keyInventory.resetStatistics();
	}

	/**
     * Part 10 extends the legacy classical XT admission point with one extra
     * condition: all already-active QKD sessions must remain viable if the
     * classical candidate were established. The candidate has already received
     * route/core/spectrum/modulation from RMCSA at this point, but has not yet
     * been allocated, so the QKD check is non-destructive.
     */
	@Override
	public boolean isAdmissibleGeneralCrosstalk(Circuit circuit) throws Exception {
		if (!super.isAdmissibleGeneralCrosstalk(circuit)) {
			return false;
		}

		// Also protect occupied QCh spectrum against a newly offered DCh.
		// Used only for explicitly aligned same-band grids; no cross-band
		// guard is imposed on MF or MB configurations by default.
		final qkd.network.QKDNetworkParameters qnp =
				((qkd.network.QKDMesh) getMesh()).getQkdNetworkParameters();

		if (qnp.isSameBandAlignedGrids() && circuit != null
				&& circuit.getRoute() != null && circuit.getSpectrumAssigned() != null) {
			for (network.Link link : circuit.getRoute().getLinkList()) {
				if (!qkd.resource.SameBandSpectrumGuard.classicalAllowed(
						link, qnp.getQuantumCoreId(), circuit.getSpectrumAssigned(),
						qnp.getSameBandGuardSlots())) return false;
			}
		}

		// Existing physical PIChs have the same modulation-specific QoT
		// guarantees as DChs. Protect them against any pending classical DCh.
		if (!existingPichQualityPreserved(circuit)) {
			lastQkdRejectCause = BlockingDetailRecorder.C_QKD_PICH;

			return false;
		}

		/* First protect QKD sessions that are already active. */
		if (sessionRegistry.isEmpty()) {
			lastExistingSessionImpactResult = null;
		} else {
			qkdExistingSessionImpactChecks++;
			final long t0 = costRecorder == null ? 0L : System.nanoTime();
			QKDExistingSessionImpactResult result =
					existingSessionImpactEvaluator.evaluateCandidate(
							circuit, sessionRegistry, true);

			if (costRecorder != null) {
				costRecorder.add("QKDSessionProtection", "QKDExistingSessionImpactEvaluator", System.nanoTime() - t0);
			}

			lastExistingSessionImpactResult = result;

			if (!result.isAdmissible()) {
				qkdClassicalRejectionsByExistingSessionImpact++;
				lastQkdRejectCause = BlockingDetailRecorder.C_QKD_PROTECT;
				QKDSessionImpact violation = result.getFirstViolation();
				trace("QKD_EXISTING_SESSION_PROTECT",
						primaryRequest(circuit),
						violation == null ? result.toString() : violation.toString());

				return false;
			}
		}

		/*
         * Part 15: if this candidate belongs to a QKD-protected request,
         * reserve/evaluate its QKD resources BEFORE the classical circuit is
         * committed. The candidate is included hypothetically in the PXT
         * calculation, so the decision already reflects its own interference.
         */
		RequestForConnection request = primaryRequest(circuit);

		if (request != null && request.isQkdProtected()
				&& !sessionRegistry.contains(request)) {
			qkdPrecommitAdmissionChecks++;

			QKDActiveConnection stale = pendingQkdAdmissions.remove(circuit);

			if (stale != null) {
				if (!provisioningService.release(stale))
					throw new IllegalStateException("Unable to release stale QKD admission");
			}

			final long t1 = costRecorder == null ? 0L : System.nanoTime();
			QKDProvisioningResult qkd = provisioningService.provision(request, circuit);

			if (costRecorder != null) {
				costRecorder.add("QKDAdmission", "QKDProvisioningService", System.nanoTime() - t1);
			}

			if (!qkd.isSuccess()) {
				qkdPrecommitAdmissionRejections++;
				lastQkdPrecommitFailureReason = qkd.getFailureReason();
				lastQkdRejectCause = qkdCauseOf(qkd.getFailureReason());
				trace("QKD_PRECOMMIT_REJECT", request, qkd.getFailureReason());

				return false;
			}

			/*
             * The tentative physical PIChs are already registered as optical
             * sources at this point. Re-evaluate established QKD sessions so
             * DCh+PICh are accepted atomically only when no active QCh loses
             * its SKR/QBER guarantee. [MOGH21][MEHDI24][DIBAJ24]
             */
			if (!sessionRegistry.isEmpty()) {
				qkdExistingSessionImpactChecks++;
				QKDExistingSessionImpactResult withPich =
						existingSessionImpactEvaluator.evaluateCandidate(circuit, sessionRegistry, true);
				lastExistingSessionImpactResult = withPich;

				if (!withPich.isAdmissible()) {
					if (!provisioningService.release(qkd.getConnection()))
						throw new IllegalStateException("Unable to rollback rejected QKD admission");
					qkdPrecommitAdmissionRejections++;
					qkdClassicalRejectionsByExistingSessionImpact++;
					lastQkdPrecommitFailureReason = "[QKD_PROTECT] candidate DCh/PICh degrades an active QKD session";
					lastQkdRejectCause = BlockingDetailRecorder.C_QKD_PROTECT;
					trace("QKD_PRECOMMIT_REJECT", request, lastQkdPrecommitFailureReason);

					return false;
				}
			}

			// PIChs are now physically present. Protect the DCh candidate
			// and existing DChs from the PICh-induced inter-core interference.
			// No network state is changed by the quality evaluation below.
			if (!isPichSafeForClassicalTraffic(circuit, qkd.getConnection())) {
				if (!provisioningService.release(qkd.getConnection()))
					throw new IllegalStateException("Unable to rollback rejected PICh admission");
				qkdPrecommitAdmissionRejections++;
				lastQkdPrecommitFailureReason = "[PICH] PICh degrades classical QoT";
				lastQkdRejectCause = BlockingDetailRecorder.C_QKD_PICH;
				trace("QKD_PRECOMMIT_REJECT", request, lastQkdPrecommitFailureReason);

				return false;
			}

			if (!canOpenInventory(qkd.getConnection())) {
				if (!provisioningService.release(qkd.getConnection()))
					throw new IllegalStateException("Unable to rollback missing QKD stock");
				qkdPrecommitAdmissionRejections++;
				lastQkdPrecommitFailureReason = "[KEY_STOCK] insufficient secret-key stock";
				lastQkdRejectCause = BlockingDetailRecorder.C_QKD_SKR;
				return false;
			}
			lastQkdPrecommitFailureReason = null;
			pendingQkdAdmissions.put(circuit, qkd.getConnection());
			trace("QKD_PRECOMMIT_OK", request,
					"reservedRateMbps="
					+ (qkd.getConnection().getRequiredKeyRateBps() / 1.0e6));
		}

		return true;
	}

	/** Check active PIChs on affected directed links; no state mutation. */
	private boolean existingPichQualityPreserved(Circuit pendingDch) {
		qkd.capacity.QKDPichPool pool = provisioningService.getPichPool();

		if (pool == null || !((QKDMesh)getMesh()).getQkdNetworkParameters()
				.isPichOsnrCheckEnabled()) return true;
		network.PhysicalLayer physical = getMesh().getPhysicalLayer();

		for (qkd.capacity.QKDPichPool.Channel ch : pool.snapshotOpenChannels()) {
			if (pendingDch != null && pendingDch.getRoute() != null
					&& !pendingDch.getRoute().getLinkList().contains(ch.link)) continue;

			if (!physical.evaluatePich(ch.getOpticalCircuit(), pendingDch).admissible)
				return false;
		}

		return true;
	}

	/** Test the full DCh path against newly added physical PICh sources. */
	private boolean isPichSafeForClassicalTraffic(Circuit candidate,
			QKDActiveConnection provisional) {
		final network.PhysicalLayer pl = getMesh().getPhysicalLayer();

		// PIChs change GN-NLI and amplifier load even with XT disabled. In
		// XT_SEPARATE they must additionally pass the *separate* XT threshold.
		// Never skip the complete validation just because XT is not TOGETHER.
		if (candidate != null && (pl.isActiveQoT() || (pl.isActiveXT()
				&& pl.getXTModel() == network.PhysicalLayer.XT_SEPARATE))
				&& !pichClassicalQuality(pl, candidate)) return false;

		// Provisionally registered PIChs may degrade older PIChs on
		// neighbouring cores, even when classical DCh QoT is unchanged.
		if (!existingPichQualityPreserved(candidate)) return false;
		final boolean testExisting = pl.isActiveQoTForOther()
				|| (pl.isActiveXTForOther()
					&& pl.getXTModel() == network.PhysicalLayer.XT_SEPARATE);

		if (!testExisting || provisional.getPichShares() == null)
			return true;
		java.util.Set<Circuit> seen = java.util.Collections.newSetFromMap(
				new java.util.IdentityHashMap<Circuit, Boolean>());

		for (qkd.capacity.QKDPichPool.Channel ch : provisional.getPichShares()) {
			for (network.Core c : ch.link.getCores()) {
				for (Circuit existing : c.getCircuitList()) {
					if (existing != candidate && seen.add(existing)
							&& !pichClassicalQuality(pl, existing)) return false;
				}
			}
		}

		return true;
	}

	private static boolean pichClassicalQuality(network.PhysicalLayer pl, Circuit circuit) {
		if (circuit == null || circuit.getRoute() == null
				|| circuit.getModulation() == null || circuit.getSpectrumAssigned() == null)
			return false;

		if (pl.isActiveQoT()) {
			final double snr = pl.computeOSNRSegment(circuit, circuit.getRoute(),
					0, circuit.getRoute().getNodeList().size() - 1,
					circuit.getModulation(), circuit.getIndexCore(),
					circuit.getSpectrumAssigned(), null, false);

			if (!Double.isFinite(snr) || !pl.isAdmissible(circuit.getModulation(), snr))
				return false;
		}

		if (pl.isActiveXT() && pl.getXTModel() == network.PhysicalLayer.XT_SEPARATE) {
			final double xtDb = pl.getCrosstalk().calculateCrosstalk(circuit, null, false);

			return Double.isFinite(xtDb) && pl.getCrosstalk().isAdmissible(circuit, xtDb);
		}

		return true;
	}

	/**
     * After a classical circuit is actually committed, refresh the stored
     * PXT/QBER/SKR snapshots of all active QKD sessions from the live network
     * state. This keeps the session registry consistent for later metrics and
     * future admission decisions.
     */
	@Override
	public void allocateCircuit(Circuit circuit) throws Exception {
		final QKDActiveConnection pending = pendingQkdAdmissions.remove(circuit);
		boolean classicalAllocated = false;
		boolean classicalAttempted = false;
		boolean qkdRegistered = false;

		try {
			if (pending != null && sessionRegistry.contains(pending.getRequest()))
				throw new IllegalStateException("Duplicate QKD admission before classical commit");
			classicalAttempted = true;
			super.allocateCircuit(circuit);
			classicalAllocated = true;

			if (pending != null) {
				openInventory(pending);
				sessionRegistry.register(pending.getRequest(), pending);
				qkdRegistered = true;
				trace("QKD_RESOURCE_COMMIT", pending.getRequest(),
						"activeSessions=" + sessionRegistry.size());
			}

			refreshActiveQkdPhysicalState(circuit);
		} catch (Exception failure) {
			// Undo in reverse order; preserve all rollback errors as suppressed
			// exceptions rather than masking the original admission failure.
			if (pending != null) rollbackInventory(pending.getRequest());
			if (qkdRegistered) sessionRegistry.remove(pending.getRequest());

			// The legacy allocateCircuit may have partially changed its state
			// before throwing. Attempt its release even when no success return
			// was observed; retain any cleanup exception for diagnosis.
			if (classicalAttempted) {
				try { super.releaseCircuit(circuit); }
				catch (Exception rollbackError) { failure.addSuppressed(rollbackError); }
			}

			if (pending != null) {
				try {
					if (!provisioningService.release(pending))
						failure.addSuppressed(new IllegalStateException(
								"QKD release returned false during rollback"));
				} catch (Exception rollbackError) { failure.addSuppressed(rollbackError); }
			}

			throw failure;
		}
	}

	/**
     * Classical departures can only remove interference. Refresh the remaining
     * active QKD sessions after the legacy resource release completes.
     */
	@Override
	public void releaseCircuit(Circuit circuit) throws Exception {
		super.releaseCircuit(circuit);
		refreshActiveQkdPhysicalState(circuit);
	}

	@Override
	public boolean handleRequisition(RequestForConnection request) throws Exception {
		lastQkdRejectCause = null;
		if (request != null) advanceInventory(request.getTimeOfRequestHours());

		if (request != null) blockingDetails.arrival();
		final qkd.capacity.QKDSharedResourceManager rmLog = provisioningService.getResourceManager();
		final boolean protectedReq = request != null && request.isQkdProtected();

		if (protectedReq && rmLog != null) rmLog.beginQchAttemptLog();
		boolean ok = false;
		java.util.List<Object[]> tried = java.util.Collections.emptyList();

		try {
			ok = handleRequisitionQkd(request);
		} finally {
			if (protectedReq && rmLog != null) tried = rmLog.endQchAttemptLog();
		}

		if (request != null) {
			blockingDetails.observe(this, request, ok, ok ? null : lastQkdRejectCause);

			// Part 33: QCh bands on the quantum core (observation only)
			if (protectedReq) {
				java.util.List<Object[]> bands = tried;

				if (ok) bands = sessionBands(sessionRegistry.get(request));
				blockingDetails.markQch(request, ok, bands, !tried.isEmpty() || lastQkdRejectCause != null);

				// Parte 39: PICh of the protected request (channel that failed, observation only)
				blockingDetails.markPich(request, ok, ok ? sessionPichBands(sessionRegistry.get(request)) : null,
						ok ? null : provisioningService.getLastPichFailureLink());
			}
		}

		return ok;
	}

	/** Parte 39: {link, core, band} of each PICh of an admitted session (segment or route mode). */
	private static java.util.List<Object[]> sessionPichBands(QKDActiveConnection c) {
		java.util.List<Object[]> out = new java.util.ArrayList<Object[]>();

		if (c == null) return out;

		if (c.getPichShares() != null) {
			for (qkd.capacity.QKDPichPool.Channel ch : c.getPichShares()) {
				if (ch == null || ch.link == null || ch.band == null) continue;
				out.add(new Object[] {ch.link, Integer.valueOf(ch.coreId), ch.band.clone()});
			}

		} else if (c.getPich() != null && c.getPich().links != null && c.getPich().band != null) {
			for (network.Link l : c.getPich().links) out.add(new Object[] {l, Integer.valueOf(c.getPich().coreId), c.getPich().band.clone()});
		}

		return out;
	}

	/** Part 33: {link, core, band} of each QCh of an admitted session. */
	private static java.util.List<Object[]> sessionBands(QKDActiveConnection c) {
		java.util.List<Object[]> out = new java.util.ArrayList<Object[]>();

		if (c == null || c.getQkdSession() == null || c.getQkdSession().getAllocations() == null) return out;

		for (qkd.resource.QKDChannelAllocation a : c.getQkdSession().getAllocations()) {
			if (a == null || a.getLink() == null || a.getSpectrumBand() == null) continue;
			out.add(new Object[] {a.getLink(), Integer.valueOf(a.getQuantumCoreId()), a.getSpectrumBand().clone()});
		}

		return out;
	}

	/** Part 25: blocking details (observation only, see BlockingDetailRecorder). */
	public qkd.metrics.BlockingDetailRecorder.Snapshot snapshotBlockingDetails() {
		return blockingDetails.snapshot();
	}

	private static String qkdCauseOf(String reason) {
		if (reason == null) return qkd.metrics.BlockingDetailRecorder.C_QKD_OTHER;

		if (reason.startsWith("[SPECTRUM]")) return qkd.metrics.BlockingDetailRecorder.C_QKD_SPECTRUM;

		if (reason.startsWith("[SKR]") || reason.startsWith("[QBER]"))
			return qkd.metrics.BlockingDetailRecorder.C_QKD_SKR;

		if (reason.startsWith("[PICH]")) return qkd.metrics.BlockingDetailRecorder.C_QKD_PICH;

		if (reason.startsWith("[QTRX]")) return qkd.metrics.BlockingDetailRecorder.C_QKD_QTRX;

		return qkd.metrics.BlockingDetailRecorder.C_QKD_OTHER;
	}

	/**
	 * Atomic QKD-first admission for SimpleTrafficGrooming's no-expansion case.
	 * For expansion and other grooming algorithms the old compensating path is
	 * retained and audited; arbitrary grooming is not a transactional API.
	 */
	private Boolean admitExactFitSimpleGrooming(RequestForConnection request, long[] pairStats)
			throws Exception {
		if (!(grooming instanceof gprmcsa.trafficGrooming.SimpleTrafficGrooming)
				|| request.getPair() == null) return null;
		java.util.List<Circuit> candidates = searchForActiveCircuits(
				request.getPair().getSource().getName(),
				request.getPair().getDestination().getName());
		if (candidates == null || candidates.isEmpty()) return null;
		// Only the first circuit can be safely predicted without executing the
		// legacy expansion policy, which can mutate the spectrum of that circuit.
		Circuit first = candidates.get(0);
		if (first == null || first.getModulation() == null || first.getSpectrumAssigned() == null)
			return null;
		int finalSlots = first.getModulation().requiredSlots(
				first.getRequiredBitRate() + request.getRequiredBitRate());
		int currentSlots = first.getSpectrumAssigned()[1]-first.getSpectrumAssigned()[0]+1;
		if (finalSlots != currentSlots) return null;
		QKDProvisioningResult preflight = provisioningService.provision(request);
		String failure = preflight.isSuccess() ? null : preflight.getFailureReason();
		if (failure == null && !isPichSafeForClassicalTraffic(null, preflight.getConnection()))
			failure = "[PICH] optical PICh degrades existing DCh/PICh QoT";
		if (failure == null && !sessionRegistry.isEmpty()
				&& !existingSessionImpactEvaluator.evaluateCandidate(null, sessionRegistry, true).isAdmissible())
			failure = "[QKD_PROTECT] PICh degrades an active QKD session";
		if (failure == null && !canOpenInventory(preflight.getConnection()))
			failure = "[KEY_STOCK] no initial secret key on every QKDSpan";
		if (failure != null) {
			if (preflight.isSuccess() && !provisioningService.release(preflight.getConnection()))
				throw new IllegalStateException("Unable to rollback exact-fit QKD preflight");
			markQkdAdmissionFailure(request);
			lastQkdRejectCause = qkdCauseOf(failure);
			qkdRejected++;
			pairStats[1]++;
			countBlockingCause(failure);
			trace("QKD_GROOMING_PRECHECK_REJECT", request, failure);
			return Boolean.FALSE;
		}
		boolean requestAdded = false;
		boolean registered = false;
		try {
			first.addRequest(request);
			requestAdded = true;
			request.getCircuits().add(first);
			openInventory(preflight.getConnection());
			sessionRegistry.register(request, preflight.getConnection());
			registered = true;
		} catch (RuntimeException failureCommit) {
			rollbackInventory(request);
			if (registered) sessionRegistry.remove(request);
			if (requestAdded) first.removeRequest(request);
			request.getCircuits().remove(first);
			try { if (!provisioningService.release(preflight.getConnection()))
				failureCommit.addSuppressed(new IllegalStateException("QKD rollback returned false")); }
			catch (RuntimeException rollback) { failureCommit.addSuppressed(rollback); }
			throw failureCommit;
		}
		qkdAccepted++;
		recordAdmissionMetrics(preflight.getConnection());
		trace("QKD_GROOMING_ATOMIC_ADMIT", request, "without classical expansion");
		return Boolean.TRUE;
	}

	/** Ensure the request was not left on any active classical circuit. */
	private void verifyGroomingRollback(RequestForConnection request) {
		if (sessionRegistry.contains(request))
			throw new IllegalStateException("QKD session survived rejected grooming rollback");
		for (Circuit circuit : searchForActiveCircuits()) {
			if (circuit != null && circuit.getRequests().contains(request))
				throw new IllegalStateException("Grooming rollback left request on active DCh " + circuit.getId());
		}
	}

	private boolean handleRequisitionQkd(RequestForConnection request) throws Exception {
		if (request != null) observeArrival();

		if (request != null) totalRequestAttempts++;

		if (request == null || !request.isQkdProtected()) {
			return super.handleRequisition(request);
		}

		qkdProtectedAttempts++;
		if (request.getProtectionType() == qkd.traffic.RequestProtectionType.QKD_ONLY)
			return handleIndependentQkd(request);
		final long[] pairStats = pairStats(request);
		pairStats[0]++;

		// Exact-fit SimpleTrafficGrooming can commit a request without changing
		// the optical circuit. Provision and validate QKD first, so rejection
		// never needs a compensating classical mutation in this common case.
		final Boolean exactFit = admitExactFitSimpleGrooming(request, pairStats);
		if (exactFit != null) return exactFit.booleanValue();

		final boolean classicalSuccess = super.handleRequisition(request);

		if (!classicalSuccess) {
			releasePendingForRequest(request);
			qkdRejected++;
			pairStats[1]++;
			countBlockingCause(lastQkdPrecommitFailureReason);
			trace(lastQkdPrecommitFailureReason == null
							? "CLASSICAL_REJECT" : "QKD_REJECT",
					request, lastQkdPrecommitFailureReason);
			lastQkdPrecommitFailureReason = null;

			return false;
		}

		/*
         * Normal no-grooming path: allocateCircuit has already committed the
         * pre-checked QKD connection. A fallback is kept for grooming paths
         * that can satisfy a request without establishing a new circuit and
         * therefore never pass through isAdmissibleGeneralCrosstalk.
         */
		QKDActiveConnection connection = sessionRegistry.get(request);

		if (connection == null) {
			final QKDProvisioningResult fallback = provisioningService.provision(request);

			String failureReason = fallback.isSuccess() ? null : fallback.getFailureReason();
			if (failureReason == null && !isPichSafeForClassicalTraffic(null, fallback.getConnection()))
				failureReason = "[PICH] fallback PICh degrades existing classical QoT";
			if (failureReason == null && !sessionRegistry.isEmpty() &&
					!existingSessionImpactEvaluator.evaluateCandidate(null, sessionRegistry, true).isAdmissible())
				failureReason = "[QKD_PROTECT] fallback PICh degrades an active QKD session";
			if (failureReason == null && !canOpenInventory(fallback.getConnection()))
				failureReason = "[KEY_STOCK] no initial secret key on every QKDSpan";
			if (failureReason != null) {
				// Post-commit grooming expansion cannot be undone by the QKD
				// allocator. The legacy grooming implementation must compensate.
				// An incomplete compensation is fatal, never a silent reject.
				if (fallback.isSuccess() && !provisioningService.release(fallback.getConnection()))
					throw new IllegalStateException("QKD fallback rollback failed");
				super.finalizeConnection(request);
				verifyGroomingRollback(request);
				markQkdAdmissionFailure(request);
				lastQkdRejectCause = qkdCauseOf(failureReason);
				qkdRejected++;
				pairStats[1]++;
				countBlockingCause(failureReason);
				trace("QKD_REJECT_FALLBACK", request, failureReason);
				return false;
			}

			connection = fallback.getConnection();
			openInventory(connection);
			sessionRegistry.register(request, connection);
		}

		qkdAccepted++;
		recordAdmissionMetrics(connection);
		trace("QKD_ADMIT", request,
				"activeSessions=" + sessionRegistry.size());

		return true;
	}

	/** QKD session is admitted with no classical circuit or optical data wavelength. */
	private boolean handleIndependentQkd(RequestForConnection request) throws Exception {
		final long[] statistics = pairStats(request);
		statistics[0]++;
		QKDProvisioningResult outcome = provisioningService.provision(request);
		if (!outcome.isSuccess()) {
			qkdRejected++;
			statistics[1]++;
			countBlockingCause(outcome.getFailureReason());
			return false;
		}
		QKDActiveConnection accepted = outcome.getConnection();
		if (!canOpenInventory(accepted)) {
			if (!provisioningService.release(accepted))
				throw new IllegalStateException("QKD-only key stock rollback incomplete");
			qkdRejected++; statistics[1]++; countBlockingCause("[KEY_STOCK] exhausted");
			return false;
		}
		// The optical PICh must not invalidate any existing classical DCh or PICh.
		if (!isPichSafeForClassicalTraffic(null, accepted)) {
			if (!provisioningService.release(accepted))
				throw new IllegalStateException("QKD-only PICh quality rollback incomplete");
			qkdRejected++;
			statistics[1]++;
			countBlockingCause("[PICH] PICh degrades active classical QoT");
			return false;
		}
		// Optical PIChs have already been registered by the provisional allocator.
		if (!sessionRegistry.isEmpty()) {
			QKDExistingSessionImpactResult impact = existingSessionImpactEvaluator.evaluateCandidate(
					null, sessionRegistry, true);
			if (!impact.isAdmissible()) {
				if (!provisioningService.release(accepted))
					throw new IllegalStateException("QKD-only rollback incomplete");
				qkdRejected++;
				statistics[1]++;
				return false;
			}
		}
		openInventory(accepted);
		sessionRegistry.register(request, accepted);
		// The optical load of this PICh is now part of the accepted network.
		refreshActiveQkdPhysicalState(null);
		qkdAccepted++;
		recordAdmissionMetrics(accepted);
		return true;
	}

	@Override
	public void finalizeConnection(RequestForConnection request) throws Exception {
		if (request != null) advanceInventory(request.getTimeOfFinalizeHours());
		Exception qkdFailure = null;

		QKDActiveConnection connection = sessionRegistry.get(request);

		if (connection != null) {
			try {
				if (provisioningService.release(connection)) {
					if (keyInventory != null) keyInventory.close(request);
					sessionRegistry.remove(request);
					// PICh removals can affect Raman, GN, FWM and EDFA load.
					if (request.getProtectionType() == qkd.traffic.RequestProtectionType.QKD_ONLY)
						refreshActiveQkdPhysicalState(null);
					qkdReleased++;
				} else {
					throw new IllegalStateException("QKD resource release incomplete");
				}

				trace("QKD_RELEASE", request,
						"activeSessions=" + sessionRegistry.size());
			} catch (Exception exception) {
				qkdFailure = exception;
			}
		}

		/* Always try to release the classical resources as well. */
		if (request.getProtectionType() != qkd.traffic.RequestProtectionType.QKD_ONLY)
			super.finalizeConnection(request);

		if (qkdFailure != null) {
			throw qkdFailure;
		}
	}

	private void refreshActiveQkdPhysicalState(Circuit changed) {
		if (sessionRegistry.isEmpty()) {
			return;
		}

		final long t0 = costRecorder == null ? 0L : System.nanoTime();
		qkdActiveSessionRefreshes +=
				existingSessionImpactEvaluator.refreshActiveSessions(
						sessionRegistry, changed);

		if (costRecorder != null) {
			costRecorder.add("QKDSessionRefresh", "QKDExistingSessionImpactEvaluator", System.nanoTime() - t0);
		}
	}

	private long[] pairStats(RequestForConnection request) {
		String key = request.getPair() == null ? "?|?"
				: request.getPair().getSource().getName() + "|" + request.getPair().getDestination().getName();
		long[] v = qkdPerPair.get(key);

		if (v == null) { v = new long[2]; qkdPerPair.put(key, v); }
		return v;
	}

	/** Failure reasons carry a [CAUSE] tag set by the provisioning service. */
	private void countBlockingCause(String reason) {
		if (reason == null) { qkdBlockedClassical++; return; }
		if (reason.startsWith("[SPECTRUM]")) qkdBlockedSpectrum++;
		else if (reason.startsWith("[SKR]")) qkdBlockedSkr++;
		else if (reason.startsWith("[PICH]")) qkdBlockedPich++;
		else if (reason.startsWith("[QTRX]")) qkdBlockedQtrx++;
		else qkdBlockedOther++;
	}

	private RequestForConnection primaryRequest(Circuit circuit) {
		if (circuit == null || circuit.getRequests() == null
				|| circuit.getRequests().isEmpty()) {
			return null;
		}

		return circuit.getRequests().get(0);
	}

	private void releasePendingForRequest(RequestForConnection request) {
		if (request == null || pendingQkdAdmissions.isEmpty()) return;
		java.util.Iterator<Map.Entry<Circuit, QKDActiveConnection>> it =
				pendingQkdAdmissions.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Circuit, QKDActiveConnection> e = it.next();

			if (e.getValue() != null && e.getValue().getRequest() == request) {
				try {
					if (!provisioningService.release(e.getValue()))
						throw new IllegalStateException("QKD pending admission rollback was incomplete");
				} catch (Exception rollbackFailure) {
					throw new IllegalStateException(
							"Unable to rollback QKD resources for a rejected request", rollbackFailure);
				}

				it.remove();
			}
		}
	}

	private void markQkdAdmissionFailure(RequestForConnection request) {
		if (request.getCircuits() == null || request.getCircuits().isEmpty()) {
			return;
		}

		Circuit circuit = request.getCircuits().get(0);
		circuit.setWasBlocked(true);
		circuit.setBlockCause(Circuit.BY_OTHER);
	}

	public QKDSessionRegistry getQkdSessionRegistry() {
		return sessionRegistry;
	}

	public int getActiveQkdSessionCount() {
		return sessionRegistry.size();
	}

	public QKDActiveConnection getActiveQkdConnection(
			RequestForConnection request) {
		return sessionRegistry.get(request);
	}

	public long getTotalRequestAttempts() { return totalRequestAttempts; }

	public long getQkdProtectedAttempts() {
		return qkdProtectedAttempts;
	}

	public long getQkdAccepted() {
		return qkdAccepted;
	}

	public long getQkdRejected() {
		return qkdRejected;
	}

	public long getQkdReleased() {
		return qkdReleased;
	}

	public long getQkdExistingSessionImpactChecks() {
		return qkdExistingSessionImpactChecks;
	}

	public long getQkdClassicalRejectionsByExistingSessionImpact() {
		return qkdClassicalRejectionsByExistingSessionImpact;
	}

	public long getQkdActiveSessionRefreshes() {
		return qkdActiveSessionRefreshes;
	}

	public long getQkdPrecommitAdmissionChecks() {
		return qkdPrecommitAdmissionChecks;
	}

	public long getQkdPrecommitAdmissionRejections() {
		return qkdPrecommitAdmissionRejections;
	}

	private void recordAdmissionMetrics(QKDActiveConnection connection) {
		if (connection.isAlternativeRoute()) qkdAlternativeRouteAccepted++;
		qkdAdmittedQTrxSum += connection.getQkdSession().getRoutePlan().getTotalSpanCount();
		qkdAdmittedHopsSum += connection.getQkdRoute().getLinkList().size();
		qkdAdmittedRouteKmSum += connection.getQkdRoute().getDistanceAllLinks();
		qkdAcceptedDemandBpsSum += connection.getRequiredKeyRateBps();
		double raw = connection.getRawPhysicalMinimumSecretKeyRateBps();
		qkdAdmissionRawSkrBpsSum += raw;
		qkdAdmissionRawSkrBpsMin = Math.min(qkdAdmissionRawSkrBpsMin, raw);
		qkdAdmissionQberSum += connection.getMaximumQber();
		qkdAdmissionQberMax = Math.max(qkdAdmissionQberMax, connection.getMaximumQber());

		for (QKDSpanPhysicalResult span : connection.getPhysicalResult().getSpanResults()) {
			qkdAdmissionPxtWMax = Math.max(qkdAdmissionPxtWMax, span.getCrosstalkPowerW());

			if (multibandEnabled) { // Parte 44
				qkdAdmissionRamanPSum += span.getRamanBackgroundProbability();
				qkdAdmissionRamanSpans++;
				qkdAdmissionRamanPMax = Math.max(qkdAdmissionRamanPMax, span.getRamanBackgroundProbability());
				qkdAdmissionRamanWMax = Math.max(qkdAdmissionRamanWMax, span.getRamanPowerW());
				qkdAdmissionNliPSum += span.getNliBackgroundProbability();
				qkdAdmissionNliPMax = Math.max(qkdAdmissionNliPMax, span.getNliBackgroundProbability());
			}
		}
	}

	public QKDMetricsSnapshot snapshotQkdMetrics(int loadPoint, int replication) {
		QKDMetricsSnapshot m = new QKDMetricsSnapshot();
		m.loadPoint=loadPoint; m.replication=replication;
		if (keyInventory != null) {
			m.keyInventoryMode="stock";
			m.keyGeneratedBits=keyInventory.getGeneratedBits();
			m.keyConsumedBits=keyInventory.getConsumedBits();
			m.keyDiscardedBits=keyInventory.getDiscardedBits();
			m.keyUnservedBits=keyInventory.getUnservedBits();
			m.keyRemainingBits=keyInventory.getTotalBalanceBits();
			m.keyExhaustionEvents=keyInventory.getExhaustionEvents();
			m.keyStockRejected=keyInventory.getRejectedAdmissions();
			m.keyServedRenewals=keyInventory.getServedRenewals();
			m.keyUnservedRenewals=keyInventory.getUnservedRenewals();
			m.keyBatchedWindows=keyInventory.getBatchedWindows();
			m.keyApproximatedRenewals=keyInventory.getApproximatedRenewals();
		}
		m.multiband = multibandEnabled;
		m.nli = nliEnabled;
		m.totalRequestAttempts=totalRequestAttempts; m.protectedAttempts=qkdProtectedAttempts; m.accepted=qkdAccepted; m.rejected=qkdRejected; m.released=qkdReleased;
		m.existingSessionImpactChecks=qkdExistingSessionImpactChecks;
		m.classicalRejectionsByQkdImpact=qkdClassicalRejectionsByExistingSessionImpact;
		m.activeRefreshes=qkdActiveSessionRefreshes;
		m.precommitAdmissionChecks=qkdPrecommitAdmissionChecks;
		m.precommitAdmissionRejections=qkdPrecommitAdmissionRejections;
		qkd.capacity.QKDSharedResourceManager rm = provisioningService.getResourceManager();
		m.activatedQchs=rm.getActivatedChannels(); m.deactivatedQchs=rm.getDeactivatedChannels();
		m.tdmReuses=rm.getReusedCapacityReservations(); m.capacityFailures=rm.getFailedCapacityReservations();
		m.peakActiveQchs=rm.getPeakActiveChannels(); m.peakQTrxPairs=rm.getPeakQTrxPairs();
		m.totalDemandMbps=qkdAcceptedDemandBpsSum/1e6;
		m.acceptanceRatio = qkdProtectedAttempts > 0 ? ((double) qkdAccepted) / qkdProtectedAttempts : Double.NaN;
		m.blockingProbability = qkdProtectedAttempts > 0 ? ((double) qkdRejected) / qkdProtectedAttempts : Double.NaN;

		if (occupancySamples > 0) {
			m.meanActiveQchs = activeQchSampleSum / occupancySamples;
			m.meanActiveQTrxPairs = activeQtrxSampleSum / occupancySamples;
			m.meanActiveSessions = activeSessionSampleSum / occupancySamples;
		}

		m.qtndKm = ((QKDMesh) getMesh()).getQkdNetworkParameters().getQtndKm();
		m.blockedClassical = qkdBlockedClassical; m.blockedSpectrum = qkdBlockedSpectrum;
		m.blockedSkr = qkdBlockedSkr; m.blockedPich = qkdBlockedPich; m.blockedOther = qkdBlockedOther;
		m.blockedQtrx = qkdBlockedQtrx;
		m.qEquipmentFailures = rm.getQEquipmentFailures();
		m.qEquipment.putAll(rm.equipmentStatistics());
		qkd.capacity.QKDPichPool pp = provisioningService.getPichPool();

		if (pp != null) { m.meanOpenPichs = pp.getMeanOpenChannels(); m.peakOpenPichs = pp.getPeakOpenChannels(); m.pichOpened = pp.getOpened(); }
		m.alternativeRouteAccepted = qkdAlternativeRouteAccepted;

		if (qkdAccepted > 0) {
			m.meanQTrxPerSession = qkdAdmittedQTrxSum / qkdAccepted;
			m.meanHopsPerSession = qkdAdmittedHopsSum / qkdAccepted;
			m.meanRouteKmPerSession = qkdAdmittedRouteKmSum / qkdAccepted;
		}

		m.routeCandidateBandsEvaluated = rm.getRouteCandidateBandsEvaluated();
		m.plannedTrustedNodes = ((QKDMesh) getMesh()).getQkdTopologyPlan().getTrustedNodeCount();
		m.plannedQkdSpans = ((QKDMesh) getMesh()).getQkdTopologyPlan().getTotalSpanCount();

		for (java.util.Map.Entry<String, long[]> e : qkdPerPair.entrySet())
			m.perPair.put(e.getKey(), new long[] {e.getValue()[0], e.getValue()[1]});
		m.warmupArrivals = warmupArrivals;

		if(qkdAccepted>0){
			m.meanAdmissionRawSkrMbps=(qkdAdmissionRawSkrBpsSum/qkdAccepted)/1e6;
			m.minimumAdmissionRawSkrMbps=qkdAdmissionRawSkrBpsMin/1e6;
			m.meanAdmissionQber=qkdAdmissionQberSum/qkdAccepted;
			m.maximumAdmissionQber=qkdAdmissionQberMax;
			m.maximumAdmissionPxtW=qkdAdmissionPxtWMax;
			m.meanAdmissionRamanProbability = qkdAdmissionRamanSpans > 0 ? qkdAdmissionRamanPSum / qkdAdmissionRamanSpans : 0.0;
			m.maximumAdmissionRamanProbability = qkdAdmissionRamanPMax;
			m.maximumAdmissionRamanPowerW = qkdAdmissionRamanWMax;
			m.meanAdmissionNliProbability = qkdAdmissionRamanSpans > 0 ? qkdAdmissionNliPSum / qkdAdmissionRamanSpans : 0.0;
			m.maximumAdmissionNliProbability = qkdAdmissionNliPMax;
		}

		return m;
	}

	public QKDExistingSessionImpactResult getLastExistingSessionImpactResult() {
		return lastExistingSessionImpactResult;
	}

	private void trace(String action, RequestForConnection request,
			String detail) {
		if (!TRACE) {
			return;
		}

		String pair = request != null && request.getPair() != null
				? request.getPair().getPairName() : "?";
		System.out.println("[QKD-LIFECYCLE] " + action + " pair=" + pair
				+ (detail == null ? "" : " " + detail));
	}
}
