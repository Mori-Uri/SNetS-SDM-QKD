package qkd.verification;

import java.util.Arrays;
import qkd.multiband.AmplifierOperatingPoint;
import qkd.multiband.ClassicalPowerEvolution;
import qkd.multiband.ClassicalPowerEvolution.LoadSource;

/** Standalone deterministic numerical checks for the operating-point contract. */
public final class QKDSharedAmplifierGainVerification {
	private QKDSharedAmplifierGainVerification() { }

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	public static void main(String[] args) {
		final double alpha = 0.2 * Math.log(10.0) / 10.0;
		final double f = 193.1e12;
		final double h = 6.62607015e-34;
		final ClassicalPowerEvolution saturated = new ClassicalPowerEvolution(
				40.0, 8.0, ClassicalPowerEvolution.GainModel.SATURATED,
				12.0, 0.0, 5.0, 100.0);
		final ClassicalPowerEvolution low = saturated.withDynamicLoad(
				Arrays.asList(new LoadSource(0.0001, 0, 100, f)), 100.0, alpha);
		final ClassicalPowerEvolution high = saturated.withDynamicLoad(
				Arrays.asList(new LoadSource(0.0001, 0, 100, f),
						new LoadSource(0.005, 0, 100, f)), 100.0, alpha);
		final AmplifierOperatingPoint lowPoint = low.operatingPointAt(1, alpha, f, h);
		final AmplifierOperatingPoint highPoint = high.operatingPointAt(1, alpha, f, h);
		check(highPoint.getTotalInputW() > lowPoint.getTotalInputW(), "load not reflected");
		check(highPoint.getGainLinear() < lowPoint.getGainLinear(), "gain not compressed");
		check(highPoint.getAsePsdWHz() >= 0.0, "negative ASE");
		check(lowPoint.getGainLinear() == low.operatingPointAt(1, alpha, f, h).getGainLinear(),
				"snapshot not deterministic");
		final ClassicalPowerEvolution ideal = new ClassicalPowerEvolution(
				40.0, 8.0, ClassicalPowerEvolution.GainModel.IDEAL,
				12.0, 0.0, 5.0, 100.0);
		final double gi = ideal.withDynamicLoad(Arrays.asList(
				new LoadSource(0.005, 0, 100, f)), 100, alpha)
				.operatingPointAt(1, alpha, f, h).getGainLinear();
		check(Math.abs(gi - Math.pow(10, 0.8)) < 1e-10, "ideal gain affected by load");

		// PICh starting after the first EDFA must not affect that amplifier.
		final double late = saturated.withDynamicLoad(Arrays.asList(
				new LoadSource(0.0001, 0, 100, f),
				new LoadSource(0.005, 50, 100, f)), 100, alpha)
				.operatingPointAt(1, alpha, f, h).getGainLinear();
		check(Math.abs(late - lowPoint.getGainLinear()) < 1e-12,
				"late PICh changes upstream amplifier");
		System.out.println("PASS QKDSharedAmplifierGainVerification");
	}
}
