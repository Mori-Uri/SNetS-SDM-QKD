package qkd.multiband;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import network.Circuit;
import network.Core;
import network.Link;
import qkd.capacity.QKDPichCircuit;

/**
 * Builds a per-evaluation directed-fibre/core amplifier load snapshot from the
 * SAME optical sources used for classical QoT and QKD. Never commits a circuit.
 * No reverse-link sources are inspected; no gain is applied to QCh photons.
 */
public final class DirectedAmplifierSnapshotFactory {
	private DirectedAmplifierSnapshotFactory() { }

	public interface PowerResolver {
		double powerW(Circuit source);
	}

	/**
     * @param circuits committed DChs plus any explicitly selected candidates;
     *                 auxiliary PIChs are collected separately from the core.
     * @param bandStartHz frequency at the lower edge of slot 1.
     * @param alphaNeperKm classical fibre power attenuation (not field attenuation).
     */
	public static ClassicalPowerEvolution build(ClassicalPowerEvolution template,
			Link link, int coreId, Collection<? extends Circuit> circuits,
			double bandStartHz, double alphaNeperKm, PowerResolver resolver) {
		if (template == null || link == null || resolver == null || circuits == null
				|| !Double.isFinite(bandStartHz) || bandStartHz <= 0.0
				|| !Double.isFinite(alphaNeperKm) || alphaNeperKm < 0.0)
			throw new IllegalArgumentException("invalid shared EDFA input");
		final Core core = link.getCore(coreId);

		if (core == null) throw new IllegalArgumentException("unknown EDFA core");
		final List<ClassicalPowerEvolution.LoadSource> loads =
				new ArrayList<ClassicalPowerEvolution.LoadSource>();

		// Copy and explicitly deduplicate by object identity. PICh is NOT DCh.
		final java.util.IdentityHashMap<Circuit, Boolean> seen =
				new java.util.IdentityHashMap<Circuit, Boolean>();

		for (Circuit c : circuits)
			add(loads, seen, c, link, core, bandStartHz, resolver);

		for (Circuit c : core.getAuxiliaryClassicalCircuitList())
			add(loads, seen, c, link, core, bandStartHz, resolver);

		return template.withDynamicLoad(loads, link.getDistance(), alphaNeperKm);
	}

	private static void add(List<ClassicalPowerEvolution.LoadSource> loads,
			java.util.IdentityHashMap<Circuit, Boolean> seen, Circuit c,
			Link link, Core core, double bandStartHz, PowerResolver resolver) {
		if (c == null || seen.put(c, Boolean.TRUE) != null) return;
		final int[] slots = c.getSpectrumAssigned();

		if (slots == null || slots.length < 2 || slots[0] < 1 || slots[1] < slots[0]) return;

		if (c instanceof QKDPichCircuit
				&& ((QKDPichCircuit)c).getPhysicalLink() != link) return;
		final double power = resolver.powerW(c);

		if (!Double.isFinite(power) || power < 0.0)
			throw new IllegalArgumentException("invalid EDFA source power");

		if (power == 0.0) return;
		final double from, to;

		if (c instanceof QKDPichCircuit) {
			from = ((QKDPichCircuit)c).getSpan().getStartKm();
			to = ((QKDPichCircuit)c).getSpan().getEndKm();
		} else {
			from = 0.0;
			to = link.getDistance();
		}

		if (to <= from) return;
		final double frequency = bandStartHz +
				(0.5 * (slots[0] + slots[1] - 1.0)) * core.getSlotSpectrumBand();
		loads.add(new ClassicalPowerEvolution.LoadSource(power, from, to, frequency,
				(slots[1] - slots[0] + 1.0) * core.getSlotSpectrumBand(),
				network.GnModelMath.phi(c.getModulation())));
	}
}
