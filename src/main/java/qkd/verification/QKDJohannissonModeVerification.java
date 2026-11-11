package qkd.verification;

import java.util.Arrays;

import network.GnModelMath;
import network.LongitudinalClassicalGN;
import network.Mesh;
import network.PhysicalLayer;
import qkd.multiband.ClassicalPowerEvolution;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;
import simulationControl.parsers.NetworkConfig.ModulationConfig;
import simulationControl.parsers.NetworkConfig.NodeConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.TrafficConfig;
import simulationControl.Util;

/**
 * Checks the configurable Johannisson XCI approximation without changing
 * the GN/EGN models, their power conventions or the nonnegative NLI guard.
 */
public final class QKDJohannissonModeVerification {

	private QKDJohannissonModeVerification() { }

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void close(double value, double reference,
			double relativeTolerance, String message) {
		double delta = Math.abs(value - reference);
		double limit = Math.max(1e-30, Math.abs(reference)) * relativeTolerance;

		check(Double.isFinite(value) && delta <= limit,
				message + ": " + value + " vs " + reference);
	}

	/** Verify that PhysicalLayer consumes the config flag, not only the math helper. */
	private static void checkPhysicalLayerIntegration(boolean integral) {
		NetworkConfig network = new NetworkConfig();
		network.setNodes(Arrays.asList(new NodeConfig("A", 2, 2, 0),
				new NodeConfig("B", 2, 2, 0)));
		network.setCores(Arrays.asList(new CoreConfig(0, Arrays.asList(1)),
				new CoreConfig(1, Arrays.asList(0))));
		network.setLinks(Arrays.asList(new LinkConfig("A", "B", 16, 12.5e9, 40.0)));
		network.setModulations(Arrays.asList(
				new ModulationConfig("QPSK", 4.0, 500.0, -100.0, -10.0)));

		PhysicalLayerConfig config = new PhysicalLayerConfig();
		config.setPhysicalLayerModel(PhysicalLayer.MODEL_JOHANNISSON);
		config.setJohannissonNliMode(integral ? "integral" : "approximate");
		config.setSpanLength(20.0);
		config.setFiberLoss(0.2);
		config.setFiberNonlinearity(1.3e-3);
		config.setFiberDispersion(16e-6);
		config.setCenterFrequency(193.4e12);
		config.setConstantOfPlanck(6.62607015e-34);
		config.setNoiseFigureOfOpticalAmplifier(5.0);
		config.setPowerSaturationOfOpticalAmplifier(18.0);
		config.setAmplificationFrequency(193.4e12);
		config.setPolarizationModes(2.0);

		Mesh mesh = new Mesh(network, new TrafficConfig(), config,
				new OthersConfig(), null, new Util());

		check(mesh.getPhysicalLayer().getJohannissonNliMode().equals(integral ? "integral" : "approximate"),
				"PhysicalLayer did not consume the Johannisson configuration");
	}

	public static void main(String[] args) {
		PhysicalLayerConfig config = new PhysicalLayerConfig();
		check("adaptive".equals(config.getJohannissonNliMode()),
                "adaptive is the NLI configuration default");
		checkPhysicalLayerIntegration(false);
		checkPhysicalLayerIntegration(true);
        config.setJohannissonNliMode("approximate");

		final double alpha = 0.2 * Math.log(10.0) / 10.0 / 1000.0;
		final double beta = 21.3e-27;
		final double xi = 4.0 * Math.PI * Math.PI * beta / alpha;
		final double gapHz = 75.0e9;

		for (double bandwidthHz : new double[]{12.5e9, 25.0e9, 32e9}) {
			final double referenceLog = Math.log((gapHz + bandwidthHz / 2.0)
					/ (gapHz - bandwidthHz / 2.0));
			final double chosenLog = GnModelMath.johannissonXciKernel(
					gapHz, bandwidthHz, bandwidthHz, xi,
					"integral".equals(config.getJohannissonNliMode()));

			close(chosenLog, referenceLog, 1e-12,
					"Eq.16 logarithm for " + bandwidthHz);
			check(chosenLog >= 0.0, "negative logarithmic XCI");
		}

		config.setJohannissonNliMode("integral");
		check("integral".equals(config.getJohannissonNliMode()), "integral mode not selected");

		final double integral = GnModelMath.johannissonXciKernel(
				gapHz, 12.5e9, 12.5e9, xi,
				"integral".equals(config.getJohannissonNliMode()));
		final double exact = GnModelMath.johannissonXciKernel(
				gapHz, 12.5e9, 12.5e9, xi);
		final double log = GnModelMath.johannissonXciKernel(
				gapHz, 12.5e9, 12.5e9, xi, false);

		close(integral, exact, 1e-12, "Eq.15 integral selected");
		check(integral > 0.0 && log > integral,
				"the kernels should differ for narrow XCI");

		// In-band XCI violates the logarithmic formula's denominator domain;
		// it must fall back to the integral rather than generate NaN / -PSD.
		final double overlapLog = GnModelMath.johannissonXciKernel(
				0.0, 12.5e9, 25e9, xi, false);
		final double overlapIntegral = GnModelMath.johannissonXciKernel(
				0.0, 12.5e9, 25e9, xi, true);

		close(overlapLog, overlapIntegral, 1e-12, "overlap fallback");
		check(overlapLog >= 0.0, "negative overlap XCI");

		final double frequency = 193.4e12;
		ClassicalPowerEvolution snapshot = new ClassicalPowerEvolution(80.0, 16.0)
				.withDynamicLoad(Arrays.asList(
						new ClassicalPowerEvolution.LoadSource(
								1e-3, 0.0, 80.0, frequency, 12.5e9),
						new ClassicalPowerEvolution.LoadSource(
								1e-3, 0.0, 80.0, frequency + gapHz, 12.5e9)),
						80.0, 0.05);

		double longitudinalLog = LongitudinalClassicalGN.sectionPsd(
				snapshot, 40.0, 2.0, frequency, 12.5e9, 1e-3, 0.0,
				1.3e-3, beta, alpha, 0.05, 2.0, false);
		double longitudinalIntegral = LongitudinalClassicalGN.sectionPsd(
				snapshot, 40.0, 2.0, frequency, 12.5e9, 1e-3, 0.0,
				1.3e-3, beta, alpha, 0.05, 2.0, true);
		double longitudinalDefault = LongitudinalClassicalGN.sectionPsd(
				snapshot, 40.0, 2.0, frequency, 12.5e9, 1e-3,
				1.3e-3, beta, alpha, 0.05, 2.0);

		check(longitudinalLog > longitudinalIntegral
						&& longitudinalIntegral >= 0.0,
				"longitudinal XCI mode must change results without negative noise");
		close(longitudinalDefault, longitudinalLog, 1e-12,
				"longitudinal default is logarithmic");

		double narrowSci = GnModelMath.johannissonSciKernel(
				Math.PI * Math.PI * beta * 12.5e9 * 12.5e9 / alpha,
				12.5e9);
		check(narrowSci > 0.0, "narrow-band SCI must stay positive");

		System.out.println("PASS QKDJohannissonModeVerification");
		System.out.printf(java.util.Locale.ROOT,
				"XCI_12.5_GHz integral=%.9f logarithmic=%.9f difference=%.4f%% %n",
				integral, log, 100.0 * (log / integral - 1.0));
	}
}
