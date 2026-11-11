package qkd.verification;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import network.Spectrum;
import qkd.multiband.MultiBandSettings;
import qkd.multiband.SpontaneousRamanModel;
import qkd.network.QKDNetworkParameters;
import qkd.physical.QKDPhysicalLayerConfig;
import simulationControl.parsers.NetworkConfig;

/**
 * Regressao para a incorporacao seletiva de recursos da v2 na v1.
 * Independente de JSON, do launcher e de bibliotecas externas de teste.
 */
public final class QKDV2SelectiveIntegrationVerification {
    private QKDV2SelectiveIntegrationVerification() { }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void close(double actual, double expected, double tolerance, String message) {
        require(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                message + ": expected " + expected + " got " + actual);
    }

    private static void sameBands(List<int[]> a, List<int[]> b, String message) {
        require(a.size() == b.size(), message + " (band count)");
        for (int i = 0; i < a.size(); i++) {
            require(Arrays.equals(a.get(i), b.get(i)), message + " (band #" + i + ")");
        }
    }

    private static void verifyMaxSkrPolicy() {
        NetworkConfig n = new NetworkConfig();
        n.setCores(Arrays.asList(
                new NetworkConfig.CoreConfig(0, Arrays.asList(1)),
                new NetworkConfig.CoreConfig(1, Arrays.asList(0))));
        NetworkConfig.QKDConfig q = new NetworkConfig.QKDConfig(true, 25.0, 1);
        q.setQchGranularity("route");
        q.setQchSlotPolicy("maxskr");
        n.setQkd(q);
        QKDNetworkParameters p = QKDNetworkParameters.from(n);
        require(p.isMaxSkrSlots() && p.isRouteGranularity(), "maxskr + route not accepted");
        q.setQchGranularity("span");
        boolean rejected = false;
        try { QKDNetworkParameters.from(n); }
        catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "maxskr must reject non-route granularity");
        q.setQchSlotPolicy("firstfit");
        require(!QKDNetworkParameters.from(n).isMaxSkrSlots(), "legacy slot policy modified");
        System.out.println("[OK] maxskr aceita somente route; politica antiga preservada");
    }

    private static void verifyRaman() throws Exception {
        SpontaneousRamanModel none = SpontaneousRamanModel.standard();
        SpontaneousRamanModel measured = none.withAntiStokesCorrection(
                SpontaneousRamanModel.AntiStokesCorrection.MEASURED);
        close(SpontaneousRamanModel.measuredAntiStokesFactor(17.0), 2.3, 1e-12,
                "factor at 17 THz");
        close(SpontaneousRamanModel.measuredAntiStokesFactor(24.7), 2.2, 1e-12,
                "factor at 24.7 THz");
        close(SpontaneousRamanModel.measuredAntiStokesFactor(30.0), 1.0, 1e-12,
                "factor outside calibrated range");
        double s = none.crossSectionPerKmNm(1550.0, 1425.0); // anti-Stokes
        double m = measured.crossSectionPerKmNm(1550.0, 1425.0);
        require(s > 0 && m > s, "empirical anti-Stokes must increase noise at 1425 nm");
        require(none.crossSectionPerKmNm(1550.0, 1580.0)
                        == measured.crossSectionPerKmNm(1550.0, 1580.0),
                "Stokes cross-section must remain unchanged");
        require(none.crossSectionPerKmNm(1550.0, 1540.0)
                        == measured.crossSectionPerKmNm(1550.0, 1540.0),
                "near-pump anti-Stokes must remain unchanged");
        require(SpontaneousRamanModel.AntiStokesCorrection.parse("nenhuma")
                        == SpontaneousRamanModel.AntiStokesCorrection.NONE,
                "legacy Raman default invalid");
        require(SpontaneousRamanModel.AntiStokesCorrection.parse("medida")
                        == SpontaneousRamanModel.AntiStokesCorrection.MEASURED,
                "empirical Raman selector invalid");
        // Test that the JSON backing field is actually used by the config bridge.
        QKDPhysicalLayerConfig phys = new QKDPhysicalLayerConfig();
        QKDPhysicalLayerConfig.Multiband mb = new QKDPhysicalLayerConfig.Multiband();
        Field correction = QKDPhysicalLayerConfig.Multiband.class.getDeclaredField("antiStokesCorrection");
        correction.setAccessible(true);
        correction.set(mb, "medida");
        Field multiband = QKDPhysicalLayerConfig.class.getDeclaredField("multiband");
        multiband.setAccessible(true);
        multiband.set(phys, mb);
        MultiBandSettings settings = phys.toMultiBandSettings();
        require(settings.getRaman().getAntiStokesCorrection()
                        == SpontaneousRamanModel.AntiStokesCorrection.MEASURED,
                "JSON config bridge is ignoring antiStokesCorrection");
        correction.set(mb, "nenhuma");
        require(phys.toMultiBandSettings().getRaman().getAntiStokesCorrection()
                        == SpontaneousRamanModel.AntiStokesCorrection.NONE,
                "legacy JSON config must preserve original behavior");
        System.out.printf(java.util.Locale.ROOT,
                "[OK] Raman: rho(1425 nm) sem=%.6e com=%.6e, ratio=%.4f%n", s, m, m/s);
    }

    private static void verifySpectrumCache() throws Exception {
        Spectrum spectrum = new Spectrum(16, 12.5e9);
        List<int[]> baseline = spectrum.getFreeSpectrumBands(1);
        List<int[]> repeated = spectrum.getFreeSpectrumBands(1);
        sameBands(baseline, repeated, "initial cache hit changed bands");
        require(Boolean.getBoolean("snets.noPerfCache")
                        || spectrum.getGuardedBandsCacheHits() >= 1, "cache did not record hit");
        // The caller must not be able to mutate the cache by editing the result.
        repeated.get(0)[0] = 999;
        sameBands(baseline, spectrum.getFreeSpectrumBands(1), "cache aliasing");
        spectrum.useSpectrum(new int[]{5, 6}, 1);
        List<int[]> afterUse = spectrum.getFreeSpectrumBands(1);
        String previousCacheSetting = System.getProperty("snets.noPerfCache");
        System.setProperty("snets.noPerfCache", "true");
        try { sameBands(afterUse, spectrum.getFreeSpectrumBands(1), "allocation invalidation"); }
        finally {
            if (previousCacheSetting == null) System.clearProperty("snets.noPerfCache");
            else System.setProperty("snets.noPerfCache", previousCacheSetting);
        }
        spectrum.freeSpectrum(new int[]{5, 6}, 1);
        List<int[]> afterFree = spectrum.getFreeSpectrumBands(1);
        sameBands(baseline, afterFree, "release invalidation");
        // Cache verification against non-cached computation should never fail.
        System.setProperty("snets.verifyPerf", "true");
        try { spectrum.getFreeSpectrumBands(1); }
        finally { System.clearProperty("snets.verifyPerf"); }
        System.out.println("[OK] cache do espectro: acertos="
                + spectrum.getGuardedBandsCacheHits() + " faltas="
                + spectrum.getGuardedBandsCacheMisses());
    }

    public static void main(String[] args) throws Exception {
        verifyMaxSkrPolicy();
        verifyRaman();
        verifySpectrumCache();
        System.out.println("PASS QKDV2SelectiveIntegrationVerification");
    }
}
