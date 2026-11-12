package qkd.multiband;

/**
 * Transmission bands of an ITU-T G.652.D fibre used by the multi-band (MB) model.
 *
 * <p>The limits, the number of 50 GHz channels, the amplifier type, its noise figure and the non-linear
 * coefficient of each band follow Table II of [FERRARI20]. The U band is outside that table and is kept only
 * so that a frequency can always be classified; it has no default amplifier.</p>
 *
 * <p>Referências: [FERRARI20] A. Ferrari et al., "Assessment on the Achievable Throughput of Multi-Band
 * ITU-T G.652.D Fiber Transmission Systems", J. Lightw. Technol. 38(16):4279-4291, 2020, Tab. II.</p>
 */
public enum OpticalBand {

	/** Original band, 1260-1360 nm, PDFA, NF 7 dB, gamma 1.6 /W/km. */
	O(1260.0, 1360.0, "PDFA", 7.0, 1.6),

	/** Extended band, 1360-1460 nm, BDFA, NF 6 dB, gamma 1.5 /W/km. */
	E(1360.0, 1460.0, "BDFA", 6.0, 1.5),

	/** Short band, 1460-1530 nm, TDFA, NF 7 dB, gamma 1.4 /W/km. */
	S(1460.0, 1530.0, "TDFA", 7.0, 1.4),

	/** Conventional band, 1530-1565 nm, EDFA, NF 5.5 dB, gamma 1.3 /W/km. */
	C(1530.0, 1565.0, "EDFA", 5.5, 1.3),

	/** Long band, 1565-1625 nm, L-band EDFA, NF 6 dB, gamma 1.28 /W/km. */
	L(1565.0, 1625.0, "EDFA-L", 6.0, 1.28),

	/** Ultra-long band, 1625-1675 nm, outside [FERRARI20] (no default amplifier). */
	U(1625.0, 1675.0, "", Double.NaN, Double.NaN);

	private final double minNm;
	private final double maxNm;
	private final String amplifier;
	private final double noiseFigureDb;
	private final double gammaPerWKm;

	OpticalBand(double minNm, double maxNm, String amplifier, double noiseFigureDb, double gammaPerWKm) {
		this.minNm = minNm;
		this.maxNm = maxNm;
		this.amplifier = amplifier;
		this.noiseFigureDb = noiseFigureDb;
		this.gammaPerWKm = gammaPerWKm;
	}

	/** Lower wavelength limit in nm (inclusive). */
	public double getMinNm() { return minNm; }

	/** Upper wavelength limit in nm (exclusive, except for the last band). */
	public double getMaxNm() { return maxNm; }

	/** Default in-line amplifier technology of the band ([FERRARI20] Tab. II). */
	public String getAmplifier() { return amplifier; }

	/** Default amplifier noise figure in dB ([FERRARI20] Tab. II); NaN for the U band. */
	public double getNoiseFigureDb() { return noiseFigureDb; }

	/** Non-linear coefficient gamma in 1/(W km) ([FERRARI20] Tab. II); NaN for the U band. */
	public double getGammaPerWKm() { return gammaPerWKm; }

	/** Central wavelength of the band in nm. */
	public double getCenterNm() { return 0.5 * (minNm + maxNm); }

	/**
     * Band that contains the given wavelength.
     *
     * @param wavelengthNm wavelength in nm
     * @return the band, or {@code null} when the wavelength is outside 1260-1675 nm
     */
	public static OpticalBand of(double wavelengthNm) {
		for (OpticalBand b : values()) {
			if (wavelengthNm >= b.minNm && wavelengthNm < b.maxNm) {
				return b;
			}
		}

		return wavelengthNm == U.maxNm ? U : null;
	}
}
