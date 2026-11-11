package qkd.multiband;

import java.io.Serializable;

/**
 * Immutable result for ONE classical EDFA at ONE carrier frequency.
 * Input is the total same-core spectral load (W), not the power of the
 * carrier alone. Gain is linear and ASE is W/Hz, both polarizations.
 * No QCh optical gain is implied by this state.
 */
public final class AmplifierOperatingPoint implements Serializable {
	private static final long serialVersionUID = 1L;
	private final int index;
	private final double positionKm, frequencyHz, totalInputW, gainLinear, asePsdWHz;
	public AmplifierOperatingPoint(int index, double positionKm, double frequencyHz,
			double totalInputW, double gainLinear, double asePsdWHz) {
		if (index < 1 || !Double.isFinite(positionKm) || positionKm <= 0.0
				|| !Double.isFinite(frequencyHz) || frequencyHz <= 0.0
				|| !Double.isFinite(totalInputW) || totalInputW < 0.0
				|| !Double.isFinite(gainLinear) || gainLinear < 1.0
				|| !Double.isFinite(asePsdWHz) || asePsdWHz < 0.0)
			throw new IllegalArgumentException("invalid EDFA operating point");
		this.index = index;
		this.positionKm = positionKm;
		this.frequencyHz = frequencyHz;
		this.totalInputW = totalInputW;
		this.gainLinear = gainLinear;
		this.asePsdWHz = asePsdWHz;
	}

	public int getIndex() { return index; }
	public double getPositionKm() { return positionKm; }
	public double getFrequencyHz() { return frequencyHz; }
	public double getTotalInputW() { return totalInputW; }
	public double getGainLinear() { return gainLinear; }
	public double getAsePsdWHz() { return asePsdWHz; }
	public double asePowerW(double bandwidthHz) {
		if (!Double.isFinite(bandwidthHz) || bandwidthHz < 0)
			throw new IllegalArgumentException("invalid optical bandwidth");

		return asePsdWHz * bandwidthHz;
	}
}
