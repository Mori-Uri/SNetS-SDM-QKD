package qkd.verification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import qkd.multiband.ClassicalChannel;
import qkd.multiband.FiberAttenuationModel;
import qkd.multiband.FourWaveMixingModel;
import qkd.multiband.MultiBandLinkResult;
import qkd.multiband.MultiBandQkdLinkModel;
import qkd.multiband.NonlinearInterferenceModel;
import qkd.multiband.SpontaneousRamanModel;
import qkd.physical.QKDPhysicalParameters;

/**
 * Parte 46 (VMB-12): noise models of the simulator against measurements and calculations published in the
 * literature of quantum-classical coexistence, plus the reproduction of the NLI of [MEHDI24] with GNPy.
 *
 * <ul>
 *   <li>VMB-12a, [PETERS09] Fig. 1/text: FWM of two CW channels of 0 dBm in SSMF ({@link FourWaveMixingModel});</li>
 *   <li>VMB-12b: the GN model of the simulator against the CW FWM formula for narrow channels (the GN model has
 *       to give the same products when the channels are almost tones);</li>
 *   <li>VMB-12c, [PETERS09] Fig. 2: spontaneous Raman spectra measured with a 1560 nm pump in SMF-28e (5.5, 21.5 and
 *       41.3 km) against {@link SpontaneousRamanModel};</li>
 *   <li>VMB-12d, [MEHDI24] Fig. 3: the "NLI" plotted by the paper is the NLI that GNPy computes for the QCh as a
 *       channel under test of -80 dBm (self- and cross-phase modulation, proportional to the QCh power). The
 *       closed form used by GNPy (gn_model_analytic) reproduces the GNPy 2.14.2 run and the figure, and the
 *       resulting photon probability is negligible;</li>
 *   <li>VMB-12e, [MEHDI24] Fig. 5(b): QBER in the S band and at the end of the E band with the physical SpRS
 *       (detected fraction 1): the paper does not use the factor 0.5 of [DIBAJ25] eq. 6;</li>
 *   <li>pending: the start of the noise at 1392-1450 nm (E band), where the paper has 2 to 5 times more noise than
 *       the Raman profile with the phonon (Bose-Einstein) factor; the cross-section there is outside the measured
 *       window of [ERAERDS10] (1510-1650 nm for a 1550 nm pump) and the paper does not say how it was extended.</li>
 * </ul>
 *
 * <p>Values read from figures were digitized by hand (about 2% of full scale). The GNPy values come from the script
 * {@code verificacao/QKD_reference/Mehdizadeh_GNPy/gnpy_fig3.py} (GNPy 2.14.2).</p>
 *
 * <p>Referências: [PETERS09] N. A. Peters et al., New J. Phys. 11, 045012, 2009; [NIU18] J.-N. Niu et al., Appl.
 * Opt. 57(27), 2018; [MEHDI24] P. Mehdizadeh et al., IEEE Commun. Lett. 28(3), 2024; [DIBAJ25] M. R. Dibaj et al.,
 * JLT 43(1), 2025; [ERAERDS10] P. Eraerds et al., New J. Phys. 12, 063027, 2010; [GNPY] A. Ferrari et al., JOCN
 * 12(6), 2020; [POGGIOLINI12] P. Poggiolini et al., arXiv:1209.0394 (eqs. 120 e 123).</p>
 */
public final class MultiBandLiteratureVerification {

    private MultiBandLiteratureVerification() {
    }

    private static final double C = 299792458.0;
    private static final double H = 6.62607015e-34;
    private static int pending;

    public static void main(String[] args) {
        petersFwm();
        gnAgainstCw();
        petersRaman();
        mehdizadehFig3();
        mehdizadehFig5();
        System.out.println();
        System.out.println("Multi-band literature verification (Parte 46): ALL CHECKS PASSED (" + pending
                + " item(s) pendente(s), ver acima)");
    }

    // ---------------------------------------------------------------- VMB-12a

    private static void petersFwm() {
        // [PETERS09]: SSMF, two CW channels of 0 dBm, product in the adjacent passband, upper envelope
        final double[][] cases = {{200e9, 1, -82}, {200e9, 25, -92}, {400e9, 1, -94}, {400e9, 25, -103}};
        for (double[] c : cases) {
            final double f1 = 193.5e12 + c[0];
            final double f2 = 193.5e12 + 2 * c[0];
            final double w = FourWaveMixingModel.productPowerW(f1, f1, f2, 1e-3, 1e-3, 1e-3, c[1], 0.2, 1.3, 17.0, 0.0,
                    1550, true);
            withinDb(dbm(w), c[2], 1.5, String.format(Locale.ROOT,
                    "VMB-12a [PETERS09] FWM de 2 canais CW de 0 dBm, %.0f GHz, %.0f km = %.0f dBm", c[0] / 1e9, c[1],
                    c[2]));
        }
    }

    // ---------------------------------------------------------------- VMB-12b

    private static void gnAgainstCw() {
        final double lambda = 1550e-9;
        final double dc = 17e-6;
        final double beta2Ps2Km = -dc * lambda * lambda / (2 * Math.PI * C) * 1e27;
        final NonlinearInterferenceModel m = new NonlinearInterferenceModel(1.3, beta2Ps2Km, 0.0, C / lambda);
        final double a = 0.2 * FiberAttenuationModel.DB_TO_NEPER;
        final double b = 2.5e9;
        final double step = 0.25e9;
        for (double df : new double[] {200e9, 400e9}) {
            for (double l : new double[] {1, 25}) {
                final double f1 = 193.5e12;
                final double f2 = f1 + df;
                final List<ClassicalChannel> ch = Arrays.asList(
                        new ClassicalChannel(f1, 1e-3, ClassicalChannel.Direction.CO_PROPAGATING, b),
                        new ClassicalChannel(f2, 1e-3, ClassicalChannel.Direction.CO_PROPAGATING, b));
                final double[][] g = NonlinearInterferenceModel.grid(ch, step);
                final double fp = 2 * f1 - f2;
                double gn = 0.0;
                for (double f = fp - 1.6 * b; f <= fp + 1.6 * b; f += step) {
                    gn += m.psdWPerHz(g[1], g[0][0], g[0][1], f, l, a, a) * step;
                }
                final double cw = FourWaveMixingModel.productPowerW(f1, f1, f2, 1e-3, 1e-3, 1e-3, l, 0.2, 1.3, 17.0,
                        0.0, 1550, false);
                withinDb(dbm(gn), dbm(cw), 3.0, String.format(Locale.ROOT,
                        "VMB-12b GN do simulador (canais de 2,5 GHz) = FWM CW, %.0f GHz, %.0f km", df / 1e9, l));
            }
        }
    }

    // ---------------------------------------------------------------- VMB-12c

    private static void petersRaman() {
        // [PETERS09] Fig. 2: 1560 nm pump of 0.84 dBm (1.2 mW), 0.5 nm resolution, photons/ns at the fibre output
        final double[] lengths = {5.5, 21.5, 41.3};
        final double[] nm = {1500, 1520, 1540, 1548, 1575, 1600};
        final double[][] measured = {
            {0.035, 0.047, 0.088, 0.105, 0.130, 0.095},
            {0.066, 0.088, 0.170, 0.200, 0.257, 0.188},
            {0.053, 0.072, 0.140, 0.168, 0.212, 0.153}};
        final SpontaneousRamanModel r = SpontaneousRamanModel.standard();
        final FiberAttenuationModel fm = FiberAttenuationModel.g652d();
        final double ap = 0.185 * FiberAttenuationModel.DB_TO_NEPER;
        double min = Double.MAX_VALUE;
        double max = 0.0;
        double sum = 0.0;
        int n = 0;
        for (int i = 0; i < lengths.length; i++) {
            for (int j = 0; j < nm.length; j++) {
                final double w = 1.2e-3 * r.crossSectionPerKmNm(1560, nm[j]) * 0.5
                        * SpontaneousRamanModel.forwardLengthKm(ap, fm.nepersPerKm(nm[j]), lengths[i]);
                final double perNs = w / (H * C / (nm[j] * 1e-9)) * 1e-9;
                final double ratio = perNs / measured[i][j];
                min = Math.min(min, ratio);
                max = Math.max(max, ratio);
                sum += ratio;
                n++;
            }
        }
        check(min >= 0.6 && max <= 1.1, "VMB-12c ratio model/measurement in [0.6, 1.1]: " + min + " - " + max);
        ok("VMB-12c [PETERS09] Fig. 2, espectros de SpRS medidos (bomba de 1560 nm, 5,5/21,5/41,3 km, 18 pontos): "
                + "modelo/medida de %.2f a %.2f (media %.2f). A forma com a distancia confere; o modelo fica 13-33%% "
                + "abaixo, mais perto da bomba (a secao de choque e ajustada a [ERAERDS10], outra fibra)", min, max,
                sum / n);
    }

    // ---------------------------------------------------------------- VMB-12d

    /**
     * NLI on a channel under test by the closed form used by GNPy (gn_model_analytic, [POGGIOLINI12] eqs. 120 and
     * 123), referred to the fibre output: P = e^{-aL} sum_j w_j gamma^2 psi_j P_cut P_j^2 / B_j^2.
     */
    static double gnpyAnalyticNliW(double[] f, double[] p, double[] b, int cut, double lengthKm, double alphaDbKm,
            double gammaPerWKm, double dispersionSPerM2) {
        final double a = alphaDbKm * FiberAttenuationModel.DB_TO_NEPER / 1e3;
        final double l = lengthKm * 1e3;
        final double leff = -Math.expm1(-a * l) / a;
        final double la = 1.0 / a;
        final double gamma = gammaPerWKm / 1e3;
        double sum = 0.0;
        for (int j = 0; j < f.length; j++) {
            final double b2c = dispersionSPerM2 * C / (2 * Math.PI * f[cut] * f[cut]);
            final double b2j = dispersionSPerM2 * C / (2 * Math.PI * f[j] * f[j]);
            final double beta2 = (b2c + b2j) / 2;
            final double df = f[j] - f[cut];
            final double k = Math.PI * Math.PI * la * beta2 * b[cut];
            double psi = (asinh(k * (df + b[j] / 2)) - asinh(k * (df - b[j] / 2))) / 2;
            psi *= leff * leff / (2 * Math.PI * beta2 * la);
            final double w = j == cut ? 16.0 / 27.0 : 32.0 / 27.0;
            sum += gamma * gamma * w * psi / (b[j] * b[j]) * p[cut] * p[j] * p[j];
        }
        return sum * Math.exp(-a * l);
    }

    private static double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x + 1));
    }

    private static double fig3NliDbm(double bw, double lengthKm, double qchDbm) {
        final double fq = C / 1530.59e-9;
        final int n = bw == 75e9 ? 54 : 20;
        final double[] f = new double[n + 1];
        final double[] p = new double[n + 1];
        final double[] b = new double[n + 1];
        f[0] = fq;
        p[0] = 1e-3 * Math.pow(10, qchDbm / 10);
        b[0] = 12.5e9;
        for (int i = 0; i < n; i++) {
            f[i + 1] = fq - 200e9 - bw / 2 - bw * i;
            p[i + 1] = 1e-3;
            b[i + 1] = bw;
        }
        return dbm(gnpyAnalyticNliW(f, p, b, 0, lengthKm, 0.19, 1.2, 1.75e-5));
    }

    private static void mehdizadehFig3() {
        // GNPy 2.14.2, gn_model_analytic, QCh at 1530.59 nm (-80 dBm, 12.5 GHz), comb after a 200 GHz guard
        final double[][] gnpy = {{75e9, 5, -136.0}, {75e9, 25, -129.2}, {75e9, 50, -131.5}, {75e9, 100, -140.0},
            {200e9, 5, -144.6}, {200e9, 25, -137.8}, {200e9, 50, -140.0}, {200e9, 100, -148.6}};
        for (double[] g : gnpy) {
            withinDb(fig3NliDbm(g[0], g[1], -80), g[2], 0.3, String.format(Locale.ROOT,
                    "VMB-12d forma fechada do GNPy = execucao do GNPy 2.14.2 (CCh %.0f GHz, %.0f km)", g[0] / 1e9,
                    g[1]));
        }
        // [MEHDI24] Fig. 3, peak of the NLI curves (read from the figure): -130 dBm (75 GHz), -137 dBm (200 GHz)
        withinDb(fig3NliDbm(75e9, 25, -80), -130, 2.0, "VMB-12d [MEHDI24] Fig. 3: pico do NLI, CCh de 75 GHz");
        withinDb(fig3NliDbm(200e9, 25, -80), -137, 2.0, "VMB-12d [MEHDI24] Fig. 3: pico do NLI, CCh de 200 GHz");
        final double scale = fig3NliDbm(75e9, 25, -70) - fig3NliDbm(75e9, 25, -80);
        withinDb(scale, 10.0, 0.01, "VMB-12d esse NLI sobe 10 dB com +10 dB no QCh (proporcional a potencia do QCh)");
        // photon probability of that NLI at 25 km: eta_d = 0.3, T_g = 100 ps
        final double pw = 1e-3 * Math.pow(10, fig3NliDbm(75e9, 25, -80) / 10);
        final double prob = pw * 0.3 * 100e-12 / (H * C / 1530.59e-9);
        check(prob < 1e-6, "VMB-12d negligible photon probability: " + prob);
        ok("VMB-12d probabilidade de foton desse NLI = %.1e por janela (dark count = 1e-8): o NLI do artigo nao pesa no "
                + "p_b; o ruido do QCh vem do SpRS", prob);
        // the GN integral of the simulator at the QCh frequency (all products that land on the QCh)
        final List<ClassicalChannel> ch = new ArrayList<ClassicalChannel>();
        final double fq = C / 1530.59e-9;
        for (int i = 0; i < 54; i++) {
            ch.add(new ClassicalChannel(fq - 200e9 - 37.5e9 - 75e9 * i, 1e-3, ClassicalChannel.Direction.CO_PROPAGATING,
                    75e9));
        }
        final FiberAttenuationModel fm = FiberAttenuationModel.g652d();
        final double[][] g = NonlinearInterferenceModel.grid(ch, 2.5e9);
        final double gn = NonlinearInterferenceModel.standard().psdWPerHz(g[1], g[0][0], g[0][1], fq, 25,
                fm.nepersPerKm(1550), fm.nepersPerKm(1530.59)) * 12.5e9;
        System.out.println(String.format(Locale.ROOT, "[INFO] VMB-12d no mesmo cenario, os produtos de FWM dos canais "
                + "modulados que caem no QCh (GN do simulador) somam %.1f dBm a 25 km: o GNPy nao calcula esse termo "
                + "(so SPM/XPM sobre o canal sob teste) e a FWM aproximada do artigo conta so tons que caem no filtro.",
                dbm(gn)));
    }

    // ---------------------------------------------------------------- VMB-12e

    private static void mehdizadehFig5() {
        final QKDPhysicalParameters par = new QKDPhysicalParameters();
        // convention of [MEHDI24] eq. 1: L e^{-a_c L}, every SpRS photon in the filter counted (fraction 1)
        final MultiBandQkdLinkModel mehdi = new MultiBandQkdLinkModel(par, FiberAttenuationModel.g652d(),
                SpontaneousRamanModel.standard(), MultiBandQkdLinkModel.ForwardForm.EQUAL_LOSS,
                MultiBandQkdLinkModel.PHYSICAL_DETECTED_FRACTION);
        final MultiBandQkdLinkModel physical = MultiBandQkdLinkModel.physical(par);
        final MultiBandQkdLinkModel paper = MultiBandQkdLinkModel.paperCompatible(par);
        final List<ClassicalChannel> comb = ClassicalChannel.referenceCLComb(0.0,
                ClassicalChannel.Direction.CO_PROPAGATING);
        // [MEHDI24] Fig. 5(b), QBER read from the figure: {length km, nm, QBER}
        final double[][] sband = {{5, 1440, 0.026}, {5, 1480, 0.045}, {5, 1522, 0.066}, {30, 1470, 0.160},
            {30, 1490, 0.183}, {30, 1520, 0.215}, {70, 1470, 0.270}, {70, 1490, 0.290}, {70, 1520, 0.315}};
        double worstMehdi = 0.0;
        double worstPaper = 0.0;
        double worstPhysical = 0.0;
        for (double[] t : sband) {
            worstMehdi = Math.max(worstMehdi, Math.abs(mehdi.evaluate(t[1], t[0], comb).getQber() - t[2]));
            worstPaper = Math.max(worstPaper, Math.abs(paper.evaluate(t[1], t[0], comb).getQber() - t[2]));
            worstPhysical = Math.max(worstPhysical, Math.abs(physical.evaluate(t[1], t[0], comb).getQber() - t[2]));
        }
        check(worstMehdi <= 0.03, "VMB-12e QBER within 0.03: " + worstMehdi);
        ok("VMB-12e [MEHDI24] Fig. 5(b), 9 pontos de 1440 a 1522 nm (5, 30 e 70 km): com L e^{-a_c L} e fracao 1 a "
                + "QBER erra ate %.3f; com a fracao 0,5 de [DIBAJ25] (modelo 'artigo') o erro chega a %.3f. O artigo "
                + "nao usa o fator 0,5", worstMehdi, worstPaper);
        System.out.println(String.format(Locale.ROOT, "[INFO] VMB-12e com o modelo 'fisico' do simulador (forma exata, "
                + "fracao 1) o erro maximo e %.3f: a forma exata difere de L e^{-a_c L} quando a_q > a_c (QCh na banda "
                + "E/S, a 70 km).", worstPhysical));
        final MultiBandLinkResult r1522 = mehdi.evaluate(1522, 5, comb);
        System.out.println(String.format(Locale.ROOT, "[INFO] VMB-12e 1522 nm / 5 km na convencao do artigo: QBER %.3f "
                + "(artigo 0,066), SKR %.1f Mbit/s (artigo 25,82): perto do corte, a SKR e muito sensivel a QBER.",
                r1522.getQber(), r1522.getSecretKeyRateBps() / 1e6));
        final double[][] edge = {{30, 1410, 0.057}, {30, 1450, 0.125}, {70, 1392, 0.056}, {70, 1410, 0.140},
            {70, 1450, 0.245}};
        final StringBuilder sb = new StringBuilder();
        for (double[] t : edge) {
            sb.append(String.format(Locale.ROOT, " %.0f nm/%.0f km: %.3f x %.3f;", t[1], t[0],
                    mehdi.evaluate(t[1], t[0], comb).getQber(), t[2]));
        }
        pending++;
        System.out.println("[PENDENTE] VMB-12e inicio do ruido no fim da banda E (QBER modelo x artigo):" + sb
                + " o artigo tem 2 a 5 vezes mais SpRS a 14-20 THz da borda do comb (1392-1450 nm), fora da janela medida da secao de "
                + "choque; o artigo nao diz como a estendeu.");
    }

    // ---------------------------------------------------------------- helpers

    private static double dbm(double w) {
        return 10 * Math.log10(w * 1e3);
    }

    private static void withinDb(double value, double target, double tol, String msg) {
        check(Math.abs(value - target) <= tol, String.format(Locale.ROOT, "%s: obtido %.2f, alvo %.2f", msg, value,
                target));
        ok("%s: obtido %.1f (alvo %.1f, dif %+.1f dB)", msg, value, target, value - target);
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
