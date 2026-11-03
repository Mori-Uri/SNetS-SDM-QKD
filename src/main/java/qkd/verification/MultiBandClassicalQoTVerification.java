package qkd.verification;

import java.util.Locale;

import com.google.gson.Gson;

import network.ClassicalMultiBand;
import network.PhysicalLayer;
import simulationControl.parsers.PhysicalLayerConfig;

/**
 * Parte 49 (VMB-13): classical QoT per band ({@code physicalLayer.multiBand}, class {@link ClassicalMultiBand}).
 *
 * <ul>
 *   <li>VMB-13a: block absent or {@code enabled = false}: no model (the simulator keeps the single-band QoT; the
 *       regression of the C/Q/M cases checks the results bit by bit);</li>
 *   <li>VMB-13b: at the reference frequency with {@code attenuationModel = constante} the parameters are the global
 *       ones (fiberLoss, gamma, beta2 of the file; NF of the band of f_ref);</li>
 *   <li>VMB-13c: G.652.D loss per band ([FERRARI20]: about 0.19-0.20 dB/km in C and L, higher in S), noise figure of
 *       the band, gamma proportional to f and |beta2| growing with the wavelength (D(lambda) = D + S dlambda);</li>
 *   <li>VMB-13d: ISRS, triangular approximation: the power ratio has mean 1 over the band (energy only moves from
 *       high to low frequencies) and the tilt is 10 log10(e) P C_r L_eff B ([ZIRNGIBL98], [SEMRAU18]);</li>
 *   <li>VMB-13e: ISRS against the coupled Raman equations solved with the measured Raman gain of GNPy
 *       (script {@code verificacao/QKD_reference/ISRS_GNPy/isrs_gnpy.py}): the tilt agrees within 15%.</li>
 * </ul>
 */
public final class MultiBandClassicalQoTVerification {

    private static final double C = 299792458.0;
    private static final double F_REF = 190.6e12;

    private MultiBandClassicalQoTVerification() {
    }

    public static void main(String[] args) {
        disabled();
        reference();
        perBand();
        isrsConservation();
        isrsAgainstGnpy();
        System.out.println();
        System.out.println("Multi-band classical QoT verification (Parte 49): ALL CHECKS PASSED");
    }

    private static ClassicalMultiBand model(String json) {
        ClassicalMultiBand.Config cfg = new Gson().fromJson(json, ClassicalMultiBand.Config.class);
        return ClassicalMultiBand.of(cfg, 0.2, 0.0013, 1.6e-5, 5.0, F_REF);
    }

    private static void disabled() {
        check(ClassicalMultiBand.of(null, 0.2, 0.0013, 1.6e-5, 5.0, F_REF) == null, "VMB-13a absent block");
        check(model("{}") == null, "VMB-13a enabled defaults to false");
        check(model("{\"enabled\": false, \"isrs\": true}") == null, "VMB-13a enabled=false");
        PhysicalLayerConfig plc = new Gson().fromJson("{\"power\": 0}", PhysicalLayerConfig.class);
        check(plc.getMultiBand() == null, "VMB-13a physicalLayer without multiBand");
        plc = new Gson().fromJson("{\"multiBand\": {\"enabled\": true}}", PhysicalLayerConfig.class);
        check(plc.getMultiBand() != null && plc.getMultiBand().isEnabled(), "VMB-13a multiBand read from the file");
        ok("VMB-13a sem bloco / enabled=false: modelo de banda unica (nenhuma mudanca)");
    }

    private static void reference() {
        ClassicalMultiBand m = model("{\"enabled\": true, \"attenuationModel\": \"constante\"}");
        ClassicalMultiBand.Params p = m.at(F_REF);
        check(p.alphaDbPerKm == 0.2, "VMB-13b alpha");
        check(Math.abs(p.alphaLinear - PhysicalLayer.computeAlphaLinear(0.2)) < 1e-18, "VMB-13b alphaLinear");
        check(Math.abs(p.gamma - 0.0013) < 1e-15, "VMB-13b gamma");
        check(Math.abs(p.beta2 - PhysicalLayer.computeBeta2(1.6e-5, F_REF)) < 1e-40, "VMB-13b beta2");
        check(p.noiseFigureDb == 6.0, "VMB-13b NF of the band of f_ref (190.6 THz = 1573 nm, L)");
        ok("VMB-13b na frequencia de referencia: alpha %.2f dB/km, gamma %.4f, beta2 %.3e s2/m, NF %.1f dB",
                p.alphaDbPerKm, p.gamma, p.beta2, p.noiseFigureDb);
    }

    private static void perBand() {
        ClassicalMultiBand m = model("{\"enabled\": true}");
        double[] nm = {1500, 1550, 1600};
        String[] band = {"S", "C", "L"};
        double[] nf = {7.0, 5.0, 6.0};
        ClassicalMultiBand.Params prev = null;
        for (int i = 0; i < nm.length; i++) {
            ClassicalMultiBand.Params p = m.at(C / (nm[i] * 1e-9));
            check(p.noiseFigureDb == nf[i], "VMB-13c NF " + band[i]);
            check(Math.abs(p.gamma / 0.0013 - p.frequencyHz / F_REF) < 1e-12, "VMB-13c gamma ~ f");
            if (prev != null) {
                check(Math.abs(p.beta2) > Math.abs(prev.beta2), "VMB-13c |beta2| grows with lambda");
            }
            ok("VMB-13c %s %.0f nm: alpha %.3f dB/km, NF %.1f dB, gamma %.5f 1/(W m), beta2 %.3e s2/m", band[i], nm[i],
                    p.alphaDbPerKm, p.noiseFigureDb, p.gamma, p.beta2);
            prev = p;
        }
        double aS = m.at(C / 1500e-9).alphaDbPerKm, aC = m.at(C / 1550e-9).alphaDbPerKm,
                aL = m.at(C / 1600e-9).alphaDbPerKm;
        check(aC > 0.17 && aC < 0.21 && aL > 0.17 && aL < 0.22 && aS > aC, "VMB-13c G.652.D loss S > C, C/L ~0.19");
        ClassicalMultiBand flat = model("{\"enabled\": true, \"attenuationModel\": \"constante\"}");
        check(flat.at(C / 1500e-9).alphaDbPerKm == 0.2, "VMB-13c constante");
    }

    private static void isrsConservation() {
        ClassicalMultiBand m = model("{\"enabled\": true}");
        double fMin = 184.4e12, fMax = 196.4e12, pw = 0.24, a = PhysicalLayer.computeAlphaLinear(0.2);
        int n = 20000;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += m.isrsRatio(fMin + (i + 0.5) * (fMax - fMin) / n, fMin, fMax, pw, a, 80.0);
        }
        double mean = sum / n;
        check(Math.abs(mean - 1.0) < 1e-6, "VMB-13d mean of rho = 1: " + mean);
        double tilt = 10 * Math.log10(m.isrsRatio(fMin, fMin, fMax, pw, a, 80.0) / m.isrsRatio(fMax, fMin, fMax, pw, a, 80.0));
        double leff = -Math.expm1(-a * 80e3) / a;
        double expected = 10 * Math.log10(Math.E) * pw * 0.028e-15 * leff * (fMax - fMin);
        check(Math.abs(tilt - expected) < 1e-9, "VMB-13d tilt " + tilt + " vs " + expected);
        check(model("{\"enabled\": true, \"isrs\": false}").isrsRatio(fMin, fMin, fMax, pw, a, 80.0) == 1.0,
                "VMB-13d isrs=false");
        ok("VMB-13d ISRS conserva a potencia (media %.6f) e inclina %.2f dB em 12 THz com %.1f dBm", mean, tilt,
                10 * Math.log10(pw * 1e3));
    }

    private static void isrsAgainstGnpy() {
        // {f0, f1, channels, dBm per channel, tilt of the coupled equations with the GNPy Raman gain (dB)}
        double[][] cases = {
            {191.35e12, 196.10e12, 96, 0.0, 1.35},
            {184.5e12, 196.45e12, 240, 0.0, 7.81},
            {184.5e12, 196.45e12, 240, 3.0, 15.70},
        };
        ClassicalMultiBand m = model("{\"enabled\": true}");
        double a = PhysicalLayer.computeAlphaLinear(0.2);
        for (double[] c : cases) {
            double fMin = c[0] - 25e9, fMax = c[1] + 25e9;
            double pw = c[2] * 1e-3 * Math.pow(10, c[3] / 10);
            double tilt = 10 * Math.log10(m.isrsRatio(c[0], fMin, fMax, pw, a, 80.0) / m.isrsRatio(c[1], fMin, fMax, pw, a, 80.0));
            double rel = Math.abs(tilt - c[4]) / c[4];
            check(rel <= 0.15, "VMB-13e tilt " + tilt + " vs GNPy " + c[4]);
            ok("VMB-13e %.1f THz, %.1f dBm: inclinacao triangular %.2f dB, GNPy (eq. acopladas) %.2f dB (%.0f%%)",
                    (c[1] - c[0]) / 1e12, 10 * Math.log10(pw * 1e3), tilt, c[4], 100 * rel);
        }
    }

    private static void ok(String fmt, Object... a) {
        System.out.println("[OK] " + String.format(Locale.ROOT, fmt, a));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
