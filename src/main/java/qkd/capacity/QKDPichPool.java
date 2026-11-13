package qkd.capacity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import network.Circuit;
import network.Core;
import network.Link;

/**
 * Part 26: public interaction channels (PICh) that follow the QCh.
 *
 * <p>The QCh of a QKD session is terminated at every node of the QKD route (network
 * nodes, and the in-line trusted nodes every QTND inside each link). The PICh carries
 * the public discussion (sifting, error correction, privacy amplification) of each of
 * those QKD links, so it ends at the same nodes. Because SNetS allocates spectrum per
 * physical link, a PICh here is one band of {@code pichSlots} slots on one classical
 * core of one physical link. All sessions whose QKD route uses that link share it,
 * as they share the QChs of the link, up to {@code pichSessionsPerChannel} sessions
 * (0 = unlimited). A new PICh is opened only when no open PICh of the link has room,
 * and a PICh is closed when its last session leaves.</p>
 *
 * <p>Modelo da Parte 26 (pedido do coorientador): o PICh acompanha o QCh e termina nos mesmos n\u00f3s.</p>
 *
 * <p>Refer\u00eancias: [MOGH21] E. E. Moghaddam, H. Beyranvand, J. A. Salehi, "Resource Allocation in SDM-EONs Secured With QKD", IEEE JSAC 39(9), 2021 (Sec. III-A e Eq. (22): QCh, CCh e DCh na mesma rota); [AHMED26a] I. Ahmed, B. C. Chatterjee, E. Oki, "QKD-Analytical: Analytical Model for Blocking Probabilities in Priority-Aware QKD over SDM-EONs", IEEE INFOCOM 2026 (canal cl\u00e1ssico QKD com banda de guarda); [CHAT26] B. C. Chatterjee, E. Oki, "Towards Secure and Flexible Optical Networks - A Tutorial on Quantum-Classical Coexistence", ICNC 2026.</p>
 */
public final class QKDPichPool implements Serializable {
    private static final long serialVersionUID = 1L;

    /** One open PICh on one physical link. */
    public static final class Channel implements Serializable {
        private static final long serialVersionUID = 1L;
        public final Link link;
        public final int coreId;
        public final int[] band;
        public final int guardBand;
        /** Parte 65: the PICh as a classical channel (null when the pool has no physical layer). */
        public final QKDPichCircuit circuit;
        int sessions;

        Channel(Link link, int coreId, int[] band, int guardBand) {
            this(link, coreId, band, guardBand, null);
        }

        Channel(Link link, int coreId, int[] band, int guardBand, QKDPichCircuit circuit) {
            this.link = link; this.coreId = coreId; this.band = band; this.guardBand = guardBand; this.circuit = circuit;
        }

        public int getSessions() { return sessions; }
    }

    /**
     * Parte 65: physical layer of the PICh (pedido do coorientador: o PICh funciona como um canal cl\u00e1ssico). A new
     * PICh gets a modulation (from the most efficient down, as ModulationSelectionByQoTv2 does for the circuits),
     * the launch power of the circuits (PhysicalLayer.getCircuitLaunchPower), and is placed in the circuit list of
     * its core, so it interferes with the other channels (NLI, XT, QCh noise) and suffers their interference. It is
     * opened only when check() accepts it.
     */
    public interface Physics extends Serializable {
        /** Modulations of the network, from the least to the most efficient (Mesh.getAvaliableModulations). */
        List<gprmcsa.modulation.Modulation> modulations();

        /**
         * Called with the PICh already in the circuit list of its core and its slots in use. Returns OK or the
         * reason it cannot be opened (QOT, CLASSICAL, QKD).
         */
        int check(QKDPichCircuit pich, Circuit candidate);
    }

    public static final int OK = 0, QOT = 1, CLASSICAL = 2, QKD = 3, SPECTRUM = 4;

    private Physics physics;
    private double bitRate = 10e9;
    /** Parte 65: why the last acquire failed (SPECTRUM, QOT, CLASSICAL or QKD). */
    private transient int lastFailureKind;
    private long failSpectrum, failQot, failClassical, failQkd;
    private double openedSlotsSum;
    /** Parte 65: PIChs opened or closed since the last drainChanged (to refresh the QKD sessions of their links). */
    private final List<QKDPichCircuit> changed = new ArrayList<QKDPichCircuit>();

    /** Parte 65: turns the physical layer of the PICh on (bit rate in bit/s). */
    public void setPhysics(Physics physics, double bitRate) {
        this.physics = physics;
        this.bitRate = bitRate;
    }

    public boolean isPhysical() { return physics != null; }
    public int getLastFailureKind() { return lastFailureKind; }

    /** Parte 65: PIChs opened or closed since the last call. */
    public List<QKDPichCircuit> drainChanged() {
        if (changed.isEmpty()) return java.util.Collections.emptyList();
        List<QKDPichCircuit> out = new ArrayList<QKDPichCircuit>(changed);
        changed.clear();
        return out;
    }

    private final int pichSlots;
    private final int sessionsPerChannel;
    /** Parte 39: link of the last failed acquire (null when it succeeded). */
    private transient Link lastFailedLink;

    public Link getLastFailedLink() { return lastFailedLink; }
    private final Map<Link, List<Channel>> byLink = new IdentityHashMap<Link, List<Channel>>();
    private int openChannels;
    private long peakOpenChannels;
    private long opened, closed;
    private double openSampleSum;
    private long samples;

    /** Parte 40: placement rules of a new PICh (core and slot); null = lowest core, first fit. */
    private final qkd.network.QKDNetworkParameters placement;

    public QKDPichPool(int pichSlots, int sessionsPerChannel) {
        this(pichSlots, sessionsPerChannel, null);
    }

    public QKDPichPool(int pichSlots, int sessionsPerChannel, qkd.network.QKDNetworkParameters placement) {
        this.pichSlots = pichSlots;
        this.sessionsPerChannel = sessionsPerChannel;
        this.placement = placement;
    }

    /**
     * Parte 40: classical cores that may receive a new PICh on the given links, in the order they are tried.
     * The QKD core and the disabled cores are never used. "lowest" (default): by id; "fixed": pichCoreId first,
     * then the others by id; "leastused": fewest used slots on the links first.
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
     * Joins (or opens) one PICh on every link of the QKD route. The slots of the
     * pending classical candidate are treated as busy when a new PICh is opened.
     * Returns the PIChs used, or null (nothing held) when some link has no room.
     */
    public List<Channel> acquire(List<Link> links, int guardBand, Circuit candidate) {
        return acquire(links, null, null, guardBand, candidate);
    }

    /**
     * Parte 65: as above; sectionsKm[i] are the sections of links[i] (from one trusted node to the next, the same as
     * the QCh), used by the physical layer of a new PICh (null = the whole link); nodes are the nodes of the route
     * (links[i] goes from nodes[i] to nodes[i+1]).
     */
    public List<Channel> acquire(List<Link> links, List<network.Node> nodes, double[][] sectionsKm, int guardBand,
            Circuit candidate) {
        List<Channel> got = new ArrayList<Channel>();
        lastFailedLink = null;
        lastFailureKind = OK;
        for (int i = 0; i < links.size(); i++) {
            Link l = links.get(i);
            double[] sec = sectionsKm == null || sectionsKm[i] == null ? new double[] {l.getDistance()} : sectionsKm[i];
            Channel c = physics == null || nodes == null ? joinOrOpen(l, guardBand, candidate)
                    : joinOrOpenPhysical(l, nodes.get(i), nodes.get(i + 1), sec, guardBand, candidate);
            if (c == null) {
                if (physics == null) lastFailureKind = SPECTRUM;
                switch (lastFailureKind) {
                    case QOT: failQot++; break;
                    case CLASSICAL: failClassical++; break;
                    case QKD: failQkd++; break;
                    default: failSpectrum++; break;
                }
                lastFailedLink = l; // Parte 39: link where no PICh fitted (observation only)
                release(got);
                return null;
            }
            got.add(c);
        }
        return got;
    }

    /**
     * Parte 65: joins an open PICh of the link with room, or opens a new one as a classical channel. Modulations are
     * tried from the most efficient down; for each one, the cores in the PICh core order, each with the first free
     * band of the slot order (the band has max(pichSlots, slots of the modulation) slots). The first placement that
     * the physical check accepts is kept. lastFailureKind tells the most advanced check that failed.
     */
    private Channel joinOrOpenPhysical(Link link, network.Node from, network.Node to, double[] sectionsKm, int guardBand,
            Circuit candidate) {
        List<Channel> list = byLink.get(link);
        if (list != null) {
            for (Channel c : list) {
                if (sessionsPerChannel <= 0 || c.sessions < sessionsPerChannel) {
                    c.sessions++;
                    return c;
                }
            }
        }
        final QKDPichCircuit pich = new QKDPichCircuit(from, to, sectionsKm, bitRate);
        pich.setGuardBand(guardBand);
        final List<gprmcsa.modulation.Modulation> mods = physics.modulations();
        final List<Core> cores = pichCoreOrder(java.util.Collections.singletonList(link), placement);
        int worst = SPECTRUM;
        for (int m = mods.size() - 1; m >= 0; m--) {
            final gprmcsa.modulation.Modulation mod = mods.get(m);
            final int slots = Math.max(pichSlots, mod.requiredSlots(bitRate));
            pich.setModulation(mod);
            for (Core core : cores) {
                final int n = core.getNumOfSlots();
                boolean[] free = new boolean[n + 2];
                for (int[] b : core.getFreeSpectrumBands(guardBand))
                    for (int k = Math.max(1, b[0]); k <= Math.min(n, b[1]); k++) free[k] = true;
                if (candidate != null && candidate.getSpectrumAssigned() != null && candidate.getRoute() != null
                        && rawCore(candidate) == core.getId() && traverses(candidate, link)) {
                    int[] cb = candidate.getSpectrumAssigned();
                    for (int k = Math.max(1, cb[0] - guardBand); k <= Math.min(n, cb[1] + guardBand); k++) free[k] = false;
                }
                int[] band = null;
                for (int s : pichSlotOrder(n, slots, core.getId(), link.getNumberOfCores(), placement)) {
                    boolean ok = true;
                    for (int k = s; k < s + slots; k++) if (!free[k]) { ok = false; break; }
                    if (ok) { band = new int[] {s, s + slots - 1}; break; }
                }
                if (band == null) continue;
                try {
                    if (!core.useSpectrum(band, guardBand)) continue;
                } catch (Exception e) {
                    continue;
                }
                pich.setIndexCore(core.getId());
                pich.setSpectrumAssigned(band);
                core.addCircuit(pich);
                int verdict;
                try {
                    verdict = physics.check(pich, candidate);
                } catch (RuntimeException e) {
                    core.removeCircuit(pich);
                    try { core.liberateSpectrum(band, guardBand); } catch (Exception ignored) { }
                    throw e;
                }
                if (verdict == OK) {
                    Channel c = new Channel(link, core.getId(), band, guardBand, pich);
                    c.sessions = 1;
                    if (list == null) { list = new ArrayList<Channel>(); byLink.put(link, list); }
                    list.add(c);
                    openChannels++;
                    opened++;
                    openedSlotsSum += slots;
                    peakOpenChannels = Math.max(peakOpenChannels, openChannels);
                    changed.add(pich);
                    return c;
                }
                core.removeCircuit(pich);
                try { core.liberateSpectrum(band, guardBand); } catch (Exception ignored) { }
                if (verdict != SPECTRUM && (worst == SPECTRUM || verdict > worst)) worst = verdict;
            }
        }
        lastFailureKind = worst;
        return null;
    }

    private Channel joinOrOpen(Link link, int guardBand, Circuit candidate) {
        List<Channel> list = byLink.get(link);
        if (list != null) {
            for (Channel c : list) {
                if (sessionsPerChannel <= 0 || c.sessions < sessionsPerChannel) {
                    c.sessions++;
                    return c;
                }
            }
        }
        for (Core core : pichCoreOrder(java.util.Collections.singletonList(link), placement)) {
            int n = core.getNumOfSlots();
            boolean[] free = new boolean[n + 2];
            for (int[] b : core.getFreeSpectrumBands(guardBand))
                for (int k = Math.max(1, b[0]); k <= Math.min(n, b[1]); k++) free[k] = true;
            if (candidate != null && candidate.getSpectrumAssigned() != null && candidate.getRoute() != null
                    && rawCore(candidate) == core.getId() && traverses(candidate, link)) {
                int[] cb = candidate.getSpectrumAssigned();
                for (int k = Math.max(1, cb[0] - guardBand); k <= Math.min(n, cb[1] + guardBand); k++) free[k] = false;
            }
            for (int s : pichSlotOrder(n, pichSlots, core.getId(), link.getNumberOfCores(), placement)) {
                boolean ok = true;
                for (int k = s; k < s + pichSlots; k++) if (!free[k]) { ok = false; break; }
                if (!ok) continue;
                int[] band = new int[] {s, s + pichSlots - 1};
                try {
                    if (!core.useSpectrum(band, guardBand)) continue;
                } catch (Exception e) {
                    continue;
                }
                Channel c = new Channel(link, core.getId(), band, guardBand);
                c.sessions = 1;
                if (list == null) { list = new ArrayList<Channel>(); byLink.put(link, list); }
                list.add(c);
                openChannels++;
                opened++;
                peakOpenChannels = Math.max(peakOpenChannels, openChannels);
                return c;
            }
        }
        return null;
    }

    /** Leaves the given PIChs; a PICh without sessions is closed and its slots freed. */
    public void release(List<Channel> channels) {
        if (channels == null) return;
        for (Channel c : channels) {
            c.sessions--;
            if (c.sessions <= 0) {
                if (c.circuit != null) { // Parte 65: the PICh leaves the circuit list of its core
                    c.link.getCore(c.coreId).removeCircuit(c.circuit);
                    changed.add(c.circuit);
                }
                try { c.link.getCore(c.coreId).liberateSpectrum(c.band, c.guardBand); } catch (Exception ignored) { }
                List<Channel> list = byLink.get(c.link);
                if (list != null) { list.remove(c); if (list.isEmpty()) byLink.remove(c.link); }
                openChannels--;
                closed++;
            }
        }
        channels.clear();
    }

    private static int rawCore(Circuit c) {
        try {
            java.lang.reflect.Field f = Circuit.class.getDeclaredField("indexCore");
            f.setAccessible(true);
            return f.getInt(c);
        } catch (Exception e) {
            return -1;
        }
    }

    private static boolean traverses(Circuit c, Link link) {
        for (Link l : c.getRoute().getLinkList()) if (l == link) return true;
        return false;
    }

    public void sample() {
        samples++;
        openSampleSum += openChannels;
        if (physics == null) return;
        for (List<Channel> list : byLink.values()) { // Parte 65: slots and modulation of the open PIChs
            for (Channel c : list) {
                if (c.circuit == null) continue;
                openSlotsSampleSum += c.band[1] - c.band[0] + 1;
                final String mod = c.circuit.getModulation().getName();
                final double[] v = modSamples.get(mod);
                if (v == null) modSamples.put(mod, new double[] {1.0}); else v[0] += 1.0;
            }
        }
    }

    private double openSlotsSampleSum;
    private final java.util.TreeMap<String, double[]> modSamples = new java.util.TreeMap<String, double[]>();

    /** Parte 65: mean number of slots of an open PICh (sampled at the arrivals). */
    public double getMeanSlotsPerOpenChannel() { return openSampleSum > 0 ? openSlotsSampleSum / openSampleSum : Double.NaN; }

    /** Parte 65: mean number of open PIChs with each modulation (sampled at the arrivals). */
    public java.util.Map<String, Double> getMeanOpenChannelsByModulation() {
        java.util.Map<String, Double> out = new java.util.TreeMap<String, Double>();
        for (java.util.Map.Entry<String, double[]> e : modSamples.entrySet())
            out.put(e.getKey(), Double.valueOf(samples > 0 ? e.getValue()[0] / samples : Double.NaN));
        return out;
    }

    public void resetStatistics() {
        peakOpenChannels = openChannels; opened = 0; closed = 0; openSampleSum = 0; samples = 0;
        failSpectrum = 0; failQot = 0; failClassical = 0; failQkd = 0; openedSlotsSum = 0;
        openSlotsSampleSum = 0; modSamples.clear();
    }

    /** Parte 65: failed PICh openings by reason (the link of a QKD route where no PICh could be joined or opened). */
    public long getFailSpectrum() { return failSpectrum; }
    public long getFailQot() { return failQot; }
    public long getFailClassical() { return failClassical; }
    public long getFailQkd() { return failQkd; }
    /** Parte 65: mean number of slots of the PIChs opened (it depends on the modulation chosen). */
    public double getMeanOpenedSlots() { return opened > 0 ? openedSlotsSum / opened : Double.NaN; }

    public int getOpenChannels() { return openChannels; }
    public long getPeakOpenChannels() { return peakOpenChannels; }
    public double getMeanOpenChannels() { return samples > 0 ? openSampleSum / samples : Double.NaN; }
    public long getOpened() { return opened; }
    public long getClosed() { return closed; }
}
