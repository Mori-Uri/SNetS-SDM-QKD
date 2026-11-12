package qkd.capacity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import gprmcsa.routing.Route;
import network.Circuit;
import network.Core;
import network.Link;
import network.PhysicalLayer;
import gprmcsa.modulation.Modulation;
import qkd.network.QKDLinkPlan;
import qkd.network.QKDRoutePlan;
import qkd.network.QKDSpan;

/**
 * Physical Public Interaction Channel (PICh) allocator.
 *
 * <p>Stage 1 changes the former link-shared PICh abstraction into one physical
 * PICh per {@link QKDSpan}. The existing SNetS rule is preserved: a QKDSpan
 * never crosses an optical node, therefore each PICh is always contained in a
 * single physical {@link Link}. If QTND divides one link into several spans,
 * each span receives an independent PICh reservation.</p>
 *
 * <p>Each PICh consumes classical-core spectrum and is registered as an
 * auxiliary classical optical source in that core. The QKD physical layer can
 * therefore include its launch power in Raman/NLI calculations. Spectrum is
 * represented per physical SNetS link. Independent longitudinally disjoint
 * QKDSpans of one admission may share the same reserved spectrum lease, but
 * each has its own optical PICh source. Inter-request sharing continues to be
 * governed by the existing placement configuration.</p>
 *
 * <p>References: [MOGH21] E. E. Moghaddam, H. Beyranvand, J. A. Salehi,
 * IEEE JSAC 39(9), 2021 (QCh/CCh/DCh consume link/core/FS resources);
 * [DIBAJ24] M. R. Dibaj et al., IWCIT 2024 (PICh accompanies QKD exchange and
 * CCh+PICh contribute to QCh background noise); [MEHDI24] P. Mehdizadeh et
 * al., IEEE Communications Letters 28(3), 2024.</p>
 */
public final class QKDPichPool implements Serializable {
	private static final long serialVersionUID = 1L;

	/** One physical SNetS spectrum reservation can back multiple non-overlapping PIChs. */
	private static final class SpectrumLease implements Serializable {
		private static final long serialVersionUID = 1L;
		int references = 1;
	}

	public static final class Channel implements Serializable {
		private static final long serialVersionUID = 1L;
		public final Link link;
		public final QKDSpan span;
		public final int coreId;
		public final int[] band;
		public final int guardBand;
		public final double launchPowerW;
		private final QKDPichCircuit opticalCircuit;
		private final SpectrumLease lease;
		int sessions;

		Channel(Link link, QKDSpan span, int coreId, int[] band, int guardBand,
				double launchPowerW, QKDPichCircuit opticalCircuit, SpectrumLease lease) {
			this.link = link; this.span = span; this.coreId = coreId; this.band = band;
			this.guardBand = guardBand; this.launchPowerW = launchPowerW;
			this.opticalCircuit = opticalCircuit; this.lease = lease; this.sessions = 1;
		}

		public int getSessions() { return sessions; }
		public QKDPichCircuit getOpticalCircuit() { return opticalCircuit; }
	}

	private final int pichSlots;

	/** Maximum simultaneous TDM sessions on a physical PICh (0 = unlimited). */
	private final int sessionsPerChannel;
	private final double launchPowerW;
	private transient Link lastFailedLink;
	public Link getLastFailedLink() { return lastFailedLink; }
	private final Map<Link, List<Channel>> byLink = new IdentityHashMap<Link, List<Channel>>();
	private int openChannels;
	private long peakOpenChannels, opened, closed;
	private double openSampleSum;
	private long samples;
	private final qkd.network.QKDNetworkParameters placement;
	private PhysicalLayer classicalPhysicalLayer;
	private Modulation selectedModulation;

	/** Reuse the exact modulation object and thresholds configured for DChs. */
	public void setClassicalPhysicalLayer(PhysicalLayer layer, Modulation modulation) {
		if (layer == null || modulation == null)
			throw new IllegalArgumentException("PICh needs classical QoT and modulation");
		this.classicalPhysicalLayer = layer;
		this.selectedModulation = modulation;
	}

	public QKDPichPool(int pichSlots, int sessionsPerChannel) {
		this(pichSlots, sessionsPerChannel, null, 1.0e-3);
	}

	public QKDPichPool(int pichSlots, int sessionsPerChannel, qkd.network.QKDNetworkParameters placement) {
		this(pichSlots, sessionsPerChannel, placement, 1.0e-3);
	}

	public QKDPichPool(int pichSlots, int sessionsPerChannel,
			qkd.network.QKDNetworkParameters placement, double launchPowerW) {
		if (pichSlots <= 0) throw new IllegalArgumentException("pichSlots must be > 0");

		if (!Double.isFinite(launchPowerW) || launchPowerW < 0.0)
			throw new IllegalArgumentException("launchPowerW must be finite and >= 0");
		this.pichSlots = pichSlots;
		this.sessionsPerChannel = sessionsPerChannel;
		this.placement = placement;
		this.launchPowerW = launchPowerW;
	}

	/**
     * Parte 40: classical cores that may receive a new PICh on the given links, in the order they are tried.
     * The QKD core and the disabled cores are never used. "lowest" (default): by id; "fixed": only pichCoreId (no fallback); "leastused": fewest used slots on the links first.
     */
	public static List<Core> pichCoreOrder(List<Link> links, qkd.network.QKDNetworkParameters np) {
		final Link first = links.get(0);
		List<Core> out = new ArrayList<Core>();

		for (Core core : first.getCores()) {
			boolean ok = true;

			for (Link l : links) {
				Core c = l.getCore(core.getId());

				if (c == null || c.isQkdReserved() || c.isDisabled()) { ok = false; break; }
			}

			if (ok) out.add(core);
		}

		final String policy = np == null ? "lowest" : np.getPichCorePolicy();
		final java.util.Map<Integer, Long> used = new java.util.HashMap<Integer, Long>();

		for (Core c : out) {
			long u = 0;

			for (Link l : links) u += l.getCore(c.getId()).getUsedSlots();
			used.put(Integer.valueOf(c.getId()), Long.valueOf(u));
		}

		final int fixed = np == null ? -1 : np.getPichCoreId();

		// "fixed" is a constraint, not a preferred core with fallback.
		if ("fixed".equals(policy)) {
			java.util.Iterator<Core> it = out.iterator();
			while (it.hasNext()) if (it.next().getId() != fixed) it.remove();
		}

		java.util.Collections.sort(out, new java.util.Comparator<Core>() {
			@Override public int compare(Core a, Core b) {
				if ("fixed".equals(policy)) {
					if (a.getId() == fixed && b.getId() != fixed) return -1;

					if (b.getId() == fixed && a.getId() != fixed) return 1;
				} else if ("leastused".equals(policy)) {
					int c = Long.compare(used.get(Integer.valueOf(a.getId())).longValue(), used.get(Integer.valueOf(b.getId())).longValue());

					if (c != 0) return c;
				}

				return Integer.compare(a.getId(), b.getId());
			}

		});

		return out;
	}

	/**
     * Parte 40: first slots of a PICh of {@code slots} slots in a core of {@code n} slots, in the order they are
     * tried: "firstfit" (default) ascending, "lastfit" descending, "abne" the rule of the core (medium fit in the
     * central core, last fit in even outer cores, first fit in odd outer cores).
     */
	public static int[] pichSlotOrder(int n, int slots, int coreId, int numberOfCores, qkd.network.QKDNetworkParameters np) {
		final int m = Math.max(0, n - slots + 1);
		int rule = 1;

		if (np != null) {
			if ("lastfit".equals(np.getPichSlotPolicy())) rule = 2;
			else if ("abne".equals(np.getPichSlotPolicy())) rule = qkd.network.QKDNetworkParameters.abneRule(coreId, numberOfCores);
		}

		Integer[] order = new Integer[m];

		for (int i = 0; i < m; i++) order[i] = Integer.valueOf(1 + i);

		if (rule == 2) java.util.Collections.reverse(java.util.Arrays.asList(order));
		else if (rule == 0) {
			final int reference = n / 2;
			java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
				@Override public int compare(Integer a, Integer b) {
					int c = Integer.compare(Math.abs(reference - a.intValue()), Math.abs(reference - b.intValue()));

					return c != 0 ? c : a.compareTo(b);
				}

			});
		}

		int[] out = new int[m];

		for (int i = 0; i < m; i++) out[i] = order[i].intValue();

		return out;
	}

	/**
     * Reserves one dedicated physical PICh for every QKDSpan in the route plan.
     * The route/link-plan ordering is required to be identical, which is the
     * invariant already enforced by QKDRoutePlanner.
     */
	public List<Channel> acquire(Route route, QKDRoutePlan routePlan, int guardBand, Circuit candidate) {
		if (route == null || routePlan == null || route.getLinkList() == null) return null;

		if (route.getLinkList().size() != routePlan.getLinkPlans().size())
			throw new IllegalArgumentException("route and QKDRoutePlan have different physical-link counts");
		List<Channel> got = new ArrayList<Channel>();
		lastFailedLink = null;
		Channel preceding = null;

		try {
			for (int i = 0; i < route.getLinkList().size(); i++) {
				// Only an original SNetS optical node may convert the slot band.
				// Never reset at the artificial QTND trusted-node boundaries.
				if (i > 0 && placement != null && !placement.isPichSlotContinuityAtNetworkNodes())
					preceding = null;
				Link link = route.getLinkList().get(i);
				QKDLinkPlan lp = routePlan.getLinkPlans().get(i);

				if (!lp.getSource().equals(link.getSource().getName())
						|| !lp.getDestination().equals(link.getDestination().getName()))
					throw new IllegalStateException("QKD link plan does not belong to SNetS physical link");

				for (QKDSpan span : lp.getSpans()) {
					validateSpanInsideLink(link, span);
					// Trusted nodes terminate the preceding authenticated PICh.
					// A new session may select another optical wavelength/core.
					preceding = null;
					Channel c = acquireSpanChannel(link, span, guardBand, candidate, preceding);

					if (c == null) {
						lastFailedLink = link;
						rollbackAcquisition(got, null);

						return null;
					}

					got.add(c);
					preceding = c;
				}
			}

		} catch (RuntimeException e) {
			rollbackAcquisition(got, e);

			throw e;
		}

		return got;
	}

	/** Fail closed if a QKDSpan attempts to cross an optical SNetS node. */
	public static void validateSpanInsideLink(Link link, QKDSpan span) {
		if (link == null || span == null
				|| !link.getSource().getName().equals(span.getPhysicalLinkSource())
				|| !link.getDestination().getName().equals(span.getPhysicalLinkDestination())
				|| span.getStartKm() < 0.0 || span.getEndKm() > link.getDistance() + 1e-9
				|| span.getEndKm() <= span.getStartKm())
			throw new IllegalArgumentException("QKDSpan must remain within exactly one SNetS link");
	}

	/** Legacy API retained for binary/source compatibility; opens one physical PICh per link. */
	public List<Channel> acquire(List<Link> links, int guardBand, Circuit candidate) {
		List<Channel> got = new ArrayList<Channel>();
		lastFailedLink = null;

		try {
			for (Link link : links) {
				QKDSpan compatibilitySpan = new QKDSpan(link.getSource().getName(), link.getDestination().getName(),
						0, 1, 0.0, link.getDistance(), link.getSource().getName(), link.getDestination().getName());
				Channel c = acquireSpanChannel(link, compatibilitySpan, guardBand, candidate);

				if (c == null) {
					lastFailedLink = link;
					rollbackAcquisition(got, null);

					return null;
				}

				got.add(c);
			}

		} catch (RuntimeException e) {
			rollbackAcquisition(got, e);

			throw e;
		}

		return got;
	}

	/** Roll back all previously opened spans even when validation/placement throws.
     * A cleanup failure is never silently discarded, since the request may
     * otherwise appear rejected while retaining spectrum and optical sources.
     */
	private void rollbackAcquisition(List<Channel> acquired, RuntimeException original) {
		try {
			release(acquired);
		} catch (RuntimeException cleanupFailure) {
			if (original != null) {
				original.addSuppressed(cleanupFailure);
			} else {
				throw new IllegalStateException("PICh acquisition rollback incomplete", cleanupFailure);
			}
		}
	}

	/** Independently testable physical PICh spectral placement restriction. */
	public static boolean withinPichSlotWindow(int firstSlot, int slots,
			int allowedFirst, int allowedLast) {
		if (firstSlot < 1 || slots < 1) return false;

		if (allowedFirst < 0 && allowedLast < 0) return true;

		return allowedFirst >= 1 && allowedLast >= allowedFirst
				&& firstSlot >= allowedFirst
				&& (long) firstSlot + slots - 1L <= allowedLast;
	}

	/** Reuse is restricted to exactly the same directed link and QKDSpan.
	 * The optical carrier stays registered once. Session teardown only removes it
	 * when the final client releases its share. No new optical noise is added for a share.
	 */
	private Channel acquireSpanChannel(Link link, QKDSpan span, int guardBand, Circuit candidate) {
		return acquireSpanChannel(link, span, guardBand, candidate, null);
	}

	private Channel acquireSpanChannel(Link link, QKDSpan span, int guardBand,
			Circuit candidate, Channel required) {
		if (placement != null && placement.isPichTdmSharing()) {
			List<Channel> active = byLink.get(link);
			if (active != null) for (Channel channel : active) {
				if (channel.guardBand != guardBand || channel.sessions <= 0) continue;
				if (required != null && ((required.link == link && channel.coreId != required.coreId)
						|| channel.band[0] != required.band[0] || channel.band[1] != required.band[1])) continue;
				if (sessionsPerChannel > 0 && channel.sessions >= sessionsPerChannel) continue;
				if (channel.span.getStartKm() == span.getStartKm()
						&& channel.span.getEndKm() == span.getEndKm()
						&& channel.span.getPhysicalLinkSource().equals(span.getPhysicalLinkSource())
						&& channel.span.getPhysicalLinkDestination().equals(span.getPhysicalLinkDestination())) {
					channel.sessions++;
					return channel;
				}
			}
		}
		return openDedicated(link, span, guardBand, candidate, required);
	}

	/** Existing physical reservation, reusable only when its full optical band matches
	 * and ALL its spans are longitudinally disjoint from the proposed PICh.
	 * The shared slot remains unavailable to DChs along the entire physical link.
	 */
	private SpectrumLease reusableLease(Link link, int coreId, int[] band,
			int guardBand, QKDSpan span, Circuit candidate, SpectrumLease withinRequestLease) {
		// Same request: sequential PIChs can share their OWN optical spectrum
		// lease between trusted nodes. This does not enable inter-request reuse
		// when the experimental pichLongitudinalReuse option is disabled.
		if (withinRequestLease == null
				&& (placement == null || !placement.isPichLongitudinalReuse())) return null;
		if (candidate != null && rawCore(candidate) == coreId && traverses(candidate, link)
				&& candidate.getSpectrumAssigned() != null) {
			int[] cb = candidate.getSpectrumAssigned();
			if (cb[0] <= band[1] + guardBand && cb[1] >= band[0] - guardBand) return null;
		}
		List<Channel> onLink = byLink.get(link);
		if (onLink == null) return null;
		SpectrumLease matched = null;
		for (Channel old : onLink) {
			if (old.coreId != coreId || old.guardBand != guardBand
					|| old.band[0] != band[0] || old.band[1] != band[1]) continue;
			if (Math.min(span.getEndKm(), old.span.getEndKm())
					- Math.max(span.getStartKm(), old.span.getStartKm()) > 1e-9)
				return null;
				if (withinRequestLease != null && old.lease != withinRequestLease) return null;
			if (matched == null) matched = old.lease;
			else if (matched != old.lease) return null;
		}
		return matched;
	}

	private Channel openDedicated(Link link, QKDSpan span, int guardBand,
			Circuit candidate, Channel required) {
		if (placement != null && (placement.isPichOsnrCheckEnabled()
				|| placement.isPichRxPowerCheckEnabled())
				&& (classicalPhysicalLayer == null || selectedModulation == null))
			throw new IllegalStateException("PICh QoT requires SNetS PhysicalLayer and configured modulation");

		for (Core core : pichCoreOrder(java.util.Collections.singletonList(link), placement)) {
			// Physical SNetS nodes may change classical cores; only slots are continuous.
			// Inside the same physical link (across QTND trusted nodes), retain the core.
			if (required != null && required.link == link && core.getId() != required.coreId) continue;
			int n = core.getNumOfSlots();
			boolean[] free = new boolean[n + 2];

			for (int[] b : core.getFreeSpectrumBands(guardBand))
				for (int k = Math.max(1, b[0]); k <= Math.min(n, b[1]); k++) free[k] = true;

			if (candidate != null && candidate.getSpectrumAssigned() != null && candidate.getRoute() != null
					&& rawCore(candidate) == core.getId() && traverses(candidate, link)) {
				int[] cb = candidate.getSpectrumAssigned();

				for (int k = Math.max(1, cb[0] - guardBand); k <= Math.min(n, cb[1] + guardBand); k++) free[k] = false;
			}

			for (int s : (required == null
					? pichSlotOrder(n, pichSlots, core.getId(), link.getNumberOfCores(), placement)
					: new int[] {required.band[0]})) {
				if (s < 1 || s + pichSlots - 1 > n) continue;
				if (placement != null && !withinPichSlotWindow(s, pichSlots,
						placement.getPichFirstSlot(), placement.getPichLastSlot())) continue;
				boolean ok = true;
				for (int k = s; k < s + pichSlots; k++) if (!free[k]) { ok = false; break; }
				int[] band = new int[] {s, s + pichSlots - 1};
				SpectrumLease shared = reusableLease(link, core.getId(), band, guardBand, span,
						candidate, required != null && required.link == link ? required.lease : null);
				if (!ok && shared == null) continue;

				// Same-band guard between the classical PICh and established QChs.
				if (placement != null && placement.isSameBandAlignedGrids()
						&& !qkd.resource.SameBandSpectrumGuard.classicalAllowed(link,
								placement.getQuantumCoreId(), band,
								placement.getSameBandGuardSlots())) continue;

				// Evaluate a provisional, unregistered PICh using exactly the
				// modulation and fibre/XT/GN/EDFA inputs of SNetS classical QoT.
				QKDPichCircuit trial = new QKDPichCircuit(link, span,
						core.getId(), band, guardBand, launchPowerW);

				if (selectedModulation != null) trial.assignModulation(selectedModulation);

				if (placement != null && (placement.isPichOsnrCheckEnabled()
						|| placement.isPichRxPowerCheckEnabled())) {
					final PhysicalLayer.PichQoTResult result =
							classicalPhysicalLayer.evaluatePich(trial, candidate);

					if (placement.isPichOsnrCheckEnabled() && !result.admissible) continue;

					if (placement.isPichRxPowerCheckEnabled() &&
							!PichReceivePowerBudget.isReceivedPowerFeasible(
									result.receivedSignalW,
									placement.getPichReceiverSensitivityDbm())) continue;
				}

				final boolean reserved;

				try {
					reserved = shared != null || core.useSpectrum(band, guardBand);
				} catch (Exception e) {
					throw new IllegalStateException("PICh spectrum reservation failed", e);
				}

				if (!reserved) continue;
				QKDPichCircuit optical = null;
				boolean registered = false;

				try {
					optical = trial;
					core.addAuxiliaryClassicalCircuit(optical);
					registered = true;
					SpectrumLease lease = shared == null ? new SpectrumLease() : shared;
					Channel c = new Channel(link, span, core.getId(), band, guardBand, launchPowerW, optical, lease);
					List<Channel> list = byLink.get(link);

					if (list == null) { list = new ArrayList<Channel>(); byLink.put(link, list); }
					list.add(c);
					if (shared != null) shared.references++;
					openChannels++; opened++; peakOpenChannels = Math.max(peakOpenChannels, openChannels);

					return c;
				} catch (RuntimeException e) {
					// An exception after useSpectrum must restore BOTH source
					// registration and occupancy before trying another slot.
					if (registered) core.removeAuxiliaryClassicalCircuit(optical);

					try {
						if (shared == null) core.liberateSpectrum(band, guardBand);
					} catch (Exception cleanupFailure) {
						e.addSuppressed(cleanupFailure);

						throw new IllegalStateException("PICh slot cleanup failed", e);
					}

					throw e;
				}
			}
		}

		return null;
	}

	/**
     * Releases each PICh, reporting failures instead of silently losing the
     * reservation bookkeeping. A failed channel remains in the caller list and
     * in byLink so that cleanup can be attempted again.
     */
	public void release(List<Channel> channels) {
		if (channels == null) return;
		IllegalStateException failure = null;

		for (Channel c : new ArrayList<Channel>(channels)) {
			if (c == null) continue;
			if (c.sessions > 1) {
				c.sessions--;
				channels.remove(c);
				continue;
			}
			final Core core = c.link.getCore(c.coreId);

			if (core == null) {
				if (failure == null) failure = new IllegalStateException("PICh release failed");
				failure.addSuppressed(new IllegalStateException("Missing core " + c.coreId
						+ " on link " + c.link.getName()));
				continue;
			}

			try {
				// A shared band remains reserved until its last independent
				// longitudinal PICh has been released.
				if (c.lease.references == 1) core.liberateSpectrum(c.band, c.guardBand);
			} catch (Exception e) {
				if (failure == null) failure = new IllegalStateException("PICh release failed");
				failure.addSuppressed(e);
				continue;
			}

			c.lease.references--;
			core.removeAuxiliaryClassicalCircuit(c.opticalCircuit);
			List<Channel> list = byLink.get(c.link);

			if (list != null) {
				list.remove(c);

				if (list.isEmpty()) byLink.remove(c.link);
			}

			channels.remove(c);
			openChannels = Math.max(0, openChannels - 1);
			closed++;
			c.sessions = 0;
		}

		if (failure != null) throw failure;
	}

	public List<Channel> snapshotOpenChannels() {
		List<Channel> out = new ArrayList<Channel>();

		for (List<Channel> list : byLink.values()) out.addAll(list);

		return out;
	}

	private static int rawCore(Circuit c) {
		try { return c.getIndexCore(); } catch (Exception e) { return -1; }
	}

	private static boolean traverses(Circuit c, Link link) {
		if (c == null || c.getRoute() == null) return false;

		for (Link l : c.getRoute().getLinkList()) if (l == link) return true;

		return false;
	}

	public void sample() { samples++; openSampleSum += openChannels; }
	public void resetStatistics() { peakOpenChannels=openChannels; opened=0; closed=0; openSampleSum=0; samples=0; }
	public int getOpenChannels() { return openChannels; }
	public long getPeakOpenChannels() { return peakOpenChannels; }
	public double getMeanOpenChannels() { return samples > 0 ? openSampleSum / samples : Double.NaN; }
	public long getOpened() { return opened; }
	public long getClosed() { return closed; }
	public double getLaunchPowerW() { return launchPowerW; }
	public int getConfiguredSessionsPerChannel() { return sessionsPerChannel; }
}
