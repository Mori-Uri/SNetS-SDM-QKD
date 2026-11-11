package qkd.verification;

import java.util.Arrays;
import java.util.Collections;
import qkd.multiband.ClassicalPowerEvolution;
import qkd.multiband.ClassicalPowerEvolution.LoadSource;

/** Standalone no-dependency tests for actual traffic-dependent EDFA gain. */
public final class QKDDynamicAmplifierVerification {
	private QKDDynamicAmplifierVerification() {}
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static void near(double actual, double expected, String message) {
		if (Math.abs(actual - expected) > 1e-9 * Math.max(1e-15, Math.abs(expected)))
			throw new AssertionError(message + " actual=" + actual + " expected=" + expected);
	}

	public static void main(String[] args) {
		final double alpha = 0.2 * Math.log(10.0) / 10.0;
		final ClassicalPowerEvolution base = new ClassicalPowerEvolution(20, 12,
				ClassicalPowerEvolution.GainModel.SATURATED, 10, 0, 5, 60);
		final ClassicalPowerEvolution empty = base.withDynamicLoad(
				Collections.<LoadSource>emptyList(), 65, alpha);
		final ClassicalPowerEvolution one = base.withDynamicLoad(
				Arrays.asList(new LoadSource(0.01, 0, 65)), 65, alpha);
		final ClassicalPowerEvolution two = base.withDynamicLoad(Arrays.asList(
				new LoadSource(0.01, 0, 65), new LoadSource(0.01, 0, 65)), 65, alpha);
		check(two.gainLinearAt(1, alpha) < one.gainLinearAt(1, alpha),
				"more real active traffic must compress gain more");
		check(one.gainLinearAt(1, alpha) < empty.gainLinearAt(1, alpha),
				"zero traffic must not be mistaken for constant reference load");
		near(two.inputPowerWAt(1), 0.02 * Math.exp(-alpha * 20),
				"EDFA input must use physical preamplifier powers");
		final ClassicalPowerEvolution late = base.withDynamicLoad(Arrays.asList(
				new LoadSource(0.01, 20, 65)), 65, alpha);
		near(late.inputPowerWAt(1), 0.0,
				"PICh inserted at EDFA location must not contribute to its input");
		check(late.inputPowerWAt(2) > 0, "later EDFA must see inserted PICh");
		final ClassicalPowerEvolution dropped = base.withDynamicLoad(Arrays.asList(
				new LoadSource(0.01, 0, 35)), 65, alpha);
		near(dropped.inputPowerWAt(2), 0.0,
				"PICh dropped before next EDFA must be excluded");
		final ClassicalPowerEvolution idealBase = new ClassicalPowerEvolution(20, 12);
		final ClassicalPowerEvolution ideal = idealBase.withDynamicLoad(Arrays.asList(
				new LoadSource(0.01, 0, 65)), 65, alpha);
		near(ideal.gainLinearAt(1, alpha), Math.pow(10, 1.2),
				"ideal EDFA gain must remain independent of load");
		check(two.powerRatio(0, 45, alpha) < one.powerRatio(0, 45, alpha),
				"GN/Raman longitudinal power must use actual compressed gains");
		check(one.aseAtQuantumReceiverW(0, 65, alpha, alpha, 193.5e12,
				12.5e9, 6.626e-34) > 0, "ASE must be generated per EDFA");
		check(!base.hasDynamicLoad(), "global configuration may not be mutated");
		System.out.println("PASS QKDDynamicAmplifierVerification");
	}
}
