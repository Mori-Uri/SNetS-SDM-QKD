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
public final class QKDThesisSNetSPhysicalLayerVerification {

	private QKDThesisSNetSPhysicalLayerVerification() {
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
		double gn = mesh.getPhysicalLayer().getGnli2(target, link, power, bandwidth,
				power / bandwidth, f, circuits, modulation);
		double normalized = gn * bandwidth / (power * power * power);
		double db = 10.0 * Math.log10(normalized);

		System.out.printf(Locale.US,
				"SNetS engine Eq3.22: channels=%d, B=%.3f GHz, PSD=%.9g, normalized=%.9f dB%n",
				circuits.size(), bandwidth / 1e9, gn, db);

		// Reference thesis Fig 22 approx 28 dB for one 100-km span.
		// 0.7 dB accepts the small beta2 difference arising from D/lambda.
		if (!(Double.isFinite(db) && Math.abs(db - 28.0860340391) < 0.7)) {
			throw new AssertionError("SNetS Habibi NLI differs from thesis Table 6: " + db);
		}

		System.out.println("spans,engine_egn_normalized_db");

		for (int spans : new int[]{1, 2, 5, 10, 20, 50}) {
			double accumulated = normalized * spans;
			double accumulatedDb = 10.0 * Math.log10(accumulated);

			System.out.printf(Locale.US, "%d,%.9f%n", spans, accumulatedDb);
		}

		System.out.println("PASS QKDThesisSNetSPhysicalLayerVerification");
	}
}
