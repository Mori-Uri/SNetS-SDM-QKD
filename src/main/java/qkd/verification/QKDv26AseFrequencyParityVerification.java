package qkd.verification;

import java.util.Arrays;
import java.util.Locale;
import java.util.Vector;
import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ClassicalMultiBand;
import qkd.multiband.MultiBandEdFA;
import network.Mesh;
import network.Node;
import network.Pair;
import network.PhysicalLayer;
import qkd.multiband.ClassicalPowerEvolution;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.TrafficConfig;

/** Differential test of classic vs longitudinal DCh QoT at matching EDFA settings. */
public final class QKDv26AseFrequencyParityVerification {
	private QKDv26AseFrequencyParityVerification() { }

	private static double run(int hops, double secondKm, boolean nli,
			boolean saturated, int model, double referenceHz,
			boolean longitudinal, double a1, boolean multiBand,
			boolean sciIntegral, boolean xciIntegral) {
		return run(hops, secondKm, nli, saturated, model, referenceHz,
				longitudinal, a1, multiBand, sciIntegral, xciIntegral, true, false);
	}

	private static double run(int hops, double secondKm, boolean nli,
			boolean saturated, int model, double referenceHz,
			boolean longitudinal, double a1, boolean multiBand,
			boolean sciIntegral, boolean xciIntegral, boolean spectralProfile) {
		return run(hops, secondKm, nli, saturated, model, referenceHz,
				longitudinal, a1, multiBand, sciIntegral, xciIntegral,
				spectralProfile, false);
	}

	private static double run(int hops, double secondKm, boolean nli,
			boolean saturated, int model, double referenceHz,
			boolean longitudinal, double a1, boolean multiBand,
			boolean sciIntegral, boolean xciIntegral, boolean spectralProfile,
			boolean withInterferer) {
		NetworkConfig n = new NetworkConfig();
		n.setNodes(Arrays.asList(new NetworkConfig.NodeConfig("A", 4, 4, 0),
				new NetworkConfig.NodeConfig("B", 4, 4, 0),
				new NetworkConfig.NodeConfig("C", 4, 4, 0)));
		n.setCores(Arrays.asList(new NetworkConfig.CoreConfig(0, Arrays.asList(1)),
				new NetworkConfig.CoreConfig(1, Arrays.asList(0))));
		n.setLinks(Arrays.asList(new NetworkConfig.LinkConfig("A", "B", 64, 12.5e9, 80.0),
				new NetworkConfig.LinkConfig("B", "C", 64, 12.5e9, secondKm)));
		n.setModulations(Arrays.asList(new NetworkConfig.ModulationConfig(
				"QPSK", 4.0, 10000.0, -100.0, -10.0)));
		PhysicalLayerConfig p = new PhysicalLayerConfig();
		p.setPhysicalLayerModel(model);
		p.setJohannissonNliMode((sciIntegral || xciIntegral) ? "integral" : "approximate");
		p.setPower(0.0);
		p.setSpanLength(40.0);
		p.setFiberLoss(0.2);
		p.setFiberNonlinearity(0.0013);
		p.setFiberDispersion(16.7e-6);
		p.setCenterFrequency(193.4e12);
		p.setConstantOfPlanck(6.62607015e-34);
		p.setNoiseFigureOfOpticalAmplifier(5.0);
		p.setPowerSaturationOfOpticalAmplifier(18.0);
		p.setNoiseFactorModelParameterA1(a1);
		p.setNoiseFactorModelParameterA2(0.005);
		p.setTypeOfAmplifierGain(saturated ? PhysicalLayer.AMP_GAIN_SATURATED : PhysicalLayer.AMP_GAIN_FIXED);
		p.setAmplificationFrequency(referenceHz);
		p.setActiveASE(true);
		p.setActiveNLI(nli);
		p.setActiveXT(false);
		p.setPolarizationModes(2.0);
		p.setMuxInsertionLoss(3.0);
		p.setDemuxInsertionLoss(5.0);

		if (multiBand && !longitudinal && spectralProfile) {
			ClassicalMultiBand.Config bands = new ClassicalMultiBand.Config();
			bands.setEnabled(true);
			bands.setAttenuationModel("constant");
			bands.setIsrs(false);
			bands.setGammaScalesWithFrequency(false);
			bands.setDispersionSlopePsNm2Km(0.0);
			p.setMultiBand(bands);
		}

		Mesh mesh = new Mesh(n, new TrafficConfig(), p, new OthersConfig(), null, new Util());
		PhysicalLayer layer = mesh.getPhysicalLayer();

		if (multiBand && !spectralProfile)
			layer.setMultibandAseCarrierMode(true);

		if (longitudinal) {
			ClassicalPowerEvolution template = new ClassicalPowerEvolution(40.0, 8.0,
					saturated ? ClassicalPowerEvolution.GainModel.SATURATED
							: ClassicalPowerEvolution.GainModel.IDEAL,
					18.0, 0.0, 5.0, 100.0);

			if (multiBand && spectralProfile) {
				template = template.withMultiBandEdFA(new MultiBandEdFA(Arrays.asList(
						new MultiBandEdFA.Band("L", 186e12, 192e12, 8.0, 6.0),
						new MultiBandEdFA.Band("C", 192e12, 195e12, 8.0, 5.0)),
						18.0, saturated));
			}

			layer.enableLongitudinalAmplifierQoT(template,
					193.4e12 - 64 * 12.5e9 / 2.0, 0.2 * Math.log(10.0) / 10.0,
					multiBand);
		}

		Vector<Node> nodes = new Vector<Node>();
		nodes.add(mesh.getNodeList().get(0));
		nodes.add(mesh.getNodeList().get(1));

		if (hops == 2) nodes.add(mesh.getNodeList().get(2));
		Route route = new Route(nodes);
		Modulation mod = mesh.getAvaliableModulations().get(0);
		Circuit c = new Circuit();
		c.setRoute(route);
		c.setPair(new Pair(route.getSource(), route.getDestination()));
		c.setModulation(mod);
		c.setIndexCore(0);
		c.setSpectrumAssigned(new int[] {12, 15});
		c.setLaunchPowerLinear(1e-3);
		RequestForConnection req = new RequestForConnection();
		req.setPair(c.getPair());
		req.setRequiredBitRate(mod.potentialBitRate(4));
		c.addRequest(req);

		if (withInterferer) {
			Circuit aggressor = new Circuit();
			aggressor.setRoute(route);
			aggressor.setPair(c.getPair());
			aggressor.setModulation(mod);
			aggressor.setIndexCore(0);
			aggressor.setSpectrumAssigned(new int[] {30, 33});
			aggressor.setLaunchPowerLinear(0.8e-3);
			RequestForConnection other = new RequestForConnection();
			other.setPair(c.getPair());
			other.setRequiredBitRate(mod.potentialBitRate(4));
			aggressor.addRequest(other);

			for (network.Link link : route.getLinkList())
				link.getCore(0).addCircuit(aggressor);
		}

		return layer.computeOSNRSegment(c, route, 0, hops, mod, 0,
				c.getSpectrumAssigned(), null, false);
	}

	private static void near(double a, double b, double relative, String label) {
		if (!Double.isFinite(a) || !Double.isFinite(b)
				|| Math.abs(a-b) > relative*Math.max(1e-30,Math.abs(a))) {
			throw new AssertionError(label+": "+a+" vs "+b);
		}
	}

	public static void main(String[] args) {
		int compared = 0;
		double maximumDelta = 0.0;

		for (int model : new int[] {PhysicalLayer.MODEL_JOHANNISSON, PhysicalLayer.MODEL_HABIBI}) {
			for (boolean nli : new boolean[] {false, true}) {
				for (boolean sat : new boolean[] {false, true}) {
					for (int hops : new int[] {1, 2}) {
						for (boolean integral : new boolean[] {false, true}) {
							double a = run(hops, 95.0, nli, sat, model, 193.85e12,
									false, 4.0, false, integral, integral);
							double b = run(hops, 95.0, nli, sat, model, 193.85e12,
									true, 4.0, false, integral, integral);
							near(a, b, 1e-11, "classical-longitudinal parity, model=" + model
									+ " nli=" + nli + " sat=" + sat + " hops=" + hops
									+ " integral=" + integral);
							compared++;
							maximumDelta = Math.max(maximumDelta, Math.abs(a-b)/a);
						}
					}
				}
			}
		}

		// XCI must use the same physical interferer and per-span model too.
		for (int model : new int[] {PhysicalLayer.MODEL_JOHANNISSON, PhysicalLayer.MODEL_HABIBI}) {
			for (boolean sat : new boolean[] {false, true}) {
				for (int hops : new int[] {1, 2}) {
					for (boolean integral : new boolean[] {false, true}) {
						double a = run(hops, 95.0, true, sat, model, 193.85e12,
								false, 4.0, false, integral, integral, true, true);
						double b = run(hops, 95.0, true, sat, model, 193.85e12,
								true, 4.0, false, integral, integral, true, true);
						near(a, b, 1e-11, "XCI parity, model=" + model
								+ " sat=" + sat + " hops=" + hops);
						compared++;
						maximumDelta = Math.max(maximumDelta, Math.abs(a-b)/a);
					}
				}
			}
		}

		// Single-band: a different configured ASE reference changes the SNR
		// in the same way for both calculation engines, independent of real fi.
		final double reference = 193.85e12;
		final double alternate = 192.50e12;

		for (boolean longitudinal : new boolean[] {false, true}) {
			double a = run(1, 95.0, false, false, PhysicalLayer.MODEL_JOHANNISSON,
					reference, longitudinal, 0.0, false, false, false);
			double b = run(1, 95.0, false, false, PhysicalLayer.MODEL_JOHANNISSON,
					alternate, longitudinal, 0.0, false, false, false);
			near(b/a, reference/alternate, 1e-12,
					"single-band ASE must use amplificationFrequency, longitudinal=" + longitudinal);
		}

		// Multi-band: changing amplificationFrequency MUST NOT change ASE.
		// The true slot-derived centre frequency is used in both engines.
		for (boolean longitudinal : new boolean[] {false, true}) {
			double a = run(1, 95.0, false, false, PhysicalLayer.MODEL_JOHANNISSON,
					reference, longitudinal, 0.0, true, false, false);
			double b = run(1, 95.0, false, false, PhysicalLayer.MODEL_JOHANNISSON,
					alternate, longitudinal, 0.0, true, false, false);
			near(a, b, 1e-13,
					"multi-band ASE must ignore amplificationFrequency, longitudinal=" + longitudinal);
		}

		// MB/MB-SDM remain channel-resolved without a calibrated spectral
		// EDFA: the architecture flag alone selects the carrier frequency.
		for (boolean longitudinal : new boolean[] {false, true}) {
			double a = run(1, 95.0, false, false, PhysicalLayer.MODEL_JOHANNISSON,
					reference, longitudinal, 0.0, true, false, false, false);
			double b = run(1, 95.0, false, false, PhysicalLayer.MODEL_JOHANNISSON,
					alternate, longitudinal, 0.0, true, false, false, false);
			near(a, b, 1e-13, "MB architecture without spectral EDFA, longitudinal=" + longitudinal);
		}

		System.out.printf(Locale.US,
				"PASS QKDv26AseFrequencyParityVerification: %d model comparisons, maxRel=%.3g%n",
				compared, maximumDelta);
	}
}
