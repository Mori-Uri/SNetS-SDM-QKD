package qkd.multiband;

import java.util.Arrays;

/**
 * Raman gain profile of a standard single-mode fibre (SSMF), used as the spectral shape of the spontaneous Raman
 * scattering (SpRS) cross-section in {@link SpontaneousRamanModel}.
 *
 * <p>The table is the SSMF profile g0(delta nu) distributed with the open-source GNPy library (BSD-3-Clause),
 * {@code gnpy.core.parameters.DEFAULT_RAMAN_COEFFICIENT}: 90 points from 0 to 42 THz of frequency offset, with a
 * finer step around the 13 THz peak. Values are in 1/(W m) at the GNPy reference frequency (206.18 THz). Only the
 * <i>shape</i> is used here; the absolute scale of the SpRS cross-section is fitted separately (see
 * {@link SpontaneousRamanModel#DEFAULT_SCALE}).</p>
 *
 * <p>The class has only static members and is thread-safe.</p>
 *
 * <p>Table values: Copyright (c) 2018, Telecom Infra Project, all rights reserved; redistributed under the BSD
 * 3-Clause License of GNPy (redistributions must retain this notice; the names of the copyright holder and
 * contributors may not be used to endorse products derived from it; provided "as is", without warranty).</p>
 *
 * <p>Referências: [GNPY] Telecom Infra Project, GNPy, {@code gnpy/core/parameters.py} (perfil g0 da SSMF);
 * [ERAERDS10] P. Eraerds et al., New J. Phys. 12, 063027, 2010 (seção de choque medida, usada na
 * calibração).</p>
 */
public final class RamanGainProfile {

	/** Frequency offsets of the table in THz (strictly increasing, 0 to 42 THz). */
	private static final double[] OFFSET_THZ = {
		0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5,
		5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5,
		10.0, 10.5, 11.0, 11.5, 12.0, 12.5, 12.75, 13.0, 13.25, 13.5,
		14.0, 14.5, 14.75, 15.0, 15.5, 16.0, 16.5, 17.0, 17.5, 18.0,
		18.25, 18.5, 18.75, 19.0, 19.5, 20.0, 20.5, 21.0, 21.5, 22.0,
		22.5, 23.0, 23.5, 24.0, 24.5, 25.0, 25.5, 26.0, 26.5, 27.0,
		27.5, 28.0, 28.5, 29.0, 29.5, 30.0, 30.5, 31.0, 31.5, 32.0,
		32.5, 33.0, 33.5, 34.0, 34.5, 35.0, 35.5, 36.0, 36.5, 37.0,
		37.5, 38.0, 38.5, 39.0, 39.5, 40.0, 40.5, 41.0, 41.5, 42.0
	};

	/** Raman gain g0 at each offset in 1/(W m) (GNPy SSMF profile). */
	private static final double[] G0_PER_W_M = {
		0.00000000e+00, 1.12351610e-05, 3.47838074e-05, 5.79356636e-05, 8.06921680e-05,
		9.79845709e-05, 1.10454361e-04, 1.18735302e-04, 1.24736889e-04, 1.30110053e-04,
		1.41001273e-04, 1.46383247e-04, 1.57011792e-04, 1.70765865e-04, 1.88408911e-04,
		2.05914127e-04, 2.24074028e-04, 2.47508283e-04, 2.77729174e-04, 3.08044243e-04,
		3.34764439e-04, 3.56481704e-04, 3.77127256e-04, 3.96269124e-04, 4.10955175e-04,
		4.18718761e-04, 4.19511263e-04, 4.17025384e-04, 4.13565369e-04, 4.07726048e-04,
		3.83671291e-04, 4.08564283e-04, 3.69571936e-04, 3.14442090e-04, 2.16074535e-04,
		1.23097823e-04, 8.95457457e-05, 7.52470400e-05, 7.19806145e-05, 8.87961158e-05,
		9.30812065e-05, 9.37058268e-05, 8.45719619e-05, 6.90585286e-05, 4.50407159e-05,
		3.36521245e-05, 3.02292475e-05, 2.69376939e-05, 2.60020897e-05, 2.82958958e-05,
		3.08667558e-05, 3.66024657e-05, 5.80610307e-05, 6.54797937e-05, 6.25022715e-05,
		5.37806442e-05, 3.94996621e-05, 2.68120644e-05, 2.33038554e-05, 1.79140757e-05,
		1.52472424e-05, 1.32707565e-05, 1.06541760e-05, 9.84649374e-06, 9.13999627e-06,
		9.08971012e-06, 1.04227525e-05, 1.50419271e-05, 1.77838232e-05, 2.15810815e-05,
		2.03744008e-05, 1.81939341e-05, 1.31862121e-05, 9.65352116e-06, 8.62698322e-06,
		9.18688016e-06, 1.01737784e-05, 1.08017817e-05, 1.03903588e-05, 9.30040333e-06,
		8.30809173e-06, 6.90650401e-06, 5.52238029e-06, 3.90648708e-06, 2.22908227e-06,
		1.55796177e-06, 9.77218716e-07, 3.23477236e-07, 1.60602454e-07, 7.97306386e-08
	};

	/** Largest offset covered by the table in Hz; above it the gain is taken as zero. */
	public static final double MAX_OFFSET_HZ = 42.0e12;

	private RamanGainProfile() {
	}

	/**
     * Raman gain at a frequency offset, linearly interpolated.
     *
     * @param offsetHz frequency offset between pump and signal in Hz (sign ignored)
     * @return g0 in 1/(W m); zero above {@link #MAX_OFFSET_HZ}
     */
	public static double g0(double offsetHz) {
		final double thz = Math.abs(offsetHz) * 1e-12;
		final int last = OFFSET_THZ.length - 1;

		if (thz >= OFFSET_THZ[last]) {
			return thz == OFFSET_THZ[last] ? G0_PER_W_M[last] : 0.0;
		}

		int i = Arrays.binarySearch(OFFSET_THZ, thz);

		if (i >= 0) {
			return G0_PER_W_M[i];
		}

		i = -i - 2;
		final double t = (thz - OFFSET_THZ[i]) / (OFFSET_THZ[i + 1] - OFFSET_THZ[i]);

		return G0_PER_W_M[i] + t * (G0_PER_W_M[i + 1] - G0_PER_W_M[i]);
	}

	/** Number of points in the table (for tests). */
	static int size() {
		return OFFSET_THZ.length;
	}
}
