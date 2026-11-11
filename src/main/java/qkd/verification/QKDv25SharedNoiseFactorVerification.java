package qkd.verification;

import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import gprmcsa.routing.Route;
import gprmcsa.modulation.Modulation;
import network.Circuit;
import network.Mesh;
import network.Node;
import network.Pair;
import network.PhysicalLayer;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.TrafficConfig;
import network.Amplifier;
import network.Amplifier;
import network.LongitudinalClassicalQoT;
import qkd.multiband.AmplifierOperatingPoint;
import qkd.multiband.ClassicalPowerEvolution;
import qkd.multiband.MultiBandEdFA;

/** Regression of the one shared EDFA gain, NF(Pin) and ASE law. */
public final class QKDv25SharedNoiseFactorVerification {

	private QKDv25SharedNoiseFactorVerification() { }

	private static void check(boolean condition, String label) {
		if (!condition) throw new AssertionError(label);
	}

	private static void near(double actual, double expected, String label) {
		double tolerance = Math.max(1e-30, Math.abs(expected) * 1e-10);
		check(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
				label + ": " + actual + " versus " + expected);
	}

	/** End-to-end call through PhysicalLayer.computeSNRSegment. */
	private static double snrWithNoiseFactor(double a1, boolean longitudinal,
			boolean saturatedGain) {
		NetworkConfig network = new NetworkConfig();
		network.setNodes(Arrays.asList(new NetworkConfig.NodeConfig("A", 4, 4, 0),
				new NetworkConfig.NodeConfig("B", 4, 4, 0)));
		network.setCores(Arrays.asList(new NetworkConfig.CoreConfig(0, Arrays.asList(1)),
				new NetworkConfig.CoreConfig(1, Arrays.asList(0))));
		network.setLinks(Arrays.asList(new NetworkConfig.LinkConfig(
				"A", "B", 64, 12.5e9, 80.0)));
		network.setModulations(Arrays.asList(new NetworkConfig.ModulationConfig(
				"QPSK", 4.0, 10000.0, -100.0, -10.0)));

		PhysicalLayerConfig config = new PhysicalLayerConfig();
		config.setPhysicalLayerModel(PhysicalLayer.MODEL_JOHANNISSON);
		config.setPower(0.0);
		config.setSpanLength(40.0);
		config.setFiberLoss(0.2);
		config.setFiberNonlinearity(0.0013);
		config.setFiberDispersion(16.7e-6);
		config.setCenterFrequency(193.4e12);
		config.setConstantOfPlanck(6.62607015e-34);
		config.setNoiseFigureOfOpticalAmplifier(5.0);
		config.setPowerSaturationOfOpticalAmplifier(18.0);
		config.setNoiseFactorModelParameterA1(a1);
		config.setNoiseFactorModelParameterA2(0.005);
		config.setTypeOfAmplifierGain(saturatedGain
				? PhysicalLayer.AMP_GAIN_SATURATED : PhysicalLayer.AMP_GAIN_FIXED);
		config.setAmplificationFrequency(193.4e12);
		config.setActiveASE(true);
		config.setActiveNLI(false);
		config.setActiveXT(false);
		config.setPolarizationModes(2.0);
		config.setMuxInsertionLoss(3.0);
		config.setDemuxInsertionLoss(5.0);

		Mesh mesh = new Mesh(network, new TrafficConfig(), config,
				new OthersConfig(), null, new Util());
		PhysicalLayer physical = mesh.getPhysicalLayer();

		if (longitudinal) {
			double alpha = 0.2 * Math.log(10.0) / 10.0;
			ClassicalPowerEvolution template = new ClassicalPowerEvolution(
					40.0, 8.0, saturatedGain
							? ClassicalPowerEvolution.GainModel.SATURATED
							: ClassicalPowerEvolution.GainModel.IDEAL,
					18.0, 0.0, 5.0, 100.0);
			physical.enableLongitudinalAmplifierQoT(template, 193e12, alpha);
		}

		Vector<Node> nodes = new Vector<Node>();
		nodes.add(mesh.getNodeList().get(0));
		nodes.add(mesh.getNodeList().get(1));
		Route route = new Route(nodes);
		Modulation modulation = mesh.getAvaliableModulations().get(0);
		Circuit circuit = new Circuit();
		circuit.setRoute(route);
		circuit.setPair(new Pair(route.getSource(), route.getDestination()));
		circuit.setModulation(modulation);
		circuit.setIndexCore(0);
		circuit.setSpectrumAssigned(new int[] {12, 15});
		circuit.setLaunchPowerLinear(1e-3);
		RequestForConnection request = new RequestForConnection();
		request.setPair(circuit.getPair());
		request.setRequiredBitRate(modulation.potentialBitRate(4));
		circuit.addRequest(request);

		return physical.computeOSNRSegment(circuit, route, 0, 1, modulation,
				0, circuit.getSpectrumAssigned(), null, false);
	}

	public static void main(String[] args) {
		final double f = 193.4e12;
		final double h = 6.62607015e-34;
		final double input = 0.002;
		final double nf = Math.pow(10.0, 5.0 / 10.0);
		final double gain0 = Math.pow(10.0, 10.0 / 10.0);
		final double saturation = 0.015;
		final double a1 = 4.0;
		final double a2 = 0.005;
		final double alpha = 0.2 * Math.log(10.0) / 10.0;

		final double expectedNf = nf * (1.0 + a1 - a1 / (1.0 + input / a2));
		near(Amplifier.noiseFactor(nf, input, a1, a2, true),
				expectedNf, "saturated NF");
		near(Amplifier.noiseFactor(nf, input, a1, a2, false), nf,
				"ideal NF unchanged");
		near(Amplifier.noiseFactor(nf, input, a1, 0.0, true), nf,
				"disabled empirical NF");

		double gain = Amplifier.gain(gain0, input, saturation, true);
		double ase = Amplifier.asePsd(f, nf, gain, h, input, a1, a2, true);
		near(ase, h * f * expectedNf * (gain - 1.0), "shared ASE");
		check(ase > Amplifier.asePsd(f, nf, gain, h),
				"saturated NF must affect ASE");

		Amplifier classical = new Amplifier(10.0, 10.0 * Math.log10(saturation / 1e-3),
				5.0, h, f, 0.0, a1, a2);
		near(classical.getFamp(input), expectedNf, "classical getFamp");
		near(classical.getGainSaturated(input), gain, "classical gain");
		near(classical.getAseByGain(input, gain), ase, "classical output ASE");
		near(classical.getAseBySaturation(input), ase, "classical saturated ASE");
		near(classical.getAse(), h * f * nf * (gain0 - 1.0), "classical ideal ASE");

		final ClassicalPowerEvolution template = new ClassicalPowerEvolution(
				40.0, 10.0, ClassicalPowerEvolution.GainModel.SATURATED,
				10.0 * Math.log10(saturation / 1e-3), 0.0, 5.0, 100.0)
				.withNoiseFactorModel(a1, a2).withBooster(2.0, saturation, true);
		final ClassicalPowerEvolution loaded = template.withDynamicLoad(
				Arrays.asList(new ClassicalPowerEvolution.LoadSource(0.003, 0, 95, f),
						new ClassicalPowerEvolution.LoadSource(0.002, 0, 95, f)),
				95.0, alpha);
		final AmplifierOperatingPoint point = loaded.operatingPointAt(1, alpha, f, h);
		near(point.getAsePsdWHz(), Amplifier.asePsd(f, nf,
				point.getGainLinear(), h, point.getTotalInputW(), a1, a2, true),
				"snapshot ASE operating point");

		final LongitudinalClassicalQoT.Result result = LongitudinalClassicalQoT.evaluate(
				loaded, f, h, 0.005, 2.0, 20.0, saturation, true, nf, 95.0, alpha);
		check(Double.isFinite(result.aseAtReceiverWHz)
				&& result.aseAtReceiverWHz > 0.0,
				"longitudinal cascade ASE");

		final ClassicalPowerEvolution noNfCorrection = template.withNoiseFactorModel(0.0, 0.0)
				.withDynamicLoad(Arrays.asList(
						new ClassicalPowerEvolution.LoadSource(0.003, 0, 95, f),
						new ClassicalPowerEvolution.LoadSource(0.002, 0, 95, f)), 95.0, alpha);
		final LongitudinalClassicalQoT.Result reference = LongitudinalClassicalQoT.evaluate(
				noNfCorrection, f, h, 0.005, 2.0, 20.0, saturation, true, nf, 95.0, alpha);
		check(result.aseAtReceiverWHz > reference.aseAtReceiverWHz,
				"NF dependence propagates to receiver");
		near(result.signalTransfer, reference.signalTransfer,
				"NF must not change signal gain");

		final List<MultiBandEdFA.Band> bands = Arrays.asList(
				new MultiBandEdFA.Band("C", 190e12, 195e12, 10.0, 5.0),
				new MultiBandEdFA.Band("L", 185e12, 190e12, 9.0, 6.0));
		final MultiBandEdFA edfa = new MultiBandEdFA(bands,
				10.0 * Math.log10(saturation / 1e-3), true).withNoiseFactorModel(a1, a2);
		final MultiBandEdFA.State spectral = edfa.evaluate(Arrays.asList(
				new MultiBandEdFA.Input(f, 0.001),
				new MultiBandEdFA.Input(187e12, 0.002)));
		final double spectralNf = Amplifier.noiseFactor(nf,
				spectral.getInputW(), a1, a2, true);
		near(spectral.asePsd(f, h),
				h * f * spectralNf * (spectral.gain(f) - 1.0),
				"multiband NF and ASE");

		final MultiBandEdFA.State spectralIdeal = new MultiBandEdFA(bands,
				10.0 * Math.log10(saturation / 1e-3), false)
				.withNoiseFactorModel(a1, a2).evaluate(Arrays.asList(
						new MultiBandEdFA.Input(f, 0.001)));
		near(spectralIdeal.asePsd(f, h), h * f * nf * (spectralIdeal.gain(f) - 1.0),
				"fixed multiband NF");

		for (boolean longitudinal : new boolean[] {false, true}) {
			double saturatedNominal = snrWithNoiseFactor(0.0, longitudinal, true);
			double saturatedVariable = snrWithNoiseFactor(4.0, longitudinal, true);
			check(Double.isFinite(saturatedNominal) && Double.isFinite(saturatedVariable),
					"finite OSNR with ASE");
			check(saturatedVariable < saturatedNominal,
					"power-dependent NF must reduce classical OSNR; longitudinal=" + longitudinal
					+ " nominal=" + saturatedNominal + " variable=" + saturatedVariable);

			double fixedNominal = snrWithNoiseFactor(0.0, longitudinal, false);
			double fixedVariable = snrWithNoiseFactor(4.0, longitudinal, false);
			near(fixedNominal, fixedVariable,
					"fixed-gain QoT unaffected by A1; longitudinal=" + longitudinal);
			System.out.println("QoT longitudinal=" + longitudinal
					+ " saturated NF0=" + saturatedNominal
					+ " NF(Pin)=" + saturatedVariable);
		}

		System.out.println("PASS QKDv25SharedNoiseFactorVerification");
	}
}
