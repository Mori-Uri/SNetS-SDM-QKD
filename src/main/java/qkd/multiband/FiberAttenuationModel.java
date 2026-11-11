package qkd.multiband;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Wavelength-dependent loss of a standard single-mode fibre (ITU-T G.652.D) from the O to the U band.
 *
 * <p>Two forms are available:</p>
 * <ul>
 *   <li><b>Physical fit</b> (default): alpha(lambda) = R / lambda^4 + A_IR exp(-B_IR / lambda) + OH(lambda), with
 *       lambda in micrometres. The first term is Rayleigh scattering, the second the infrared absorption tail of
 *       silica, the third an optional Lorentzian water peak centred at 1383 nm (zero for a low-water-peak
 *       G.652.D fibre). With the default constants the curve gives 0.377 dB/km at 1260 nm and 0.185 dB/km at
 *       1550 nm, i.e. the 0.38-0.18 dB/km range quoted by [MEHDI24] and [DIBAJ25] for the fibre of [FERRARI20],
 *       and 0.26-0.28 dB/km at 1360-1381 nm, the values that reproduce their O/E-band key rates
 *       (verification {@code MultiBandPhysicsVerification}).</li>
 *   <li><b>Table</b>: (wavelength, loss) points given by the user, linearly interpolated (constant outside).</li>
 * </ul>
 *
 * <p>The object is immutable and thread-safe.</p>
 *
 * <p>Referências: [FERRARI20] A. Ferrari et al., JLT 38(16), 2020 (perda G.652.D de O a L, Fig. 2);
 * [MEHDI24] P. Mehdizadeh et al., IEEE Commun. Lett. 28(3), 2024 (alpha de 0,38 a 0,18 dB/km);
 * [DIBAJ25] M. R. Dibaj et al., JLT 43(1), 2025 (mesmos parâmetros). Forma Rayleigh + IR: modelo clássico de perdas
 * da sílica (constante de absorção IR 7,81e11 dB/km e 48,48 um).</p>
 */
public final class FiberAttenuationModel implements Serializable {

	private static final long serialVersionUID = 1L;

	/** Default Rayleigh coefficient in dB km^-1 um^4 (calibrated to 0.38 dB/km at 1260 nm). */
	public static final double DEFAULT_RAYLEIGH_DB_UM4_PER_KM = 0.95;

	/** Infrared absorption amplitude in dB/km. */
	public static final double DEFAULT_IR_AMPLITUDE_DB_PER_KM = 7.81e11;

	/** Infrared absorption decay constant in micrometres. */
	public static final double DEFAULT_IR_DECAY_UM = 48.48;

	/** Centre of the OH (water) absorption peak in nm. */
	public static final double WATER_PEAK_NM = 1383.0;

	private final double rayleigh;
	private final double irAmplitude;
	private final double irDecay;
	private final double waterPeakDbPerKm;
	private final double waterPeakFwhmNm;
	private final double[] tableNm;
	private final double[] tableDbPerKm;

	private FiberAttenuationModel(double rayleigh, double irAmplitude, double irDecay, double waterPeakDbPerKm,
			double waterPeakFwhmNm, double[] tableNm, double[] tableDbPerKm) {
		this.rayleigh = rayleigh;
		this.irAmplitude = irAmplitude;
		this.irDecay = irDecay;
		this.waterPeakDbPerKm = waterPeakDbPerKm;
		this.waterPeakFwhmNm = waterPeakFwhmNm;
		this.tableNm = tableNm;
		this.tableDbPerKm = tableDbPerKm;
	}

	/** Default low-water-peak G.652.D fibre (no OH peak). */
	public static FiberAttenuationModel g652d() {
		return physical(DEFAULT_RAYLEIGH_DB_UM4_PER_KM, 0.0, 30.0);
	}

	/**
     * Physical fit with a chosen Rayleigh coefficient and water peak.
     *
     * @param rayleighDbUm4PerKm Rayleigh coefficient in dB km^-1 um^4 (typically 0.8-1.0)
     * @param waterPeakDbPerKm extra loss at 1383 nm in dB/km (0 for G.652.D; about 0.1-0.5 for old G.652 fibre)
     * @param waterPeakFwhmNm full width at half maximum of the water peak in nm
     */
	public static FiberAttenuationModel physical(double rayleighDbUm4PerKm, double waterPeakDbPerKm,
			double waterPeakFwhmNm) {
		requireNonNegative(rayleighDbUm4PerKm, "rayleighDbUm4PerKm");
		requireNonNegative(waterPeakDbPerKm, "waterPeakDbPerKm");

		if (!(waterPeakFwhmNm > 0.0)) {
			throw new IllegalArgumentException("waterPeakFwhmNm must be > 0");
		}

		return new FiberAttenuationModel(rayleighDbUm4PerKm, DEFAULT_IR_AMPLITUDE_DB_PER_KM, DEFAULT_IR_DECAY_UM,
				waterPeakDbPerKm, waterPeakFwhmNm, null, null);
	}

	/**
     * Loss given as a table (e.g. a measured fibre or GNPy values).
     *
     * @param wavelengthsNm strictly increasing wavelengths in nm
     * @param lossDbPerKm loss at each wavelength in dB/km
     */
	public static FiberAttenuationModel table(double[] wavelengthsNm, double[] lossDbPerKm) {
		if (wavelengthsNm == null || lossDbPerKm == null || wavelengthsNm.length != lossDbPerKm.length
				|| wavelengthsNm.length < 2) {
			throw new IllegalArgumentException("table needs at least two (wavelength, loss) points");
		}

		for (int i = 0; i < wavelengthsNm.length; i++) {
			requireNonNegative(lossDbPerKm[i], "lossDbPerKm");

			if (i > 0 && !(wavelengthsNm[i] > wavelengthsNm[i - 1])) {
				throw new IllegalArgumentException("wavelengths must be strictly increasing");
			}
		}

		return new FiberAttenuationModel(0, 0, 0, 0, 1, Arrays.copyOf(wavelengthsNm, wavelengthsNm.length),
				Arrays.copyOf(lossDbPerKm, lossDbPerKm.length));
	}

	/**
     * Fibre loss at a wavelength.
     *
     * @param wavelengthNm wavelength in nm (&gt; 0)
     * @return loss in dB/km
     */
	public double dbPerKm(double wavelengthNm) {
		if (!(wavelengthNm > 0.0) || Double.isInfinite(wavelengthNm)) {
			throw new IllegalArgumentException("wavelengthNm must be finite and > 0");
		}

		if (tableNm != null) {
			return interpolate(wavelengthNm);
		}

		final double um = wavelengthNm / 1000.0;
		final double um2 = um * um;
		double loss = rayleigh / (um2 * um2) + irAmplitude * Math.exp(-irDecay / um);

		if (waterPeakDbPerKm > 0.0) {
			final double x = (wavelengthNm - WATER_PEAK_NM) / (0.5 * waterPeakFwhmNm);
			loss += waterPeakDbPerKm / (1.0 + x * x);
		}

		return loss;
	}

	/**
     * Fibre loss as a natural (neper) coefficient, the form used inside exponentials.
     *
     * @param wavelengthNm wavelength in nm
     * @return loss in 1/km (alpha_dB * ln(10) / 10)
     */
	public double nepersPerKm(double wavelengthNm) {
		return dbPerKm(wavelengthNm) * DB_TO_NEPER;
	}

	/** Conversion factor from dB to neper: ln(10) / 10. */
	public static final double DB_TO_NEPER = Math.log(10.0) / 10.0;

	private double interpolate(double x) {
		if (x <= tableNm[0]) {
			return tableDbPerKm[0];
		}

		final int last = tableNm.length - 1;

		if (x >= tableNm[last]) {
			return tableDbPerKm[last];
		}

		int i = Arrays.binarySearch(tableNm, x);

		if (i >= 0) {
			return tableDbPerKm[i];
		}

		i = -i - 2;
		final double t = (x - tableNm[i]) / (tableNm[i + 1] - tableNm[i]);

		return tableDbPerKm[i] + t * (tableDbPerKm[i + 1] - tableDbPerKm[i]);
	}

	private static void requireNonNegative(double v, String name) {
		if (!Double.isFinite(v) || v < 0.0) {
			throw new IllegalArgumentException(name + " must be finite and >= 0");
		}
	}
}
