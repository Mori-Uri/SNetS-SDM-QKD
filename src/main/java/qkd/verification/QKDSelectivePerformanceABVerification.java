package qkd.verification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Locale;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.Core;
import network.Link;
import network.Mesh;
import network.Node;
import network.Pair;
import network.PhysicalLayer;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.NodeConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.ModulationConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.TrafficConfig;

/**
 * Controlled SNetS fixture with the same 15-channel spectrum as Table 6 of the
 * Lacerda Junior thesis. Calls the ACTUAL PhysicalLayer.getGnli2 entry point.
 */
public final class QKDSelectivePerformanceABVerification {

	private QKDSelectivePerformanceABVerification() {
	}

	private static Circuit createCircuit(Mesh mesh, Modulation modulation, Route route,
			int firstSlot, double power) {
		Circuit circuit = new Circuit();
		circuit.setRoute(route);
		circuit.setPair(new Pair(route.getSource(), route.getDestination()));
		circuit.setModulation(modulation);
		circuit.setIndexCore(0);
		circuit.setGuardBand(0);
		circuit.setSpectrumAssigned(new int[]{firstSlot, firstSlot + 19});
		circuit.setLaunchPowerLinear(power);

		RequestForConnection request = new RequestForConnection();
		request.setPair(circuit.getPair());
		request.setRequiredBitRate(modulation.potentialBitRate(20));
		circuit.addRequest(request);

		return circuit;
	}

	public static void main(String[] args) {
		final double slotHz = 1.6e9;
		final double center = 299792458.0 / 1550e-9;
		final int totalSlots = 336;
		final double power = 1e-3;

		NetworkConfig network = new NetworkConfig();
		network.setNodes(Arrays.asList(new NodeConfig("A", 4, 4, 0),
				new NodeConfig("B", 4, 4, 0)));
		network.setCores(Arrays.asList(new CoreConfig(0, new ArrayList<Integer>())));
		network.setLinks(Arrays.asList(new LinkConfig("A", "B", totalSlots, slotHz, 100.0)));
		network.setGuardBand(0);
		network.setModulations(Arrays.asList(
				new ModulationConfig("QPSK", 4.0, 10000.0, -100.0, -10.0)));

		PhysicalLayerConfig physical = new PhysicalLayerConfig();
		physical.setPhysicalLayerModel(PhysicalLayer.MODEL_HABIBI);
		physical.setActiveASE(false);
		physical.setActiveNLI(true);
		physical.setActiveXT(false);
		physical.setActiveQoT(true);
		physical.setPower(0.0);
		physical.setRateOfFEC(0.0);
		physical.setSpanLength(100.0);
		physical.setFiberLoss(0.2);
		physical.setFiberNonlinearity(1.3e-3);
		physical.setFiberDispersion(16.7e-6);
		physical.setCenterFrequency(center);
		physical.setConstantOfPlanck(6.626e-34);
		physical.setNoiseFigureOfOpticalAmplifier(5.0);
		physical.setPowerSaturationOfOpticalAmplifier(20.0);
		physical.setTypeOfAmplifierGain(PhysicalLayer.AMP_GAIN_FIXED);
		physical.setAmplificationFrequency(center);
		physical.setSwitchInsertionLoss(0.0);
		physical.setPolarizationModes(2.0);
		physical.setPropagationConstant(5.9e6);
		physical.setBendingRadius(0.05);
		physical.setCouplingCoefficient(1e-4);
		physical.setCorePitch(41e-6);

		Mesh mesh = new Mesh(network, new TrafficConfig(), physical,
				new OthersConfig(), null, new Util());
		Link link = mesh.getLinkList().get(0);
		Route route = new Route(new Vector<Node>(Arrays.asList(
				mesh.getNodeList().get(0), mesh.getNodeList().get(1))));
		Modulation modulation = mesh.getAvaliableModulations().get(0);

		TreeSet<Circuit> circuits = new TreeSet<Circuit>();
		Circuit target = null;

		for (int i = 0; i < 15; i++) {
			Circuit current = createCircuit(mesh, modulation, route, 1 + 21*i, power);
			circuits.add(current);

			if (i == 7) {
				target = current;
			}
		}

		if (circuits.size() != 15 || target == null) {
			throw new AssertionError("invalid 15-channel fixture");
		}

		double bandwidth = modulation.getBandwidthFromBitRate(target.getRequiredBitRate());
		double lower = center - totalSlots * slotHz / 2.0;
		int[] slots = target.getSpectrumAssigned();
		double f = lower + slotHz * (slots[0] + slots[1] - 1) / 2.0;
        // Repeated fixed geometry yields many identical XCI pairs; compare two JVM runs
        // with the exact same fixture. Only cache flags differ.
        final int iterations = Integer.getInteger("snets.abIterations", 120).intValue();
        double[] values = new double[iterations];
        final long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            values[i] = mesh.getPhysicalLayer().getGnli2(target, link, power, bandwidth,
                    power / bandwidth, f, circuits, modulation);
            if (Double.doubleToRawLongBits(values[i]) != Double.doubleToRawLongBits(values[0]))
                throw new AssertionError("NLI changed in unchanged fixture at iteration " + i);
        }
        long elapsed = System.nanoTime() - start;
        // A/B mutation matrix: the cached geometry must be invalidated by frequency
        // and bandwidth changes, and power changes must never be memoized.
        Circuit interferer = circuits.first();
        double initialPower = interferer.getLaunchPowerLinear();
        int[] initialSlots = interferer.getSpectrumAssigned().clone();
        RequestForConnection initialRequest = interferer.getRequests().get(0);
        double initialBitrate = initialRequest.getRequiredBitRate();
        interferer.setLaunchPowerLinear(initialPower * 1.10);
        double changedPower = mesh.getPhysicalLayer().getGnli2(target, link, power, bandwidth,
                power / bandwidth, f, circuits, modulation);
        interferer.setLaunchPowerLinear(initialPower);
        interferer.setSpectrumAssigned(new int[]{initialSlots[0] + 1, initialSlots[1] + 1});
        double changedFrequency = mesh.getPhysicalLayer().getGnli2(target, link, power, bandwidth,
                power / bandwidth, f, circuits, modulation);
        interferer.setSpectrumAssigned(initialSlots);
        initialRequest.setRequiredBitRate(initialBitrate * 1.01);
        double changedBandwidth = mesh.getPhysicalLayer().getGnli2(target, link, power, bandwidth,
                power / bandwidth, f, circuits, modulation);
        initialRequest.setRequiredBitRate(initialBitrate);
        double restored = mesh.getPhysicalLayer().getGnli2(target, link, power, bandwidth,
                power / bandwidth, f, circuits, modulation);
        if (Double.doubleToRawLongBits(restored) != Double.doubleToRawLongBits(values[0]))
            throw new AssertionError("restored geometry must restore identical NLI");
        // No timing information in METRIC fields: fc /b compares these exactly.
        System.out.printf(Locale.US, "METRIC power_change_bits=%s%n", Double.toHexString(changedPower));
        System.out.printf(Locale.US, "METRIC frequency_change_bits=%s%n", Double.toHexString(changedFrequency));
        System.out.printf(Locale.US, "METRIC bandwidth_change_bits=%s%n", Double.toHexString(changedBandwidth));
        System.out.printf(Locale.US, "METRIC restored_bits=%s%n", Double.toHexString(restored));
        System.out.printf(Locale.US, "METRIC gnli_bits=%s%n", Double.toHexString(values[0]));
        System.out.printf(Locale.US, "METRIC bandwidth_bits=%s%n", Double.toHexString(bandwidth));
        System.out.printf(Locale.US, "METRIC required_key_mbps=%.9f%n", 256.0 / 0.000064 / 1.0e6);
        System.out.printf(Locale.US, "TIMING n=%d elapsed_ms=%.3f%n", iterations, elapsed / 1.0e6);
        System.out.println("PASS QKDSelectivePerformanceABVerification");
    }
}
