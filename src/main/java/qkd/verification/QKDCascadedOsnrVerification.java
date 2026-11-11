package qkd.verification;

import java.util.Arrays;
import java.util.Vector;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.Mesh;
import network.Node;
import network.Pair;
import network.PhysicalLayer;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;
import simulationControl.parsers.NetworkConfig.ModulationConfig;
import simulationControl.parsers.NetworkConfig.NodeConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.TrafficConfig;

/** Regression for the Vale/Almeida (2019) cascaded EDFA/GN physical layer. */
public final class QKDCascadedOsnrVerification {

	private QKDCascadedOsnrVerification() { }

	private static void check(boolean valid, String message) {
		if (!valid) throw new AssertionError(message);
	}

	private static void close(double a, double b, String message) {
		double tol = 1e-10 * Math.max(1.0e-12, Math.max(Math.abs(a), Math.abs(b)));
		check(Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) <= tol,
				message + " " + a + " != " + b);
	}

	private static PhysicalLayerConfig config(int model, boolean sciIntegral,
			boolean xciIntegral, boolean ase, boolean nli, boolean saturated) {

		PhysicalLayerConfig p = new PhysicalLayerConfig();
		p.setPhysicalLayerModel(model);
		p.setJohannissonNliMode((sciIntegral || xciIntegral) ? "integral" : "approximate");
		p.setActiveASE(ase);
		p.setActiveNLI(nli);
		p.setActiveXT(false);
		p.setPower(0.0);
		p.setSpanLength(80.0);
		p.setFiberLoss(0.2);
		p.setFiberNonlinearity(1.3e-3);
		p.setFiberDispersion(16.7e-6);
		p.setCenterFrequency(193.4e12);
		p.setConstantOfPlanck(6.62607015e-34);
		p.setNoiseFigureOfOpticalAmplifier(5.0);
		p.setPowerSaturationOfOpticalAmplifier(18.0);
		p.setAmplificationFrequency(193.4e12);
		p.setSwitchInsertionLoss(1.0);
		p.setPolarizationModes(2.0);
		p.setTypeOfAmplifierGain(saturated
				? PhysicalLayer.AMP_GAIN_SATURATED : PhysicalLayer.AMP_GAIN_FIXED);

		return p;
	}

	private static void verify(int model, boolean sciIntegral, boolean xciIntegral,
			double secondLengthKm, boolean saturated, boolean ase, boolean nli) {

		NetworkConfig n = new NetworkConfig();
		n.setNodes(Arrays.asList(new NodeConfig("A", 4, 4, 0),
				new NodeConfig("B", 4, 4, 0), new NodeConfig("C", 4, 4, 0)));
		n.setCores(Arrays.asList(new CoreConfig(0, Arrays.<Integer>asList(1)),
				new CoreConfig(1, Arrays.<Integer>asList(0))));
		n.setLinks(Arrays.asList(new LinkConfig("A", "B", 64, 12.5e9, 80.0),
				new LinkConfig("B", "C", 64, 12.5e9, secondLengthKm)));
		n.setModulations(Arrays.asList(
				new ModulationConfig("QPSK", 4.0, 10000.0, -100.0, -10.0)));

		Mesh mesh = new Mesh(n, new TrafficConfig(),
				config(model, sciIntegral, xciIntegral, ase, nli, saturated),
				new OthersConfig(), null, new Util());
		PhysicalLayer layer = mesh.getPhysicalLayer();
		Modulation m = mesh.getAvaliableModulations().get(0);
		Vector<Node> nodes = new Vector<Node>(mesh.getNodeList());
		Route route = new Route(nodes);
		Circuit circuit = new Circuit();
		circuit.setRoute(route);
		circuit.setPair(new Pair(route.getSource(), route.getDestination()));
		circuit.setModulation(m);
		circuit.setIndexCore(0);
		circuit.setSpectrumAssigned(new int[] {12, 15});
		circuit.setLaunchPowerLinear(1.0e-3);
		RequestForConnection r = new RequestForConnection();
		r.setPair(circuit.getPair());
		r.setRequiredBitRate(m.potentialBitRate(4));
		circuit.addRequest(r);

		double rate = circuit.getRequiredBitRate();
		double power1 = layer.computePowerThatMaximizesOSNR(circuit, rate, route,
				0, 2, m, 0, circuit.getSpectrumAssigned());
		double repeatedPower = layer.computePowerThatMaximizesOSNR(circuit, rate, route,
				0, 2, m, 0, circuit.getSpectrumAssigned());

		close(power1, repeatedPower, "single estimator must be deterministic");
		check(circuit.getLaunchPowerLinear() == 1.0e-3,
				"analytical power estimator must not mutate the tested circuit");
		check(Double.isFinite(power1) && power1 >= 1.0e-9 && power1 <= 0.1,
				"optimum must be finite and bounded");

		double snr = layer.computeOSNRSegment(circuit, route, 0, 2,
				m, 0, circuit.getSpectrumAssigned(), null, false);
		check(Double.isFinite(snr) && snr >= 0.0,
				"cascaded OSNR must be finite and nonnegative");

		// Independent OSNR power-law check in a gain-fixed, ASE+SCI case:
		// the analytic maximum must improve on nearby launch powers.
		if (!saturated && ase && nli && power1 > 1.0e-8 && power1 < 0.05) {
			circuit.setLaunchPowerLinear(power1);
			double peak = layer.computeOSNRSegment(circuit, route, 0, 2,
					m, 0, circuit.getSpectrumAssigned(), null, false);
			circuit.setLaunchPowerLinear(power1 * 0.8);
			double left = layer.computeOSNRSegment(circuit, route, 0, 2,
					m, 0, circuit.getSpectrumAssigned(), null, false);
			circuit.setLaunchPowerLinear(power1 * 1.2);
			double right = layer.computeOSNRSegment(circuit, route, 0, 2,
					m, 0, circuit.getSpectrumAssigned(), null, false);
			check(peak + 1e-12 >= left && peak + 1e-12 >= right,
					"power coefficient inconsistent with cascaded OSNR: "
					+ peak + ", " + left + ", " + right);
		}

		if (!saturated && ase && nli && model == PhysicalLayer.MODEL_JOHANNISSON
				&& secondLengthKm == 95.0 && !sciIntegral && !xciIntegral) {
			double beforeCheck = circuit.getLaunchPowerLinear();
			double checked = layer.computeMaximumPower(circuit, rate, route,
					0, 2, m, 0, circuit.getSpectrumAssigned());
			check(Double.isFinite(checked) && checked > 0.0 && checked <= 0.1,
					"computeMaximumPower refinement returned invalid power");
			check(circuit.getLaunchPowerLinear() == beforeCheck,
					"power optimizer must restore candidate launch power");
		}

		if (!saturated && ase && nli) {
			// Add an actual same-core interferer on each link. Its XCI should
			// decrease OSNR but must not change the analytic cubic self term.
			circuit.setLaunchPowerLinear(1.0e-3);
			double withoutXci = layer.computeOSNRSegment(circuit, route, 0, 2,
					m, 0, circuit.getSpectrumAssigned(), null, false);
			Circuit aggressor = new Circuit();
			aggressor.setRoute(route);
			aggressor.setPair(circuit.getPair());
			aggressor.setModulation(m);
			aggressor.setIndexCore(0);
			aggressor.setSpectrumAssigned(new int[] {30, 33});
			aggressor.setLaunchPowerLinear(0.8e-3);
			RequestForConnection ri = new RequestForConnection();
			ri.setPair(aggressor.getPair());
			ri.setRequiredBitRate(m.potentialBitRate(4));
			aggressor.addRequest(ri);

			for (network.Link link : route.getLinkList()) {
				link.getCore(0).addCircuit(aggressor);
			}

			double withXci = layer.computeOSNRSegment(circuit, route, 0, 2,
					m, 0, circuit.getSpectrumAssigned(), null, false);
			check(withXci < withoutXci,
					"same-core interferer must decrease cascaded OSNR");
			double maxWithXci = layer.computePowerThatMaximizesOSNR(circuit,
					rate, route, 0, 2, m, 0, circuit.getSpectrumAssigned());
			close(power1, maxWithXci,
					"linear XCI must not change constant-gain cubic optimum");
		}

		System.out.printf(java.util.Locale.US,
				"PASS model=%d SCIintegral=%s XCIintegral=%s L2=%.1f sat=%s ASE=%s NLI=%s P=%.9g W OSNR=%.5f dB%n",
				model, sciIntegral, xciIntegral, secondLengthKm, saturated, ase, nli,
				power1, 10.0 * Math.log10(Math.max(snr, 1e-30)));
	}

	public static void main(String[] args) {
		for (int model : new int[] {PhysicalLayer.MODEL_JOHANNISSON,
									 PhysicalLayer.MODEL_HABIBI}) {
			for (double secondLengthKm : new double[] {40.0, 95.0, 160.0}) {
				for (boolean saturated : new boolean[] {false, true}) {
					verify(model, false, false, secondLengthKm, saturated, true, true);
				}
			}
		}

		verify(PhysicalLayer.MODEL_JOHANNISSON, true, true, 95.0,
				false, true, true);
		verify(PhysicalLayer.MODEL_JOHANNISSON, false, true, 95.0,
				false, true, true);
		verify(PhysicalLayer.MODEL_JOHANNISSON, true, false, 95.0,
				false, true, true);
		verify(PhysicalLayer.MODEL_HABIBI, false, false, 95.0,
				false, false, true);
		verify(PhysicalLayer.MODEL_HABIBI, false, false, 95.0,
				false, true, false);

		// Numerical verification of the shared generic cascade state.
		network.OpticalNoiseCascade state = new network.OpticalNoiseCascade(10.0);
		state.addAse(1.0);
		state.addNli(2.0);
		state.scale(0.5);
		state.addAse(0.5);
		close(state.getSignalPsd(), 5.0, "signal cascade");
		close(state.getAsePsd(), 1.0, "ASE cascade");
		close(state.getNliPsd(), 1.0, "NLI cascade");

		System.out.println("PASS QKDCascadedOsnrVerification");
	}
}
