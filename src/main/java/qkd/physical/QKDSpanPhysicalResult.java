package qkd.physical;

import java.io.Serializable;

import qkd.network.QKDSpan;

/**
 * Physical-layer evaluation of one logical QKD span.
 *
 * <p>The object keeps the complete XT-power, detector-noise and Decoy-BB84
 * results together so later admission-control code can inspect both the final
 * viability decision and all intermediate quantities.</p>
 */
public final class QKDSpanPhysicalResult implements Serializable {

	private static final long serialVersionUID = 1L;

	private final QKDSpan span;
	private final QKDCrosstalkPowerResult crosstalkPower;
	private final QKDCrosstalkNoiseResult crosstalkNoise;
	private final QKDPhysicalResult bb84Result;
	private final double minimumSecretKeyRateBps;
	private final double maximumQber;
	private final boolean viable;

	/** Parte 43: SpRS power at the QCh receiver (W) and its background probability; 0 outside multi-band mode. */
	private final double ramanPowerW;
	private final double ramanBackgroundProbability;

	/** Parte 45: NLI power at the QCh receiver (W) and its background probability. */
	private double nliPowerW;
	private double nliBackgroundProbability;
	private double fwmPowerW;
	private double fwmBackgroundProbability;

	public QKDSpanPhysicalResult(
			QKDSpan span,
			QKDCrosstalkPowerResult crosstalkPower,
			QKDCrosstalkNoiseResult crosstalkNoise,
			QKDPhysicalResult bb84Result,
			double minimumSecretKeyRateBps,
			double maximumQber) {
		this(span, crosstalkPower, crosstalkNoise, bb84Result, minimumSecretKeyRateBps, maximumQber, 0.0, 0.0);
	}

	/** Parte 43: span result with the spontaneous-Raman noise of the multi-band layer. */
	public QKDSpanPhysicalResult(
			QKDSpan span,
			QKDCrosstalkPowerResult crosstalkPower,
			QKDCrosstalkNoiseResult crosstalkNoise,
			QKDPhysicalResult bb84Result,
			double minimumSecretKeyRateBps,
			double maximumQber,
			double ramanPowerW,
			double ramanBackgroundProbability) {

		if (span == null || crosstalkPower == null
				|| crosstalkNoise == null || bb84Result == null) {
			throw new IllegalArgumentException(
					"span/power/noise/bb84Result cannot be null");
		}

		if (!Double.isFinite(minimumSecretKeyRateBps)
				|| minimumSecretKeyRateBps < 0.0) {
			throw new IllegalArgumentException(
					"minimumSecretKeyRateBps must be finite and >= 0");
		}

		if (!Double.isFinite(maximumQber)
				|| maximumQber < 0.0 || maximumQber > 1.0) {
			throw new IllegalArgumentException(
					"maximumQber must be finite and in [0,1]");
		}

		this.span = span;
		this.crosstalkPower = crosstalkPower;
		this.crosstalkNoise = crosstalkNoise;
		this.bb84Result = bb84Result;
		this.minimumSecretKeyRateBps = minimumSecretKeyRateBps;
		this.maximumQber = maximumQber;
		this.ramanPowerW = ramanPowerW;
		this.ramanBackgroundProbability = ramanBackgroundProbability;
		this.viable = bb84Result.getSecretKeyRateBps() >= minimumSecretKeyRateBps
				&& bb84Result.getQber() <= maximumQber;
	}

	public QKDSpan getSpan() {
		return span;
	}

	public QKDCrosstalkPowerResult getCrosstalkPower() {
		return crosstalkPower;
	}

	public QKDCrosstalkNoiseResult getCrosstalkNoise() {
		return crosstalkNoise;
	}

	public QKDPhysicalResult getBb84Result() {
		return bb84Result;
	}

	public double getMinimumSecretKeyRateBps() {
		return minimumSecretKeyRateBps;
	}

	public double getMaximumQber() {
		return maximumQber;
	}

	public boolean isViable() {
		return viable;
	}

	public double getCrosstalkPowerW() {
		return crosstalkPower.getTotalPowerW();
	}

	public double getCrosstalkBackgroundProbability() {
		return crosstalkNoise.getCrosstalkBackgroundProbability();
	}

	public double getTotalBackgroundProbability() {
		return ramanBackgroundProbability > 0.0 || nliBackgroundProbability > 0.0 || fwmBackgroundProbability > 0.0 ? bb84Result.getBackgroundProbability()
				: crosstalkNoise.getTotalBackgroundProbability();
	}

	/** Parte 45: records the NLI noise of this span (multi-band layer with nliModel = gn). */
	QKDSpanPhysicalResult withNli(double powerW, double probability) {
		this.nliPowerW = powerW;
		this.nliBackgroundProbability = probability;

		return this;
	}

	/** Parte 45: NLI power at the QCh receiver (W); 0 without the NLI model. */
	/** Discrete FWM noise product at the QCh receiver, independent of GN-NLI. */
	QKDSpanPhysicalResult withFwm(double powerW, double probability) {
		this.fwmPowerW = powerW;
		this.fwmBackgroundProbability = probability;
		return this;
	}
	public double getFwmPowerW() { return fwmPowerW; }
	public double getFwmBackgroundProbability() { return fwmBackgroundProbability; }

	public double getNliPowerW() { return nliPowerW; }

	/** Parte 45: background probability per gate due to NLI; 0 without the NLI model. */
	public double getNliBackgroundProbability() { return nliBackgroundProbability; }

	/** Parte 43: SpRS power at the QCh receiver (W); 0 outside multi-band mode. */
	public double getRamanPowerW() {
		return ramanPowerW;
	}

	/** Parte 43: background probability per gate due to SpRS; 0 outside multi-band mode. */
	public double getRamanBackgroundProbability() {
		return ramanBackgroundProbability;
	}

	public double getQber() {
		return bb84Result.getQber();
	}

	public double getSecretKeyRateBps() {
		return bb84Result.getSecretKeyRateBps();
	}

	public double getSecretKeyRateMbps() {
		return bb84Result.getSecretKeyRateMbps();
	}

	@Override
	public String toString() {
		return "QKDSpanPhysicalResult{" +
				"span=" + span +
				", PxtW=" + getCrosstalkPowerW() +
				", pXt=" + getCrosstalkBackgroundProbability() +
				", QBER=" + getQber() +
				", SKR_Mbps=" + getSecretKeyRateMbps() +
				", viable=" + viable +
				'}';
	}
}
