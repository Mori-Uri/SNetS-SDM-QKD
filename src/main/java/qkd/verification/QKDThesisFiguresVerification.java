package qkd.verification;

import java.util.Locale;

import network.GnModelMath;

/**
 * Scientific reference check for Lacerda Junior (2024), Table 6 and Figures
 * 21(b), 22 (Eqs. 3.21-3.32), and Johannisson/Agrell (2014) Eqs. 11/15/16.
 *
 * This computes the published analytical scenario independently. It does not
 * constitute an end-to-end dynamic SNetS traffic simulation.
 */
public final class QKDThesisFiguresVerification {

	private QKDThesisFiguresVerification() {
	}

	private static final double ALPHA_FIELD = 0.1 * Math.log(10.0) / 10.0 / 1000.0;
	private static final double ALPHA_POWER = 2.0 * ALPHA_FIELD;
	private static final double BETA = 21.3e-27;
	private static final double GAMMA = 1.3e-3;
	private static final double LENGTH_METERS = 100_000.0;
	private static final double BANDWIDTH = 32e9;
	private static final double SPACING = 33.6e9;
	private static final double PHI = 1.0;

	private static double asinh(double value) {
		return Math.log(value + Math.sqrt(1.0 + value * value));
	}

	private static double db(double value) {
		return 10.0 * Math.log10(value);
	}

	private static double inverseTangentIntegral(double argument) {
		if (argument > 1.0) {
			return Math.PI * 0.5 * Math.log(argument)
					+ inverseTangentIntegral(1.0 / argument);
		}

		int steps = 4000;
		double width = argument / steps;
		double sum = 0.0;

		for (int i = 0; i <= steps; i++) {
			double x = i * width;
			double y = x == 0.0 ? 1.0 : Math.atan(x) / x;
			double weight = i == 0 || i == steps ? 1.0 : i % 2 == 0 ? 2.0 : 4.0;

			sum += weight * y;
		}

		return sum * width / 3.0;
	}

	private static void near(double value, double expected, double tolerance, String name) {
		if (!Double.isFinite(value) || Math.abs(value - expected) > tolerance) {
			throw new AssertionError(name + ": expected " + expected + ", actual " + value);
		}
	}

	public static void main(String[] args) {
		double leff = -Math.expm1(-2.0 * ALPHA_FIELD * LENGTH_METERS)
				/ (2.0 * ALPHA_FIELD);
		double xi = (8.0 * GAMMA * GAMMA * leff * leff * (2.0 * ALPHA_FIELD))
				/ (27.0 * Math.PI * BETA);

		// Thesis Eqs. 3.23-3.31, 15 PM-QPSK channels (one central CUT).
		double psiSci = xi * asinh(Math.PI * Math.PI * BETA * BANDWIDTH * BANDWIDTH
				/ (4.0 * ALPHA_FIELD));
		double correctionSci = xi * 5.0 * PHI / (3.0 * ALPHA_FIELD * LENGTH_METERS);
		double psiXci = 0.0;
		double correctionXci = 0.0;

		for (int channel = -7; channel <= 7; channel++) {
			if (channel == 0) {
				continue;
			}

			double delta = Math.abs(channel) * SPACING;
			double argument = Math.PI * Math.PI * BETA * BANDWIDTH
					/ (2.0 * ALPHA_FIELD);

			psiXci += xi * (asinh(argument * (delta + BANDWIDTH / 2.0))
					- asinh(argument * (delta - BANDWIDTH / 2.0)));
			correctionXci += xi * 5.0 * PHI * BANDWIDTH
					/ (6.0 * ALPHA_FIELD * delta * LENGTH_METERS);
		}

		double gnNormalized = (psiSci + psiXci) / (BANDWIDTH * BANDWIDTH);
		double egnNormalized = Math.max(0.0, (psiSci + psiXci
				- correctionSci - correctionXci) / (BANDWIDTH * BANDWIDTH));

		near(db(gnNormalized), 30.1458226903712, 1e-7, "Fig 21b GN N=1");
		near(db(egnNormalized), 28.0860340391505, 1e-7, "Fig 21b EGN N=1");
		near(db(50.0 * gnNormalized), 47.1355227337314, 1e-7, "Fig 21b GN N=50");
		near(db(50.0 * egnNormalized), 45.0757340825107, 1e-7, "Fig 22 EGN N=50");
		near(leff, 21497.5768542, 0.001, "Thesis Table 6 Leff");

		System.out.println("spans,gn_normalized_db,egn_normalized_db");

		for (int spans : new int[]{1, 2, 5, 10, 20, 50}) {
			System.out.printf(Locale.US, "%d,%.9f,%.9f%n",
					spans, db(gnNormalized * spans), db(egnNormalized * spans));
		}

		// Independent Eq. 11/15 SCI rectangle integral versus the SNetS kernel,
		// and Eq. 16 asymptotic logarithm, especially at 12.5 and 25 GHz.
		double xiJohannisson = 4.0 * Math.PI * Math.PI * BETA / ALPHA_POWER;

		for (double ghz : new double[]{12.5, 25.0, 28.0, 32.0, 50.0}) {
			double bandwidth = ghz * 1e9;
			double rho = xiJohannisson * bandwidth * bandwidth / 4.0;
			double normalizedIntegral = (2.0 / Math.PI) * inverseTangentIntegral(rho);
			double kernel = GnModelMath.johannissonSciKernel(rho, bandwidth);

			near(kernel, normalizedIntegral, 3e-8, "Johannisson Eq 11/15 integral");

			if (ghz == 12.5 && !(kernel > 0.0 && Math.log(rho) < 0.0)) {
				throw new AssertionError("Narrow SCI not positive or asymptotic invalid");
			}
		}

		System.err.println("PASS QKDThesisFiguresVerification (analytical reference; not end-to-end SNetS)");
	}
}
