package qkd.verification;

import network.GnModelMath;

/**
 * Independent 2-D Simpson quadrature of Johannisson/Agrell (2014),
 * Eqs. (10), (11) and (15), versus the production 1-D integral kernel.
 * The old logarithmic Eq. (16) is reported for comparison, not an oracle.
 */
public final class QKDJohannissonExactXciVerification {

	private QKDJohannissonExactXciVerification() { }

	public static void main(String[] args) {
		final double alphaPower = 0.2 * Math.log(10.0) / 10.0 / 1000.0;
		final double beta2 = 21.3e-27;
		final double xi = 4.0 * Math.PI * Math.PI * beta2 / alphaPower;
		final double[] widths = {12.5e9, 25.0e9, 32.0e9, 50.0e9};

		for (double victimWidth : widths) {
			for (double sourceWidth : widths) {
				final double separation = 75.0e9;
				final double exact = GnModelMath.johannissonXciKernel(
						separation, victimWidth, sourceWidth, xi);
				final double reference = independentDoubleQuadrature(
						victimWidth, sourceWidth, separation, xi);

				close(exact, reference, 2e-7, "Eq.15 XCI");

				if (!(exact > 0.0) || !Double.isFinite(exact)) {
					throw new AssertionError("XCI not positive and finite");
				}

				if (victimWidth == 12.5e9 || victimWidth == 25.0e9) {
					double logarithmic = Math.log((separation + sourceWidth / 2.0)
							/ (separation - sourceWidth / 2.0));
					System.out.printf(java.util.Locale.ROOT,
							"victim=%.1f GHz source=%.1f GHz Eq15=%e Eq16=%e rel=%.4f%% %n",
							victimWidth / 1e9, sourceWidth / 1e9,
							exact, logarithmic, 100.0 * (logarithmic / exact - 1.0));
				}
			}
		}

		// The extension of Eq.15 has finite overlap even where log Eq.16 diverges.
		double crossing = GnModelMath.johannissonXciKernel(0.0,
				12.5e9, 25.0e9, xi);

		if (!(crossing > 0.0) || !Double.isFinite(crossing)) {
			throw new AssertionError("rectangle integral invalid at zero separation");
		}

		System.out.println("PASS QKDJohannissonExactXciVerification: 16 two-dimensional cases");
	}

	private static double independentDoubleQuadrature(
			double victimWidth, double sourceWidth, double separation, double xi) {
		// Normalized Eq. 11: xi/pi * integral[-Bi/2,Bi/2]
		// integral[d-Bj/2,d+Bj/2] (1 + xi^2*u^2*v^2)^-1 dv du.
		final int n = 1200;
		final double hu = victimWidth / n;
		final double hv = sourceWidth / n;
		double sum = 0.0;

		for (int i = 0; i <= n; i++) {
			final double u = -0.5 * victimWidth + i * hu;
			final int wu = i == 0 || i == n ? 1 : (i % 2 == 0 ? 2 : 4);

			for (int j = 0; j <= n; j++) {
				final double v = separation - 0.5 * sourceWidth + j * hv;
				final int wv = j == 0 || j == n ? 1 : (j % 2 == 0 ? 2 : 4);
				final double q = xi * u * v;
				sum += wu * wv / (1.0 + q * q);
			}
		}

		return xi / Math.PI * sum * hu * hv / 9.0;
	}

	private static void close(double a, double b, double relative, String label) {
		if (!Double.isFinite(a) || !Double.isFinite(b)
				|| Math.abs(a-b) > relative * Math.max(1e-30, Math.abs(b))) {
			throw new AssertionError(label + ": " + a + " vs " + b);
		}
	}
}
