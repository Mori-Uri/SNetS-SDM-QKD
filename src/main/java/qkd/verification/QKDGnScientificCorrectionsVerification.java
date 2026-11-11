package qkd.verification;

import java.util.Arrays;
import network.GnModelMath;
import network.LongitudinalClassicalGN;
import qkd.multiband.ClassicalPowerEvolution;

/** Deterministic GN/EGN consistency checks; no external dependency or publication claim. */
public final class QKDGnScientificCorrectionsVerification {
	private static void check(boolean yes, String message) {
		if(!yes) throw new AssertionError(message);
	}

	private static void rel(double actual, double expected, double tolerance, String reason) {
		check(Double.isFinite(actual) && Math.abs(actual-expected) <= tolerance*Math.max(1.e-30,Math.abs(expected)),
				reason+": expected="+expected+" obtained="+actual);
	}

	public static void main(String[] args) {
		// Johannisson: the rectangle integral is positive in the narrow-band range.
		double alpha=0.2*Math.log(10.0)/10.0/1000.0;
		double beta=21.3e-27;
		double rho12=Math.PI*Math.PI*beta*(12.5e9*12.5e9)/alpha;
		double narrow=GnModelMath.johannissonSciKernel(rho12,12.5e9);
		check(rho12<1.0 && narrow>0.0 && narrow<1.0,"positive narrowband SCI, no logarithm floor");
		double atBelow=GnModelMath.johannissonSciKernel(
				Math.PI*Math.PI*beta*Math.pow(27.999999e9,2)/alpha,27.999999e9);
		double atAbove=GnModelMath.johannissonSciKernel(
				Math.PI*Math.PI*beta*Math.pow(28.000001e9,2)/alpha,28.000001e9);
		check(Math.abs(atBelow-atAbove)<1.e-6,"no discontinuity at 28 GHz");
		double[] spans=GnModelMath.spanLengthsKm(100.0,80.0);
		check(spans.length==2,"two spans");
		rel(spans[0],80.0,1e-12,"first span");
		rel(spans[1],20.0,1e-12,"partial span");
		double tail=GnModelMath.partialSpanFactor(20.0,80.0,alpha);
		check(tail>0.0 && tail<1.0,"tail emits less NLI than full span");
		rel(GnModelMath.partialSpanFactor(80.0,80.0,alpha),1.0,1e-12,"full span unchanged");
		rel(GnModelMath.centerHz(193e12,12.5e9,new int[]{2,4}),
				193.03125e12,1e-12,"center of allocated interval");

		final double f=193.4e12, other=193.5e12;
		ClassicalPowerEvolution base=new ClassicalPowerEvolution(80.0,16.0);
		ClassicalPowerEvolution withQpsk=base.withDynamicLoad(Arrays.asList(
				new ClassicalPowerEvolution.LoadSource(1e-3,0.0,80.0,f,32e9,1.0),
				new ClassicalPowerEvolution.LoadSource(1e-3,0.0,80.0,other,32e9,1.0)),80.0,0.04605);
		ClassicalPowerEvolution with64qam=base.withDynamicLoad(Arrays.asList(
				new ClassicalPowerEvolution.LoadSource(1e-3,0.0,80.0,f,32e9,1.0),
				new ClassicalPowerEvolution.LoadSource(1e-3,0.0,80.0,other,32e9,13.0/21.0)),80.0,0.04605);
		double habQpsk=LongitudinalClassicalGN.sectionPsdHabibi(withQpsk,40.0,80.0,
				f,32e9,1e-3,0.0,1.0,1.3e-3,beta,alpha,0.04605);
		double hab64=LongitudinalClassicalGN.sectionPsdHabibi(with64qam,40.0,80.0,
				f,32e9,1e-3,0.0,1.0,1.3e-3,beta,alpha,0.04605);
		check(habQpsk>0 && hab64>habQpsk,"Habibi XCI correction depends on interfering modulation Phi");
		double joh=LongitudinalClassicalGN.sectionPsd(withQpsk,40.0,80.0,
				f,32e9,1e-3,1.3e-3,beta,alpha,0.04605,2.0);
		check(joh>0 && Double.isFinite(joh),"Johannisson remains valid");
		check(Math.abs(joh-habQpsk)>1.e-24,"models are physically distinct");

		// Negative EGN values must NEVER reach a noise denominator.
		GnModelMath.resetDiagnostics();
		rel(GnModelMath.nonnegative(-1.0e-17,"verification"),0.0,0.0,"negative PSD clamp");
		check(GnModelMath.negativeClampCount()==1,"negative PSD diagnostic counter");
		rel(GnModelMath.nonnegative(1.0e-17,"verification"),1.0e-17,1e-12,"positive PSD unchanged");
		System.out.println("PASS QKDGnScientificCorrectionsVerification");
		System.out.println("johannisson_narrow_sci_kernel="+narrow);
		System.out.println("johannisson_80km_nli_psd="+joh);
		System.out.println("habibi_qpsk_80km_nli_psd="+habQpsk);
		System.out.println("habibi_64qam_80km_nli_psd="+hab64);
		System.out.println("partial_20_over_80_factor="+tail);
	}
}
