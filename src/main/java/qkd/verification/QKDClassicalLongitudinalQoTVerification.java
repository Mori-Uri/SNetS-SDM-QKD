package qkd.verification;

import java.util.ArrayList;
import java.util.List;
import network.LongitudinalClassicalQoT;
import qkd.multiband.ClassicalPowerEvolution;

/** Self-contained deterministic checks: run with java -ea. */
public final class QKDClassicalLongitudinalQoTVerification {
	private QKDClassicalLongitudinalQoTVerification() { }
	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}

	private static ClassicalPowerEvolution snapshot(boolean saturated, double launchW) {
		ClassicalPowerEvolution model = new ClassicalPowerEvolution(80.0,16.0,
				saturated ? ClassicalPowerEvolution.GainModel.SATURATED
						  : ClassicalPowerEvolution.GainModel.IDEAL,
				16.0,0.0,5.0,100.0);
		List<ClassicalPowerEvolution.LoadSource> loads =
				new ArrayList<ClassicalPowerEvolution.LoadSource>();
		loads.add(new ClassicalPowerEvolution.LoadSource(launchW,0,160,193.4e12));

		return model.withDynamicLoad(loads,160.0,0.20);
	}

	public static void main(String[] args) {
		double h=6.62607015e-34, frequency=193.4e12;
		LongitudinalClassicalQoT.Result a = LongitudinalClassicalQoT.evaluate(
				snapshot(false,0.001),frequency,h,0.001,2.0,5.0,0.04,
				false,3.16227766017,160.0,0.20);
		LongitudinalClassicalQoT.Result b = LongitudinalClassicalQoT.evaluate(
				snapshot(true,0.1),frequency,h,0.1,2.0,5.0,0.04,
				true,3.16227766017,160.0,0.20);
		check(a.signalTransfer>0,"signal transfer must be positive");
		check(a.aseAtReceiverWHz>0,"ASE must be positive");
		check(b.boosterGain<a.boosterGain,"booster must saturate under higher load");
		check(b.signalTransfer<a.signalTransfer,"saturation must reduce link transfer");
		check(a.spanStartRatio.length==2,"unexpected spans");
		check(a.spanLengthKm[0]==80.0 && a.spanLengthKm[1]==80.0,"span lengths");
		check(a.spanOutputTransfer[0]<a.spanOutputTransfer[1],"downstream gain calculation");

		// Disabling booster and preamp gains removes their ASE contributions.
		LongitudinalClassicalQoT.Result noEnds = LongitudinalClassicalQoT.evaluate(
				snapshot(false,0.001),frequency,h,0.001,1.0,1.0,0.04,
				false,3.16227766017,160.0,0.20);
		check(noEnds.aseAtReceiverWHz < a.aseAtReceiverWHz,"boundary ASE");
		System.out.println("PASS QKDClassicalLongitudinalQoTVerification");
	}
}
