package network;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import qkd.multiband.FiberAttenuationModel;
import qkd.multiband.OpticalBand;

/**
 * Parte 49: classical QoT per band (optional block "multiBand" of the physicalLayer file).
 *
 * <pre>
 * "multiBand": { "enabled": true, "attenuationModel": "g652d", "waterPeakDbPerKm": 0.0,
 *                "noiseFigureDb": { "S": 7.0, "C": 5.0, "L": 6.0 }, "dispersionSlopePsNm2Km": 0.057,
 *                "gammaScalesWithFrequency": true, "isrs": true, "ramanGainSlopePerWKmTHz": 0.028 }
 * </pre>
 *
 * <p>With the block absent or {@code enabled = false} nothing changes. When enabled, the SNR of a circuit uses the
 * values at its own centre frequency f instead of the global ones:</p>
 * <ul>
 *   <li>loss a(f): G.652.D curve (or the global fiberLoss with attenuationModel "constante"); the line and
 *       pre-amplifiers compensate a(f) of the span, so the ASE of each band follows its own span loss;</li>
 *   <li>amplifier noise figure of the band of f (O, E, S, C, L, U), and photon energy h f;</li>
 *   <li>dispersion D(lambda) = D + S (lambda - lambda_ref) and beta2 at f; gamma(f) = gamma f / f_ref (constant
 *       effective area), used by the NLI (GN closed forms of the simulator);</li>
 *   <li>ISRS (inter-channel stimulated Raman scattering), triangular approximation of the Raman gain
 *       ([ZIRNGIBL98], [SEMRAU18]): in a span with total power P and channels between f_min and f_max (B), the
 *       channel at f ends the span with rho(f) = P C L_eff B e^{-P C L_eff (f - f_min)} / (1 - e^{-P C L_eff B})
 *       of the power it would have without ISRS; high frequencies lose, low frequencies gain. The amplifier of
 *       the span restores the launch power (per-channel gain equalisation), so the ISRS enters as an extra span
 *       loss -10 log10 rho(f) in the ASE. The change of the NLI by ISRS (GGN) is not modelled.</li>
 * </ul>
 * <p>f_ref and lambda_ref are the centerFrequency of the physicalLayer file. The classical slot grid is still
 * centerFrequency +- slots/2 x slot width: for a C+L grid set centerFrequency to the middle of the band.</p>
 *
 * <p>Referências: [ZIRNGIBL98] M. Zirngibl, Electron. Lett. 34(8), 1998; [SEMRAU18] D. Semrau, R. Killey, P.
 * Bayvel, JLT 36(14), 2018; [FERRARI20] A. Ferrari et al., JLT 38(16), 2020 (G.652.D e bandas); [GNPY]
 * A. Ferrari et al., JOCN 12(6), 2020.</p>
 */
public final class ClassicalMultiBand implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final double C = 299792458.0;

    /** Contents of the "multiBand" block of the physicalLayer file (Gson). */
    public static final class Config implements Serializable {
        private static final long serialVersionUID = 1L;
        boolean enabled = false;
        /** g652d (loss curve of [FERRARI20]) or constante (global fiberLoss in every band). */
        String attenuationModel = "g652d";
        double waterPeakDbPerKm = 0.0;
        /** Amplifier noise figure per band (dB); bands absent use noiseFigureOfOpticalAmplifier. */
        Map<String, Double> noiseFigureDb = defaultNf();
        double dispersionSlopePsNm2Km = 0.057;
        boolean gammaScalesWithFrequency = true;
        boolean isrs = true;
        /** Slope of the Raman gain, 1/(W km THz) (SSMF, [SEMRAU18]). */
        double ramanGainSlopePerWKmTHz = 0.028;

        public boolean isEnabled() { return enabled; }

        private static Map<String, Double> defaultNf() {
            Map<String, Double> m = new LinkedHashMap<String, Double>();
            m.put("S", 7.0);
            m.put("C", 5.0);
            m.put("L", 6.0);
            return m;
        }
    }

    /** Values used for one circuit. */
    public static final class Params {
        public final double alphaDbPerKm;
        public final double alphaLinear; // 1/m (power)
        public final double gamma;       // 1/(W m)
        public final double beta2;       // s^2/m
        public final double noiseFigureDb;
        public final double frequencyHz;

        Params(double alphaDbPerKm, double gamma, double beta2, double noiseFigureDb, double frequencyHz) {
            this.alphaDbPerKm = alphaDbPerKm;
            this.alphaLinear = PhysicalLayer.computeAlphaLinear(alphaDbPerKm);
            this.gamma = gamma;
            this.beta2 = beta2;
            this.noiseFigureDb = noiseFigureDb;
            this.frequencyHz = frequencyHz;
        }
    }

    private final Config cfg;
    private final FiberAttenuationModel fiber;
    private final double alpha0;
    private final double gamma0;
    private final double d0;
    private final double nf0;
    private final double fRef;

    public ClassicalMultiBand(Config cfg, double alphaDbPerKm, double gamma, double dispersion, double nfDb,
            double centerFrequencyHz) {
        this.cfg = cfg;
        this.alpha0 = alphaDbPerKm;
        this.gamma0 = gamma;
        this.d0 = dispersion;
        this.nf0 = nfDb;
        this.fRef = centerFrequencyHz;
        final String model = cfg.attenuationModel == null ? "g652d" : cfg.attenuationModel.trim().toLowerCase();
        if (model.equals("g652d")) {
            this.fiber = FiberAttenuationModel.physical(FiberAttenuationModel.DEFAULT_RAYLEIGH_DB_UM4_PER_KM,
                    cfg.waterPeakDbPerKm, 30.0);
        } else if (model.equals("constante") || model.equals("constant")) {
            this.fiber = null;
        } else {
            throw new IllegalArgumentException("physicalLayer.multiBand.attenuationModel must be g652d or constante");
        }
    }

    /** Null when the block is absent or disabled. */
    public static ClassicalMultiBand of(Config cfg, double alphaDbPerKm, double gamma, double dispersion, double nfDb,
            double centerFrequencyHz) {
        return cfg == null || !cfg.enabled ? null
                : new ClassicalMultiBand(cfg, alphaDbPerKm, gamma, dispersion, nfDb, centerFrequencyHz);
    }

    /** Parameters at frequency f (Hz). */
    public Params at(double f) {
        final double nm = C / f * 1e9;
        final double a = fiber == null ? alpha0 : fiber.dbPerKm(nm);
        final double g = cfg.gammaScalesWithFrequency ? gamma0 * f / fRef : gamma0;
        final double lambdaRef = C / fRef;
        final double lambda = C / f;
        final double slope = cfg.dispersionSlopePsNm2Km * 1e3; // ps/(nm^2 km) -> s/m^3
        final double d = d0 + slope * (lambda - lambdaRef);
        final double b2 = PhysicalLayer.computeBeta2(d, f);
        Double nf = null;
        if (cfg.noiseFigureDb != null) {
            final OpticalBand band = OpticalBand.of(nm);
            nf = band == null ? null : cfg.noiseFigureDb.get(band.name());
        }
        return new Params(a, g, b2, nf == null ? nf0 : nf.doubleValue(), f);
    }

    public boolean isIsrs() { return cfg.isrs; }

    /**
     * ISRS power ratio rho(f) at the end of a span of {@code spanKm} (triangular Raman gain).
     *
     * @param f channel centre frequency (Hz)
     * @param fMin lowest frequency of the channels in the fibre (Hz)
     * @param fMax highest frequency (Hz)
     * @param totalPowerW total launch power in the fibre (W)
     * @param alphaLinear loss (1/m) used for the effective length
     * @param spanKm span length (km)
     */
    public double isrsRatio(double f, double fMin, double fMax, double totalPowerW, double alphaLinear, double spanKm) {
        final double b = fMax - fMin;
        if (!cfg.isrs || b <= 0.0 || totalPowerW <= 0.0 || spanKm <= 0.0) return 1.0;
        final double l = spanKm * 1e3;
        final double leff = -Math.expm1(-alphaLinear * l) / alphaLinear;
        final double cr = cfg.ramanGainSlopePerWKmTHz * 1e-3 * 1e-12; // 1/(W m Hz)
        final double x = totalPowerW * cr * leff;
        if (x * b < 1e-12) return 1.0;
        return x * b * Math.exp(-x * (f - fMin)) / (-Math.expm1(-x * b));
    }
}
