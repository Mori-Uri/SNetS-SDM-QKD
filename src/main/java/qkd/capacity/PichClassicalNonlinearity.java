package qkd.capacity;

import network.Circuit;
import network.Core;
import network.Link;

/**
 * First-order same-core PICh -> DCh cross-channel NLI (XCI).
 * Only PICh sources in the victim DCh core are considered. Orthogonal cores
 * are intentionally not treated as same-core Kerr sources.
 *
 * A PICh occupies one QKDSpan rather than the full SNetS physical link;
 * contributions are scaled by its length fraction as a first-order mean
 * longitudinal occupancy approximation, NOT by blindly adding one full-link
 * amplifier span per PICh. Full gain maps and pulse modulation corrections
 * require a separate calibrated physical model.
 */
public final class PichClassicalNonlinearity {
	private PichClassicalNonlinearity() { }

	public static double crossTermJohannisson(Link link, int coreId, double victimFrequencyHz,
			double victimPsdWPerHz, double victimMi, double lowerFrequencyHz,
			double slotBandwidthHz, double polarizationModes) {
		return crossTermJohannisson(link, coreId, victimFrequencyHz, victimPsdWPerHz,
				victimMi, lowerFrequencyHz, slotBandwidthHz, polarizationModes,
				0.0, link.getDistance());
	}

	public static double crossTermJohannisson(Link link, int coreId, double victimFrequencyHz,
			double victimPsdWPerHz, double victimMi, double lowerFrequencyHz,
			double slotBandwidthHz, double polarizationModes, double fromKm, double toKm) {
		if (victimMi == 0.0) return 0.0;
		final Core core = link.getCore(coreId);

		if (core == null) return 0.0;
		double sum = 0.0;

		for (Circuit source : core.getAuxiliaryClassicalCircuitList()) {
			if (!(source instanceof QKDPichCircuit)) continue;
			QKDPichCircuit pich = (QKDPichCircuit) source;

			if (pich.getPhysicalLink() != link) continue;
			final double bandwidth = bandwidthHz(pich, slotBandwidthHz);
			final double gj = pich.getLaunchPowerLinear() / polarizationModes / bandwidth;
			final double delta = Math.abs(victimFrequencyHz - centerHz(pich, lowerFrequencyHz, slotBandwidthHz));
			sum += overlapFraction(pich,fromKm,toKm) * gj * gj * logFrequencyRatio(delta, bandwidth);
		}

		return victimMi * sum;
	}

	/**
     * Johannisson Eq. (11)/(15) PICh XCI. A PICh does not turn into a full-link
     * source: only its longitudinal overlap with the evaluated span contributes.
     */
	public static double crossTermJohannissonExact(Link link, int coreId,
			double victimFrequencyHz, double victimBandwidthHz,
			double victimMi, double lowerFrequencyHz, double slotBandwidthHz,
			double polarizationModes, double xi, double fromKm, double toKm) {
		return crossTermJohannissonKernel(link, coreId, victimFrequencyHz,
				victimBandwidthHz, victimMi, lowerFrequencyHz, slotBandwidthHz,
				polarizationModes, xi, fromKm, toKm, true);
	}

    /** Explicit NLI policy for all PICh-to-DCh XCI contributions. */
    public static double crossTermJohannissonKernel(Link link,int coreId,
            double victimFrequencyHz,double victimBandwidthHz,double victimMi,
            double lowerFrequencyHz,double slotBandwidthHz,double polarizationModes,
            double xi,double fromKm,double toKm,String mode,double narrowbandGHz,
            boolean legacyUseIntegral) {
        if(victimMi==0.0) return 0.0;
        final Core core=link.getCore(coreId);
        if(core==null) return 0.0;
        double sum=0.0;
        for(Circuit source:core.getAuxiliaryClassicalCircuitList()) {
            if(!(source instanceof QKDPichCircuit)) continue;
            QKDPichCircuit pich=(QKDPichCircuit)source;
            if(pich.getPhysicalLink()!=link) continue;
            double width=bandwidthHz(pich,slotBandwidthHz);
            double gj=pich.getLaunchPowerLinear()/polarizationModes/width;
            double delta=Math.abs(victimFrequencyHz-
                    centerHz(pich,lowerFrequencyHz,slotBandwidthHz));
            double xci=network.GnModelMath.johannissonXciKernel(
                    delta,victimBandwidthHz,width,xi,mode,narrowbandGHz,legacyUseIntegral);
            sum+=overlapFraction(pich,fromKm,toKm)*gj*gj*xci;
        }
        return network.GnModelMath.nonnegative(victimMi*sum,"Johannisson PICh XCI");
    }

	/** The PICh uses exactly the same XCI kernel selection as ordinary DChs. */
	public static double crossTermJohannissonKernel(Link link, int coreId,
			double victimFrequencyHz, double victimBandwidthHz,
			double victimMi, double lowerFrequencyHz, double slotBandwidthHz,
			double polarizationModes, double xi, double fromKm, double toKm,
			boolean johannissonUseIntegral) {
		if (victimMi == 0.0) return 0.0;

		final Core core = link.getCore(coreId);

		if (core == null) return 0.0;

		double sum = 0.0;

		for (Circuit source : core.getAuxiliaryClassicalCircuitList()) {
			if (!(source instanceof QKDPichCircuit)) continue;

			final QKDPichCircuit pich = (QKDPichCircuit) source;

			if (pich.getPhysicalLink() != link) continue;

			final double bandwidth = bandwidthHz(pich, slotBandwidthHz);
			final double sourcePsd = pich.getLaunchPowerLinear()
					/ polarizationModes / bandwidth;
			final double delta = Math.abs(victimFrequencyHz
					- centerHz(pich, lowerFrequencyHz, slotBandwidthHz));
			final double kernel = network.GnModelMath.johannissonXciKernel(
					delta, victimBandwidthHz, bandwidth, xi,
					johannissonUseIntegral);

			sum += overlapFraction(pich, fromKm, toKm)
					* sourcePsd * sourcePsd * kernel;
		}

		return network.GnModelMath.nonnegative(victimMi * sum,
				"Johannisson PICh Eq.15 XCI");
	}

	/** Habibi GN XCI without a modulation-dependent PICh correction term. */
	public static double crossTermHabibi(Link link, int coreId, double victimFrequencyHz,
			double victimBandwidthHz, double victimPsdWPerHz,
			double coefficientE, double alphaFieldPerMeter, double absBeta2,
			double lowerFrequencyHz, double slotBandwidthHz) {
		return crossTermHabibi(link, coreId, victimFrequencyHz, victimBandwidthHz,
				victimPsdWPerHz, coefficientE, alphaFieldPerMeter, absBeta2,
				lowerFrequencyHz, slotBandwidthHz, 0.0, link.getDistance());
	}

	public static double crossTermHabibi(Link link, int coreId, double victimFrequencyHz,
			double victimBandwidthHz, double victimPsdWPerHz, double coefficientE,
			double alphaFieldPerMeter, double absBeta2, double lowerFrequencyHz,
			double slotBandwidthHz, double fromKm, double toKm) {
		final Core core = link.getCore(coreId);

		if (core == null) return 0.0;
		double sum = 0.0;

		for (Circuit source : core.getAuxiliaryClassicalCircuitList()) {
			if (!(source instanceof QKDPichCircuit)) continue;
			QKDPichCircuit pich = (QKDPichCircuit) source;

			if (pich.getPhysicalLink() != link) continue;
			final double bw = bandwidthHz(pich, slotBandwidthHz);
			final double gj = pich.getLaunchPowerLinear() / bw;
			final double delta = Math.abs(victimFrequencyHz - centerHz(pich, lowerFrequencyHz, slotBandwidthHz));
			final double r = (Math.PI * Math.PI * absBeta2 * victimBandwidthHz) / (2.0 * alphaFieldPerMeter);
			final double pnm = coefficientE * (asinh(r * (delta + bw / 2.0))
					- asinh(r * (delta - bw / 2.0)));
			sum += overlapFraction(pich,fromKm,toKm) * victimPsdWPerHz * gj * gj * Math.max(0.0, pnm);
		}

		return sum;
	}

	/** Habibi/Poggiolini modulation-dependent XCI correction, thesis Eq. 3.31.
     * Subtracted from GN XCI BEFORE total NLI is clamped nonnegative.
     */
	public static double habibiCorrection(Link link, int coreId,
			double victimFrequencyHz, double victimPsdWHz, double e,
			double alphaFieldPerMeter, double spanMeters, double lowerFrequencyHz,
			double slotBandwidthHz, double fromKm, double toKm) {
		Core core=link.getCore(coreId);

		if (core==null) return 0.0;
		double result=0.0;

		for (Circuit source:core.getAuxiliaryClassicalCircuitList()) {
			if (!(source instanceof QKDPichCircuit)) continue;
			QKDPichCircuit pich=(QKDPichCircuit)source;

			if (pich.getPhysicalLink()!=link || pich.getModulation()==null) continue;
			double phi=network.GnModelMath.phi(pich.getModulation());

			if(phi==0.0) continue;
			double width=bandwidthHz(pich,slotBandwidthHz);
			double delta=Math.abs(victimFrequencyHz-centerHz(pich,lowerFrequencyHz,slotBandwidthHz));

			if(delta<=0.5*width) continue; // requires a separate overlapping-channel model
			double gj=pich.getLaunchPowerLinear()/width;
			double w=e*5.0*phi*width/(6.0*alphaFieldPerMeter*spanMeters*delta);
			result+=overlapFraction(pich,fromKm,toKm)*victimPsdWHz*gj*gj*w;
		}

		return result;
	}

	/** Physical PICh overlap with one amplifier span, normalized to that span. */
	private static double overlapFraction(QKDPichCircuit pich,double fromKm,double toKm) {
		if(!(toKm>fromKm)) return 0.0;
		double left=Math.max(fromKm,pich.getSpan().getStartKm());
		double right=Math.min(toKm,pich.getSpan().getEndKm());

		return Math.max(0.0,right-left)/(toKm-fromKm);
	}

	/** Mean source power on a directed link, for approximate EDFA saturation. */
	public static double meanPichPowerW(Link link, int coreId) {
		final Core core = link.getCore(coreId);

		if (core == null) return 0.0;
		double total = 0.0;

		for (Circuit source : core.getAuxiliaryClassicalCircuitList()) {
			if (source instanceof QKDPichCircuit) {
				QKDPichCircuit pich = (QKDPichCircuit) source;

				if (pich.getPhysicalLink() == link)
					total += pich.getLaunchPowerLinear() * lengthFraction(pich, link);
			}
		}

		return total;
	}

	private static double lengthFraction(QKDPichCircuit source, Link link) {
		return Math.max(0.0, Math.min(1.0, source.getSpan().getLengthKm() / link.getDistance()));
	}

	private static double bandwidthHz(QKDPichCircuit pich, double slotHz) {
		return pich.getOpticalBandwidthHz(slotHz);
	}

	private static double centerHz(QKDPichCircuit pich, double lowerHz, double slotHz) {
		return pich.getCenterFrequencyHz(lowerHz, slotHz);
	}

	private static double logFrequencyRatio(double delta, double width) {
		final double a = delta + width / 2.0;
		final double b = Math.max(1e-30, delta - width / 2.0);

		return Math.log(Math.max(1.000001, a / b));
	}

	private static double asinh(double x) { return Math.log(x + Math.sqrt(x * x + 1.0)); }
}
