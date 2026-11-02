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
import qkd.multiband.MultiBandSettings;
import qkd.network.QKDSpan;
import qkd.physical.QKDPhysicalLayer;
import qkd.physical.QKDPhysicalLayerConfig;
import qkd.physical.QKDPhysicalParameters;
import qkd.physical.QKDSpanPhysicalResult;
import simulationControl.parsers.NetworkConfig.CoreConfig;

/**
 * Parte 43: the multi-band physical layer inside the simulator ({@code qkdPhysicalLayer.multiband}) gives the same
 * numbers as the verified link model ({@link MultiBandQkdLinkModel}, items VMB-1 to VMB-8). Items VMB-9a to VMB-9h:
 *
 * <ul>
 *   <li>VMB-9a: without the {@code multiband} group nothing changes (no settings, quadratic C-band loss);</li>
 *   <li>VMB-9b: the group is read from the case file (loss, QCh centre, architecture, Raman model);</li>
 *   <li>VMB-9c/d: MB, a live SNetS link with classical circuits on the host core: SpRS power equal to the link model
 *       for the {@code fisico} and {@code artigo} models, and the same SKR;</li>
 *   <li>VMB-9e: Raman model {@code nenhum} and architecture MF: p_b = p_dark, same SKR as a dark fibre;</li>
 *   <li>VMB-9f: MBSDM: intercore SpRS from the cores adjacent to the host equal to the link model ([CAI20]);</li>
 *   <li>VMB-9g: linear crosstalk counted only in band (QCh inside the classical grid), never in MF;</li>
 *   <li>VMB-9h: ordering nenhum &gt; artigo/fisico in SKR, and SpRS grows with the number of classical channels.</li>
 * </ul>
 */
public final class MultiBandNetworkVerification {

    private static final double SLOT_HZ = 12.5e9;
    private static final double START_HZ = 191.3e12;
    private static final double QCH_NM = 1381.0;
    private static final double SPAN_KM = 25.0;

    private MultiBandNetworkVerification() {
    }

    public static void main(String[] args) {
        configParsing();
        sameCore("fisico", MultiBandQkdLinkModel.ForwardForm.EXACT, MultiBandQkdLinkModel.PHYSICAL_DETECTED_FRACTION);
        sameCore("artigo", MultiBandQkdLinkModel.ForwardForm.EQUAL_LOSS, MultiBandQkdLinkModel.PAPER_DETECTED_FRACTION);
        darkCases();
        intercore();
        crosstalkInBand();
        ordering();
        System.out.println();
        System.out.println("Multi-band network verification (Parte 43): ALL CHECKS PASSED");
    }

    // ------------------------------------------------------------------ VMB-9a/b

    private static void configParsing() {
        final Gson gson = new Gson();
        final QKDPhysicalLayerConfig plain = gson.fromJson("{}", QKDPhysicalLayerConfig.class);
        final QKDPhysicalParameters p0 = plain.toParameters();
        check(p0.getMultiband() == null, "VMB-9a no multiband group -> no settings");
        within(p0.attenuationDbPerKm(1550.0), 0.18, 1e-15, "VMB-9a quadratic C-band loss kept at 1550 nm");
        within(p0.getAttenuationCenterNm(), 1550.0, 0.0, "VMB-9a QCh wavelength kept at 1550 nm");

        final QKDPhysicalLayerConfig off = gson.fromJson("{\"multiband\":{\"enabled\":false}}",
                QKDPhysicalLayerConfig.class);
        check(off.toParameters().getMultiband() == null, "VMB-9a multiband.enabled = false -> disabled");
        ok("VMB-9a without the group (or enabled = false) the parameters are the ones of Parte 42");

        final QKDPhysicalLayerConfig mb = gson.fromJson("{\"multiband\":{\"architecture\":\"MB-SDM\","
                + "\"ramanModel\":\"artigo\",\"qchCenterNm\":1381,\"hostCoreId\":1}}", QKDPhysicalLayerConfig.class);
        final QKDPhysicalParameters p = mb.toParameters();
        final MultiBandSettings s = p.getMultiband();
        check(s != null && s.getArchitecture() == MultiBandSettings.Architecture.MBSDM
                && s.getRamanModel() == MultiBandSettings.RamanModel.ARTIGO && s.getHostCoreId() == 1,
                "VMB-9b architecture, Raman model and host core read from the file");
        within(p.getAttenuationCenterNm(), 1381.0, 0.0, "VMB-9b QCh centre moved to qchCenterNm");
        within(p.attenuationDbPerKm(1381.0), FiberAttenuationModel.g652d().dbPerKm(1381.0), 0.0,
                "VMB-9b loss of the G.652.D model used everywhere");
        within(p.getMu(), 0.7, 0.0, "VMB-9b other parameters unchanged");
        ok("VMB-9b group read: %s; alpha(1381 nm) = %.4f dB/km", s, p.attenuationDbPerKm(1381.0));
    }

    // ------------------------------------------------------------------ VMB-9c/d

    private static void sameCore(String model, MultiBandQkdLinkModel.ForwardForm form, double fraction) {
        final QKDPhysicalParameters p = params("MB", model, 0);
        final Link link = link(Arrays.asList(new CoreConfig(0, Collections.<Integer>emptyList()),
                new CoreConfig(1, Collections.<Integer>emptyList())));
        final int[][] bands = {{1, 4}, {100, 105}, {200, 203}, {300, 319}};
        final double[] powers = {1e-3, 2e-3, 0.5e-3, 1.5e-3};
        final List<ClassicalChannel> channels = new ArrayList<ClassicalChannel>();
        for (int i = 0; i < bands.length; i++) {
            link.getCore(0).addCircuit(circuit(0, bands[i], powers[i]));
            channels.add(channel(bands[i], powers[i]));
        }
        final QKDSpanPhysicalResult r = new QKDPhysicalLayer(p).evaluateSpanUsingStoredLaunchPowers(link, span(), 1,
                new int[] {1, 1}, 0.0, QCH_NM);
        final MultiBandLinkResult ref = new MultiBandQkdLinkModel(p, FiberAttenuationModel.g652d(),
                p.getMultiband().getRaman(), form, fraction).evaluate(QCH_NM, SPAN_KM, channels);
        relative(r.getRamanPowerW(), ref.getRamanPowerSameCoreW(), 1e-12,
                "VMB-9" + ("fisico".equals(model) ? "c" : "d") + " MB/" + model + ": SpRS power equals the link model");
        // the simulator uses p = 1 - exp(-N); the link model uses p = N (difference ~N/2 relative, N ~ 1e-5)
        relative(r.getSecretKeyRateBps(), ref.getSecretKeyRateBps(), 1e-4,
                "VMB-9" + ("fisico".equals(model) ? "c" : "d") + " MB/" + model + ": SKR equals the link model");
    }

    // ------------------------------------------------------------------ VMB-9e

    private static void darkCases() {
        final double darkSkr = MultiBandQkdLinkModel.physical(params("MF", "nenhum", 0)).evaluateDarkFiber(QCH_NM,
                SPAN_KM).getSecretKeyRateBps();
        for (String[] c : new String[][] {{"MB", "nenhum"}, {"MF", "fisico"}, {"MF", "artigo"}}) {
            final QKDPhysicalParameters p = params(c[0], c[1], 0);
            final Link link = link(Arrays.asList(new CoreConfig(0, Collections.singletonList(1)),
                    new CoreConfig(1, Collections.singletonList(0))));
            link.getCore(0).addCircuit(circuit(0, new int[] {1, 1}, 1e-3)); // same slot as the QCh, adjacent core
            link.getCore(0).addCircuit(circuit(0, new int[] {150, 160}, 5e-3));
            final QKDSpanPhysicalResult r = new QKDPhysicalLayer(p).evaluateSpanUsingStoredLaunchPowers(link, span(),
                    1, new int[] {1, 1}, 1e-6, QCH_NM);
            within(r.getTotalBackgroundProbability(), p.getDarkCountProbability(), 0.0,
                    "VMB-9e " + c[0] + "/" + c[1] + ": p_b = p_dark");
            check(Double.compare(r.getSecretKeyRateBps(), darkSkr) == 0, "VMB-9e " + c[0] + "/" + c[1]
                    + ": SKR equals the dark fibre");
        }
        ok("VMB-9e MB/nenhum and MF (any Raman model) give the dark-fibre SKR %.4f Mbit/s", darkSkr / 1e6);
    }

    // ------------------------------------------------------------------ VMB-9f

    private static void intercore() {
        final QKDPhysicalParameters p = params("MBSDM", "fisico", 0);
        // host core 0 adjacent to 1 and 2; virtual QKD core 3 without neighbours
        final Link link = link(Arrays.asList(new CoreConfig(0, Arrays.asList(1, 2)),
                new CoreConfig(1, Arrays.asList(0)), new CoreConfig(2, Arrays.asList(0)),
                new CoreConfig(3, Collections.<Integer>emptyList())));
        final double hPerM = 1e-9; // 1e-6 /km, [CAI20]
        final int[][] bands = {{10, 13}, {50, 60}, {250, 255}};
        final List<ClassicalChannel> neighbours = new ArrayList<ClassicalChannel>();
        for (int core = 1; core <= 2; core++) {
            for (int i = 0; i < bands.length; i++) {
                final double w = 1e-3 * (core + i);
                link.getCore(core).addCircuit(circuit(core, bands[i], w));
                neighbours.add(channel(bands[i], w));
            }
        }
        final List<ClassicalChannel> same = new ArrayList<ClassicalChannel>();
        link.getCore(0).addCircuit(circuit(0, new int[] {120, 125}, 2e-3));
        same.add(channel(new int[] {120, 125}, 2e-3));
        final QKDSpanPhysicalResult r = new QKDPhysicalLayer(p).evaluateSpanUsingStoredLaunchPowers(link, span(), 3,
                new int[] {1, 1}, hPerM, QCH_NM);
        final MultiBandLinkResult ref = MultiBandQkdLinkModel.physical(p).evaluate(QCH_NM, SPAN_KM, same, neighbours,
                hPerM * 1000.0);
        relative(r.getRamanPowerW(), ref.getRamanPowerSameCoreW() + ref.getRamanPowerNeighbourW(), 1e-12,
                "VMB-9f MBSDM: same-core + intercore SpRS equals the link model");
        check(ref.getRamanPowerNeighbourW() > 0.0, "VMB-9f intercore term present");
        ok("VMB-9f intercore share of the SpRS: %.2e of the total", ref.getRamanPowerNeighbourW()
                / (ref.getRamanPowerSameCoreW() + ref.getRamanPowerNeighbourW()));
    }

    // ------------------------------------------------------------------ VMB-9g

    private static void crosstalkInBand() {
        // QKD core 1 adjacent to the host core 0, circuit on core 0 on the same slots as the QCh
        final Link link = link(Arrays.asList(new CoreConfig(0, Collections.singletonList(1)),
                new CoreConfig(1, Collections.singletonList(0))));
        link.getCore(0).addCircuit(circuit(0, new int[] {150, 160}, 1e-3));
        final int[] qch = {155, 155};
        final double inBandNm = SpontaneousC() / (START_HZ + 154.5 * SLOT_HZ) * 1e9;
        final QKDSpanPhysicalResult inBand = new QKDPhysicalLayer(params("MBSDM", "nenhum", 0))
                .evaluateSpanUsingStoredLaunchPowers(link, span(), 1, qch, 1e-9, inBandNm);
        final QKDSpanPhysicalResult outBand = new QKDPhysicalLayer(params("MBSDM", "nenhum", 0))
                .evaluateSpanUsingStoredLaunchPowers(link, span(), 1, qch, 1e-9, QCH_NM);
        final QKDSpanPhysicalResult mf = new QKDPhysicalLayer(params("MF", "nenhum", 0))
                .evaluateSpanUsingStoredLaunchPowers(link, span(), 1, qch, 1e-9, inBandNm);
        check(inBand.getCrosstalkPowerW() > 0.0, "VMB-9g QCh in the C grid: linear XT counted");
        check(outBand.getCrosstalkPowerW() == 0.0, "VMB-9g QCh at 1381 nm: XT filtered out");
        check(mf.getCrosstalkPowerW() == 0.0, "VMB-9g MF: no XT");
        ok("VMB-9g linear XT: %.3e W in band (%.2f nm), 0 at 1381 nm and 0 in MF", inBand.getCrosstalkPowerW(),
                inBandNm);
    }

    // ------------------------------------------------------------------ VMB-9h

    private static void ordering() {
        double previous = Double.MAX_VALUE;
        for (String model : new String[] {"nenhum", "artigo", "fisico"}) {
            final Link link = link(Arrays.asList(new CoreConfig(0, Collections.<Integer>emptyList()),
                    new CoreConfig(1, Collections.<Integer>emptyList())));
            for (int k = 0; k < 40; k++) {
                link.getCore(0).addCircuit(circuit(0, new int[] {1 + 8 * k, 4 + 8 * k}, 1e-3));
            }
            final double skr = new QKDPhysicalLayer(params("MB", model, 0)).evaluateSpanUsingStoredLaunchPowers(link,
                    span(), 1, new int[] {1, 1}, 0.0, QCH_NM).getSecretKeyRateBps();
            ok("VMB-9h MB, 40 channels of 1 mW, %s: SKR %.3f Mbit/s", model, skr / 1e6);
            if (model.equals("nenhum") || model.equals("artigo")) {
                check(skr < previous, "VMB-9h SKR decreases nenhum > artigo > fisico");
            }
            previous = skr;
        }
        double last = -1;
        for (int n : new int[] {1, 10, 40}) {
            final Link link = link(Arrays.asList(new CoreConfig(0, Collections.<Integer>emptyList()),
                    new CoreConfig(1, Collections.<Integer>emptyList())));
            for (int k = 0; k < n; k++) {
                link.getCore(0).addCircuit(circuit(0, new int[] {1 + 8 * k, 4 + 8 * k}, 1e-3));
            }
            final double w = new QKDPhysicalLayer(params("MB", "fisico", 0)).evaluateSpanUsingStoredLaunchPowers(link,
                    span(), 1, new int[] {1, 1}, 0.0, QCH_NM).getRamanPowerW();
            check(w > last, "VMB-9h SpRS grows with the number of classical channels");
            last = w;
        }
        ok("VMB-9h SpRS grows with the number of classical channels");
    }

    // ------------------------------------------------------------------ helpers

    private static double SpontaneousC() {
        return qkd.multiband.SpontaneousRamanModel.C;
    }

    private static QKDPhysicalParameters params(String arch, String raman, int host) {
        final String json = String.format(Locale.ROOT, "{\"multiband\":{\"architecture\":\"%s\",\"ramanModel\":\"%s\","
                + "\"qchCenterNm\":%.1f,\"hostCoreId\":%d,\"classicalBandStartThz\":%.1f}}", arch, raman, QCH_NM,
                host, START_HZ * 1e-12);
        return new Gson().fromJson(json, QKDPhysicalLayerConfig.class).toParameters();
    }

    private static Link link(List<CoreConfig> cores) {
        return new Link(new Oxc("A"), new Oxc("B"), 320, SLOT_HZ, SPAN_KM, cores);
    }

    private static QKDSpan span() {
        return new QKDSpan("A", "B", 0, 1, 0.0, SPAN_KM, "A", "B");
    }

    private static Circuit circuit(int core, int[] band, double powerW) {
        final Circuit c = new Circuit();
        c.setIndexCore(core);
        c.setSpectrumAssigned(band);
        c.setLaunchPowerLinear(powerW);
        return c;
    }

    private static ClassicalChannel channel(int[] band, double powerW) {
        final double f = START_HZ + ((band[0] + band[1]) / 2.0 - 0.5) * SLOT_HZ;
        return new ClassicalChannel(f, powerW, ClassicalChannel.Direction.CO_PROPAGATING);
    }

    private static void within(double value, double target, double tol, String msg) {
        check(Math.abs(value - target) <= tol, String.format(Locale.ROOT, "%s: obtido %.10g (alvo %.10g)", msg, value,
                target));
    }

    private static void relative(double value, double target, double tol, String msg) {
        final double err = Math.abs(value - target) / Math.abs(target);
        check(err <= tol, String.format(Locale.ROOT, "%s: obtido %.10g, alvo %.10g (erro %.2e)", msg, value, target,
                err));
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
