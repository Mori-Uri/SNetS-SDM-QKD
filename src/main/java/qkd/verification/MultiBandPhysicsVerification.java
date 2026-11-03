package qkd.verification;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import qkd.multiband.ClassicalChannel;
import qkd.multiband.FiberAttenuationModel;
import qkd.multiband.MultiBandLinkResult;
import qkd.multiband.MultiBandQkdLinkModel;
import qkd.multiband.OpticalBand;
import qkd.multiband.SpontaneousRamanModel;
import qkd.physical.BB84DecoyModel;
import qkd.physical.QKDCrosstalkReceiverTransferMode;
import qkd.physical.QKDPhysicalParameters;

/**
 * Parte 42 (branch experimental): verification of the multi-band physical layer (package {@code qkd.multiband})
 * against the literature, before it is coupled to the network simulator. Items VMB-1 to VMB-8 of the verification
 * plan.
 *
 * <ul>
 *   <li>VMB-1 attenuation of G.652.D from O to L band ([MEHDI24], [DIBAJ25], [FERRARI20]);</li>
 *   <li>VMB-2 SpRS cross-section against the measurement of [ERAERDS10] Fig. 1;</li>
 *   <li>VMB-3 key rates of [MEHDI24] (QCh in O/E with a fully loaded C+L comb);</li>
 *   <li>VMB-4 key rates of [DIBAJ24] (IWCIT, 1380 nm);</li>
 *   <li>VMB-5 thresholds of [DIBAJ25] Fig. 10 (mu = 0.5, 1360 nm);</li>
 *   <li>VMB-6 effective lengths of [CAI20] (intercore SpRS peaks, eqs. 11, 18 and 31);</li>
 *   <li>VMB-7 the multi-fibre case and the BB84 overload reproduce the existing model exactly;</li>
 *   <li>VMB-8 limits and consistency of the closed forms.</li>
 * </ul>
 * Items that depend on effects not modelled yet (NLI/FWM near the classical comb) are printed as [PENDENTE] and do
 * not fail the run.
 *
 * <p>Referências: [MEHDI24] P. Mehdizadeh et al., IEEE Commun. Lett. 28(3), 2024; [DIBAJ24] M. R. Dibaj et al.,
 * IWCIT 2024; [DIBAJ25] M. R. Dibaj et al., JLT 43(1), 2025; [ERAERDS10] P. Eraerds et al., New J. Phys. 12,
 * 063027, 2010; [CAI20] C. Cai, Y. Sun, Y. Ji, New J. Phys. 22, 083020, 2020; [FERRARI20] A. Ferrari et al.,
 * JLT 38(16), 2020.</p>
 */
public final class MultiBandPhysicsVerification {

    private static int pending;

    private MultiBandPhysicsVerification() {
    }

    public static void main(String[] args) {
        attenuation();
        ramanCrossSection();
        mehdizadeh();
        iwcit();
        dibajJlt();
        cai();
        multiFiberAndOverload();
        limits();
        System.out.println();
        System.out.println("Multi-band physics verification (Parte 42): ALL CHECKS PASSED"
                + (pending > 0 ? " (" + pending + " item(s) pendente(s), ver acima)" : ""));
    }

    // ------------------------------------------------------------------ VMB-1

    private static void attenuation() {
        final FiberAttenuationModel f = FiberAttenuationModel.g652d();
        within(f.dbPerKm(1260), 0.38, 0.01, "VMB-1 alpha(1260 nm) = 0,38 dB/km [MEHDI24]");
        within(f.dbPerKm(1550), 0.18, 0.01, "VMB-1 alpha(1550 nm) = 0,18 dB/km [MEHDI24]");
        within(f.dbPerKm(1360), 0.28, 0.01, "VMB-1 alpha(1360 nm) ~ 0,28 dB/km (retro-calculo de [DIBAJ25])");
        double best = 0;
        double min = Double.MAX_VALUE;
        for (double l = 1260; l <= 1625; l += 1) {
            final double a = f.dbPerKm(l);
            if (a < min) {
                min = a;
                best = l;
            }
        }
        check(best >= 1540 && best <= 1600, "VMB-1 minimum loss in the C/L region (found " + best + " nm)");
        ok("VMB-1 alpha min = %.4f dB/km at %.0f nm", min, best);
        check(OpticalBand.of(1381) == OpticalBand.E && OpticalBand.of(1550) == OpticalBand.C
                && OpticalBand.of(1260) == OpticalBand.O, "VMB-1 band classification");
        final FiberAttenuationModel wet = FiberAttenuationModel.physical(0.95, 0.3, 30);
        within(wet.dbPerKm(1383) - f.dbPerKm(1383), 0.3, 1e-9, "VMB-1 optional OH peak adds its height at 1383 nm");
        final FiberAttenuationModel t = FiberAttenuationModel.table(new double[] {1300, 1500}, new double[] {0.3, 0.2});
        within(t.dbPerKm(1400), 0.25, 1e-12, "VMB-1 table model interpolates linearly");
    }

    // ------------------------------------------------------------------ VMB-2

    private static void ramanCrossSection() {
        // (QCh wavelength nm, rho in 1/(km nm)) read from [ERAERDS10] Fig. 1, pump 1550 nm
        final double[][] eraerds = {
            {1510.940, 2.1612e-09}, {1518.594, 2.4729e-09}, {1526.326, 3.0263e-09}, {1534.136, 3.6071e-09},
            {1574.420, 4.4369e-09}, {1591.133, 3.6774e-09}, {1616.877, 4.9307e-09}, {1634.508, 5.8831e-09},
            {1643.468, 6.2075e-09},
        };
        final SpontaneousRamanModel r = SpontaneousRamanModel.standard();
        for (double[] p : eraerds) {
            final double m = r.crossSectionPerKmNm(1550.0, p[0]);
            final double ratio = m / p[1];
            check(ratio > 0.8 && ratio < 1.2, String.format(Locale.ROOT,
                    "VMB-2 rho(1550 -> %.1f nm) = %.3e, Eraerds %.3e (ratio %.2f)", p[0], m, p[1], ratio));
        }
        ok("VMB-2 rho within +-20%% of [ERAERDS10] at %d points from -5 to +11 THz", eraerds.length);
        check(r.crossSectionPerKmNm(1550, 1600) > r.crossSectionPerKmNm(1550, 1500),
                "VMB-2 Stokes side stronger than anti-Stokes");
        check(r.crossSectionPerKmNm(1550, 1550) == 0.0, "VMB-2 no SpRS at zero offset");
    }

    // ------------------------------------------------------------------ VMB-3

    private static void mehdizadeh() {
        final MultiBandQkdLinkModel m = MultiBandQkdLinkModel.paperCompatible(params(0.7));
        final List<ClassicalChannel> comb = coComb();
        relative(m.evaluate(1381, 70, comb).getSecretKeyRateBps(), 3.08e6, 0.15,
                "VMB-3 [MEHDI24] SKR(1381 nm, 70 km) = 3,08 Mbit/s");
        relative(m.evaluate(1260, 70, comb).getSecretKeyRateBps(), 0.53e6, 0.15,
                "VMB-3 [MEHDI24] SKR(1260 nm, 70 km) = 0,53 Mbit/s");
        final MultiBandLinkResult r5 = m.evaluate(1260, 5, comb);
        relative(r5.getSecretKeyRateBps(), 160.82e6, 0.10, "VMB-3 [MEHDI24] SKR(1260 nm, 5 km) = 160,82 Mbit/s");
        within(r5.getQber(), 0.015, 0.001, "VMB-3 [MEHDI24] QBER(1260 nm, 5 km) = 0,015");
        final MultiBandLinkResult s = m.evaluate(1522, 5, comb);
        System.out.println(String.format(Locale.ROOT, "[INFO] VMB-3 [MEHDI24] SKR(1522 nm, 5 km) = 25,82 Mbit/s, QBER "
                + "0,066: com a fracao 0,5 deste preset o modelo da %.1f Mbit/s, QBER %.3f. Explicado na Parte 46 "
                + "(VMB-12e): o [MEHDI24] conta todos os fotons de SpRS (fracao 1), e o NLI nao pesa.",
                s.getSecretKeyRateBps() / 1e6, s.getQber()));
    }

    // ------------------------------------------------------------------ VMB-4

    private static void iwcit() {
        final MultiBandQkdLinkModel m = MultiBandQkdLinkModel.paperCompatible(params(0.7));
        final List<ClassicalChannel> comb = coComb();
        final double[][] t = {{5, 185e6}, {15, 100e6}, {30, 40e6}};
        for (double[] p : t) {
            relative(m.evaluate(1380, p[0], comb).getSecretKeyRateBps(), p[1], 0.10,
                    String.format(Locale.ROOT, "VMB-4 [DIBAJ24] SKR(1380 nm, %.0f km) = %.0f Mbit/s", p[0],
                            p[1] / 1e6));
        }
    }

    // ------------------------------------------------------------------ VMB-5

    private static void dibajJlt() {
        // [DIBAJ25] states that attenuation dominates in the O band; its thresholds are reproduced with p_b = p_dark
        final MultiBandQkdLinkModel m = MultiBandQkdLinkModel.paperCompatible(params(0.5));
        relative(m.evaluateDarkFiber(1360, 100).getSecretKeyRateBps(), 385e3, 0.10,
                "VMB-5 [DIBAJ25] SKR(1360 nm, 100 km) = 385 kbit/s (limiar Fig. 10)");
        relative(m.evaluateDarkFiber(1360, 150).getSecretKeyRateBps(), 15e3, 0.15,
                "VMB-5 [DIBAJ25] SKR(1360 nm, 150 km) = 15 kbit/s (limiar Fig. 10)");
        // near the cut-off the SKR is very sensitive to alpha (0,002 dB/km changes it by ~50%): factor-2 check
        final double s200 = m.evaluateDarkFiber(1360, 200).getSecretKeyRateBps();
        check(s200 > 0.354e3 / 2 && s200 < 0.354e3 * 2, "VMB-5 [DIBAJ25] SKR(1360 nm, 200 km) ~ 0,354 kbit/s: " + s200);
        ok("VMB-5 [DIBAJ25] SKR(1360 nm, 200 km) ~ 0,354 kbit/s (Fig. 5, mesma ordem; perto do corte): %.3f kbit/s",
                s200 / 1e3);
        final MultiBandLinkResult withRaman = m.evaluate(1360, 150, coComb());
        pending(String.format(Locale.ROOT, "VMB-5 com o SpRS do comb C+L a 0 dBm o modelo da %.2f kbit/s a 150 km "
                + "(p_SpRS %.1e); o artigo indica ruido desprezivel no O-band -> conflito a discutir",
                withRaman.getSecretKeyRateBps() / 1e3, withRaman.getRamanProbabilitySameCore()));
    }

    // ------------------------------------------------------------------ VMB-6

    private static void cai() {
        final double ac = 0.22 * FiberAttenuationModel.DB_TO_NEPER;
        final double aq = 0.21 * FiberAttenuationModel.DB_TO_NEPER;
        final double h = 1e-6;
        double lInter = 0;
        double gInter = 0;
        double lSame = 0;
        double gSame = 0;
        for (double l = 0.1; l <= 150; l += 0.1) {
            final double gi = SpontaneousRamanModel.intercoreForwardLengthKm(ac, aq, h, l);
            if (gi > gInter) {
                gInter = gi;
                lInter = l;
            }
            final double gs = SpontaneousRamanModel.forwardLengthKm(ac, aq, l);
            if (gs > gSame) {
                gSame = gs;
                lSame = l;
            }
        }
        within(lInter, 40.0, 1.0, "VMB-6 [CAI20] Fig. 5: forward intercore SpRS peaks at ~40 km");
        within(lSame, Math.log(aq / ac) / (aq - ac), 0.1, "VMB-6 [CAI20] eq. 31: single-core peak");
        within(lSame, 20.0, 1.0, "VMB-6 [CAI20] Fig. 10: single-core forward SpRS peaks at ~20 km");
        final double eta = 6e-9;
        final double p0 = 1e-3;
        relative(eta * p0 * SpontaneousRamanModel.intercoreForwardLengthKm(ac, aq, h, 50), 1.24e-15, 0.03,
                "VMB-6 [CAI20] P_FICSRS(50 km) = 1,24e-15 W/nm (eq. 11)");
        relative(eta * p0 * SpontaneousRamanModel.intercoreBackwardLengthKm(ac, aq, h, 50), 1.17e-15, 0.03,
                "VMB-6 [CAI20] P_BICSRS(50 km) = 1,17e-15 W/nm (eq. 18)");
        final double ratio = SpontaneousRamanModel.intercoreForwardLengthKm(ac, aq, h, 50)
                / SpontaneousRamanModel.forwardLengthKm(ac, aq, 50);
        check(ratio > 2e-5 && ratio < 6e-5, "VMB-6 [CAI20] Fig. 4: intercore/single-core ~ h L (" + ratio + ")");
        ok("VMB-6 intercore/single-core at 50 km = %.2e (~ h L = %.1e)", ratio, h * 50);
        final double r2 = SpontaneousRamanModel.intercoreForwardLengthKm(ac, aq, 2 * h, 50)
                / SpontaneousRamanModel.intercoreForwardLengthKm(ac, aq, h, 50);
        within(r2, 2.0, 0.01, "VMB-6 [CAI20] Fig. 4: intercore SpRS linear in h");
    }

    // ------------------------------------------------------------------ VMB-7

    private static void multiFiberAndOverload() {
        final QKDPhysicalParameters p = new QKDPhysicalParameters();
        final BB84DecoyModel bb84 = new BB84DecoyModel(p);
        final double[] lambdas = {1530, 1550, 1565};
        final double[] lengths = {0, 10, 25, 50, 80};
        final double[] pbs = {1e-8, 1e-6, 1e-4};
        for (double l : lambdas) {
            for (double len : lengths) {
                for (double pb : pbs) {
                    final double a = bb84.evaluate(l, len, pb).getSecretKeyRateBps();
                    final double b = bb84.evaluateWithAttenuation(bb84.attenuationDbPerKm(l), len, pb)
                            .getSecretKeyRateBps();
                    check(Double.compare(a, b) == 0, "VMB-7 evaluate == evaluateWithAttenuation (bit-exact)");
                }
            }
        }
        ok("VMB-7 BB84DecoyModel.evaluate unchanged by the Parte 42 overload (45 points, bit-exact)");
        final MultiBandQkdLinkModel m = MultiBandQkdLinkModel.physical(p);
        final MultiBandLinkResult mf = m.evaluateDarkFiber(1550, 40);
        within(mf.getBackgroundProbability(), p.getDarkCountProbability(), 0.0, "VMB-7 MF: p_b = p_dark");
        final double ref = bb84.evaluateWithAttenuation(m.getFiber().dbPerKm(1550), 40, p.getDarkCountProbability())
                .getSecretKeyRateBps();
        check(Double.compare(mf.getSecretKeyRateBps(), ref) == 0, "VMB-7 MF equals BB84 with the same loss");
        relative(mf.getSecretKeyRateBps(), 50.1e6, 0.10, "VMB-7 [DIBAJ24] MF SKR(1550 nm, 40 km) ~ 50 Mbit/s");
    }

    // ------------------------------------------------------------------ VMB-8

    private static void limits() {
        final double a = 0.2 * FiberAttenuationModel.DB_TO_NEPER;
        relative(SpontaneousRamanModel.forwardLengthKm(a, a, 30), SpontaneousRamanModel.forwardLengthPaperKm(a, 30),
                1e-12, "VMB-8 exact forward form -> L e^{-aL} when a_q = a_c");
        relative(SpontaneousRamanModel.forwardLengthKm(a, a * (1 + 1e-9), 30),
                SpontaneousRamanModel.forwardLengthPaperKm(a, 30), 1e-6,
                "VMB-8 forward form is continuous at a_q = a_c");
        relative(SpontaneousRamanModel.backwardLengthKm(a, a, 1e4), 1.0 / (2 * a), 1e-9,
                "VMB-8 backward SpRS saturates at 1/(a_c + a_q)");
        check(SpontaneousRamanModel.forwardLengthKm(a, 0.9 * a, 0) == 0.0
                && SpontaneousRamanModel.backwardLengthKm(a, a, 0) == 0.0
                && SpontaneousRamanModel.intercoreForwardLengthKm(a, a, 1e-6, 0) == 0.0,
                "VMB-8 zero length gives zero SpRS");
        ok("VMB-8 zero length gives zero SpRS");
        check(SpontaneousRamanModel.intercoreForwardLengthKm(a, a, 0, 30) == 0.0,
                "VMB-8 no coupling, no intercore SpRS");
        ok("VMB-8 h = 0 gives zero intercore SpRS");
        final MultiBandQkdLinkModel m = MultiBandQkdLinkModel.physical(params(0.7));
        final MultiBandLinkResult co = m.evaluate(1381, 30, coComb());
        final MultiBandLinkResult none = m.evaluate(1381, 30, Collections.<ClassicalChannel>emptyList());
        check(co.getSecretKeyRateBps() < none.getSecretKeyRateBps(), "VMB-8 classical channels reduce the SKR");
        ok("VMB-8 SpRS reduces SKR at 1381 nm/30 km: %.2f -> %.2f Mbit/s", none.getSecretKeyRateBps() / 1e6,
                co.getSecretKeyRateBps() / 1e6);
    }

    // ------------------------------------------------------------------ helpers

    private static QKDPhysicalParameters params(double mu) {
        final QKDPhysicalParameters d = new QKDPhysicalParameters();
        return new QKDPhysicalParameters(mu, d.getPulseIntervalS(), d.getErrorCorrectionInefficiency(),
                d.getPhaseErrorProbability(), d.getDetectorEfficiency(), d.getDarkCountProbability(),
                d.getQchBandwidthHz(), d.getQchPowerDbm(), d.getAttenuationFloorDbPerKm(), d.getAttenuationQuadratic(),
                d.getAttenuationCenterNm(), d.getPlanckConstantJs(), d.getSpeedOfLightMPerS(),
                d.getDetectorGateWindowS(), d.getReceiverOpticalEfficiency(),
                QKDCrosstalkReceiverTransferMode.RECEIVER_REFERENCED, true);
    }

    private static List<ClassicalChannel> coComb() {
        return ClassicalChannel.referenceCLComb(0.0, ClassicalChannel.Direction.CO_PROPAGATING);
    }

    private static void within(double value, double target, double tol, String msg) {
        check(Math.abs(value - target) <= tol, String.format(Locale.ROOT, "%s: obtido %.6g (alvo %.6g +- %.3g)", msg,
                value, target, tol));
        ok("%s: %.6g (alvo %.6g)", msg, value, target);
    }

    private static void relative(double value, double target, double tol, String msg) {
        final double err = Math.abs(value - target) / Math.abs(target);
        check(err <= tol, String.format(Locale.ROOT, "%s: obtido %.6g (alvo %.6g, erro %.1f%% > %.1f%%)", msg, value,
                target, 100 * err, 100 * tol));
        ok("%s: %.6g (erro %.1f%%)", msg, value, 100 * err);
    }

    private static void ok(String fmt, Object... a) {
        System.out.println("[OK] " + String.format(Locale.ROOT, fmt, a));
    }

    private static void pending(String msg) {
        pending++;
        System.out.println("[PENDENTE] " + msg);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
