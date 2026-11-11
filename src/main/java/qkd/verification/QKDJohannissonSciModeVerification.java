package qkd.verification;

import java.util.Arrays;

import network.GnModelMath;
import network.LongitudinalClassicalGN;
import network.Mesh;
import network.PhysicalLayer;
import qkd.multiband.ClassicalPowerEvolution;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;
import simulationControl.parsers.NetworkConfig.ModulationConfig;
import simulationControl.parsers.NetworkConfig.NodeConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.TrafficConfig;

/**
 * Regression for independent Johannisson SCI/XCI selection in v21.
 * No physical coefficients, Habibi formulae or NLI positivity rules are changed.
 */
public final class QKDJohannissonSciModeVerification {

	private QKDJohannissonSciModeVerification() { }

	private static void check(boolean condition, String description) {
		if (!condition) {
			throw new AssertionError(description);
		}
	}

	private static void close(double actual, double expected, double tolerance,
			String description) {
		double limit = Math.max(1e-12, Math.abs(expected) * tolerance);

		check(Double.isFinite(actual) && Math.abs(actual - expected) <= limit,
				description + ": actual=" + actual + ", expected=" + expected);
	}

	private static void checkConfiguration() {
        PhysicalLayerConfig config = new PhysicalLayerConfig();
        check("adaptive".equals(config.getJohannissonNliMode()),
                "new scenarios must default to adaptive mode");
        config.setJohannissonNliMode("approximate");
        check("approximate".equals(config.getJohannissonNliMode()),
                "approximate mode not retained");
        config.setJohannissonNliMode("integral");
        check("integral".equals(config.getJohannissonNliMode()),
                "integral mode not retained");
    }

	private static void checkPhysicalLayer(boolean integralSci, boolean integralXci) {
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
		config.setJohannissonNliMode(integralSci || integralXci ? "integral" : "approximate");
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
		PhysicalLayer layer = mesh.getPhysicalLayer();

		check(layer.getJohannissonNliMode().equals(integralSci || integralXci ? "integral" : "approximate"),
                "PhysicalLayer ignored NLI mode");
	}

	private static void checkSciKernels() {
		double alpha = 0.2 * Math.log(10.0) / 10.0 / 1000.0;
		double beta = 21.3e-27;

		for (double bandwidthHz : new double[] {25e9, 32e9, 50e9}) {
			double rho = Math.PI * Math.PI * beta * bandwidthHz * bandwidthHz / alpha;
			double logarithmic = GnModelMath.johannissonSciKernel(rho, bandwidthHz, false);
			double integral = GnModelMath.johannissonSciKernel(rho, bandwidthHz, true);

			close(logarithmic, Math.log(rho), 1e-12,
					"SCI logarithmic formula for " + bandwidthHz);
			check(integral > 0.0 && logarithmic > 0.0,
					"SCI must remain positive for regular channels");
			check(Math.abs(integral - logarithmic) > 1e-4,
					"SCI selections unexpectedly identical");
		}

		for (double bandwidthHz : new double[] {12.5e9, 1.0e9}) {
			double rho = Math.PI * Math.PI * beta * bandwidthHz * bandwidthHz / alpha;
			double logarithmic = GnModelMath.johannissonSciKernel(rho, bandwidthHz, false);
			double integral = GnModelMath.johannissonSciKernel(rho, bandwidthHz, true);

			check(Math.log(rho) < 0.0, "narrow-band test did not enter fallback");
			close(logarithmic, integral, 1e-12,
					"SCI invalid-log fallback for " + bandwidthHz);
			check(logarithmic > 0.0, "SCI fallback removed physical interference");
		}
	}

	private static void checkLongitudinal() {
		final double frequency = 193.4e12;
		final double alpha = 0.2 * Math.log(10.0) / 10.0 / 1000.0;
		final double beta = 21.3e-27;
		final double bandwidth = 25e9;
		final double powerW = 1e-3;

		ClassicalPowerEvolution snapshot = new ClassicalPowerEvolution(80.0, 16.0)
				.withDynamicLoad(Arrays.asList(
						new ClassicalPowerEvolution.LoadSource(
								powerW, 0.0, 80.0, frequency, bandwidth),
						new ClassicalPowerEvolution.LoadSource(
								powerW, 0.0, 80.0, frequency + 75e9, bandwidth)),
						80.0, 0.05);

		double[][] values = new double[2][2];

		for (int sci = 0; sci < 2; sci++) {
			for (int xci = 0; xci < 2; xci++) {
				values[sci][xci] = LongitudinalClassicalGN.sectionPsd(
						snapshot, 40.0, 2.0, frequency, bandwidth, powerW,
						0.0, 1.3e-3, beta, alpha, 0.05, 2.0,
						sci == 1, xci == 1);

				check(Double.isFinite(values[sci][xci]) && values[sci][xci] >= 0.0,
						"negative longitudinal NLI");
			}
		}

		check(Math.abs(values[0][0] - values[1][0]) > 1e-30,
				"longitudinal SCI selector has no effect");
		check(Math.abs(values[0][0] - values[0][1]) > 1e-30,
				"longitudinal XCI selector has no effect");

		double withoutFlags = LongitudinalClassicalGN.sectionPsd(
				snapshot, 40.0, 2.0, frequency, bandwidth, powerW,
				0.0, 1.3e-3, beta, alpha, 0.05, 2.0);

		close(withoutFlags, values[0][0], 1e-12,
				"default longitudinal SCI/XCI must use logarithms");
	}

	public static void main(String[] args) {
		checkConfiguration();
		checkSciKernels();
		checkLongitudinal();

		for (int sci = 0; sci < 2; sci++) {
			for (int xci = 0; xci < 2; xci++) {
				checkPhysicalLayer(sci == 1, xci == 1);
			}
		}

		System.out.println("PASS QKDJohannissonSciModeVerification");
	}
}
