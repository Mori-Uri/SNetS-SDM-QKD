package qkd.verification;

import java.util.Locale;
import network.GnModelMath;

/**
 * Numerical verification of Johannisson/Agrell (2014), Eqs. 11/15/16.
 *
 * Independent composite-Simpson quadrature is used as a mathematical oracle
 * for the RECTANGULAR-domain GN integral; it is not an SSFM or experiment.
 * Output is machine-readable CSV. The simplification in Eq. 16 is reported,
 * not treated as exact or silently forced to agree with the integral.
 *
 * Units: total power loss 0.2 dB/km; |beta2| = 21.3 ps^2/km; Hz and meters.
 * gamma is irrelevant for the normalized SCI/XCI kernels checked here.
 */
public final class QKDStage5QuantitativeSweepVerification {
    private QKDStage5QuantitativeSweepVerification() { }
    private static final int STEPS = 1200; // even, independent 2-D Simpson grid
    private static int cases;
    private static double worstError;

    public static void main(String[] args) {
        final double alphaPower = 0.2 * Math.log(10.0) / (10.0 * 1000.0);
        final double beta2 = 21.3e-27;
        final double xi = 4.0 * Math.PI * Math.PI * beta2 / alphaPower;
        double[] widths = {12.5e9, 25.0e9, 32.0e9, 50.0e9};
        double[] distances = {0.0, 12.5e9, 37.5e9, 75.0e9, 200.0e9};

        System.out.println("reference,term,cut_GHz,interferer_GHz,separation_GHz,"+
                "engine_integral,independent_quadrature,relative_error,eq16_approximation,eq16_relative_error,eq16_valid");
        for (double cut : widths) {
            double rho = xi * cut * cut / 4.0;
            double exact = GnModelMath.johannissonSciKernel(rho, cut);
            double ref = sciQuadrature(rho);
            double approx = Math.log(rho); // NOT physically usable for rho <= 1
            compare("SCI", cut, 0, 0, exact, ref, approx, rho > 1.0, 1e-7);
        }
        for (double cut : widths) {
            for (double interferer : widths) {
                for (double d : distances) {
                    double exact = GnModelMath.johannissonXciKernel(d, cut, interferer, xi);
                    double ref = xciQuadrature(d, cut, interferer, xi);
                    boolean eq16Valid = d > interferer * 0.5;
                    double approx = eq16Valid ? Math.log((d + interferer / 2.0) / (d - interferer / 2.0))
                                               : Double.NaN;
                    compare("XCI", cut, interferer, d, exact, ref, approx, eq16Valid, 8e-4);
                }
            }
        }
        System.err.printf(Locale.ROOT, "PASS QKDStage5QuantitativeSweepVerification: %d cases; worst integral-vs-quadrature relative error %.8g%n", cases, worstError);
    }

    private static void compare(String term, double cut, double interferer, double d,
            double engine, double ref, double approx, boolean eq16Valid, double tolerance) {
        if (!Double.isFinite(engine) || !(engine > 0) || !Double.isFinite(ref) || !(ref > 0)) {
            throw new AssertionError(term + " integral invalid: " + engine + " / " + ref);
        }
        double error = Math.abs(engine - ref) / ref;
        if (error > tolerance) {
            throw new AssertionError(String.format(Locale.ROOT,
                    "%s cut=%.1f GHz src=%.1f GHz d=%.1f GHz integral %.12g independent %.12g error %.6g > %.6g",
                    term, cut/1e9, interferer/1e9, d/1e9, engine, ref, error, tolerance));
        }
        worstError = Math.max(worstError, error);
        cases++;
        String eq16 = eq16Valid ? String.format(Locale.ROOT, "%.12g", approx) : "";
        String eq16Error = eq16Valid ? String.format(Locale.ROOT, "%.12g", (approx / ref - 1.0)) : "";
        System.out.printf(Locale.ROOT, "Johannisson2014,%s,%.5f,%.5f,%.5f,%.12g,%.12g,%.12g,%s,%s,%s%n",
                term, cut/1e9, interferer/1e9, d/1e9, engine, ref, error,
                eq16, eq16Error, Boolean.toString(eq16Valid));
    }

    private static double sciQuadrature(double rho) {
        // (2/pi) * integral_0^1 atan(rho*u)/u du; continuous at u=0.
        int n = 8000;
        double step = 1.0 / n;
        double sum = 0.0;
        for (int i = 0; i <= n; i++) {
            double u = i * step;
            double f = i == 0 ? rho : Math.atan(rho*u)/u;
            sum += (i == 0 || i == n ? 1 : (i % 2 == 0 ? 2 : 4)) * f;
        }
        return (2.0 / Math.PI) * sum * step / 3.0;
    }

    private static double xciQuadrature(double d, double cut, double source, double xi) {
        // (xi/pi) * integral[-cut/2,cut/2]du integral[d-source/2,d+source/2]dv
        //                         1/(1+(xi*u*v)^2)
        int n = STEPS;
        double du = cut / n;
        double dv = source / n;
        double sum = 0;
        for (int i = 0; i <= n; i++) {
            double u = -cut / 2.0 + i*du;
            int wi = i == 0 || i == n ? 1 : (i % 2 == 0 ? 2 : 4);
            for (int j = 0; j <= n; j++) {
                double v = d-source/2.0 + j*dv;
                int wj = j == 0 || j == n ? 1 : (j % 2 == 0 ? 2 : 4);
                double q = xi*u*v;
                sum += wi*wj/(1+q*q);
            }
        }
        return xi / Math.PI * sum * du * dv / 9.0;
    }
}
