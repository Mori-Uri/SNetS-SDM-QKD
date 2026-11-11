package qkd.capacity;

import java.util.Collection;
import network.Circuit;
import network.Core;
import network.Link;

/**
 * Opt-in, explicitly calibrated PICh SNR admission model. The model sums
 * receiver noise power, adjacent-core linear leakage and a GN-like cubic
 * nonlinear term from all co-propagating classical sources on the link.
 * This is a bounded engineering approximation, NOT a substitute for the
 * full SNetS modulation-specific QoT/BER engine. Coefficients must be
 * supplied by the experiment; no unreported physical default is assumed.
 */
@Deprecated
public final class PichSnrAdmission {
	private PichSnrAdmission() { }

	public static double snrDb(double launchW, double lengthKm, double lossDbPerKm,
			double insertionLossDb, double receiverNoiseW, double xtPerKm,
			double gnPerW2Km, double overlappingAdjacentPowerW,
			double allClassicalPowerW) {
		if (!(launchW > 0) || !Double.isFinite(launchW)
				|| !(lengthKm > 0) || !Double.isFinite(lengthKm)
				|| lossDbPerKm < 0 || !Double.isFinite(lossDbPerKm)
				|| insertionLossDb < 0 || !Double.isFinite(insertionLossDb)
				|| !(receiverNoiseW > 0) || !Double.isFinite(receiverNoiseW)
				|| xtPerKm < 0 || !Double.isFinite(xtPerKm)
				|| gnPerW2Km < 0 || !Double.isFinite(gnPerW2Km)
				|| overlappingAdjacentPowerW < 0 || !Double.isFinite(overlappingAdjacentPowerW)
				|| allClassicalPowerW < 0 || !Double.isFinite(allClassicalPowerW))
			throw new IllegalArgumentException("Invalid calibrated PICh SNR parameters");
		double transmission = Math.pow(10.0, -(lossDbPerKm * lengthKm + insertionLossDb) / 10.0);
		double signal = launchW * transmission;
		double xt = xtPerKm * lengthKm * overlappingAdjacentPowerW * transmission;
		double nli = gnPerW2Km * lengthKm * Math.pow(allClassicalPowerW, 3) * transmission;

		return 10.0 * Math.log10(signal / (receiverNoiseW + xt + nli));
	}

	/** Adjacent-core sources only; spectrum overlap is defined on the common link grid. */
	public static double adjacentOverlappingPower(Core selected, int[] band, Circuit candidate) {
		double power = 0;

		if (selected.getAdjacentCores() == null) return 0;

		for (Core neighbor : selected.getAdjacentCores()) {
			for (Circuit c : neighbor.getCircuitList())
				power += overlappingPower(c, band);

			for (Circuit c : neighbor.getAuxiliaryClassicalCircuitList())
				power += overlappingPower(c, band);
		}

		// Candidate is not committed yet and might share an adjacent core.
		if (candidate != null && candidate.getIndexCore() != selected.getId())
			for (Core neighbor : selected.getAdjacentCores())
				if (neighbor.getId() == candidate.getIndexCore()) {
					power += overlappingPower(candidate, band);
					break;
				}

		return power;
	}

	private static double overlappingPower(Circuit c, int[] band) {
		if (c == null || c.getSpectrumAssigned() == null) return 0;
		int[] b = c.getSpectrumAssigned();
		int overlap = Math.max(0, Math.min(b[1], band[1]) - Math.max(b[0], band[0]) + 1);
		int count = b[1] - b[0] + 1;

		return count > 0 ? overlap / (double) count * Math.max(0, c.getLaunchPowerLinear()) : 0;
	}

	/** All established same-fiber classical launch powers plus the pending DCh. */
	public static double totalClassicalPower(Link link, Circuit candidate, double ownPichW) {
		double power = ownPichW;
		java.util.Set<Circuit> seen = java.util.Collections.newSetFromMap(
				new java.util.IdentityHashMap<Circuit, Boolean>());

		for (Core core : link.getCores()) {
			for (Circuit c : core.getCircuitList())
				if (seen.add(c)) power += Math.max(0, c.getLaunchPowerLinear());

			for (Circuit c : core.getAuxiliaryClassicalCircuitList())
				if (seen.add(c)) power += Math.max(0, c.getLaunchPowerLinear());
		}

		if (candidate != null && candidate.getRoute() != null
				&& candidate.getRoute().getLinkList().contains(link)
				&& seen.add(candidate))
			power += Math.max(0, candidate.getLaunchPowerLinear());

		return power;
	}
}
