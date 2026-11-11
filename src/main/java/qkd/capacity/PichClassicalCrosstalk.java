package qkd.capacity;

import network.Circuit;
import network.Core;
import network.Link;

/**
 * Auxiliary PICh -> DCh inter-core crosstalk, evaluated per occupied longitudinal
 * QKD span. The existing DCh->DCh calculation remains in network.Crosstalk.
 *
 * This is a first-order, non-coherent coupling approximation. It intentionally
 * does NOT include same-core GN XCI, ASE or changes to amplifier saturation;
 * those require a consistent PICh waveform/modulation and power evolution.
 */
public final class PichClassicalCrosstalk {
	private PichClassicalCrosstalk() { }

	/** Returns interfering PSD in W/Hz, never a dimensionless XT ratio. */
	public static double noisePsd(Link link, int victimCore, int[] victimBand,
			double victimBandwidthHz, double couplingPerMeter,
			double polarizationModes, boolean perPolarization) {
		if (link == null || victimBand == null || victimBand.length < 2
				|| !(victimBandwidthHz > 0) || !Double.isFinite(victimBandwidthHz))
			throw new IllegalArgumentException("invalid DCh/PICh noise inputs");

		if (perPolarization && (!(polarizationModes > 0.0)
				|| !Double.isFinite(polarizationModes)))
			throw new IllegalArgumentException("invalid number of polarization modes");

		if (!Double.isFinite(couplingPerMeter) || couplingPerMeter < 0)
			throw new IllegalArgumentException("invalid inter-core coupling");
		final Core victim = link.getCore(victimCore);

		if (victim == null) return 0.0;
		final int victimSlots = victimBand[1] - victimBand[0] + 1;

		if (victimSlots <= 0) return 0.0;
		double powerW = 0.0;

		for (Core source : link.getCores()) {
			if (source.getId() == victimCore || !adjacent(victim, source)) continue;

			for (Circuit auxiliary : source.getAuxiliaryClassicalCircuitList()) {
				if (!(auxiliary instanceof QKDPichCircuit)) continue;
				final QKDPichCircuit pich = (QKDPichCircuit) auxiliary;

				if (pich.getPhysicalLink() != link) continue;
				final int[] band = pich.getSpectrumAssigned();

				if (band == null || band.length < 2) continue;
				final double iso = network.Crosstalk.interfererFraction(
						victimBand, band);

				if (iso == 0.0) continue;

				// Thesis Eq. 3.35: Pxt = Pn * ISO * h * L. A PICh exists only
				// between its trusted-node endpoints, not along the whole link.
				final double lengthMeters = pich.getSpan().getLengthKm() * 1000.0;
				powerW += pich.getLaunchPowerLinear()
						* iso * couplingPerMeter * lengthMeters;
			}
		}

		// For Johannisson the signal PSD is per polarization. This assumes
		// equal split of PICh power between both polarization modes.
		if (perPolarization) powerW /= polarizationModes;

		return powerW / victimBandwidthHz;
	}

	private static boolean adjacent(Core a, Core b) {
		// Prefer the actual SDM geometry instead of assuming id+/-1.
		for (Core c : a.getAdjacentCores()) if (c.getId() == b.getId()) return true;

		return false;
	}
}
