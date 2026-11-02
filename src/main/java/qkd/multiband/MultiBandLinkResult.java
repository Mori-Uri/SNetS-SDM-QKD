package qkd.multiband;

import java.io.Serializable;
import java.util.Locale;

import qkd.physical.QKDPhysicalResult;

/**
 * Result of {@link MultiBandQkdLinkModel#evaluate}: the noise budget of the quantum channel and the decoy-state
 * BB84 key rate obtained with it.
 *
 * <p>Noise budget per detection gate: p_b = p_dark + p_SpRS(same core) + p_SpRS(neighbour cores). The NLI term of
 * the multi-band literature (level N2 of the plan) is not modelled yet and is reported as zero.</p>
 */
public final class MultiBandLinkResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final double wavelengthNm;
    private final double lengthKm;
    private final double attenuationDbPerKm;
    private final double ramanPowerSameCoreW;
    private final double ramanPowerNeighbourW;
    private final double darkProbability;
    private final double ramanProbabilitySameCore;
    private final double ramanProbabilityNeighbour;
    private final QKDPhysicalResult qkd;

    MultiBandLinkResult(double wavelengthNm, double lengthKm, double attenuationDbPerKm, double ramanPowerSameCoreW,
            double ramanPowerNeighbourW, double darkProbability, double ramanProbabilitySameCore,
            double ramanProbabilityNeighbour, QKDPhysicalResult qkd) {
        this.wavelengthNm = wavelengthNm;
        this.lengthKm = lengthKm;
        this.attenuationDbPerKm = attenuationDbPerKm;
        this.ramanPowerSameCoreW = ramanPowerSameCoreW;
        this.ramanPowerNeighbourW = ramanPowerNeighbourW;
        this.darkProbability = darkProbability;
        this.ramanProbabilitySameCore = ramanProbabilitySameCore;
        this.ramanProbabilityNeighbour = ramanProbabilityNeighbour;
        this.qkd = qkd;
    }

    /** QCh wavelength in nm. */
    public double getWavelengthNm() { return wavelengthNm; }

    /** Span length in km. */
    public double getLengthKm() { return lengthKm; }

    /** Fibre loss at the QCh wavelength in dB/km. */
    public double getAttenuationDbPerKm() { return attenuationDbPerKm; }

    /** SpRS power from classical channels of the same core/fibre, inside the QCh filter, in W. */
    public double getRamanPowerSameCoreW() { return ramanPowerSameCoreW; }

    /** SpRS power from classical channels of neighbour cores (intercore SpRS), inside the QCh filter, in W. */
    public double getRamanPowerNeighbourW() { return ramanPowerNeighbourW; }

    /** Detector dark-count probability per gate. */
    public double getDarkProbability() { return darkProbability; }

    /** Noise probability per gate due to same-core SpRS. */
    public double getRamanProbabilitySameCore() { return ramanProbabilitySameCore; }

    /** Noise probability per gate due to intercore SpRS. */
    public double getRamanProbabilityNeighbour() { return ramanProbabilityNeighbour; }

    /** Non-linear interference noise probability (not modelled yet, always 0). */
    public double getNliProbability() { return 0.0; }

    /** Total background probability p_b used in the BB84 bound. */
    public double getBackgroundProbability() { return qkd.getBackgroundProbability(); }

    /** Decoy-state BB84 result (SKR, QBER, ...). */
    public QKDPhysicalResult getQkd() { return qkd; }

    /** Secret key rate in bit/s. */
    public double getSecretKeyRateBps() { return qkd.getSecretKeyRateBps(); }

    /** QBER (E_mu). */
    public double getQber() { return qkd.getQber(); }

    /** CSV header matching {@link #toCsvRow()}. */
    public static String csvHeader() {
        return "wavelength_nm,length_km,alpha_db_km,p_raman_same_w,p_raman_neighbour_w,pb_dark,pb_raman_same,"
                + "pb_raman_neighbour,pb_nli,pb_total,qber,skr_bps";
    }

    /** One CSV row (dot as decimal separator). */
    public String toCsvRow() {
        return String.format(Locale.ROOT, "%.4f,%.4f,%.6f,%.6e,%.6e,%.6e,%.6e,%.6e,%.6e,%.6e,%.6f,%.6e",
                wavelengthNm, lengthKm, attenuationDbPerKm, ramanPowerSameCoreW, ramanPowerNeighbourW,
                darkProbability, ramanProbabilitySameCore, ramanProbabilityNeighbour, getNliProbability(),
                getBackgroundProbability(), getQber(), getSecretKeyRateBps());
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "MultiBandLinkResult[%.2f nm, %.1f km, alpha %.4f dB/km, pb %.3e (dark %.1e, SpRS %.3e + %.3e), "
                        + "QBER %.4f, SKR %.4g bit/s]",
                wavelengthNm, lengthKm, attenuationDbPerKm, getBackgroundProbability(), darkProbability,
                ramanProbabilitySameCore, ramanProbabilityNeighbour, getQber(), getSecretKeyRateBps());
    }
}
