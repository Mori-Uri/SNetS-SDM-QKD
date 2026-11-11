package network;

/** Single absolute frequency mapping for classical DCh and PICh spectra.
 * Slot identifiers are one-based and denote frequency intervals, not tones.
 */
public final class AbsoluteFrequencyGrid {
	private AbsoluteFrequencyGrid() { }
	public static double lowerEdgeHz(double centerHz, int numberOfSlots, double slotWidthHz) {
		if (!Double.isFinite(centerHz) || centerHz <= 0.0 || numberOfSlots <= 0
				|| !Double.isFinite(slotWidthHz) || slotWidthHz <= 0.0)
			throw new IllegalArgumentException("invalid absolute spectral grid");
		final double lower = centerHz - 0.5 * numberOfSlots * slotWidthHz;
		if (!(lower > 0.0)) throw new IllegalArgumentException("nonphysical lower edge");
		return lower;
	}
	public static double centerHz(double lowerEdgeHz, int firstSlot, int lastSlot, double slotWidthHz) {
		if (firstSlot < 1 || lastSlot < firstSlot || !(slotWidthHz > 0.0))
			throw new IllegalArgumentException("invalid slot interval");
		return lowerEdgeHz + (firstSlot + lastSlot - 1.0) * 0.5 * slotWidthHz;
	}
}
