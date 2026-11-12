package qkd.control;

import gprmcsa.routing.Route;
import network.Core;
import network.Link;
import network.Circuit;
import qkd.key.QKDKeyDemand;
import qkd.network.QKDMesh;
import qkd.network.QKDRoutePlan;
import qkd.network.QKDRoutePlanner;
import qkd.physical.QKDPhysicalParameters;
import qkd.physical.QKDPhysicalLayer;
import qkd.physical.QKDRoutePhysicalResult;
import qkd.resource.QKDSession;
import qkd.capacity.QKDSharedAllocationResult;
import qkd.capacity.QKDSharedResourceManager;
import request.RequestForConnection;

/**
 * Performs QKD-side planning, discrete-QCh/TDM capacity reservation and
 * physical feasibility evaluation for a QKD-protected request.
 *
 * <p>The service supports both committed-state evaluation and a pre-commit
 * mode in which one candidate classical circuit is included hypothetically in
 * the crosstalk calculation. The latter is used by the QKD control plane so
 * classical and quantum feasibility are checked before the classical circuit
 * is permanently committed.</p>
 *
 * <p>Refer\u00eancias: [MORI26], [DIBAJ24], [MOGH21] (PICh).</p>
 */
public final class QKDProvisioningService implements java.io.Serializable {

	/** BB84 decoy SKR is always checked; optional network.qkd.qberThresholdEnabled
	 * adds an independent QBER cap (network.qkd.maximumQber). */
	private static final int QKD_GUARD_BAND_SLOTS = 0;

	private final QKDMesh mesh;
	private final QKDRoutePlanner routePlanner;
	private final QKDSharedResourceManager resourceManager;
	private final QKDPhysicalLayer physicalEvaluator;
	private final QKDPhysicalParameters physicalParameters;
	private final qkd.network.QKDAlternativeRoutes alternativeRoutes;
	private final int pichSlots;

	/** Part 26: PIChs that follow the QCh (one per link, shared), when pichMode = segment. */
	private final qkd.capacity.QKDPichPool pichPool;

	/** Part 23 counters (reset with the QKD statistics after the warm-up). */
	private long alternativeRouteAdmissions;

	/** Parte 39: link where the last PICh failed (null = unknown or no PICh failure). */
	private transient network.Link lastPichFailureLink;

	public network.Link getLastPichFailureLink() { return lastPichFailureLink; }
	private long pichFailures;
	private long routesTried;

	public QKDProvisioningService(QKDMesh mesh) {
		if (mesh == null) {
			throw new IllegalArgumentException("mesh cannot be null");
		}

		this.mesh = mesh;
		this.routePlanner = new QKDRoutePlanner(mesh.getQkdTopologyPlan());
		this.resourceManager = new QKDSharedResourceManager(mesh);
		this.physicalParameters = mesh.getQkdPhysicalParameters();
		this.physicalEvaluator = new QKDPhysicalLayer(physicalParameters);
		this.physicalEvaluator.setMaximumQber(mesh.getQkdNetworkParameters().getEffectiveMaximumQber());
		this.physicalEvaluator.setLongitudinalXtEnabled(mesh.getQkdNetworkParameters().isLongitudinalXtEnabled());

		// Both QoT and QKD use the same source-list -> EDFA snapshot law.
		// Only activate when the QKD multiband longitudinal grid is configured.
		final qkd.multiband.MultiBandSettings mb = physicalParameters.getMultiband();

		if (mb != null && mb.getArchitecture() != qkd.multiband.MultiBandSettings.Architecture.MF) {
			// MB/MB-SDM use the real DCh/PICh carrier in h*nu*F*(G-1),
			// even when the optional longitudinal EDFA model is not enabled.
			mesh.getPhysicalLayer().setMultibandAseCarrierMode(true);
		}

		if (mb != null && mb.getPowerEvolution() != null
				&& (mb.isLongitudinalRaman() || mb.isLongitudinalNli())
				&& mb.getArchitecture() != qkd.multiband.MultiBandSettings.Architecture.MF) {
			final double middleHz = mb.getClassicalBandStartHz() +
					0.5 * mesh.getLinkList().firstElement().getCore(mb.getHostCoreId())
					.getNumOfSlots() * mesh.getLinkList().firstElement()
					.getCore(mb.getHostCoreId()).getSlotSpectrumBand();
			final double alpha = physicalParameters.attenuationDbPerKm(
					physicalParameters.getSpeedOfLightMPerS() / middleHz * 1e9)
					* qkd.multiband.FiberAttenuationModel.DB_TO_NEPER;
			mesh.getPhysicalLayer().enableLongitudinalAmplifierQoT(
					mb.getPowerEvolution(), mb.getClassicalBandStartHz(), alpha,
					true); // MB or MBSDM; MF was excluded above.
			physicalEvaluator.setSharedBoosterTemplate(
					mesh.getPhysicalLayer().sharedBoosterTemplate(mb.getPowerEvolution()));
			physicalEvaluator.setSharedClassicalMuxInsertionLossDb(
					mesh.getPhysicalLayer().getConfiguredMuxInsertionLossDb());
		}

		this.alternativeRoutes = new qkd.network.QKDAlternativeRoutes(mesh,
				mesh.getQkdNetworkParameters().getAlternativeRoutes());
		this.pichSlots = mesh.getQkdNetworkParameters().getPichSlots();

		// Stage 1: PICh is always a physical optical resource. One dedicated
		// PICh is reserved per QKDSpan; the legacy pichMode no longer turns it
		// into an abstract/shared control channel.
		this.pichPool = pichSlots > 0 && !mesh.getQkdNetworkParameters().isPichOverIp()
				? new qkd.capacity.QKDPichPool(pichSlots, mesh.getQkdNetworkParameters().getPichSessionsPerChannel(),
						mesh.getQkdNetworkParameters(), physicalParameters.getPichLaunchPowerW())
				: null;

		if (this.pichPool != null) {
			gprmcsa.modulation.Modulation selected =
					qkd.capacity.PichModulationSelection.select(
							mesh.getAvaliableModulations(),
							mesh.getQkdNetworkParameters().getPichModulation());
			this.pichPool.setClassicalPhysicalLayer(mesh.getPhysicalLayer(), selected);
		}
	}

	/**
     * Provisions QKD against the currently committed classical network state.
     * Kept for direct verification and for callers that do not have a pending
     * classical candidate.
     */
	public QKDProvisioningResult provision(RequestForConnection request) {
		return provision(request, null);
	}

	/**
     * Pre-commit QKD feasibility/provisioning. When additionalClassicalCircuit
     * is non-null, its crosstalk contribution is included as a hypothetical
     * interferer even though its classical spectrum has not yet been committed.
     * QKD resources are tentatively reserved and must either be committed by
     * the caller (by registering the returned connection after classical
     * allocation) or released on failure.
     */
	public QKDProvisioningResult provision(RequestForConnection request,
			Circuit additionalClassicalCircuit) {
		if (request == null) {
			return QKDProvisioningResult.failure("request is null");
		}

		if (!request.isQkdProtected()) {
			return QKDProvisioningResult.failure(
					"QKD provisioning requested for a classical-only request");
		}

		final QKDKeyDemand keyDemand = request.getQkdKeyDemand();

		if (keyDemand == null) {
			return QKDProvisioningResult.failure(
					"QKD-protected request has no key-demand requirement");
		}

		lastPichFailureLink = null;
		// Multiple data circuits can be served by one QKD key session. The QKD
		// route is allowed to differ from the routes of all classical circuits.
		Route classicalRoute = additionalClassicalCircuit == null ? null : additionalClassicalCircuit.getRoute();
		if (classicalRoute == null && request.getCircuits() != null)
			for (Circuit data : request.getCircuits())
				if (data != null && data.getRoute() != null) { classicalRoute = data.getRoute(); break; }
		final java.util.List<Route> candidates = new java.util.ArrayList<Route>();
		if (classicalRoute != null) {
			candidates.add(classicalRoute);
			candidates.addAll(alternativeRoutes.alternatives(
					classicalRoute.getSource().getName(),
					classicalRoute.getDestination().getName(), classicalRoute));
		} else if (request.getPair() != null) {
			// The QKD-only case has no DCh. Route directly on the directed topology.
			candidates.addAll(new qkd.network.QKDAlternativeRoutes(mesh,
					Math.max(1, mesh.getQkdNetworkParameters().getAlternativeRoutes() + 1))
					.alternatives(request.getPair().getSource().getName(),
							request.getPair().getDestination().getName(), null));
		} else {
			return QKDProvisioningResult.failure("[OTHER] no QKD source/destination pair");
		}
		if (candidates.isEmpty()) return QKDProvisioningResult.failure("[OTHER] no QKD route available");
		if (classicalRoute == null) classicalRoute = candidates.get(0);

		String failure = null;

		for (int r = 0; r < candidates.size(); r++) {
			routesTried++;
			QKDProvisioningResult result = provisionOnRoute(request, keyDemand, candidates.get(r),
					classicalRoute, r > 0, additionalClassicalCircuit);

			if (result.isSuccess()) {
				if (r > 0) alternativeRouteAdmissions++;

				return result;
			}

			// most informative cause over all routes: SKR > PICH > SPECTRUM > OTHER
			if (failure == null || causeRank(result.getFailureReason()) > causeRank(failure))
				failure = result.getFailureReason();
		}

		return QKDProvisioningResult.failure(
				candidates.size() > 1 ? failure + " (also tried " + (candidates.size() - 1) + " alternative QKD route(s))" : failure);
	}

	private QKDProvisioningResult provisionOnRoute(RequestForConnection request, QKDKeyDemand keyDemand,
			Route route, Route classicalRoute, boolean alternative, Circuit additionalClassicalCircuit) {
		final QKDRoutePlan routePlan;

		try {
			routePlan = routePlanner.plan(request, route);
		} catch (RuntimeException exception) {
			return QKDProvisioningResult.failure(
					"[OTHER] QKD route planning failed: " + exception.getMessage());
		}

		final int quantumCoreId = mesh.getQkdNetworkParameters().getQuantumCoreId();
		final int requiredSlots;

		try {
			requiredSlots = calculateRequiredQuantumSlots(route, quantumCoreId);
		} catch (RuntimeException exception) {
			return QKDProvisioningResult.failure(
					"[OTHER] QKD slot calculation failed: " + exception.getMessage());
		}

		// Stage 1 transaction order:
		// 1) reserve the physical PIChs (one per QKDSpan);
		// 2) evaluate/allocate QCh while those PIChs are already visible as
		//    physical optical interferers;
		// 3) keep both tentative until the classical DCh is committed.
		java.util.List<qkd.capacity.QKDPichPool.Channel> pichShares = null;

		if (pichPool != null) {
			pichShares = pichPool.acquire(route, routePlan,
					mesh.getQkdNetworkParameters().pichGuardBandOr(mesh.getGuardBand()),
					additionalClassicalCircuit);

			if (pichShares == null) {
				pichFailures++;
				lastPichFailureLink = pichPool.getLastFailedLink();

				return QKDProvisioningResult.failure("[PICH] no " + pichSlots
						+ "-slot physical PICh available for every QKDSpan");
			}
		}

		// Stock mode maintains a physical QCh as a source of generated keys, but
		// does not reserve each client's long-run average consumption as TDM
		// throughput. Its per-span temporal balance is authoritative for key
		// availability. A minimal 1-bit/s QCh anchor preserves the existing
		// spectrum, PICh, Q-TRx and physical feasibility checks.
		final boolean stockMode = mesh.getKeyInventoryConfig().isStock();
		final double reservedRateBps = stockMode ? 1.0 : keyDemand.getRequiredKeyRateBps();
		final QKDSharedAllocationResult allocation;

		try {
			allocation = resourceManager.allocate(
					route,
					routePlan,
					reservedRateBps,
					requiredSlots,
					getQkdGuardBandSlots(),
					physicalParameters.getAttenuationCenterNm(),
					additionalClassicalCircuit);
		} catch (RuntimeException allocationFailure) {
			if (pichPool != null) {
				try { pichPool.release(pichShares); }
				catch (RuntimeException rollbackFailure) {
					allocationFailure.addSuppressed(rollbackFailure);
				}
			}

			throw allocationFailure;
		}

		if (!allocation.isSuccess()) {
			if (pichPool != null) pichPool.release(pichShares);
			String reason = allocation.getFailureReason();

			if (reason == null || !reason.startsWith("[")) reason = "[SKR] " + reason;

			return QKDProvisioningResult.failure(
					reason.substring(0, reason.indexOf(']') + 1)
					+ " QKD shared-capacity allocation failed: " + reason.substring(reason.indexOf(']') + 1).trim());
		}

		final QKDSession session = allocation.getSession();

		// Same-band QCh/PICh/DCh physical separation is checked after QCh
		// placement, while resources remain tentative and revertible.
		if (mesh.getQkdNetworkParameters().isSameBandAlignedGrids()) {
			boolean allowed = true;

			for (qkd.resource.QKDChannelAllocation a : session.getAllocations()) {
				if (!qkd.resource.SameBandSpectrumGuard.quantumAllowed(
						a.getLink(), quantumCoreId, a.getSpectrumBand(),
						mesh.getQkdNetworkParameters().getSameBandGuardSlots(),
						containsLink(additionalClassicalCircuit == null ? null : additionalClassicalCircuit.getRoute(),
								a.getLink()) ? additionalClassicalCircuit : null)) {
					allowed = false;
					break;
				}
			}

			if (!allowed) {
				releaseQuietly(session);

				if (pichPool != null) pichPool.release(pichShares);

				return QKDProvisioningResult.failure(
						"[SPECTRUM] QCh conflicts with classical same-band guard");
			}
		}

		final QKDRoutePhysicalResult physicalResult;

		try {
			physicalResult = additionalClassicalCircuit == null
					? resourceManager.buildSessionPhysicalSnapshot(session)
					: resourceManager.buildSessionPhysicalSnapshotWithAdditionalClassicalCircuit(
							session, additionalClassicalCircuit,
							physicalParameters.getAttenuationCenterNm());
		} catch (RuntimeException exception) {
			releaseQuietly(session);

			if (pichPool != null) pichPool.release(pichShares);

			return QKDProvisioningResult.failure(
					"[OTHER] QKD physical snapshot failed: " + exception.getMessage());
		}

		// BB84 SKR remains the primary viability requirement. A separate QBER
		// cap is enforced only when expressly enabled in network.qkd; both
		// failure paths roll back tentative QCh and PICh reservations.
		final boolean qberGate = mesh.getQkdNetworkParameters().isQberThresholdEnabled();
		final double qberLimit = mesh.getQkdNetworkParameters().getEffectiveMaximumQber();
		if (qberGate && physicalResult.getMaximumQber() > qberLimit) {
			releaseQuietly(session);
			if (pichPool != null) pichPool.release(pichShares);
			return QKDProvisioningResult.failure("[QBER] QKD physical admission rejected: maxQber="
					+ physicalResult.getMaximumQber() + ", limit=" + qberLimit);
		}
		if (!physicalResult.isViable()) {
			releaseQuietly(session);
			if (pichPool != null) pichPool.release(pichShares);
			return QKDProvisioningResult.failure("[SKR] QKD physical admission rejected: minSKR="
					+ physicalResult.getMinimumSecretKeyRateBps() + " bit/s");
		}

		if (!stockMode && session.getProvisionedKeyRateBps() + 1e-6 < keyDemand.getRequiredKeyRateBps()) {
			releaseQuietly(session);

			if (pichPool != null) pichPool.release(pichShares);

			return QKDProvisioningResult.failure(
					"[SKR] QKD route cannot satisfy key demand: required="
					+ keyDemand.getRequiredKeyRateMbps()
					+ " Mbit/s, provisioned="
					+ (session.getProvisionedKeyRateBps() / 1.0e6)
					+ " Mbit/s");
		}

		QKDActiveConnection connection = new QKDActiveConnection(
				request, classicalRoute, session, physicalResult);
		connection.setStockManaged(stockMode);
		connection.setQkdRoute(route, alternative);

		if (pichShares != null) connection.setPichShares(pichShares);

		return QKDProvisioningResult.success(connection);
	}

	/**
     * Legacy route-continuous PICh allocator retained for source compatibility.
     * Stage 1 production provisioning uses QKDPichPool and one physical PICh
     * per QKDSpan, including its optical power in the QKD physical layer.
     */
	private QKDActiveConnection.PichReservation reservePich(Route route, Circuit candidate) {
		final java.util.List<Link> links = route.getLinkList();
		final int guard = mesh.getQkdNetworkParameters().pichGuardBandOr(mesh.getGuardBand());
		final Link first = links.get(0);

		// Parte 40: core order and slot order from network.qkd.pichCorePolicy / pichSlotPolicy (disabled cores skipped)
		for (Core core : qkd.capacity.QKDPichPool.pichCoreOrder(links, mesh.getQkdNetworkParameters())) {
			final int coreId = core.getId();
			int n = Integer.MAX_VALUE;
			boolean usable = true;

			for (Link l : links) {
				Core c = l.getCore(coreId);

				if (c == null || c.isQkdReserved()) { usable = false; break; }
				n = Math.min(n, c.getNumOfSlots());
			}

			if (!usable) continue;
			boolean[] free = new boolean[n + 2];
			java.util.Arrays.fill(free, 1, n + 1, true);

			for (Link l : links) {
				boolean[] f = new boolean[n + 2];

				for (int[] b : l.getCore(coreId).getFreeSpectrumBands(guard))
					for (int k = Math.max(1, b[0]); k <= Math.min(n, b[1]); k++) f[k] = true;

				if (candidate != null && qkd.metrics.BlockingDetailRecorder.rawCore(candidate) == coreId && candidate.getSpectrumAssigned() != null
						&& candidate.getRoute() != null && containsLink(candidate.getRoute(), l)) {
					int[] cb = candidate.getSpectrumAssigned();

					for (int k = Math.max(1, cb[0] - guard); k <= Math.min(n, cb[1] + guard); k++) f[k] = false;
				}

				for (int k = 1; k <= n; k++) free[k] = free[k] && f[k];
			}

			for (int s : qkd.capacity.QKDPichPool.pichSlotOrder(n, pichSlots, coreId, first.getNumberOfCores(), mesh.getQkdNetworkParameters())) {
				boolean ok = true;

				for (int k = s; k < s + pichSlots; k++) if (!free[k]) { ok = false; break; }
				if (!ok) continue;
				int[] band = new int[] {s, s + pichSlots - 1};
				java.util.List<Link> done = new java.util.ArrayList<Link>();

				try {
					for (Link l : links) {
						if (!l.getCore(coreId).useSpectrum(band, guard)) throw new IllegalStateException("busy");
						done.add(l);
					}

					return new QKDActiveConnection.PichReservation(
							java.util.Collections.unmodifiableList(new java.util.ArrayList<Link>(links)), coreId, band, guard);
				} catch (Exception e) {
					for (Link l : done) {
						try { l.getCore(coreId).liberateSpectrum(band, guard); } catch (Exception ignored) { }
					}
				}
			}
		}

		return null;
	}

	private static int causeRank(String reason) {
		if (reason == null) return 0;

		if (reason.startsWith("[SKR]")) return 5;

		if (reason.startsWith("[QTRX]")) return 4;

		if (reason.startsWith("[PICH]")) return 3;

		if (reason.startsWith("[SPECTRUM]")) return 2;

		return 1;
	}

	private static boolean containsLink(Route route, Link link) {
		if (route == null || route.getLinkList() == null) return false;

		for (Link l : route.getLinkList()) if (l == link) return true;

		return false;
	}

	private void releasePich(QKDActiveConnection connection) {
		if (pichPool != null && connection.getPichShares() != null) {
			pichPool.release(connection.getPichShares());
			connection.setPichShares(null);
		}

		QKDActiveConnection.PichReservation p = connection.getPich();

		if (p == null) return;
		IllegalStateException failure = null;

		for (Link l : p.links) {
			try {
				l.getCore(p.coreId).liberateSpectrum(p.band, p.guardBand);
			} catch (Exception e) {
				if (failure == null) failure = new IllegalStateException("Legacy PICh release failed");
				failure.addSuppressed(e);
			}
		}

		if (failure != null) throw failure;
		connection.setPich(null);
	}

	public long getAlternativeRouteAdmissions() { return alternativeRouteAdmissions; }
	public long getPichFailures() { return pichFailures; }
	public long getRoutesTried() { return routesTried; }
	public int getPichSlots() { return pichSlots; }

	/** Stage 1 physical PICh pool (null only when pichSlots = 0). */
	public qkd.capacity.QKDPichPool getPichPool() { return pichPool; }

	public void resetStatistics() {
		if (pichPool != null) pichPool.resetStatistics();
		alternativeRouteAdmissions = 0;
		pichFailures = 0;
		routesTried = 0;
		resourceManager.resetStatistics();
	}

	public boolean release(QKDActiveConnection connection) throws Exception {
		if (connection == null) {
			return false;
		}

		Exception pichFailure = null;

		try {
			releasePich(connection);
		} catch (Exception e) {
			pichFailure = e;
		}

		boolean qchReleased = false;

		try {
			qchReleased = resourceManager.release(connection.getQkdSession());
		} catch (Exception qchFailure) {
			if (pichFailure != null) qchFailure.addSuppressed(pichFailure);

			throw qchFailure;
		}

		if (pichFailure != null) throw pichFailure;

		return qchReleased;
	}

	/**
     * Converts the reference QCh bandwidth into the number of SNetS slots on
     * the dedicated quantum core. In the current NSFNet scenario both are
     * 12.5 GHz, so this resolves to one slot without hard-coding that result.
     */
	int calculateRequiredQuantumSlots(Route route, int quantumCoreId) {
		if (route == null || route.getLinkList() == null
				|| route.getLinkList().isEmpty()) {
			throw new IllegalArgumentException("route cannot be empty");
		}

		int required = 1;

		for (Link link : route.getLinkList()) {
			Core core = link.getCore(quantumCoreId);

			if (core == null || !core.isQkdReserved()) {
				throw new IllegalStateException(
						"configured QKD core is unavailable on link " + link.getName());
			}

			double slotBandwidth = core.getSlotSpectrumBand();

			if (!Double.isFinite(slotBandwidth) || slotBandwidth <= 0.0) {
				throw new IllegalStateException(
						"invalid QKD slot bandwidth on link " + link.getName());
			}

			int linkRequired = (int) Math.ceil(
					physicalParameters.getQchBandwidthHz() / slotBandwidth - 1e-12);
			required = Math.max(required, Math.max(1, linkRequired));
		}

		return required;
	}

	public QKDSharedResourceManager getResourceManager() {
		return resourceManager;
	}

	public double getStageWavelengthNm() {
		return physicalParameters.getAttenuationCenterNm();
	}

	/** Guard band between QChs in the QKD core: network.qkd.qchGuardBand (Parte 40; 0 = none, as before). */
	public int getQkdGuardBandSlots() {
		int g = mesh.getQkdNetworkParameters().getQchGuardBand();

		return g > 0 ? g : QKD_GUARD_BAND_SLOTS;
	}

	private void releaseQuietly(QKDSession session) {
		try {
			if (!resourceManager.release(session) && session != null && session.isActive())
				throw new IllegalStateException("Unable to release active QKD session");
		} catch (Exception e) {
			throw new IllegalStateException("QKD allocation rollback failed", e);
		}
	}
}
