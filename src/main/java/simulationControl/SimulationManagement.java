package simulationControl;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import measurement.Measurements;
import network.Mesh;
import network.Pair;
import network.RequestGenerator;
import qkd.network.QKDMeshFactory;
import qkd.control.QKDControlPlane;
import qkd.metrics.QKDMetricsSnapshot;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.SimulationRequest;
import simulationControl.parsers.TrafficConfig;
import simulationControl.resultManagers.ResultManager;
import simulator.Simulation;
import simulator.Simulator;

/**
 * Coordinates all load-point/replication executions.
 *
 * <p>Part 6 changes only mesh creation: when {@code network.qkd.enabled=true},
 * {@link QKDMeshFactory} creates a QKD-aware mesh whose runtime request
 * generators are {@code QKDRequestGenerator}s. Legacy scenarios still receive
 * the original {@link Mesh}.</p>
 */
public class SimulationManagement {

	private SimulationRequest simulationRequest;
	private List<List<Simulation>> simulations;
	private volatile int done;
	private volatile int failed;
	private volatile int stopped;
	private simulator.SaveStateStore saveStates;

	public simulator.SaveStateStore getSaveStates() { return saveStates; }
	private int numOfSimulations;
	private List<List<Measurements>> mainMeasuremens;

	public interface SimulationProgressListener {
		void onSimulationProgressUpdate(double progress);
		void onSimulationFinished();
	}

	public List<List<Measurements>> getMainMeasuremens() {
		return mainMeasuremens;
	}

	public SimulationManagement(SimulationRequest simulationRequest) {
		this.simulationRequest = simulationRequest;
		this.simulations = createAllSimulations(simulationRequest);
		this.done = 0;
		this.numOfSimulations = 0;
		this.mainMeasuremens = new ArrayList<List<Measurements>>();

		for (List<Simulation> loadPoint : simulations) {
			List<Measurements> measurements = new ArrayList<Measurements>();
			mainMeasuremens.add(measurements);

			for (int i = 0; i < loadPoint.size(); i++) {
				measurements.add(null);
				numOfSimulations++;
			}
		}
	}

	public static List<List<Simulation>> createAllSimulations(
			SimulationRequest simulationRequest) {

		Util util = new Util();
		String separator = System.getProperty("file.separator");
		String projectPath = simulationRequest.getPath()
				+ separator + simulationRequest.getName();
		util.projectPath = projectPath;

		NetworkConfig networkConfig = simulationRequest.getNetworkConfig();
		SimulationConfig simulationConfig = simulationRequest.getSimulationConfig();
		TrafficConfig trafficConfig = simulationRequest.getTrafficConfig();
		PhysicalLayerConfig physicalLayerConfig =
				simulationRequest.getPhysicalLayerConfig();
		OthersConfig othersConfig = simulationRequest.getOthersConfig();

		System.out.println("Calculating the modulations transmission ranges");

		Mesh baseMesh = QKDMeshFactory.create(
				networkConfig,
				trafficConfig,
				physicalLayerConfig,
				othersConfig,
				null,
				util,
				simulationConfig);

		List<List<Simulation>> simulations =
				new ArrayList<List<Simulation>>();

		for (int loadPoint = 0;
				loadPoint < simulationConfig.getLoadPoints();
				loadPoint++) {

			List<Simulation> replications = new ArrayList<Simulation>();

			for (int replication = 0;
					replication < simulationConfig.getReplications();
					replication++) {

				Mesh mesh = QKDMeshFactory.create(
						networkConfig,
						trafficConfig,
						physicalLayerConfig,
						othersConfig,
						baseMesh.getModTrDistance(),
						util,
						simulationConfig,
						loadPoint,
						replication);

				incArrivedRate(mesh.getPairList(), loadPoint);

				Simulation simulation = new Simulation(
						simulationConfig,
						mesh,
						loadPoint,
						replication,
						util);

				replications.add(simulation);
			}

			simulations.add(replications);
		}

		util.pairs.addAll(simulations.get(0).get(0).getMesh().getPairList());

		return simulations;
	}

	private static void incArrivedRate(Vector<Pair> pairs, int mult) {
		for (Pair pair : pairs) {
			for (RequestGenerator generator : pair.getRequestGenerators()) {
				generator.incArrivedRate(mult);
			}
		}
	}

	public void startSimulations(
			final SimulationProgressListener simulationProgressListener) {

		final ExecutorService executor = Executors.newScheduledThreadPool(
				simulationRequest.getSimulationConfig().getThreads());
		done = 0;
		failed = 0;
		stopped = 0;
		// Parte 45: save states (simulation.saveState). Parte 52: on unless "enabled": false
		final simulator.SaveStateStore saveStates = simulator.SaveStateStore.forRequest(simulationRequest);
		this.saveStates = saveStates;

		if (saveStates != null) saveStates.begin(simulations, simulationRequest.getSimulationConfig().getRequests());

		// Parte 54 (desempenho): with more than one thread, the simulations of the highest load points (the slowest)
		// are started first, so the last ones to finish are short and the threads are not left idle at the end of the
		// run. Each simulation is independent and keeps its own seed, so the results do not change. With one thread
		// the original order is kept.
		final List<List<Simulation>> order = new java.util.ArrayList<List<Simulation>>(simulations);

		if (simulationRequest.getSimulationConfig().getThreads() > 1) {
			java.util.Collections.reverse(order);
		}

		for (List<Simulation> loadPoint : order) {
			for (int index = 0; index < loadPoint.size(); index++) {
				final simulator.SaveStateStore.State state = saveStates == null ? null
						: saveStates.load(loadPoint.get(index).getLoadPoint(), loadPoint.get(index).getReplication(),
								loadPoint.get(index).getUtil());

				if (state != null) {
					loadPoint.set(index, state.getSimulation());
					System.out.println("Save state: carga " + state.getSimulation().getLoadPoint() + ", replicacao "
							+ state.getSimulation().getReplication() + (state.isFinished() ? " ja concluida"
							: " retomada do ponto salvo"));
				}

				final Simulation replication = loadPoint.get(index);

				if (state != null && state.isFinished()) {
					mainMeasuremens.get(replication.getLoadPoint()).set(replication.getReplication(),
							replication.getMeasurements());

					synchronized (SimulationManagement.this) {
						done++;
					}

					continue;
				}

				executor.execute(new Runnable() {
					@Override
					public void run() {
						// Parte 52: a stop was requested before this simulation started: it is left for the resume
						if (simulator.SaveStateStore.isStopRequested()) {
							synchronized (SimulationManagement.this) {
								stopped++;
								done++;
							}

							return;
						}

						try {
							Simulator simulator = new Simulator(replication);
							simulator.setSaveStates(saveStates);

							final long wall0 = System.nanoTime(); // Parte 45
							mainMeasuremens
								.get(replication.getLoadPoint())
								.set(replication.getReplication(),
										state != null ? simulator.resume(state) : simulator.start());

							if (replication.getCostRecorder() != null) {
								replication.getCostRecorder().setWallNanos(System.nanoTime() - wall0);
							}

							int finished;

							synchronized (SimulationManagement.this) {
								finished = ++done;
							}

							simulationProgressListener.onSimulationProgressUpdate(
									((double) finished) / ((double) numOfSimulations));
						} catch (simulator.SaveStateStore.Stopped st) {
							synchronized (SimulationManagement.this) {
								stopped++;
								done++;
							}

						} catch (Throwable ex) {
							ex.printStackTrace();
							/*
                             * Count the failed replication so that the wait loop
                             * below terminates instead of hanging forever.
                             */
							synchronized (SimulationManagement.this) {
								failed++;
								done++;
							}
						}
					}

				});
			}
		}

		while (done < numOfSimulations) {
			try {
				Thread.sleep(200L);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		executor.shutdown();

		if (stopped > 0) {
			// Parte 52: stopped on request (launcher, Ctrl+C): the progress stays in savestate/, no results are
			// written and the process ends with exit code 3
			if (saveStates != null) saveStates.writeProgress("stopped");
			System.out.println("[snets] execucao interrompida: " + stopped + " de " + numOfSimulations
					+ " simulacoes paradas" + (saveStates != null ? "; progresso salvo em " + saveStates.getDir()
					: " (save states desligados: nada foi salvo)"));
			System.out.println("[snets] rode o mesmo caso de novo para continuar de onde parou.");
			System.out.flush();
			simulator.SaveStateStore.markExitHandled();

			if (!Boolean.getBoolean("snets.noExitOnStop")) System.exit(simulator.SaveStateStore.EXIT_STOPPED);

			throw new simulator.SaveStateStore.Stopped(stopped + " of " + numOfSimulations + " simulations stopped");
		}

		if (failed > 0) {
			throw new IllegalStateException(failed + " of " + numOfSimulations
					+ " simulations failed; see the stack traces above. No results were saved.");
		}

		ResultManager resultManager = new ResultManager(mainMeasuremens);
		SimulationRequest.Result finalResult = resultManager.getResults();
		finalResult.qkdStatistics = buildQkdStatisticsCsv();
		finalResult.algorithmCost = buildAlgorithmCostCsv();
		finalResult.qkdMetrics = buildQkdMetricsCsv();
		finalResult.blockingDetails = buildBlockingDetailsCsv();
		finalResult.qkdChannelBlocking = buildChannelCsv(false);

		if (simulationRequest.getSimulationConfig().getActiveMetrics().SpectrumUtilization
				&& simulationRequest.getSimulationConfig().getActiveMetrics().SlotUtilization) {
			finalResult.slotUtilization = buildSlotUtilizationCsv();
		}

		if (simulationRequest.getSimulationConfig().getActiveMetrics().BlockingProbability
				&& simulationRequest.getSimulationConfig().getActiveMetrics().SlotBlocking) {
			finalResult.slotBlocking = buildSlotBlockingCsv();
			finalResult.qkdChannelSlotBlocking = buildChannelCsv(true);
		}

		blockingSnapshotsCache = null;
		simulationRequest.setResult(finalResult);
		// Parte 52: every simulation finished; the save states are deleted when the results are on disk
		if (saveStates != null) saveStates.caseFinished();
		simulationProgressListener.onSimulationFinished();
	}

	private interface QkdValue { double of(QKDMetricsSnapshot m); }

	/**
     * Part 27: SlotUtilization.csv. One row per load point, link, core and replication; the columns
     * 1..S are the fraction of the observations in which that slot was occupied (data and guard bands,
     * QChs and PIChs included), i.e. the time average sampled at each arrival, like SpectrumUtilization.
     */
	private String buildSlotUtilizationCsv() {
		List<measurement.SpectrumUtilization> all = new ArrayList<measurement.SpectrumUtilization>();

		for (List<Measurements> lp : mainMeasuremens) {
			for (Measurements ms : lp) {
				if (ms == null) continue;

				for (measurement.Measurement m : ms.getMetrics()) {
					if (m instanceof measurement.SpectrumUtilization) all.add((measurement.SpectrumUtilization) m);
				}
			}
		}

		if (all.isEmpty()) return null;
		int slots = 0;

		for (measurement.SpectrumUtilization su : all) slots = Math.max(slots, su.getMaxSlotsByLinks());
		StringBuilder sb = new StringBuilder();
		sb.append("Metrics,LoadPoint,Link,Core,Replication");

		for (int s = 1; s <= slots; s++) sb.append(',').append(s);
		sb.append('\n');

		for (measurement.SpectrumUtilization su : all) su.appendSlotUtilizationRows(sb, slots);

		return sb.toString();
	}

	/** Part 25: BlockingDetails.csv (observation-only counters of the control plane). */
	private String buildBlockingDetailsCsv() {
		List<List<qkd.metrics.BlockingDetailRecorder.Snapshot>> snaps = blockingSnapshots();

		if (snaps == null) return null;

		try {
			return qkd.metrics.BlockingDetailRecorder.toCsv(snaps);
		} catch (RuntimeException e) {
			e.printStackTrace();

			return null;
		}
	}

	/** Parte 39: QKDChannelBlocking.csv / QKDChannelSlotBlocking.csv (channel that failed in the protected requests). */
	private String buildChannelCsv(boolean slots) {
		List<List<qkd.metrics.BlockingDetailRecorder.Snapshot>> snaps = blockingSnapshots();

		if (snaps == null) return null;

		try {
			return slots ? qkd.metrics.BlockingDetailRecorder.toChannelSlotCsv(snaps)
					: qkd.metrics.BlockingDetailRecorder.toChannelCsv(snaps);
		} catch (RuntimeException e) {
			e.printStackTrace();

			return null;
		}
	}

	/** Part 29: SlotBlocking.csv (where in the spectrum the requests were tried and blocked). */
	private String buildSlotBlockingCsv() {
		List<List<qkd.metrics.BlockingDetailRecorder.Snapshot>> snaps = blockingSnapshots();

		if (snaps == null) return null;

		try {
			return qkd.metrics.BlockingDetailRecorder.toSlotCsv(snaps);
		} catch (RuntimeException e) {
			e.printStackTrace();

			return null;
		}
	}

	private List<List<qkd.metrics.BlockingDetailRecorder.Snapshot>> blockingSnapshotsCache;

	private List<List<qkd.metrics.BlockingDetailRecorder.Snapshot>> blockingSnapshots() {
		if (blockingSnapshotsCache != null) return blockingSnapshotsCache;
		List<List<qkd.metrics.BlockingDetailRecorder.Snapshot>> snaps =
				new ArrayList<List<qkd.metrics.BlockingDetailRecorder.Snapshot>>();

		for (List<Simulation> load : simulations) {
			List<qkd.metrics.BlockingDetailRecorder.Snapshot> row =
					new ArrayList<qkd.metrics.BlockingDetailRecorder.Snapshot>();

			for (Simulation sim : load) {
				Object cp = sim.getControlPlane();

				if (cp instanceof QKDControlPlane) row.add(((QKDControlPlane) cp).snapshotBlockingDetails());
				else if (cp instanceof qkd.metrics.ObservedClassicalControlPlane)
					row.add(((qkd.metrics.ObservedClassicalControlPlane) cp).snapshotBlockingDetails());
				else return null;
			}

			snaps.add(row);
		}

		blockingSnapshotsCache = snaps;

		return snaps;
	}

	private static double ratio(double a, double b) { return b > 0 ? a / b : Double.NaN; }

	private static double sumEquip(QKDMetricsSnapshot m, int k) {
		double t = 0;

		for (double[] v : m.qEquipment.values()) t += v[k];

		return t;
	}

	/**
     * Part 23: QKD metrics in the standard SNetS layout
     * (Metrics,LoadPoint,src,dest, ,rep0..repN), one row per metric and load
     * point, so they can be averaged/plotted like the classical CSVs.
     */
	private String buildQkdMetricsCsv() {
		List<List<QKDMetricsSnapshot>> snaps = new ArrayList<List<QKDMetricsSnapshot>>();
		int reps = 0;

		for (List<Simulation> load : simulations) {
			List<QKDMetricsSnapshot> row = new ArrayList<QKDMetricsSnapshot>();

			for (Simulation sim : load) {
				if (!(sim.getControlPlane() instanceof QKDControlPlane)) return null;
				row.add(((QKDControlPlane) sim.getControlPlane()).snapshotQkdMetrics(sim.getLoadPoint(), sim.getReplication()));
			}

			reps = Math.max(reps, row.size());
			snaps.add(row);
		}

		if (snaps.isEmpty() || reps == 0) return null;
		java.util.LinkedHashMap<String, QkdValue> metrics = new java.util.LinkedHashMap<String, QkdValue>();
		metrics.put("QKD blocking probability", m -> ratio(m.rejected, m.protectedAttempts));
		metrics.put("QKD blocking probability by classical resources", m -> ratio(m.blockedClassical, m.protectedAttempts));
		metrics.put("QKD blocking probability by QCh spectrum", m -> ratio(m.blockedSpectrum, m.protectedAttempts));
		metrics.put("QKD blocking probability by SKR", m -> ratio(m.blockedSkr, m.protectedAttempts));
		metrics.put("QKD blocking probability by PICh", m -> ratio(m.blockedPich, m.protectedAttempts));
		metrics.put("QKD blocking probability by lack of QTx/QRx", m -> ratio(m.blockedQtrx, m.protectedAttempts));
		metrics.put("QKD blocking probability by other", m -> ratio(m.blockedOther, m.protectedAttempts));
		metrics.put("QKD acceptance ratio", m -> ratio(m.accepted, m.protectedAttempts));
		metrics.put("Fraction of QKD-protected requests", m -> ratio(m.protectedAttempts, m.totalRequestAttempts));
		metrics.put("Classical candidates rejected to protect QKD (per request)", m -> ratio(m.classicalRejectionsByQkdImpact, m.totalRequestAttempts));
		metrics.put("Alternative QKD route ratio", m -> ratio(m.alternativeRouteAccepted, m.accepted));
		metrics.put("Mean admission SKR (Mbit/s)", m -> m.accepted > 0 ? m.meanAdmissionRawSkrMbps : Double.NaN);
		metrics.put("Minimum admission SKR (Mbit/s)", m -> m.accepted > 0 ? m.minimumAdmissionRawSkrMbps : Double.NaN);
		metrics.put("Mean admission QBER", m -> m.accepted > 0 ? m.meanAdmissionQber : Double.NaN);
		metrics.put("Maximum admission QBER", m -> m.accepted > 0 ? m.maximumAdmissionQber : Double.NaN);

		if (snaps.get(0).get(0).multiband) { // Parte 44: only with qkdPhysicalLayer.multiband
			metrics.put("Mean admission SpRS background probability", m -> m.accepted > 0 ? m.meanAdmissionRamanProbability : Double.NaN);
			metrics.put("Maximum admission SpRS background probability", m -> m.accepted > 0 ? m.maximumAdmissionRamanProbability : Double.NaN);
			metrics.put("Maximum admission SpRS power (W)", m -> m.accepted > 0 ? m.maximumAdmissionRamanPowerW : Double.NaN);
		}

		if (snaps.get(0).get(0).nli) { // Parte 45: only with multiband.nliModel = gn
			metrics.put("Mean admission NLI background probability", m -> m.accepted > 0 ? m.meanAdmissionNliProbability : Double.NaN);
			metrics.put("Maximum admission NLI background probability", m -> m.accepted > 0 ? m.maximumAdmissionNliProbability : Double.NaN);
		}

		metrics.put("Mean key demand (Mbit/s)", m -> m.accepted > 0 ? m.totalDemandMbps / m.accepted : Double.NaN);
		metrics.put("Mean active QChs", m -> m.meanActiveQchs);
		metrics.put("Mean active Q-TRx pairs", m -> m.meanActiveQTrxPairs);
		metrics.put("Mean active QKD sessions", m -> m.meanActiveSessions);
		metrics.put("Peak active QChs", m -> m.peakActiveQchs);
		metrics.put("Peak Q-TRx pairs", m -> m.peakQTrxPairs);
		metrics.put("Mean Q-TRx pairs per admitted session", m -> m.meanQTrxPerSession);
		metrics.put("Mean hops per admitted session", m -> m.meanHopsPerSession);
		metrics.put("Mean QKD route length (km)", m -> m.meanRouteKmPerSession);
		metrics.put("Planned trusted nodes", m -> m.plannedTrustedNodes);
		metrics.put("Planned QKD spans (directed links)", m -> m.plannedQkdSpans);
		metrics.put("QTND (km)", m -> m.qtndKm);
		// Part 26: shared PIChs (pichMode = segment) and QTx/QRx totals
		metrics.put("Mean open PIChs", m -> m.meanOpenPichs);
		metrics.put("Peak open PIChs", m -> m.peakOpenPichs < 0 ? Double.NaN : m.peakOpenPichs);
		metrics.put("Mean QTx in use (network)", m -> sumEquip(m, 0));
		metrics.put("Mean QRx in use (network)", m -> sumEquip(m, 1));

		StringBuilder sb = new StringBuilder("Metrics,LoadPoint,src,dest, ");

		for (int r = 0; r < reps; r++) sb.append(",rep").append(r);
		sb.append('\n');

		for (java.util.Map.Entry<String, QkdValue> e : metrics.entrySet()) {
			for (int lp = 0; lp < snaps.size(); lp++) {
				sb.append(e.getKey()).append(',').append(lp).append(",all,all, ");

				for (QKDMetricsSnapshot m : snaps.get(lp)) sb.append(',').append(e.getValue().of(m));
				sb.append('\n');
			}
		}

		sb.append('\n');
		// Part 26: QTx/QRx per QKD node (network nodes and in-line trusted nodes)
		java.util.TreeSet<String> qnodes = new java.util.TreeSet<String>();

		for (List<QKDMetricsSnapshot> row : snaps) for (QKDMetricsSnapshot m : row) qnodes.addAll(m.qEquipment.keySet());
		String[] eqNames = {"Mean QTx in use per node", "Mean QRx in use per node", "Peak QTx in use per node", "Peak QRx in use per node"};

		for (int k = 0; k < eqNames.length; k++) {
			for (String node : qnodes) {
				for (int lp = 0; lp < snaps.size(); lp++) {
					sb.append(eqNames[k]).append(',').append(lp).append(',').append(node).append(",all, ");

					for (QKDMetricsSnapshot m : snaps.get(lp)) {
						double[] v = m.qEquipment.get(node);
						sb.append(',').append(v == null ? 0.0 : v[k]);
					}

					sb.append('\n');
				}
			}
		}

		// per pair (only pairs with QKD-protected attempts)
		java.util.TreeSet<String> pairs = new java.util.TreeSet<String>();

		for (List<QKDMetricsSnapshot> row : snaps) for (QKDMetricsSnapshot m : row) pairs.addAll(m.perPair.keySet());

		for (String pair : pairs) {
			String[] sd = pair.split("\\|", 2);

			for (int lp = 0; lp < snaps.size(); lp++) {
				sb.append("QKD blocking probability per pair,").append(lp).append(',').append(sd[0]).append(',').append(sd.length > 1 ? sd[1] : "").append(", ");

				for (QKDMetricsSnapshot m : snaps.get(lp)) {
					long[] v = m.perPair.get(pair);
					sb.append(',').append(v == null ? Double.NaN : ratio(v[1], v[0]));
				}

				sb.append('\n');
			}
		}

		return sb.toString();
	}

	/** Parte 45: AlgorithmCost.csv (activeMetrics.AlgorithmCost), or null when the metric is off. */
	private String buildAlgorithmCostCsv() {
		StringBuilder sb = new StringBuilder(simulator.AlgorithmCostRecorder.header());
		boolean any = false;
		long requests = simulationRequest.getSimulationConfig().getRequests();

		for (List<Simulation> load : simulations) {
			for (Simulation sim : load) {
				if (sim.getCostRecorder() == null) continue;
				any = true;
				sb.append(sim.getCostRecorder().rows(sim.getLoadPoint(), sim.getReplication(), requests));
			}
		}

		return any ? sb.toString() : null;
	}

	private String buildQkdStatisticsCsv() {
		boolean anyQkd = false;
		StringBuilder sb = new StringBuilder();
		sb.append("LoadPoint,Replication,TotalRequestAttempts,ProtectedAttempts,Accepted,Rejected,Released,"
				+ "ExistingSessionImpactChecks,ClassicalRejectionsByQKDImpact,ActiveRefreshes,"
				+ "PrecommitAdmissionChecks,PrecommitAdmissionRejections,"
				+ "ActivatedQChs,DeactivatedQChs,TDMReuses,CapacityFailures,PeakActiveQChs,PeakQTRxPairs,"
				+ "AcceptedDemand_Mbps,MeanAdmissionRawSKR_Mbps,MinimumAdmissionRawSKR_Mbps,"
				+ "MeanAdmissionQBER,MaximumAdmissionQBER,MaximumAdmissionPXT_W,"
				+ "QKDAcceptanceRatio,QKDBlockingProbability,MeanActiveQChs,MeanActiveQTRxPairs,"
				+ "MeanActiveQKDSessions,QTND_km,WarmupArrivals\n");

		for (List<Simulation> load : simulations) {
			for (Simulation sim : load) {
				if (!(sim.getControlPlane() instanceof QKDControlPlane)) continue;
				anyQkd = true;
				QKDMetricsSnapshot m = ((QKDControlPlane)sim.getControlPlane())
						.snapshotQkdMetrics(sim.getLoadPoint(), sim.getReplication());
				sb.append(m.loadPoint).append(',').append(m.replication).append(',')
				  .append(m.totalRequestAttempts).append(',').append(m.protectedAttempts).append(',').append(m.accepted).append(',')
				  .append(m.rejected).append(',').append(m.released).append(',')
				  .append(m.existingSessionImpactChecks).append(',')
				  .append(m.classicalRejectionsByQkdImpact).append(',').append(m.activeRefreshes).append(',')
				  .append(m.precommitAdmissionChecks).append(',').append(m.precommitAdmissionRejections).append(',')
				  .append(m.activatedQchs).append(',').append(m.deactivatedQchs).append(',')
				  .append(m.tdmReuses).append(',').append(m.capacityFailures).append(',')
				  .append(m.peakActiveQchs).append(',').append(m.peakQTrxPairs).append(',')
				  .append(m.totalDemandMbps).append(',').append(m.meanAdmissionRawSkrMbps).append(',')
				  .append(m.minimumAdmissionRawSkrMbps).append(',').append(m.meanAdmissionQber).append(',')
				  .append(m.maximumAdmissionQber).append(',').append(m.maximumAdmissionPxtW).append(',')
				  .append(m.acceptanceRatio).append(',').append(m.blockingProbability).append(',')
				  .append(m.meanActiveQchs).append(',').append(m.meanActiveQTrxPairs).append(',')
				  .append(m.meanActiveSessions).append(',').append(m.qtndKm).append(',')
				  .append(m.warmupArrivals).append('\n');
			}
		}

		return anyQkd ? sb.toString() : null;
	}
}
