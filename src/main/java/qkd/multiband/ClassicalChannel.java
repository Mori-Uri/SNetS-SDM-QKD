package qkd.multiband;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A classical channel seen by a quantum channel as a source of noise: centre frequency, launch power and the
 * direction of propagation relative to the QCh.
 *
 * <p>Immutable. Use {@link #comb} to build the fully loaded C+L comb of the reference papers.</p>
 *
 * <p>Referências: comb de 184,4 a 196,8 THz, 112,5 GHz, 0 dBm/canal em [MEHDI24] e [DIBAJ25] (C+L de
 * [FERRARI20]).</p>
 */
public final class ClassicalChannel implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Propagation direction of the classical channel relative to the quantum channel. */
    public enum Direction {
        /** Same direction as the QCh (forward SpRS reaches the QCh receiver). */
        CO_PROPAGATING,
        /** Opposite direction (backward SpRS reaches the QCh receiver). */
        COUNTER_PROPAGATING
    }

    private final double frequencyHz;
    private final double powerW;
    private final Direction direction;

    /**
     * @param frequencyHz centre frequency in Hz (&gt; 0)
     * @param powerW launch power in W (&gt;= 0)
     * @param direction direction relative to the QCh
     */
    public ClassicalChannel(double frequencyHz, double powerW, Direction direction) {
        if (!(frequencyHz > 0.0) || Double.isInfinite(frequencyHz)) {
            throw new IllegalArgumentException("frequencyHz must be finite and > 0");
        }
        if (!(powerW >= 0.0) || Double.isInfinite(powerW)) {
            throw new IllegalArgumentException("powerW must be finite and >= 0");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction cannot be null");
        }
        this.frequencyHz = frequencyHz;
        this.powerW = powerW;
        this.direction = direction;
    }

    /** Channel given by wavelength in nm and power in dBm. */
    public static ClassicalChannel ofWavelength(double wavelengthNm, double powerDbm, Direction direction) {
        return new ClassicalChannel(SpontaneousRamanModel.C / (wavelengthNm * 1e-9), dbmToW(powerDbm), direction);
    }

    /**
     * Uniform comb filling [startHz, endHz]: channel i is centred at startHz + spacing (i + 1/2), as many as fit.
     *
     * @param startHz lower edge of the occupied band in Hz
     * @param endHz upper edge of the occupied band in Hz
     * @param spacingHz channel spacing in Hz
     * @param powerDbm launch power per channel in dBm
     * @param direction direction of every channel
     * @return unmodifiable list ordered by frequency
     */
    public static List<ClassicalChannel> comb(double startHz, double endHz, double spacingHz, double powerDbm,
            Direction direction) {
        if (!(spacingHz > 0.0) || !(endHz > startHz)) {
            throw new IllegalArgumentException("need spacingHz > 0 and endHz > startHz");
        }
        final int n = (int) Math.floor((endHz - startHz) / spacingHz);
        final double w = dbmToW(powerDbm);
        final List<ClassicalChannel> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new ClassicalChannel(startHz + spacingHz * (i + 0.5), w, direction));
        }
        return Collections.unmodifiableList(list);
    }

    /** Fully loaded C+L comb of [MEHDI24]/[DIBAJ25]: 184.4-196.8 THz, 112.5 GHz, 110 channels. */
    public static List<ClassicalChannel> referenceCLComb(double powerDbm, Direction direction) {
        return comb(184.4e12, 196.8e12, 112.5e9, powerDbm, direction);
    }

    /** dBm to W. */
    public static double dbmToW(double dbm) {
        return 1e-3 * Math.pow(10.0, dbm / 10.0);
    }

    public double getFrequencyHz() { return frequencyHz; }

    public double getWavelengthNm() { return SpontaneousRamanModel.C / frequencyHz * 1e9; }

    public double getPowerW() { return powerW; }

    public Direction getDirection() { return direction; }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "ClassicalChannel[%.3f THz, %.3g W, %s]", frequencyHz * 1e-12,
                powerW, direction);
    }
}
