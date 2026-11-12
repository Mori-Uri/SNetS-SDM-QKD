package network;

import gprmcsa.modulation.Modulation;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mathematical conventions shared by both GN implementations.
 * gamma is the physical Kerr coefficient in W^-1 m^-1, WITHOUT Manakov 8/9.
 * Johannisson (2014) uses PSD per polarization and gamma_eff=8*gamma/9.
 * Habibi (as in Lacerda Jr., 2024, sec. 3.1.2) uses total PSD and the
 * explicit 8/27 GN normalization and EGN modulation corrections.
 * alphaPowerPerM describes POWER attenuation; alphaField=alphaPowerPerM/2.
 * Neither convention may be changed merely to force numerical agreement.
 */
public final class GnModelMath {
	private GnModelMath() { }
	private static final AtomicLong negativeCount = new AtomicLong();
	private static final AtomicLong invalidCount = new AtomicLong();
	private static volatile double mostNegative = 0.0;

	/**
     * Safe v2-style performance cache for PURE GN integral kernels only.
     * Unlike a cached QoT result, the kernels depend on no circuit occupancy,
     * amplifier saturation, PICh admission, modulation, or simulation state.
     */
	private static final int MAX_INTEGRAL_KERNELS = 8192;
	private static final Map<KernelKey, Double> integralKernels =
			new LinkedHashMap<KernelKey, Double>(256, 0.75f, true) {
				private static final long serialVersionUID = 1L;
				@Override protected boolean removeEldestEntry(Map.Entry<KernelKey,Double> e) {
					return size() > MAX_INTEGRAL_KERNELS;
				}

			};
	private static final AtomicLong integralHits = new AtomicLong();
	private static final AtomicLong integralMisses = new AtomicLong();

	private static final class KernelKey {
		final long[] bits;
		KernelKey(int mode, double... args) {
			bits = new long[args.length + 1];
			bits[0] = mode;

			for (int k = 0; k < args.length; k++)
				bits[k + 1] = Double.doubleToLongBits(args[k]);
		}

		@Override public int hashCode() { return Arrays.hashCode(bits); }
		@Override public boolean equals(Object o) {
			return o instanceof KernelKey && Arrays.equals(bits, ((KernelKey)o).bits);
		}
	}

	private static Double cached(KernelKey key) {
		synchronized (integralKernels) {
			Double v = integralKernels.get(key);

			if (v != null) integralHits.incrementAndGet();
			else integralMisses.incrementAndGet();

			return v;
		}
	}

	private static double remember(KernelKey key, double value) {
		synchronized (integralKernels) { integralKernels.put(key, value); }
		return value;
	}

	public static long integralKernelCacheHits() { return integralHits.get(); }
	public static long integralKernelCacheMisses() { return integralMisses.get(); }
	public static void clearIntegralKernelCache() {
		synchronized (integralKernels) { integralKernels.clear(); }
		integralHits.set(0L);
		integralMisses.set(0L);
	}

	/**
	 * Validate a generated NLI power spectral density. A negative or non-finite
	 * estimate MUST NOT become zero noise: doing so inflates OSNR and can admit
	 * a circuit that violates its real QoT constraint. Explicit approximate
	 * mode therefore fails closed; adaptive mode must select a valid numerical
	 * model before reaching this guard. Counters are kept for diagnostics.
	 *
	 * @throws ArithmeticException when the calculated NLI is nonphysical.
	 */
	public static double nonnegative(double value, String context) {
		if (!Double.isFinite(value)) {
			invalidCount.incrementAndGet();
			throw new ArithmeticException("[GN] non-finite NLI in " + context + ": " + value);
		}
		if (value < 0.0) {
			if (value < mostNegative) mostNegative = value;
			negativeCount.incrementAndGet();
			throw new ArithmeticException("[GN] negative NLI in " + context + ": " + value
					+ "; select adaptive/integral instead of approximate");
		}
		return value;
	}

	/** Count of negative NLI estimates rejected instead of clamped. */
	public static long negativeRejectionCount() { return negativeCount.get(); }
	/** @deprecated Kept for existing diagnostic dashboards; no clamping occurs. */
	@Deprecated public static long negativeClampCount() { return negativeRejectionCount(); }
	public static long nonFiniteCount() { return invalidCount.get(); }
	public static double mostNegativeUnclamped() { return mostNegative; }
	public static void resetDiagnostics() { negativeCount.set(0); invalidCount.set(0); mostNegative=0.0; }

	/**
     * Johannisson Eq. (11)/(15) SCI for narrow signals, expressed in the
     * normalized prefactor of Eq. (16). For rectangular spectra its positive
     * SCI kernel is (2/pi) integral_0^rho atan(t)/t dt, rho=pi^2*|beta2|*B^2/alphaP.
     * Eq. (16) log(rho) is only asymptotic, especially inaccurate for narrow
     * channels. We use Eq. (11)'s rectangle-integral for ALL widths to avoid
     * a nonphysical discontinuity at a 28-GHz implementation threshold.
     * The identity I(rho)=pi/2*ln(rho)+I(1/rho) for rho>=1 keeps the
     * quadrature interval bounded by 1, even at very large bandwidths.
     */
	public static double johannissonSciKernel(double rho, double bandwidthHz) {
		if (!Double.isFinite(rho) || rho < 0.0 || !(bandwidthHz > 0.0))
			throw new IllegalArgumentException("invalid Johannisson SCI arguments");

		KernelKey key = new KernelKey(1, rho);
		Double old = cached(key);

		if (old != null) return old.doubleValue();

		// Reuse the same positive quadrature and asymptotic continuation
		// as Eq. (15), rather than maintaining two independent integrators.
		return remember(key, (2.0 / Math.PI) * atanIntegralPositive(rho));
	}

	/**
     * Select Johannisson SCI: Eq. (16) log(rho) (default), or the positive
     * Eq. (11)/(15) integral. The logarithmic approximation is invalid when
     * log(rho) <= 0; fall back to the integral instead of inventing zero SCI.
     * A positive log is not a guarantee of high accuracy for narrow channels.
     */
	public static double johannissonSciKernel(double rho, double bandwidthHz,
			boolean useIntegral) {
		if (useIntegral) {
			return johannissonSciKernel(rho, bandwidthHz);
		}

		if (!Double.isFinite(rho) || rho < 0.0 || !(bandwidthHz > 0.0)
				|| !Double.isFinite(bandwidthHz)) {
			throw new IllegalArgumentException("invalid Johannisson SCI arguments");
		}

		final double log = rho > 0.0 ? Math.log(rho) : Double.NaN;

		if (!Double.isFinite(log) || log <= 0.0) {
			return johannissonSciKernel(rho, bandwidthHz);
		}

		return nonnegative(log, "Johannisson Eq.16 logarithmic SCI");
	}

	/**
     * Johannisson/Agrell (2014) Eqs. (11) and (15): EXACT rectangle XCI
     * kernel in the normalization of the XCI term in Eq. (16).
     *
     * The source is rectangular and its frequency separation is absolute.
     * Unlike log((d+Bj/2)/(d-Bj/2)), this remains finite and nonnegative for
     * narrow bands. No fictitious logarithmic lower bound is required.
     *
     * xi = 4*pi*pi*abs(beta2)/alphaPowerPerMeter [s^2].
     * The integral includes the finite width of BOTH victim and interferer.
     */
	public static double johannissonXciKernel(double separationHz,
			double victimBandwidthHz, double interfererBandwidthHz, double xi) {
		if (!Double.isFinite(separationHz) || separationHz < 0.0
				|| !(victimBandwidthHz > 0.0) || !(interfererBandwidthHz > 0.0)
				|| !Double.isFinite(victimBandwidthHz)
				|| !Double.isFinite(interfererBandwidthHz)
				|| !(xi > 0.0) || !Double.isFinite(xi)) {
			throw new IllegalArgumentException("invalid Johannisson XCI parameters");
		}

		KernelKey key = new KernelKey(2, separationHz,
				victimBandwidthHz, interfererBandwidthHz, xi);
		Double old = cached(key);

		if (old != null) return old.doubleValue();

		final double scale = xi * victimBandwidthHz * 0.5;
		final double high = scale * (separationHz + interfererBandwidthHz * 0.5);
		final double low = scale * (separationHz - interfererBandwidthHz * 0.5);

		// Signed primitive is essential if the rectangle contains zero.
		double difference;

		if (low >= 1.0) {
			// Avoid cancellation between two large asymptotic primitives.
			difference = 0.5 * Math.PI * Math.log(high / low)
					+ atanIntegralPositive(1.0 / high)
					- atanIntegralPositive(1.0 / low);
		} else {
			difference = signedAtanIntegral(high) - signedAtanIntegral(low);
		}

		return remember(key, nonnegative((2.0 / Math.PI) * difference,
				"Johannisson Eq.15 XCI kernel"));
	}

	/**
     * Configurable Johannisson/Agrell XCI kernel. The logarithmic option is
     * the asymptotic Eq. (16) and is the default for SNetS simulations.
     * The integral option retains the more accurate Eqs. (11)/(15).
     *
     * If two rectangular channels overlap, Eq. (16) is undefined. In that
     * case both selections use the positive integral, never a manufactured
     * logarithm floor or negative NLI contribution.
     */
	public static double johannissonXciKernel(double separationHz,
			double victimBandwidthHz, double interfererBandwidthHz,
			double xi, boolean useIntegral) {
		if (useIntegral) {
			return johannissonXciKernel(separationHz, victimBandwidthHz,
					interfererBandwidthHz, xi);
		}

		if (!Double.isFinite(separationHz) || separationHz < 0.0
				|| !(victimBandwidthHz > 0.0) || !Double.isFinite(victimBandwidthHz)
				|| !(interfererBandwidthHz > 0.0) || !Double.isFinite(interfererBandwidthHz)
				|| !(xi > 0.0) || !Double.isFinite(xi)) {
			throw new IllegalArgumentException("invalid Johannisson XCI parameters");
		}

		final double leftEdge = separationHz - interfererBandwidthHz / 2.0;

		if (leftEdge <= 0.0) {
			return johannissonXciKernel(separationHz, victimBandwidthHz,
					interfererBandwidthHz, xi);
		}

		// Numerically stable version of log((d+Bj/2)/(d-Bj/2)).
		return nonnegative(Math.log1p(interfererBandwidthHz / leftEdge),
				"Johannisson Eq.16 logarithmic XCI");
	}

    /** Strict three-mode selection: an invalid logarithm is rejected in
     *  approximate mode, while adaptive selects the finite positive integral. */
    public static double johannissonSciKernel(double rho,double widthHz,
            String mode,double narrowbandGHz,boolean legacyIntegral) {
        if(NliIntegrationMode.LEGACY.equals(mode))
            return johannissonSciKernel(rho,widthHz,legacyIntegral);
        if(!(rho>=0.0) || !Double.isFinite(rho) || !(widthHz>0.0))
            throw new IllegalArgumentException("invalid Johannisson SCI");
        boolean invalid=rho<=1.0;
        if(NliIntegrationMode.useJohannissonIntegral(mode,false,widthHz,narrowbandGHz,invalid))
            return johannissonSciKernel(rho,widthHz);
        return nonnegative(rho>0.0?Math.log(rho):Double.NaN,
                "Johannisson strict logarithmic SCI");
    }
    public static double johannissonXciKernel(double separationHz,double victimWidthHz,
            double sourceWidthHz,double xi,String mode,double narrowbandGHz,
            boolean legacyIntegral) {
        if(NliIntegrationMode.LEGACY.equals(mode))
            return johannissonXciKernel(separationHz,victimWidthHz,sourceWidthHz,xi,
                    legacyIntegral);
        if(!(victimWidthHz>0.0) || !(sourceWidthHz>0.0)
                || !Double.isFinite(victimWidthHz) || !Double.isFinite(sourceWidthHz)
                || !(separationHz>=0.0) || !Double.isFinite(separationHz)
                || !(xi>0.0) || !Double.isFinite(xi))
            throw new IllegalArgumentException("invalid Johannisson XCI");
        double left=separationHz-sourceWidthHz*0.5;
        boolean invalid=left<=0.0;
        if(NliIntegrationMode.INTEGRAL.equals(mode)
                || (NliIntegrationMode.ADAPTIVE.equals(mode)
                        && (victimWidthHz<=narrowbandGHz*1e9
                                || sourceWidthHz<=narrowbandGHz*1e9 || invalid)))
            return johannissonXciKernel(separationHz,victimWidthHz,sourceWidthHz,xi);
        if(invalid) throw new ArithmeticException("[GN] Johannisson approximate XCI "
                + "undefined for overlapping frequencies; choose adaptive/integral");
        return nonnegative(Math.log1p(sourceWidthHz/left),
                "Johannisson strict logarithmic XCI");
    }

	private static double signedAtanIntegral(double x) {
		return Math.copySign(atanIntegralPositive(Math.abs(x)), x);
	}

	/** A(x) = integral_0^x atan(t)/t dt for nonnegative x. */
	private static double atanIntegralPositive(double x) {
		if (x == 0.0) return 0.0;

		if (x > 1.0) {
			return 0.5 * Math.PI * Math.log(x)
					+ atanIntegralPositive(1.0 / x);
		}

		final int n = 96;
		final double dt = x / n;
		double sum = 0.0;

		for (int i = 0; i <= n; i++) {
			final double t = i * dt;
			final double integrand = t == 0.0 ? 1.0 : Math.atan(t) / t;
			sum += (i == 0 || i == n ? 1.0 : (i % 2 == 0 ? 2.0 : 4.0))
					* integrand;
		}

		return sum * dt / 3.0;
	}

	/** Actual positive fibre lengths, including a partial final amplifier span. */
	public static double[] spanLengthsKm(double linkKm, double nominalKm) {
		if (!(linkKm > 0.0) || !(nominalKm > 0.0)
				|| !Double.isFinite(linkKm) || !Double.isFinite(nominalKm))
			throw new IllegalArgumentException("invalid span lengths");
		List<Double> lengths = new ArrayList<Double>();
		double rest=linkKm;
		while (rest > Math.max(1e-10, linkKm * 1e-12)) {
			if (lengths.size()>100000) throw new IllegalArgumentException("too many spans");
			double len=Math.min(nominalKm, rest);
			lengths.add(len);
			rest-=len;
		}

		double[] out=new double[lengths.size()];

		for(int i=0;i<out.length;i++) out[i]=lengths.get(i);

		return out;
	}

	/** Johannisson Eq. (3) finite-span effective-length correction, normalized
     * to an otherwise identical nominal legacy span. For partial spans this
     * avoids multiplying the complete-span GN-PSD unchanged.
     * Does not reproduce coherent GN phase accumulation of a general short span.
     */
	public static double partialSpanFactor(double km, double nominalKm, double alphaPowerPerM) {
		if (!(km>0.0) || !(nominalKm>0.0) || !(alphaPowerPerM>0.0))
			throw new IllegalArgumentException("invalid partial span correction");
		double x=-Math.expm1(-alphaPowerPerM*km*1000.0);
		double ref=-Math.expm1(-alphaPowerPerM*nominalKm*1000.0);

		return (x/ref)*(x/ref);
	}

	/** Phi coefficients from Lacerda Jr. thesis, Tab. 5 (Poggiolini/Jiang). */
	public static double phi(Modulation modulation) {
		if (modulation==null) return 0.0;
		double m=modulation.getM();

		if(m==2.0 || m==4.0) return 1.0; // BPSK is SNetS legacy extension
		if(m==8.0) return 2.0/3.0;

		if(m==16.0) return 17.0/25.0;

		if(m==32.0) return 69.0/100.0;

		if(m==64.0) return 13.0/21.0;

		if(m>64.0) return 3.0/5.0; // SNetS extension, not in thesis Table 5
		return 0.0;
	}

	/** Physical carrier on the occupied slot region; guard slots do not alter
     * the bandwidth used to compute signal PSD (which is modulation dependent).
     */
	public static double centerHz(double lowerHz, double slotHz, int[] slots) {
		if (slots == null || slots.length!=2 || slots[0]<1 || slots[1]<slots[0]
				|| !(lowerHz>0.0) || !(slotHz>0.0))
			throw new IllegalArgumentException("invalid optical slot interval");

		return lowerHz+slotHz*(0.5*(slots[0]+slots[1]-1.0));
	}
}
