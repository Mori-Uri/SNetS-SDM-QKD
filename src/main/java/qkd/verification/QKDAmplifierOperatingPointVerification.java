package qkd.verification;

import java.util.Arrays;
import java.util.Collections;
import qkd.multiband.AmplifierOperatingPoint;
import qkd.multiband.ClassicalPowerEvolution;
import qkd.multiband.MultiBandEdFA;
import network.Amplifier;

/** Deterministic integration/regression checks: QKD and classical share state. */
public final class QKDAmplifierOperatingPointVerification {
	private QKDAmplifierOperatingPointVerification() { }
	private static void ok(boolean yes, String text) {
		if (!yes) throw new AssertionError(text);
	}

	private static void near(double got, double expected, double rel, String message) {
		if (!Double.isFinite(got) || Math.abs(got-expected) >
				rel * Math.max(Math.abs(expected), 1e-30))
			throw new AssertionError(message + ": " + got + " instead of " + expected);
	}

	private static boolean rejects(Runnable op) {
		try { op.run(); } catch (IllegalArgumentException ex) { return true; }
		return false;
	}

	public static void main(String[] args) {
		final double h = 6.62607015e-34;
		final double alpha = 0.2 * Math.log(10) / 10;
		final double c = 193.5e12, l = 187.0e12;
		final MultiBandEdFA.Band cb = new MultiBandEdFA.Band("C", 191e12,196e12,18,5);
		final MultiBandEdFA.Band lb = new MultiBandEdFA.Band("L", 186e12,191e12,14,6);
		final MultiBandEdFA ideal = new MultiBandEdFA(Arrays.asList(cb,lb),17,false);
		final MultiBandEdFA sat = new MultiBandEdFA(Arrays.asList(cb,lb),17,true);
		final ClassicalPowerEvolution blank = new ClassicalPowerEvolution(20,16,
				ClassicalPowerEvolution.GainModel.SATURATED,17,0,5,90);
		final ClassicalPowerEvolution idealBase = blank.withMultiBandEdFA(ideal);
		final ClassicalPowerEvolution saturatedBase = blank.withMultiBandEdFA(sat);
		final ClassicalPowerEvolution.LoadSource sourceC =
				new ClassicalPowerEvolution.LoadSource(0.01,0,70,c);
		final ClassicalPowerEvolution.LoadSource sourceL =
				new ClassicalPowerEvolution.LoadSource(0.01,0,70,l);
		final ClassicalPowerEvolution solo = saturatedBase.withDynamicLoad(
				Collections.singletonList(sourceC),70,alpha);
		final ClassicalPowerEvolution both = saturatedBase.withDynamicLoad(
				Arrays.asList(sourceC,sourceL),70,alpha);
		final ClassicalPowerEvolution fixed = idealBase.withDynamicLoad(
				Arrays.asList(sourceC,sourceL),70,alpha);
		final Amplifier classical = new Amplifier(18,17,5,h,c,20,0,0);
		AmplifierOperatingPoint pt = both.operatingPointAt(1,alpha,c,h);
		near(pt.getGainLinear(),both.gainLinearAt(1,alpha,c),1e-13,
				"shared operating point gain must match QKD longitudinal gain");
		near(pt.getGainLinear(),classical.getGainFromOperatingPoint(pt),1e-13,
				"classical and QKD gain must match from the SAME snapshot");
		near(pt.getAsePsdWHz(),classical.getAseFromOperatingPoint(pt),1e-13,
				"classical and QKD ASE must match from the SAME snapshot");
		near(pt.getAsePsdWHz(),both.spectralStateAt(1).asePsd(c,h),1e-13,
				"shared ASE and spectral EDFA must match");
		ok(pt.getTotalInputW()>0,"load is nonzero");
		ok(pt.asePowerW(12.5e9)>0,"ASE power within a passband is positive");
		ok(both.operatingPointAt(1,alpha,c,h).getGainLinear()
				<solo.operatingPointAt(1,alpha,c,h).getGainLinear(),
				"inter-band competition must compress C gain");
		near(fixed.operatingPointAt(1,alpha,c,h).getGainLinear(),
				Math.pow(10,18.0/10.0),1e-13,"ideal gain is load independent");
		ok(rejects(() -> saturatedBase.withDynamicLoad(
				Collections.singletonList(new ClassicalPowerEvolution.LoadSource(0.001,0,80,c)),
				70,alpha)),"source cannot exceed physical fibre");
		ok(rejects(() -> saturatedBase.withDynamicLoad(
				Collections.singletonList(new ClassicalPowerEvolution.LoadSource(0.001,0,70)),
				70,alpha)),"multi-band loads cannot invent an optical frequency");
		ok(rejects(() -> saturatedBase.withDynamicLoad(
				Collections.singletonList(new ClassicalPowerEvolution.LoadSource(0.001,0,70,150e12)),
				70,alpha)),"channels outside bands must be rejected");
		ok(rejects(() -> both.operatingPointAt(0,alpha,c,h)),"index zero is not an EDFA");
		ok(!saturatedBase.hasDynamicLoad(),"reference model remains immutable");
		System.out.println("PASS QKDAmplifierOperatingPointVerification");
	}
}
