package network;

import qkd.multiband.ClassicalPowerEvolution;
import qkd.multiband.AmplifierOperatingPoint;

/**
 * Per-link, evaluation-local power/ASE propagation in one directed core.
 * Booster -> fibre -> line EDFAs -> preamplifier. No reverse-fibre traffic.
 * Each stage uses the same gain/ASE law as the QKD longitudinal model.
 * This is an EDFA surrogate, not a rate-equation/pump model.
 */
public final class LongitudinalClassicalQoT {
	private LongitudinalClassicalQoT() { }

	public static final class Result {
		public final double signalTransfer;
		public final double aseAtReceiverWHz;
		public final double boosterGain;
		public final double preGain;
		public final double lineInputPowerW;
		public final double[] spanStartRatio;
		public final double[] spanEndKm;
		public final double[] spanLengthKm;
		public final double[] spanOutputTransfer;
		private Result(double transfer, double ase, double booster, double pre,
				double inputW, double[] start, double[] end, double[] length,
				double[] outputTransfer) {
			signalTransfer=transfer; aseAtReceiverWHz=ase;
			boosterGain=booster; preGain=pre; lineInputPowerW=inputW;
			spanStartRatio=start; spanEndKm=end; spanLengthKm=length;
			spanOutputTransfer=outputTransfer;
		}
	}

	/**
     * @param snapshot snapshot of actual co-propagating channels in the core
     * @param coreLaunchW aggregate launched power for booster gain compression
     * @param boosterGain0 unsaturated booster gain in linear units
     * @param preGain0 unsaturated preamplifier gain in linear units
     * @param saturationW shared empirical output saturation power (W)
     * @param saturated select fixed versus compressed gain
     * @param nfLinear amplifier noise figure in linear units
     * @param fibreKm physical length in km
     * @param alphaKm fibre power attenuation in neper/km
     */
	public static Result evaluate(ClassicalPowerEvolution snapshot,
			double frequencyHz, double h, double coreLaunchW,
			double boosterGain0, double preGain0, double saturationW,
			boolean saturated, double nfLinear, double fibreKm, double alphaKm) {
		return evaluate(snapshot, frequencyHz, h, coreLaunchW, boosterGain0,
				preGain0, saturationW, saturated, nfLinear, fibreKm, alphaKm,
				snapshot == null ? 0.0 : snapshot.getNoiseFactorA1(),
				snapshot == null ? 0.0 : snapshot.getNoiseFactorA2());
	}

	public static Result evaluate(ClassicalPowerEvolution snapshot,
			double frequencyHz, double h, double coreLaunchW,
			double boosterGain0, double preGain0, double saturationW,
			boolean saturated, double nfLinear, double fibreKm, double alphaKm,
			double noiseFactorA1, double noiseFactorA2) {
		if (snapshot == null || !snapshot.hasDynamicLoad() || !(frequencyHz > 0.0)
				|| !(h > 0.0) || !Double.isFinite(coreLaunchW) || coreLaunchW < 0.0
				|| !(boosterGain0 >= 1.0) || !(preGain0 >= 1.0)
				|| !(saturationW > 0.0) || !(nfLinear >= 1.0)
				|| !Double.isFinite(fibreKm) || fibreKm < 0.0
				|| !Double.isFinite(alphaKm) || alphaKm < 0.0)
			throw new IllegalArgumentException("invalid longitudinal QoT parameters");

		// The same snapshot booster MUST determine line EDFA loading,
		// received signal transfer, and the longitudinal GN source powers.
		final double booster = snapshot.hasConfiguredBooster()
				? snapshot.getBoosterGainLinear()
				: Amplifier.gain(boosterGain0, coreLaunchW, saturationW, saturated);
		final double boosterAse = Amplifier.asePsd(frequencyHz, nfLinear,
				booster, h, snapshot.getBoosterInputPowerW(),
				noiseFactorA1, noiseFactorA2, saturated);
		final int count=(int)Math.floor((fibreKm-1.e-9)/snapshot.getAmplifierSpacingKm());
		final int spans=count+1;
		final double[] ratios=new double[spans];
		final double[] ends=new double[spans];
		final double[] lengths=new double[spans];
		final double[] toReceiver=new double[spans];
		double transfer=booster;
		double ase=boosterAse;
		double start=0.0;

		for(int k=0;k<spans;k++) {
			double end=k<count?(k+1)*snapshot.getAmplifierSpacingKm():fibreKm;

			if(end<start) end=start;
			ratios[k]=transfer;
			ends[k]=end;
			lengths[k]=end-start;
			final double loss=Math.exp(-alphaKm*(end-start));
			transfer*=loss; ase*=loss;

			if(k<count) {
				final AmplifierOperatingPoint point=snapshot.operatingPointAt(k+1,alphaKm,frequencyHz,h);
				transfer*=point.getGainLinear();
				ase=ase*point.getGainLinear()+point.getAsePsdWHz();
			}

			start=end;
		}

		// Effective preamp input load follows the shared dynamic power evolution;
		// finite-core aggregate is a surrogate for all channels at link output.
		final double beforeEnd=Math.max(0.0,Math.nextAfter(fibreKm,Double.NEGATIVE_INFINITY));
		final double preInput=snapshot.aggregatePowerAt(beforeEnd,alphaKm);
		final double pre=Amplifier.gain(preGain0,preInput,saturationW,saturated);
		transfer*=pre;
		ase = ase * pre + Amplifier.asePsd(frequencyHz,
				nfLinear, pre, h, preInput, noiseFactorA1, noiseFactorA2, saturated);

		for(int k=0;k<spans;k++) {
			double from=ends[k];
			double downstream=Math.exp(-alphaKm*(fibreKm-from))*pre;

			for(int j=k+1;j<=count;j++)
				downstream*=snapshot.operatingPointAt(j,alphaKm,frequencyHz,h).getGainLinear();

			if(k<count) downstream*=snapshot.operatingPointAt(k+1,alphaKm,frequencyHz,h).getGainLinear();
			toReceiver[k]=downstream;
		}

		return new Result(transfer,ase,booster,pre,preInput,
				ratios,ends,lengths,toReceiver);
	}
}
