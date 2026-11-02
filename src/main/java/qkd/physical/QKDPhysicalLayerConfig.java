package qkd.physical;

import java.io.Serializable;

import simulationControl.parsers.PhysicalLayerConfig;

/**
 * Contents of the case file {@code qkdPhysicalLayer} (Part 26). It holds only the
 * QKD physical parameters, so the classical {@code physicalLayer} file keeps only
 * classical values.
 *
 * <pre>
 * {
 *   "source":    { "meanPhotonNumber": 0.7, "pulseIntervalPs": 250.0, "qchPowerDbm": -80.0, "qchBandwidthGHz": 12.5 },
 *   "protocol":  { "errorCorrectionInefficiency": 1.16, "misalignmentError": 0.015 },
 *   "detector":  { "detectorEfficiency": 0.3, "darkCountProbability": 1e-8,
 *                  "detectorGateWindowPs": 100.0, "receiverOpticalEfficiency": 1.0 },
 *   "fiber":     { "attenuationFloorDbPerKm": 0.18, "attenuationQuadraticDbPerKmNm2": 8e-6, "attenuationCenterNm": 1550.0 },
 *   "crosstalk": { "intercoreCrosstalkEnabled": true, "receiverTransferModel": "receiver_referenced" },
 *   "multiband": { "enabled": true, "architecture": "MB", "ramanModel": "fisico", "qchCenterNm": 1381.0,
 *                  "hostCoreId": 0, "intercoreRaman": true, "classicalBandStartThz": 191.3,
 *                  "attenuationModel": "g652d", "waterPeakDbPerKm": 0.0, "temperatureK": 300.0 }
 * }
 * </pre>
 *
 * <p>Parte 43: the optional group {@code multiband} turns on the multi-band physical layer
 * ({@link qkd.multiband.MultiBandSettings}). Without it, or with {@code "enabled": false}, nothing changes.</p>
 *
 * <p>The defaults are the values of the base article (Dibaj et al., 2024, Table 2 of the
 * qualification) and match Moghaddam et al., IEEE JSAC 2021, Table I for e_d, f,
 * eta_d and T_d. Missing groups or fields keep their defaults. Older cases that still
 * have the {@code qkdPhysical}/{@code qkdReceiver} blocks inside {@code physicalLayer}
 * keep working through {@link #fromLegacy}.</p>
 *
 * <p>Refer\u00eancias: [DIBAJ24], [MORI26] (Tabela 2) e [MOGH21] (Tabela I); ver docs/REFERENCIAS_QKD.md.</p>
 */
public final class QKDPhysicalLayerConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Name of the case file. */
    public static final String FILE_NAME = "qkdPhysicalLayer";

    private Source source = new Source();
    private Protocol protocol = new Protocol();
    private Detector detector = new Detector();
    private Fiber fiber = new Fiber();
    private CrosstalkCoupling crosstalk = new CrosstalkCoupling();
    /** Parte 43: multi-band options; null (absent) = disabled. */
    private Multiband multiband;

    /** Parte 43: multi-band physical layer (see {@link qkd.multiband.MultiBandSettings}). */
    public static final class Multiband implements Serializable {
        private static final long serialVersionUID = 1L;
        boolean enabled = true;
        /** MF, MB or MBSDM. */
        String architecture = "MB";
        /** nenhum, artigo or fisico. */
        String ramanModel = "fisico";
        /** Centre of the QCh grid (nm); also the QCh wavelength with wavelengthPolicy "fixed". */
        double qchCenterNm = 1381.0;
        /** Classical core sharing the fibre/core with the QCh (MB, MBSDM). */
        int hostCoreId = 0;
        /** MBSDM: SpRS from the cores adjacent to the host core ([CAI20]). */
        boolean intercoreRaman = true;
        /** Lower edge of classical slot 1 (THz). 191.3 = start of the C band; 184.4 for the C+L comb. */
        double classicalBandStartThz = 191.3;
        /** g652d (O to U band) or quadratica (fit of the fiber group). */
        String attenuationModel = "g652d";
        /** Extra loss of the OH peak at 1383 nm (dB/km); 0 for a low-water-peak G.652.D fibre. */
        double waterPeakDbPerKm = 0.0;
        /** Fibre temperature (K) of the Raman phonon population. */
        double temperatureK = 300.0;
    }

    /** Weak-coherent source and quantum-channel width. */
    public static final class Source implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Mean photon number per signal pulse (mu). */
        double meanPhotonNumber = 0.7;
        /** Pulse repetition interval T_s (ps). */
        double pulseIntervalPs = 250.0;
        /** QCh launch power (dBm), informative. */
        double qchPowerDbm = -80.0;
        /** QCh optical bandwidth (GHz). */
        double qchBandwidthGHz = 12.5;
    }

    /** Decoy-state BB84 post-processing. */
    public static final class Protocol implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Error-correction inefficiency f. */
        double errorCorrectionInefficiency = 1.16;
        /** Misalignment / phase-distortion error probability e_d. */
        double misalignmentError = 0.015;
    }

    /** Single-photon detector and receiver optics. */
    public static final class Detector implements Serializable {
        private static final long serialVersionUID = 1L;
        /** Detector quantum efficiency eta_d. */
        double detectorEfficiency = 0.3;
        /** Dark-count probability per gate p_dark. */
        double darkCountProbability = 1e-8;
        /** Detector gate window T_g (ps). */
        double detectorGateWindowPs = 100.0;
        /** Optical efficiency of the receiver filters eta_f. */
        double receiverOpticalEfficiency = 1.0;
    }

    /** Wavelength-dependent attenuation alpha(lambda) = floor + quadratic*(lambda-center)^2. */
    public static final class Fiber implements Serializable {
        private static final long serialVersionUID = 1L;
        double attenuationFloorDbPerKm = 0.18;
        double attenuationQuadraticDbPerKmNm2 = 8e-6;
        double attenuationCenterNm = 1550.0;
    }

    /** Coupling of the classical cores into the quantum core. */
    public static final class CrosstalkCoupling implements Serializable {
        private static final long serialVersionUID = 1L;
        /** False = p_b = p_dark (no classical noise reaches the QChs). */
        boolean intercoreCrosstalkEnabled = true;
        /** receiver_referenced (T = 1) or distributed. */
        String receiverTransferModel = "receiver_referenced";
    }

    /** Builds the configuration from the legacy blocks of the physicalLayer file. */
    public static QKDPhysicalLayerConfig fromLegacy(PhysicalLayerConfig.QKDPhysicalConfig q,
            PhysicalLayerConfig.QKDReceiverConfig r) {
        QKDPhysicalLayerConfig c = new QKDPhysicalLayerConfig();
        if (q != null) {
            c.source.meanPhotonNumber = q.getMeanPhotonNumber();
            c.source.pulseIntervalPs = q.getPulseIntervalPs();
            c.source.qchPowerDbm = q.getQchPowerDbm();
            c.source.qchBandwidthGHz = q.getQchBandwidthGHz();
            c.protocol.errorCorrectionInefficiency = q.getErrorCorrectionInefficiency();
            c.protocol.misalignmentError = q.getMisalignmentError();
            c.detector.detectorEfficiency = q.getDetectorEfficiency();
            c.detector.darkCountProbability = q.getDarkCountProbability();
            c.fiber.attenuationFloorDbPerKm = q.getAttenuationFloorDbPerKm();
            c.fiber.attenuationQuadraticDbPerKmNm2 = q.getAttenuationQuadraticDbPerKmNm2();
            c.fiber.attenuationCenterNm = q.getAttenuationCenterNm();
        }
        if (r != null) {
            c.detector.detectorGateWindowPs = r.getDetectorGateWindowPs();
            c.detector.receiverOpticalEfficiency = r.getReceiverOpticalEfficiency();
            c.crosstalk.intercoreCrosstalkEnabled = r.isIntercoreCrosstalkEnabled();
            c.crosstalk.receiverTransferModel = r.getReceiverTransferModel();
        }
        return c;
    }

    private void fillMissingGroups() {
        if (source == null) source = new Source();
        if (protocol == null) protocol = new Protocol();
        if (detector == null) detector = new Detector();
        if (fiber == null) fiber = new Fiber();
        if (crosstalk == null) crosstalk = new CrosstalkCoupling();
    }

    /** Validated physical parameters (same construction as the legacy path, Parts 1-25). */
    public QKDPhysicalParameters toParameters() {
        fillMissingGroups();
        QKDPhysicalParameters base = new QKDPhysicalParameters(
                source.meanPhotonNumber, source.pulseIntervalPs * 1e-12,
                protocol.errorCorrectionInefficiency, protocol.misalignmentError,
                detector.detectorEfficiency, detector.darkCountProbability,
                source.qchBandwidthGHz * 1e9, source.qchPowerDbm,
                fiber.attenuationFloorDbPerKm, fiber.attenuationQuadraticDbPerKmNm2,
                fiber.attenuationCenterNm, 6.62607015e-34, 299792458.0,
                100e-12, 1.0);
        if (!Double.isFinite(detector.detectorGateWindowPs) || detector.detectorGateWindowPs <= 0.0) {
            throw new IllegalArgumentException(FILE_NAME + ".detector.detectorGateWindowPs must be finite and > 0");
        }
        QKDPhysicalParameters params = base.withReceiverAssumptions(
                detector.detectorGateWindowPs * 1e-12,
                detector.receiverOpticalEfficiency,
                QKDCrosstalkReceiverTransferMode.fromConfig(crosstalk.receiverTransferModel),
                crosstalk.intercoreCrosstalkEnabled);
        qkd.multiband.MultiBandSettings mb = toMultiBandSettings();
        return mb == null ? params : rebuildWithCenter(params, mb.getQchCenterNm()).withMultiband(mb);
    }

    /** Parte 43: the multi-band settings, or {@code null} when the group is absent or disabled. */
    public qkd.multiband.MultiBandSettings toMultiBandSettings() {
        if (multiband == null || !multiband.enabled) return null;
        final String loss = multiband.attenuationModel == null ? "g652d"
                : multiband.attenuationModel.trim().toLowerCase(java.util.Locale.ROOT);
        final qkd.multiband.FiberAttenuationModel model;
        if (loss.equals("g652d")) {
            model = multiband.waterPeakDbPerKm > 0.0
                    ? qkd.multiband.FiberAttenuationModel.physical(
                            qkd.multiband.FiberAttenuationModel.DEFAULT_RAYLEIGH_DB_UM4_PER_KM,
                            multiband.waterPeakDbPerKm, 30.0)
                    : qkd.multiband.FiberAttenuationModel.g652d();
        } else if (loss.equals("quadratica") || loss.equals("quadratic")) {
            model = null;
        } else {
            throw new IllegalArgumentException(FILE_NAME + ".multiband.attenuationModel must be g652d or quadratica");
        }
        return new qkd.multiband.MultiBandSettings(
                qkd.multiband.MultiBandSettings.Architecture.parse(multiband.architecture),
                qkd.multiband.MultiBandSettings.RamanModel.parse(multiband.ramanModel),
                model,
                new qkd.multiband.SpontaneousRamanModel(qkd.multiband.SpontaneousRamanModel.DEFAULT_SCALE,
                        multiband.temperatureK),
                multiband.hostCoreId,
                multiband.classicalBandStartThz * 1e12,
                multiband.intercoreRaman,
                multiband.qchCenterNm);
    }

    /** Same parameters with the QCh grid centred at {@code centerNm} (multi-band mode). */
    private static QKDPhysicalParameters rebuildWithCenter(QKDPhysicalParameters p, double centerNm) {
        return new QKDPhysicalParameters(p.getMu(), p.getPulseIntervalS(), p.getErrorCorrectionInefficiency(),
                p.getPhaseErrorProbability(), p.getDetectorEfficiency(), p.getDarkCountProbability(),
                p.getQchBandwidthHz(), p.getQchPowerDbm(), p.getAttenuationFloorDbPerKm(),
                p.getAttenuationQuadratic(), centerNm, p.getPlanckConstantJs(), p.getSpeedOfLightMPerS(),
                p.getDetectorGateWindowS(), p.getReceiverOpticalEfficiency(), p.getReceiverTransferMode(),
                p.isIntercoreCrosstalkEnabled());
    }

    public double getMeanPhotonNumber() { fillMissingGroups(); return source.meanPhotonNumber; }
    public double getDarkCountProbability() { fillMissingGroups(); return detector.darkCountProbability; }
    public boolean isIntercoreCrosstalkEnabled() { fillMissingGroups(); return crosstalk.intercoreCrosstalkEnabled; }
}
