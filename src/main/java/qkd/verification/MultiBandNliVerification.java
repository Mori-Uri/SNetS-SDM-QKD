package qkd.verification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;

import network.Circuit;
import network.Link;
import network.Oxc;
import qkd.multiband.ClassicalChannel;
import qkd.multiband.FiberAttenuationModel;
import qkd.multiband.MultiBandLinkResult;
import qkd.multiband.MultiBandQkdLinkModel;
import qkd.multiband.NonlinearInterferenceModel;
import qkd.network.QKDSpan;
import qkd.physical.QKDPhysicalLayer;
import qkd.physical.QKDPhysicalLayerConfig;
import qkd.physical.QKDPhysicalParameters;
import qkd.physical.QKDSpanPhysicalResult;
import simulationControl.parsers.NetworkConfig.CoreConfig;

/**
 * Parte 45 (nível N2): verification of the GN-model NLI on the quantum channel ({@link NonlinearInterferenceModel}).
 *
 * <ul>
 *   <li>VMB-11a: in-band NLI at the centre of a flat spectrum equals the closed form of [POGGIOLINI14] (eq. 13);</li>
 *   <li>VMB-11b: the result does not depend on the grid step (2.5 to 10 GHz) and is linear in gamma^2 and cubic in
 *       the power;</li>
 *   <li>VMB-11c: no four-wave-mixing product reaches a QCh at or below 1400 nm from the C+L comb (exactly 0;
 *       above ~1405 nm the edge of the E band can be reached);</li>
 *   <li>VMB-11d: the NLI falls off with the distance from the edge of the classical band;</li>
 *   <li>VMB-11e: the simulator (live SNetS circuits, nliModel = gn) gives the same NLI as the link model;</li>
 *   <li>VMB-11f: [MEHDI24] 1522 nm / 5 km is reported (still [PENDENTE]): with SpRS + GN NLI the model gives about
 *       77 Mbit/s against 25.82 Mbit/s in the paper.</li>
 * </ul>
 */
public final class MultiBandNliVerification {

    private MultiBandNliVerification() {
    }

    public static void main(String[] args) {
        closedForm();
        gridAndScaling();
        outOfReach();
        edgeDecay();
        simulatorEqualsLinkModel();
        mehdizadeh1522();
        System.out.println();
        System.out.println("Multi-band NLI verification (Parte 45): ALL CHECKS PASSED (1 item pendente, ver acima)");
    }

    private static final double C = 299792458.0;

    private static double[] flat(double bandwidthHz, double stepHz, double psd) {
        final double[] g = new double[(int) Math.round(bandwidthHz / stepHz)];
        Arrays.fill(g, psd);
        return g;
    }

    // ---------------------------------------------------------------- VMB-11a

    private static void closedForm() {
        final NonlinearInterferenceModel m = new NonlinearInterferenceModel(1.3, -21.27, 0.0, 193.5e12);
        final double b = 4e12;
        final double psd = 1e-14;
        final double ap = 0.2 * FiberAttenuationModel.DB_TO_NEPER;
        final double l = 80.0;
        final double f = 193.5e12;
        final double num = m.psdWPerHz(flat(b, 10e9, psd), f - b / 2, 10e9, f, l, ap, ap) * Math.exp(ap * l);
        final double beta2 = 21.27e-24;
        final double leff = -Math.expm1(-ap * l) / ap;
        final double la = 1.0 / ap;
        final double x = Math.PI * Math.PI / 2 * beta2 * la * b * b;
        final double closed = (8.0 / 27.0) * 1.3 * 1.3 * psd * psd * psd * leff * leff
                * Math.log(x + Math.sqrt(x * x + 1)) / (Math.PI * beta2 * la);
        relative(num, closed, 0.05, "VMB-11a GN integral = closed form of [POGGIOLINI14] at the centre of 4 THz");
    }

    // ---------------------------------------------------------------- VMB-11b

    private static void gridAndScaling() {
        final NonlinearInterferenceModel m = NonlinearInterferenceModel.standard();
        final double ap = 0.2 * FiberAttenuationModel.DB_TO_NEPER;
        final double f = 193.5e12;
        final double b = 1e12;
        final double ref = m.psdWPerHz(flat(b, 2.5e9, 1e-14), f - b / 2, 2.5e9, f, 50, ap, ap);
        for (double step : new double[] {5e9, 10e9}) {
            relative(m.psdWPerHz(flat(b, step, 1e-14), f - b / 2, step, f, 50, ap, ap), ref, 0.03,
                    String.format(Locale.ROOT, "VMB-11b grid step %.1f GHz gives the 2.5 GHz value", step / 1e9));
        }
        relative(m.psdWPerHz(flat(b, 2.5e9, 2e-14), f - b / 2, 2.5e9, f, 50, ap, ap), 8 * ref, 1e-12,
                "VMB-11b NLI cubic in the PSD");
        final NonlinearInterferenceModel m2 = new NonlinearInterferenceModel(2.4, -22.317, 0.1331, 193.5e12);
        relative(m2.psdWPerHz(flat(b, 2.5e9, 1e-14), f - b / 2, 2.5e9, f, 50, ap, ap), 4 * ref, 1e-12,
                "VMB-11b NLI proportional to gamma^2");
    }

    // ---------------------------------------------------------------- VMB-11c

    private static void outOfReach() {
        final List<ClassicalChannel> comb = ClassicalChannel.referenceCLComb(0.0,
                ClassicalChannel.Direction.CO_PROPAGATING);
        final double[][] g = NonlinearInterferenceModel.grid(comb, 5e9);
        final FiberAttenuationModel fm = FiberAttenuationModel.g652d();
        for (double nm : new double[] {1260, 1310, 1381, 1400}) {
            final double v = NonlinearInterferenceModel.standard().psdWPerHz(g[1], g[0][0], g[0][1], C / (nm * 1e-9),
                    25, fm.nepersPerKm(1550), fm.nepersPerKm(nm));
            check(v == 0.0, "VMB-11c no NLI at " + nm + " nm");
        }
        ok("VMB-11c NLI = 0 for QCh at 1260, 1310, 1381 and 1400 nm with the C+L comb (2 f_max - f_q below the comb)");
    }

    // ---------------------------------------------------------------- VMB-11d

    private static void edgeDecay() {
        final List<ClassicalChannel> comb = ClassicalChannel.referenceCLComb(0.0,
                ClassicalChannel.Direction.CO_PROPAGATING);
        final double[][] g = NonlinearInterferenceModel.grid(comb, 5e9);
        final FiberAttenuationModel fm = FiberAttenuationModel.g652d();
        double last = Double.MAX_VALUE;
        for (double nm : new double[] {1522, 1510, 1500, 1480}) {
            final double v = NonlinearInterferenceModel.standard().psdWPerHz(g[1], g[0][0], g[0][1], C / (nm * 1e-9),
                    5, fm.nepersPerKm(1550), fm.nepersPerKm(nm));
            check(v < last, "VMB-11d NLI decreases away from the band edge (" + nm + " nm)");
            ok("VMB-11d G_NLI(%.0f nm, 5 km) = %.3e W/Hz", nm, v);
            last = v;
        }
    }

    // ---------------------------------------------------------------- VMB-11e

    private static void simulatorEqualsLinkModel() {
        final double startHz = 191.3e12;
        final double slotHz = 12.5e9;
        final double qchNm = 1530.0; // just above the 320-slot C grid (191.3-195.3 THz)
        final QKDPhysicalParameters p = new Gson().fromJson(String.format(Locale.ROOT,
                "{\"multiband\":{\"architecture\":\"MB\",\"ramanModel\":\"fisico\",\"qchCenterNm\":%.1f,"
                + "\"hostCoreId\":0,\"classicalBandStartThz\":191.3,\"nliModel\":\"gn\"}}", qchNm),
                QKDPhysicalLayerConfig.class).toParameters();
        final Link link = new Link(new Oxc("A"), new Oxc("B"), 320, slotHz, 20.0, Arrays.asList(
                new CoreConfig(0, Collections.<Integer>emptyList()), new CoreConfig(1, Collections.<Integer>emptyList())));
        final List<ClassicalChannel> channels = new ArrayList<ClassicalChannel>();
        final int[][] bands = {{250, 255}, {270, 275}, {290, 297}, {300, 305}, {310, 320}};
        for (int[] band : bands) {
            final Circuit c = new Circuit();
            c.setIndexCore(0);
            c.setSpectrumAssigned(band);
            c.setLaunchPowerLinear(1e-3);
            link.getCore(0).addCircuit(c);
            final double centre = startHz + ((band[0] + band[1]) / 2.0 - 0.5) * slotHz;
            channels.add(new ClassicalChannel(centre, 1e-3, ClassicalChannel.Direction.CO_PROPAGATING,
                    (band[1] - band[0] + 1) * slotHz));
        }
        final QKDSpanPhysicalResult r = new QKDPhysicalLayer(p).evaluateSpanUsingStoredLaunchPowers(link,
                new QKDSpan("A", "B", 0, 1, 0.0, 20.0, "A", "B"), 1, new int[] {1, 1}, 0.0, qchNm);
        final MultiBandLinkResult ref = MultiBandQkdLinkModel.physical(p)
                .withNli(NonlinearInterferenceModel.standard(), slotHz).evaluate(qchNm, 20.0, channels);
        check(r.getNliPowerW() > 0.0, "VMB-11e NLI present at 1530 nm");
        relative(r.getNliPowerW(), ref.getNliPowerW(), 1e-9,
                "VMB-11e simulator (live circuits, nliModel = gn) = link model, NLI at 1530 nm");
        // p = 1 - e^{-N} in the simulator and p = N in the link model: relative difference ~N/2
        relative(r.getSecretKeyRateBps(), ref.getSecretKeyRateBps(), 5e-3,
                "VMB-11e simulator SKR with SpRS + NLI = link model");
    }

    // ---------------------------------------------------------------- VMB-11f

    private static void mehdizadeh1522() {
        final QKDPhysicalParameters p = new QKDPhysicalParameters();
        final MultiBandLinkResult r = MultiBandQkdLinkModel.paperCompatible(p)
                .withNli(NonlinearInterferenceModel.standard(), 2.5e9)
                .evaluate(1522, 5, ClassicalChannel.referenceCLComb(0.0, ClassicalChannel.Direction.CO_PROPAGATING));
        System.out.println(String.format(Locale.ROOT, "[PENDENTE] VMB-11f [MEHDI24] 1522 nm / 5 km: 25,82 Mbit/s, QBER "
                + "0,066. Modelo com SpRS + NLI (GN): %.1f Mbit/s, QBER %.3f (p_SpRS %.1e, p_NLI %.1e). Para a QBER do "
                + "artigo, p_b ~ 2e-2: o NLI do GN com canais de 0 dBm/75 GHz fica ~2 ordens abaixo do que faltaria.",
                r.getSecretKeyRateBps() / 1e6, r.getQber(), r.getRamanProbabilitySameCore(), r.getNliProbability()));
    }

    // ---------------------------------------------------------------- helpers

    private static void relative(double value, double target, double tol, String msg) {
        final double err = Math.abs(value - target) / Math.abs(target);
        check(err <= tol, String.format(Locale.ROOT, "%s: obtido %.6g, alvo %.6g (erro %.2e)", msg, value, target, err));
        ok("%s (erro relativo %.1e)", msg, err);
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
