package network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import gprmcsa.modulation.Modulation;
import qkd.capacity.QKDPichCircuit;
import request.RequestForConnection;

/**
 * Complete receiver-referenced QoT state per physical link. Unlike the older
 * version-only cache, keys include the actual optical sources, their current
 * spectra, powers and modulation, including PICh longitudinal boundaries.
 * No candidate/test-circuit evaluations are cached.
 *
 * <p>Cache entries are evaluation-local, bounded, transient (not serialized),
 * and invalidated by changes to any contributing field. No stale link result
 * can be reused following add/release/reallocation of a PICh or DCh.</p>
 */
final class QotLinkCache {

	private static final int MAX_ENTRIES = 2048;
	private final Map<String, double[]> entries = new LinkedHashMap<String, double[]>(256, .75f, true) {
		private static final long serialVersionUID = 1L;
		@Override protected boolean removeEldestEntry(Map.Entry<String, double[]> eldest) {
			return size() > MAX_ENTRIES;
		}

	};
	private long hits;
	private long misses;

	static boolean enabled() {
		// Verification can be run with snets.noPerfCache=true as independent reference.
		return !Boolean.getBoolean("snets.noPerfCache");
	}

	synchronized double[] get(String key) {
		double[] state = entries.get(key);

		if (state == null) { misses++; simulator.PerfChecks.count(simulator.PerfChecks.QOT_MISS); }
		else { hits++; simulator.PerfChecks.count(simulator.PerfChecks.QOT_HIT); }

		return state == null ? null : state.clone();
	}

	synchronized void put(String key, double[] state) {
		entries.put(key, state.clone());
	}

	synchronized long hits() { return hits; }
	synchronized long misses() { return misses; }

	static String key(PhysicalLayer physicalLayer, Link link, Circuit victim,
			Modulation modulation, int core, int[] spectrum,
			double power, double bandwidth, double frequency, double[] incoming,
			boolean selfOnly, boolean longitudinal, int gainModel) {

		StringBuilder b = new StringBuilder(512);
		b.append(System.identityHashCode(physicalLayer)).append(':')
				.append(System.identityHashCode(link)).append(':')
				.append(longitudinal).append(':').append(selfOnly).append(':')
				.append(gainModel).append(':').append(core).append(':')
				.append(System.identityHashCode(modulation)).append(':');
		bits(b, power); bits(b, bandwidth); bits(b, frequency);
		band(b, spectrum);

		for (double value : incoming) bits(b, value);
		// The victim may be provisional (not present in any core).
		circuit(b, victim, link);

		for (Core c : link.getCores()) {
			b.append("core:").append(c.getId()).append(':')
					.append(c.isDisabled()).append(':').append(c.isQkdReserved()).append(':');
			List<Circuit> sources = new ArrayList<Circuit>(c.getCircuitList());
			sources.addAll(c.getAuxiliaryClassicalCircuitList());
			Collections.sort(sources, new Comparator<Circuit>() {
				@Override public int compare(Circuit a, Circuit d) {
					int n = Integer.compare(a.getId(), d.getId());

					return n != 0 ? n : Integer.compare(System.identityHashCode(a), System.identityHashCode(d));
				}

			});

			for (Circuit source : sources) circuit(b, source, link);
		}

		return b.toString();
	}

	private static void bits(StringBuilder b, double value) {
		b.append(Long.toHexString(Double.doubleToRawLongBits(value))).append(':');
	}

	private static void band(StringBuilder b, int[] slots) {
		if (slots == null) { b.append("null:"); return; }
		b.append('[');

		for (int value : slots) b.append(value).append(',');
		b.append("]:");
	}

	private static void circuit(StringBuilder b, Circuit c, Link link) {
		if (c == null) { b.append("null-circuit:"); return; }
		b.append("c:").append(System.identityHashCode(c)).append(':')
				.append(c.getId()).append(':').append(c.getClass().getName()).append(':')
				.append(System.identityHashCode(c.getModulationByLink(link))).append(':');
		bits(b, c.getLaunchPowerLinear());
		bits(b, c.getRequiredBitRate());
		b.append(c.getIndexCoreByLink(link)).append(':').append(c.getGuardBand()).append(':');
		band(b, c.getSpectrumAssignedByLink(link));

		if (c instanceof QKDPichCircuit) {
			QKDPichCircuit p = (QKDPichCircuit) c;

			if (p.getSpan() != null) {
				bits(b, p.getSpan().getStartKm());
				bits(b, p.getSpan().getEndKm());
			}
		}

		if (c.getRequests() != null) {
			b.append("requests:").append(c.getRequests().size()).append(':');

			for (RequestForConnection req : c.getRequests()) bits(b, req.getRequiredBitRate());
		}
	}
}
