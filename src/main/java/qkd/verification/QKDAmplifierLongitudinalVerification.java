package qkd.verification;

import network.Amplifier;
import network.PhysicalLayer;
import qkd.multiband.ClassicalPowerEvolution;
import qkd.multiband.NonlinearInterferenceModel;

/** Standalone validation of selectable amplifier gain, ASE and longitudinal GN. */
public final class QKDAmplifierLongitudinalVerification {
	private QKDAmplifierLongitudinalVerification() { }

	private static void require(boolean assertion, String message) {
		if (!assertion) throw new AssertionError(message);
	}

	private static void close(double x, double y, double tolerance, String message) {
		if (Math.abs(x-y) > tolerance * Math.max(1e-22,Math.abs(y)))
			throw new AssertionError(message + " actual=" + x + " expected=" + y);
	}

	public static void main(String[] args) {
		final double alpha = 0.2 * Math.log(10.0) / 10.0;
		final double aq = 0.25 * Math.log(10.0) / 10.0;
		final ClassicalPowerEvolution ideal = new ClassicalPowerEvolution(20.0, 12.0);
		final ClassicalPowerEvolution saturated = new ClassicalPowerEvolution(20.0, 12.0,
				ClassicalPowerEvolution.GainModel.SATURATED, 12.0, 10.0, 5.0, 70.0);
		require(saturated.gainLinearAt(1, alpha) < ideal.gainLinearAt(1, alpha),
				"gain compression must reduce gain under high launch load");
		require(saturated.powerRatio(0, 25, alpha) < ideal.powerRatio(0, 25, alpha),
				"compressed gain must reduce co-propagating power");
		require(saturated.aseAtQuantumReceiverW(0, 50, alpha, aq, 193.5e12, 12.5e9, 6.626e-34) > 0,
				"each classical EDFA emits calibrated ASE leakage");
		close(saturated.aseAtQuantumReceiverW(0, 19, alpha, aq, 193.5e12, 12.5e9, 6.626e-34), 0, 1e-10,
				"no ASE before first amplifier");
		require(ideal.aseAtQuantumReceiverW(0, 50, alpha, aq, 193.5e12, 12.5e9, 6.626e-34) == 0,
				"default never invents quantum-band ASE leakage");
		final double noGain = 0.0;
		final NonlinearInterferenceModel gn = NonlinearInterferenceModel.standard();
		final ClassicalPowerEvolution passive = new ClassicalPowerEvolution(1000, noGain);
		final double[] psd = new double[] {1e-13, 2e-13, 2e-13, 1e-13};
		final double start = 193.0e12;
		final double step = 12.5e9;
		final double q = start + 2.0 * step;
		final double reference = gn.psdWPerHz(psd, start, step, q, 10.0, alpha, aq);
		final double computed = gn.longitudinalPsdWPerHz(psd, start, step, q,
				0, 10, alpha, aq, passive);
		close(computed, reference, 1e-10, "single section must preserve legacy GN");
		final java.util.List<NonlinearInterferenceModel.LongitudinalSource> forward =
				java.util.Collections.singletonList(
					new NonlinearInterferenceModel.LongitudinalSource(1, 4, 4e-3, 0.0, 40.0));
		final double power0 = gn.longitudinalSourcePsdWPerHz(forward, 4, start, step,
				q, 0, 10, alpha, aq, passive);
		require(power0 > 0.0, "forward GN source must contribute");
		final java.util.List<NonlinearInterferenceModel.LongitudinalSource> late =
				java.util.Collections.singletonList(
					new NonlinearInterferenceModel.LongitudinalSource(1, 4, 4e-3, 15.0, 40.0));
		require(gn.longitudinalSourcePsdWPerHz(late, 4, start, step,
				q, 0, 10, alpha, aq, passive) == 0.0,
				"PICh launched after QKDSpan cannot contribute GN");
		require(gn.longitudinalSourcePsdWPerHz(late, 4, start, step,
				q, 15, 25, alpha, aq, passive) > 0.0,
				"PICh launched at trusted node contributes in its own span");
		final Amplifier classical = new Amplifier(16, 16, 5, 6.626e-34, 193.5e12,
				0, 100, 4);
		require(classical.getGainByType(1e-2, PhysicalLayer.AMP_GAIN_SATURATED)
				< classical.getGainByType(1e-2, PhysicalLayer.AMP_GAIN_FIXED),
				"legacy SNetS exposes both amplifier model choices");
		require(classical.getAseBySaturation(1e-2) >= 0, "ASE not negative");
		System.out.println("PASS QKDAmplifierLongitudinalVerification");
	}
}
