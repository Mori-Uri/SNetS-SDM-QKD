package qkd.verification;

import java.util.Arrays;
import java.util.Collections;
import network.Amplifier;
import network.Amplifier;
import qkd.multiband.ClassicalPowerEvolution;
import qkd.multiband.MultiBandEdFA;

/** Numerical invariants and cross-module contract checks (no external jars). */
public final class QKDUnifiedMultibandVerification {
	private QKDUnifiedMultibandVerification() { }
	private static void assertTrue(boolean ok, String msg) {
		if (!ok) throw new AssertionError(msg);
	}

	private static void near(double actual, double expected, double relative, String msg) {
		if (!Double.isFinite(actual) || Math.abs(actual-expected) > relative*Math.max(1e-25,Math.abs(expected)))
			throw new AssertionError(msg+" actual="+actual+" expected="+expected);
	}

	public static void main(String[] args) {
		final double alpha = 0.2 * Math.log(10.0)/10.0;
		final double fC = 193.5e12, fL = 187.0e12;
		final double h = 6.62607015e-34;
		final MultiBandEdFA ideal = new MultiBandEdFA(Arrays.asList(
				new MultiBandEdFA.Band("L",186e12,191e12,14,6),
				new MultiBandEdFA.Band("C",191e12,196e12,18,5)), 17, false);
		final MultiBandEdFA saturated = new MultiBandEdFA(ideal.getBands(), 17, true);
		final MultiBandEdFA.State empty = saturated.evaluate(Collections.<MultiBandEdFA.Input>emptyList());
		final MultiBandEdFA.State one = saturated.evaluate(Arrays.asList(
				new MultiBandEdFA.Input(fC,0.001)));
		final MultiBandEdFA.State two = saturated.evaluate(Arrays.asList(
				new MultiBandEdFA.Input(fC,0.001),new MultiBandEdFA.Input(fL,0.005)));
		assertTrue(two.gain(fC) < one.gain(fC),"L-band traffic must compress C-band gain");
		assertTrue(two.gain(fL) < empty.gain(fL),"C-band traffic must compress L-band gain");
		assertTrue(ideal.evaluate(Arrays.asList(new MultiBandEdFA.Input(fC,1))).gain(fC)
				== ideal.evaluate(Collections.<MultiBandEdFA.Input>emptyList()).gain(fC),
				"ideal gain independent of power");
		near(empty.gain(fC), Math.pow(10,1.8),1e-12,"unloaded gain should be G0");
		assertTrue(two.asePsd(fC,h) > 0,"C-band ASE must be positive");
		assertTrue(two.asePsd(fL,h) > 0,"L-band ASE must be positive");
		near(Amplifier.gain(100,0.001,0.01,true),100.0/11.0,1e-12,
				"identical compression law shared by QoT and QKD");
		final Amplifier classicalAmp = new Amplifier(20,10,5,h,fC,0,0,0);
		near(classicalAmp.getGainSaturated(0.001),
				Amplifier.gain(100,0.001,0.01,true),1e-12,
				"classic QoT gain law and QKD longitudinal law must agree");
		boolean invalid = false;

		try { saturated.band(160e12); } catch (IllegalArgumentException e) { invalid=true; }
		assertTrue(invalid,"do not invent EDFA gain outside calibrated bands");
		invalid=false;

		try {
			new MultiBandEdFA(Arrays.asList(
					new MultiBandEdFA.Band("C",191e12,195e12,10,5),
					new MultiBandEdFA.Band("L?",194e12,196e12,10,5)),17,true);
		} catch (IllegalArgumentException e) { invalid=true; }
		assertTrue(invalid,"overlapping gain bands must fail fast");

		final ClassicalPowerEvolution base = new ClassicalPowerEvolution(20,12,
				ClassicalPowerEvolution.GainModel.SATURATED,17,0,5,60)
				.withMultiBandEdFA(saturated);
		final ClassicalPowerEvolution oneCore = base.withDynamicLoad(Arrays.asList(
				new ClassicalPowerEvolution.LoadSource(0.01,0,70,fC)),70,alpha);
		final ClassicalPowerEvolution bothBands = base.withDynamicLoad(Arrays.asList(
				new ClassicalPowerEvolution.LoadSource(0.01,0,70,fC),
				new ClassicalPowerEvolution.LoadSource(0.01,0,70,fL)),70,alpha);
		assertTrue(bothBands.gainLinearAt(1,alpha,fC)<oneCore.gainLinearAt(1,alpha,fC),
				"spectral EDFA grid shares one compression state");
		assertTrue(bothBands.spectralStateAt(1).getInputW()>oneCore.spectralStateAt(1).getInputW(),
				"snapshot must use actual input channels");
		assertTrue(bothBands.powerRatio(0,45,alpha,fC)<oneCore.powerRatio(0,45,alpha,fC),
				"longitudinal propagation must use compressed spectral gain");
		final ClassicalPowerEvolution late = base.withDynamicLoad(Arrays.asList(
				new ClassicalPowerEvolution.LoadSource(0.01,20,70,fL)),70,alpha);
		near(late.inputPowerWAt(1),0,1e-12,"no amplification before PICh insertion");
		assertTrue(late.inputPowerWAt(2)>0,"PICh contributes after insertion");
		assertTrue(!base.hasDynamicLoad(),"baseline EDFA configuration must not mutate");
		assertTrue(bothBands.aseAtQuantumReceiverW(0,70,alpha,alpha,fC,12.5e9,h)>0,
				"per-EDFA ASE reaches receiver only with configured leakage");
		System.out.println("PASS QKDUnifiedMultibandVerification");
	}
}
