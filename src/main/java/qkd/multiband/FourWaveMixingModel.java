package qkd.multiband;

/**
 * Parte 46: four-wave mixing (FWM) of continuous-wave (CW) classical channels, the "approximate FWM model" used by
 * the DWDM-QKD literature ([PETERS09], [NIU18], and the FWM curves of [MEHDI24] Fig. 3).
 *
 * <pre>
 *   P_ijk(L) = (eta / 9) D^2 gamma^2 P_i P_j P_k e^{-a L} L_eff^2,      f_ijk = f_i + f_j - f_k
 *   eta = a^2 / (a^2 + db^2) [1 + 4 e^{-a L} sin^2(db L / 2) / (1 - e^{-a L})^2]
 *   db  = (2 pi lambda^2 / c) |f_i - f_k| |f_j - f_k| [D_c + (lambda^2 / 2c) S (|f_i - f_k| + |f_j - f_k|)]
 * </pre>
 *
 * <p>D = 3 for the degenerate case (i = j) and 6 for three distinct channels; a is the loss (neper/km), D_c the
 * dispersion and S the dispersion slope. With {@code peak = true} the oscillating factor sin^2 is replaced by 1
 * (upper envelope, as plotted in [PETERS09] Fig. 1).</p>
 *
 * <p>This model treats every channel as a single tone: a product only counts when it falls inside the QCh filter.
 * The GN model of {@link NonlinearInterferenceModel} treats the channels as modulated (Gaussian) signals with a
 * bandwidth, so their products are spread over about three channel widths; for narrow channels both agree within
 * about 2.5 dB (verification VMB-12b). The simulator uses the GN model; this class exists for the verification
 * against the literature.</p>
 *
 * <p>Referências: [PETERS09] N. A. Peters et al., New J. Phys. 11, 045012, 2009 (eqs. 1-3); [NIU18] J.-N. Niu et
 * al., Appl. Opt. 57(27), 7987, 2018 (eqs. 2-4); K. Inoue, J. Lightw. Technol. 10(11), 1992.</p>
 */
public final class FourWaveMixingModel {

	private static final double C = 299792458.0;

	private FourWaveMixingModel() {
	}

	/**
     * Power of one FWM product at the fibre output.
     *
     * @param fi frequency of pump i (Hz)
     * @param fj frequency of pump j (Hz); equal to fi for the degenerate case
     * @param fk frequency of pump k (Hz)
     * @param pi power of pump i (W)
     * @param pj power of pump j (W)
     * @param pk power of pump k (W)
     * @param lengthKm fibre length (km)
     * @param alphaDbPerKm loss (dB/km)
     * @param gammaPerWKm non-linear coefficient (1/(W km))
     * @param dispersionPsNmKm dispersion D_c at {@code lambdaNm} (ps/(nm km))
     * @param slopePsNm2Km dispersion slope (ps/(nm^2 km))
     * @param lambdaNm reference wavelength of D_c (nm)
     * @param peak true for the upper envelope (sin^2 = 1)
     * @return product power in W
     */
	public static double productPowerW(double fi, double fj, double fk, double pi, double pj, double pk,
			double lengthKm, double alphaDbPerKm, double gammaPerWKm, double dispersionPsNmKm, double slopePsNm2Km,
			double lambdaNm, boolean peak) {
		final double a = alphaDbPerKm * Math.log(10.0) / 10.0 / 1e3; // 1/m
		final double l = lengthKm * 1e3;
		final double lambda = lambdaNm * 1e-9;
		final double dc = dispersionPsNmKm * 1e-6; // s/m^2
		final double s = slopePsNm2Km * 1e3; // s/m^3
		final double dik = Math.abs(fi - fk);
		final double djk = Math.abs(fj - fk);
		final double db = 2 * Math.PI * lambda * lambda / C * dik * djk
				* (dc + lambda * lambda / (2 * C) * s * (dik + djk));
		final double loss = Math.exp(-a * l);
		final double leff = -Math.expm1(-a * l) / a;
		final double sin2 = peak ? 1.0 : Math.pow(Math.sin(db * l / 2), 2);
		final double eta = a * a / (a * a + db * db) * (1 + 4 * loss * sin2 / Math.pow(-Math.expm1(-a * l), 2));
		final double degeneracy = fi == fj ? 3.0 : 6.0;
		final double gamma = gammaPerWKm / 1e3; // 1/(W m)
		return eta / 9.0 * degeneracy * degeneracy * gamma * gamma * pi * pj * pk * loss * leff * leff;
	}
}
