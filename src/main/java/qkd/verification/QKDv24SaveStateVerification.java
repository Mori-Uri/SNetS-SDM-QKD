package qkd.verification;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.Arrays;

import network.Mesh;
import qkd.multiband.ClassicalPowerEvolution;
import qkd.physical.QKDPhysicalLayer;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;
import simulationControl.parsers.NetworkConfig.ModulationConfig;
import simulationControl.parsers.NetworkConfig.NodeConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.TrafficConfig;
import simulator.EventMachine;
import simulator.SaveStateStore;
import simulator.Simulation;
import simulator.Simulator;
import simulator.eventListeners.ArriveRequestForConexionListener;

/** Checkpoint round trip without touching the user's simulation files. */
public final class QKDv24SaveStateVerification {

	private QKDv24SaveStateVerification() { }

	public static void main(String[] args) throws Exception {
		NetworkConfig net = new NetworkConfig();
		net.setNodes(Arrays.asList(new NodeConfig("A", 4, 4, 0), new NodeConfig("B", 4, 4, 0)));
		net.setCores(Arrays.asList(new CoreConfig(0, Arrays.asList(1)),
				new CoreConfig(1, Arrays.asList(0))));
		net.setLinks(Arrays.asList(new LinkConfig("A", "B", 48, 12.5e9, 80.0)));
		net.setModulations(Arrays.asList(new ModulationConfig("QPSK", 4.0, 10000.0, -100.0, -10.0)));

		PhysicalLayerConfig physical = new PhysicalLayerConfig();
		physical.setPhysicalLayerModel(network.PhysicalLayer.MODEL_JOHANNISSON);
		physical.setPower(0.0);
		physical.setSpanLength(80.0);
		physical.setFiberLoss(0.2);
		physical.setFiberNonlinearity(1.3e-3);
		physical.setFiberDispersion(16.7e-6);
		physical.setCenterFrequency(193.4e12);
		physical.setConstantOfPlanck(6.62607015e-34);
		physical.setNoiseFigureOfOpticalAmplifier(5.0);
		physical.setPowerSaturationOfOpticalAmplifier(18.0);
		physical.setAmplificationFrequency(193.4e12);
		physical.setMuxInsertionLoss(3.0);
		physical.setDemuxInsertionLoss(5.0);
		physical.setPolarizationModes(2.0);

		Util shared = new Util();
		Mesh mesh = new Mesh(net, new TrafficConfig(), physical, new OthersConfig(), null, shared);
		mesh.getPhysicalLayer().enableLongitudinalAmplifierQoT(
				new ClassicalPowerEvolution(80.0, 16.0),
				193.4e12 - 48.0 * 12.5e9 / 2.0,
				0.2 * Math.log(10.0) / 10.0);

		SimulationConfig config = new SimulationConfig();
		config.setRequests(10);
		config.setGrooming("notrafficgrooming");
		config.setIntegratedRmlsa("spcsav2");
		config.setRouting("djk");
		config.setkRouting("newksp");
		config.setSpectrumAssignment("randomfit");
		config.setCoreAndSpectrumAssignment("qabne");
		config.setModulationSelection("modulationbyqotv2");
		config.setReallocation("fsalfav1");
		config.setPowerAssignment("cpsd");
		config.setRegeneratorAssignment("aar");
		Simulation simulation = new Simulation(config, mesh, 0, 0, shared);
		EventMachine machine = new EventMachine();
		ArriveRequestForConexionListener arrival = new ArriveRequestForConexionListener(machine, simulation);

		File location = Files.createTempDirectory("snets-v24-state-test-").toFile();

		try {
			Constructor<SaveStateStore> constructor = SaveStateStore.class.getDeclaredConstructor(
					File.class, String.class, long.class);
			constructor.setAccessible(true);
			SaveStateStore states = constructor.newInstance(location, "test-hash", 1000000L);
			states.save(simulation, machine, arrival, false);
			SaveStateStore.State state = states.load(0, 0, shared);

			if (state == null || state.isFinished()) {
				throw new AssertionError("partial checkpoint not restored");
			}

			java.lang.reflect.Field f = network.PhysicalLayer.class.getDeclaredField("longitudinalAmplifierTemplate");
			f.setAccessible(true);

			if (f.get(state.getSimulation().getMesh().getPhysicalLayer()) == null) {
				throw new AssertionError("longitudinal model lost after save/resume");
			}

			// Empty event list completes deterministically after an actual resume.
			Simulator simulator = new Simulator(state.getSimulation());
			simulator.resume(state);
			states.save(simulation, machine, arrival, true);
			SaveStateStore.State finished = states.load(0, 0, shared);

			if (finished == null || !finished.isFinished()) {
				throw new AssertionError("finished checkpoint cannot be reused");
			}

			System.out.println("PASS v24 partial/finished checkpoint serialization and resume");
		} finally {
			File[] children = location.listFiles();

			if (children != null) for (File file : children) file.delete();
			location.delete();
		}
	}
}
