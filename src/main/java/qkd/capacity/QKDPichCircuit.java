package qkd.capacity;

import network.Circuit;
import gprmcsa.modulation.Modulation;
import network.Link;
import qkd.network.QKDSpan;

/**
 * Physical Public Interaction Channel (PICh) optical source.
 *
 * <p>The PICh is a classical optical channel associated with exactly one
 * {@link QKDSpan}. It consumes spectrum on a classical core and its optical
 * launch power is included by the QKD physical layer in same-core/inter-core
 * Raman and NLI calculations.</p>
 *
 * <p>This class deliberately extends {@link Circuit} only to reuse the SNetS
 * spectrum/frequency representation. It is stored in Core's auxiliary optical
 * source list, not in the legacy classical circuit list, with PICh->DCh crosstalk included by PichClassicalCrosstalk.</p>
 *
 * <p>References: [MOGH21] Moghaddam, Beyranvand & Salehi, IEEE JSAC 2021
 * (physical QKD public/classical channel with link/core/FS resources);
 * [DIBAJ24] Dibaj et al., IWCIT 2024 (CCh+PICh coexistence and noise on QCh);
 * [MEHDI24] Mehdizadeh et al., IEEE Communications Letters 2024
 * (DCh and PICh as classical optical signals).</p>
 */
public final class QKDPichCircuit extends Circuit {
	private static final long serialVersionUID = 1L;

	private final Link physicalLink;
	private final QKDSpan span;

	public QKDPichCircuit(Link physicalLink, QKDSpan span, int coreId,
			int[] spectrumBand, int guardBand, double launchPowerW) {
		super();

		if (physicalLink == null) throw new IllegalArgumentException("physicalLink cannot be null");

		if (span == null) throw new IllegalArgumentException("span cannot be null");

		// Enforce the one-link boundary at construction, not only when the
		// allocator opens the channel. Invalid sources must never reach XT.
		QKDPichPool.validateSpanInsideLink(physicalLink, span);

		if (spectrumBand == null || spectrumBand.length != 2
				|| spectrumBand[0] < 1 || spectrumBand[1] < spectrumBand[0])
			throw new IllegalArgumentException("spectrumBand must be a valid positive [first,last]");

		if (!Double.isFinite(launchPowerW) || launchPowerW < 0.0)
			throw new IllegalArgumentException("launchPowerW must be finite and >= 0");
		this.physicalLink = physicalLink;
		this.span = span;
		setIndexCore(coreId);
		setSpectrumAssigned(spectrumBand.clone());
		setGuardBand(Math.max(0, guardBand));
		setLaunchPowerLinear(launchPowerW);
	}

	/** The same configured modulation object used for classical DChs. */
	public void assignModulation(Modulation selected) {
		if (selected == null) throw new IllegalArgumentException("PICh modulation is required");
		setModulation(selected);
	}

	/** Entire allocated PICh channel carries its configured modulation. */
	@Override public double getRequiredBitRate() {
		Modulation selected = getModulation();

		if (selected == null) throw new IllegalStateException("PICh modulation was not selected");
		int[] band = getSpectrumAssigned();

		return selected.potentialBitRate(band[1] - band[0] + 1);
	}

	public Link getPhysicalLink() { return physicalLink; }
	public QKDSpan getSpan() { return span; }

	/** Physical bandwidth derived from the SNetS slot grid, in hertz. */
	public double getOpticalBandwidthHz(double slotBandwidthHz) {
		if (!Double.isFinite(slotBandwidthHz) || slotBandwidthHz <= 0.0)
			throw new IllegalArgumentException("slotBandwidthHz must be finite and positive");
		int[] slots = getSpectrumAssigned();

		return (slots[1] - slots[0] + 1) * slotBandwidthHz;
	}

	/** Channel centre frequency in hertz on the configured classical grid.
     * No frequency is guessed when the grid origin is unavailable.
     */
	public double getCenterFrequencyHz(double lowerFrequencyHz, double slotBandwidthHz) {
		if (!Double.isFinite(lowerFrequencyHz) || lowerFrequencyHz <= 0.0)
			throw new IllegalArgumentException("lowerFrequencyHz must be finite and positive");
		int[] slots = getSpectrumAssigned();

		return network.GnModelMath.centerHz(lowerFrequencyHz,slotBandwidthHz,slots);
	}

}
