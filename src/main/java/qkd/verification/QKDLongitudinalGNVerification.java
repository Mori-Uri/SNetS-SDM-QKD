package qkd.verification;

import java.util.Arrays;
import network.LongitudinalClassicalGN;
import qkd.multiband.ClassicalPowerEvolution;

/** No external dependencies. Deterministic sanity checks, not article calibration. */
public final class QKDLongitudinalGNVerification {
	private QKDLongitudinalGNVerification() { }
	private static void assertTrue(boolean value, String reason) {
		if(!value) throw new AssertionError(reason);
	}

	private static ClassicalPowerEvolution snapshot(double victim, double interferer,
			double startKm) {
		ClassicalPowerEvolution base=new ClassicalPowerEvolution(80.0,16.0);

		return base.withDynamicLoad(Arrays.asList(
				new ClassicalPowerEvolution.LoadSource(victim,0.0,160.0,193.4e12,32e9),
				new ClassicalPowerEvolution.LoadSource(interferer,startKm,160.0,193.5e12,32e9)),
				160.0,0.05);
	}

	private static double nli(ClassicalPowerEvolution s, double z, double victim) {
		return LongitudinalClassicalGN.sectionPsd(s,z,1.0,193.4e12,32e9,
				victim,1.3e-3,21.7e-27,5.e-5,0.05,2.0);
	}

	public static void main(String[] args) {
		double small=nli(snapshot(1.e-3,0.0,0.0),20,1.e-3);
		double large=nli(snapshot(2.e-3,0.0,0.0),20,2.e-3);
		assertTrue(small>0.0 && Double.isFinite(small),"finite SCI");
		assertTrue(Math.abs(large/small-8.0)<1.e-9,"SCI cubic law");
		double cross=nli(snapshot(1.e-3,1.e-3,0.0),20,1.e-3);
		assertTrue(cross>small,"XCI from active interfering channel");
		double late=nli(snapshot(1.e-3,1.e-3,100.0),20,1.e-3);
		assertTrue(Math.abs(late-small)/small<1.e-12,"no PICh before launch");
		System.out.println("PASS QKDLongitudinalGNVerification");
	}
}
