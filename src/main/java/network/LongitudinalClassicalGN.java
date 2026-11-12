package network;

import qkd.multiband.ClassicalPowerEvolution;

/**
 * Span-local incoherent GN approximation for dynamic DCh/PICh spectra.
 * Uses the selected Johannisson SCI/XCI spectral kernels with actual per-position
 * channel powers, not the legacy (number of spans) multiplier. This is not
 * coherent EGN, ISRS-GN, or a calibrated inter-core nonlinear model.
 */
public final class LongitudinalClassicalGN {
	private LongitudinalClassicalGN() { }

	/** Return generated NLI PSD in W/Hz from a short fibre section.
     * All powers are total optical W; two-pol normalization is controlled by pol.
     * alphaPerM and beta2 are consistent with the legacy SNetS GN formulas.
     */
	public static double sectionPsd(ClassicalPowerEvolution snapshot,
			double zKm, double dzKm, double victimFrequencyHz,
			double victimBandwidthHz, double victimLaunchPowerW,
			double gamma, double beta2, double alphaPerM,
			double alphaPerKm, double pol) {
		return sectionPsd(snapshot, zKm, dzKm, victimFrequencyHz, victimBandwidthHz,
				victimLaunchPowerW, 0.0, gamma, beta2, alphaPerM,
				alphaPerKm, pol, false, false);
	}

	/** Reuse the SNetS GN kernel for a PICh inserted at a trusted node. */
	public static double sectionPsd(ClassicalPowerEvolution snapshot,
			double zKm, double dzKm, double victimFrequencyHz,
			double victimBandwidthHz, double victimLaunchPowerW,
			double victimStartKm, double gamma, double beta2,
			double alphaPerM, double alphaPerKm, double pol) {
		return sectionPsd(snapshot, zKm, dzKm, victimFrequencyHz,
				victimBandwidthHz, victimLaunchPowerW, victimStartKm,
				gamma, beta2, alphaPerM, alphaPerKm, pol, false, false);
	}

	/** Compatibility overload: the v20 boolean selected only XCI. */
	public static double sectionPsd(ClassicalPowerEvolution snapshot,
			double zKm, double dzKm, double victimFrequencyHz,
			double victimBandwidthHz, double victimLaunchPowerW,
			double victimStartKm, double gamma, double beta2,
			double alphaPerM, double alphaPerKm, double pol,
			boolean johannissonUseIntegral) {
		return sectionPsd(snapshot, zKm, dzKm, victimFrequencyHz,
				victimBandwidthHz, victimLaunchPowerW, victimStartKm,
				gamma, beta2, alphaPerM, alphaPerKm, pol,
				true, johannissonUseIntegral);
	}

    /** Explicit three-mode NLI selection for a physical longitudinal section. */
    public static double sectionPsd(ClassicalPowerEvolution snapshot,double zKm,
            double dzKm,double victimFrequencyHz,double victimBandwidthHz,
            double victimLaunchPowerW,double victimStartKm,double gamma,double beta2,
            double alphaPerM,double alphaPerKm,double pol,
            String mode,double narrowbandGHz,boolean legacySci,boolean legacyXci) {
        return sectionPsdInternal(snapshot,zKm,dzKm,victimFrequencyHz,
                victimBandwidthHz,victimLaunchPowerW,victimStartKm,gamma,beta2,
                alphaPerM,alphaPerKm,pol,legacySci,legacyXci,
                mode,narrowbandGHz);
    }

	/** Select Johannisson SCI and XCI independently for a longitudinal section. */
	public static double sectionPsd(ClassicalPowerEvolution snapshot,
			double zKm, double dzKm, double victimFrequencyHz,
			double victimBandwidthHz, double victimLaunchPowerW,
			double victimStartKm, double gamma, double beta2,
			double alphaPerM, double alphaPerKm, double pol,
	        boolean sciUseIntegral, boolean xciUseIntegral) {
        return sectionPsdInternal(snapshot,zKm,dzKm,victimFrequencyHz,
                victimBandwidthHz,victimLaunchPowerW,victimStartKm,gamma,beta2,
                alphaPerM,alphaPerKm,pol,sciUseIntegral,xciUseIntegral,
                NliIntegrationMode.LEGACY,25.0);
    }
    private static double sectionPsdInternal(ClassicalPowerEvolution snapshot,
            double zKm,double dzKm,double victimFrequencyHz,
            double victimBandwidthHz,double victimLaunchPowerW,double victimStartKm,
            double gamma,double beta2,double alphaPerM,double alphaPerKm,double pol,
            boolean sciUseIntegral,boolean xciUseIntegral,String mode,double narrowbandGHz) {
        if (snapshot == null || !(victimStartKm >= 0.0) || zKm < victimStartKm
				|| !(dzKm > 0.0) || !(victimBandwidthHz > 0.0)
				|| !(victimLaunchPowerW >= 0.0) || !(gamma >= 0.0)
				|| !(Math.abs(beta2) > 0.0) || !(alphaPerM > 0.0)
				|| !(pol >= 1.0)) throw new IllegalArgumentException("invalid GN section");
		final double pi=Math.PI;
		final double victimPower=victimLaunchPowerW * snapshot.powerRatio(
				victimStartKm,zKm,alphaPerKm,victimFrequencyHz);

		if (victimPower == 0.0) return 0.0;
		final double gi=victimPower/(victimBandwidthHz*pol);
		final double beta=Math.abs(beta2);
		final double ro=victimBandwidthHz*victimBandwidthHz
				*pi*pi*beta/alphaPerM;
		double sum=gi*gi*GnModelMath.johannissonSciKernel(ro, victimBandwidthHz, mode, narrowbandGHz, sciUseIntegral);

		for (ClassicalPowerEvolution.LoadSource source: snapshot.dynamicSources()) {
			if (zKm < source.startKm || zKm >= source.endKm
					|| !(source.bandwidthHz > 0.0) || !Double.isFinite(source.frequencyHz))
				continue;
			double delta=Math.abs(source.frequencyHz-victimFrequencyHz);

			// Suppress the victim itself in the cross term. In overlapping
			// bands the cross interaction needs a spectral overlap model.
			if (delta < 0.5*(victimBandwidthHz+source.bandwidthHz)) continue;
			double power=source.launchPowerW*snapshot.powerRatio(source.startKm,
					zKm,alphaPerKm,source.frequencyHz);
			double gj=power/(source.bandwidthHz*pol);
			final double xi = 4.0 * Math.PI * Math.PI * beta / alphaPerM;
			sum += gj * gj * GnModelMath.johannissonXciKernel(
					delta, victimBandwidthHz, source.bandwidthHz, xi,
                    mode,narrowbandGHz,xciUseIntegral);
		}

		final double kernel=gi*(3.0*64.0*gamma*gamma)
				/(2.0*81.0*pi*alphaPerM*beta);

		// Finite-section correction: GN kernel is normalized to a long span.
		final double finite= -Math.expm1(-2.0*alphaPerM*dzKm*1000.0);

		return GnModelMath.nonnegative(kernel*sum*finite, "Johannisson longitudinal");
	}

	/**
     * EGN approximation used by the legacy Habibi implementation (Lacerda
     * Junior 2024, Eqs. 3.23-3.31) evaluated for ONE physical subspan.
     * Input zKm is its midpoint; span-local power is read at its ENTRANCE
     * (after the booster/preceding amplifier), as assumed by the analytical
     * EGN section formula. Use of midpoint power would double-discount loss.
     * This is an incoherent, locally flat-power subspan approximation, not
     * an exact EGN integral through continuously varying amplifier gain.
     */
	public static double sectionPsdHabibi(ClassicalPowerEvolution snapshot,
            double zKm, double subspanKm, double victimFrequencyHz,
            double victimBandwidthHz, double victimLaunchPowerW,
            double victimStartKm, double victimPhi, double gamma, double beta2,
            double alphaPowerPerM, double alphaPerKm) {
        return sectionPsdHabibi(snapshot,zKm,subspanKm,victimFrequencyHz,
                victimBandwidthHz,victimLaunchPowerW,victimStartKm,victimPhi,
                gamma,beta2,alphaPowerPerM,alphaPerKm,false,25.0,16,6);
    }

    /** Explicit Habibi integration modes for longitudinal GN. */
    public static double sectionPsdHabibi(ClassicalPowerEvolution snapshot,
            double zKm,double subspanKm,double victimFrequencyHz,
            double victimBandwidthHz,double victimLaunchPowerW,double victimStartKm,
            double victimPhi,double gamma,double beta2,double alphaPowerPerM,
            double alphaPerKm,boolean hybridEnabled,double narrowbandGHz,
            int order,int receiverOrder,String mode) {
        return sectionPsdHabibiInternal(snapshot,zKm,subspanKm,victimFrequencyHz,
                victimBandwidthHz,victimLaunchPowerW,victimStartKm,victimPhi,
                gamma,beta2,alphaPowerPerM,alphaPerKm,hybridEnabled,narrowbandGHz,
                order,receiverOrder,mode);
    }

    /** Enable Poggiolini GN SCI/XCI quadrature only when the selected Habibi
     *  approximation is negative or the ACTUAL CUT band is narrow. MCI from
     *  multiple interferers is not included in the hybrid: see reference test. */
    public static double sectionPsdHabibi(ClassicalPowerEvolution snapshot,
            double zKm, double subspanKm, double victimFrequencyHz,
            double victimBandwidthHz, double victimLaunchPowerW,
            double victimStartKm, double victimPhi, double gamma, double beta2,
            double alphaPowerPerM, double alphaPerKm,
            boolean hybridEnabled, double narrowbandGHz, int order, int receiverOrder) {
        return sectionPsdHabibiInternal(snapshot,zKm,subspanKm,victimFrequencyHz,
                victimBandwidthHz,victimLaunchPowerW,victimStartKm,victimPhi,
                gamma,beta2,alphaPowerPerM,alphaPerKm,hybridEnabled,narrowbandGHz,
                order,receiverOrder,NliIntegrationMode.LEGACY);
    }
    private static double sectionPsdHabibiInternal(ClassicalPowerEvolution snapshot,
            double zKm,double subspanKm,double victimFrequencyHz,
            double victimBandwidthHz,double victimLaunchPowerW,double victimStartKm,
            double victimPhi,double gamma,double beta2,double alphaPowerPerM,
            double alphaPerKm,boolean hybridEnabled,double narrowbandGHz,
            int order,int receiverOrder,String mode) {
        if (snapshot==null || !(subspanKm>0.0) || !(victimBandwidthHz>0.0)
				|| !(alphaPowerPerM>0.0) || !(Math.abs(beta2)>0.0)
				|| !(victimPhi>=0.0) || zKm<victimStartKm)
			throw new IllegalArgumentException("invalid Habibi longitudinal section");
		final double absBeta=Math.abs(beta2);
		final double alphaField=alphaPowerPerM/2.0;
		final double lengthM=subspanKm*1000.0;
		final double leff=-Math.expm1(-alphaPowerPerM*lengthM)/alphaPowerPerM;
		final double e=8.0*gamma*gamma*leff*leff*alphaPowerPerM/
				(27.0*Math.PI*absBeta);
		final double entrance=Math.max(victimStartKm,zKm-0.5*subspanKm);
		final double probe=Math.nextUp(entrance); // include booster if launched at z=0
		final double p=victimLaunchPowerW * snapshot.powerRatio(
				victimStartKm,probe,alphaPerKm,victimFrequencyHz);

		if (p==0.0) return 0.0;
		final double gi=p/victimBandwidthHz; // Habibi: total PSD, not per-pol
		final double argument=(Math.PI*Math.PI*absBeta*victimBandwidthHz*
				victimBandwidthHz)/(2.0*alphaPowerPerM);
		final double sci=e*asinh(argument);
		final double corrSci=e*5.0*victimPhi/(3.0*alphaField*lengthM);
		double result=gi*gi*gi*(sci-corrSci);
		final double k=Math.PI*Math.PI*absBeta*victimBandwidthHz/alphaPowerPerM;
        final java.util.List<Poggiolini2017GnIntegral.Channel> egnSources =
                new java.util.ArrayList<Poggiolini2017GnIntegral.Channel>();

		for(ClassicalPowerEvolution.LoadSource src:snapshot.dynamicSources()) {
			if(zKm<src.startKm || zKm>=src.endKm || !(src.bandwidthHz>0.0)
					|| !Double.isFinite(src.frequencyHz)) continue;
			double d=Math.abs(src.frequencyHz-victimFrequencyHz);

			if(d<0.5*(victimBandwidthHz+src.bandwidthHz)) continue;
			double pj=src.launchPowerW*snapshot.powerRatio(src.startKm,
					Math.max(probe,Math.nextUp(src.startKm)),alphaPerKm,src.frequencyHz);
			double gj=pj/src.bandwidthHz;
            egnSources.add(PhysicalLayer.integralChannel(src.frequencyHz,
                    src.bandwidthHz,pj,src.modulationPhi,mode));
			double psi=e*(asinh(k*(d+0.5*src.bandwidthHz))
					-asinh(k*(d-0.5*src.bandwidthHz)));

			// Unsupported modulation Phi is recorded as zero, not invented.
			double phi=Double.isFinite(src.modulationPhi)?src.modulationPhi:0.0;
			double correction=e*(5.0*phi*src.bandwidthHz)/
					(6.0*alphaField*lengthM*d);
			result+=gi*gj*gj*(psi-correction);
		}

        if(NliIntegrationMode.useHabibiIntegral(mode,hybridEnabled,
                victimBandwidthHz,narrowbandGHz,result)) {
            Poggiolini2017GnIntegral.Channel cut=PhysicalLayer.integralChannel(
                    victimFrequencyHz,victimBandwidthHz,p,victimPhi,mode);
            if(cut!=null) {
                Poggiolini2017GnIntegral.Fiber fiber=new Poggiolini2017GnIntegral.Fiber(
                        alphaPowerPerM*1000.0*(10.0/Math.log(10.0)),
                        gamma*1000.0,beta2*1e27,subspanKm,1);
                result=PhysicalLayer.evaluate(fiber,cut,egnSources,result,
                        NliIntegrationMode.INTEGRAL.equals(mode)
                                ? Double.POSITIVE_INFINITY : narrowbandGHz,
                        true,order,receiverOrder).corrected;
            } else {
                if(NliIntegrationMode.INTEGRAL.equals(mode))
                    throw new IllegalArgumentException("GN integral unavailable for CUT");
                PhysicalLayer.warnUnsupportedCut(victimPhi,victimBandwidthHz);
                throw new ArithmeticException("[GN] longitudinal Habibi fallback unavailable for Phi="+victimPhi);
            }
        }
        return GnModelMath.nonnegative(result,"Habibi longitudinal + GN integral fallback");
	}

	private static double asinh(double x) {
		return Math.log(x+Math.sqrt(1.0+x*x));
	}
}
