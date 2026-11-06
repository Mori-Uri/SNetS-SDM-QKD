package network;

import gprmcsa.modulation.Modulation;

/**
 * Parte 54 (desempenho): noise terms (NLI, ASE and XT) that one link adds to the SNR of one circuit, kept in the
 * circuit and reused by {@link PhysicalLayer#computeSNRSegment}.
 *
 * <p>An entry is valid only while everything that the original computation reads is the same:</p>
 * <ul>
 *   <li>the link version ({@link Link#getPhysicalVersion()}): it changes whenever a circuit enters or leaves a core
 *       of the link, a core spectrum changes, or a circuit in the network that uses the link changes its spectrum,
 *       core, modulation, power, route or requests;</li>
 *   <li>the parameters of the evaluated circuit itself: core, spectrum, modulation, launch power, bandwidth, PSD and
 *       central frequency (compared bit for bit), and the physical layer used.</li>
 * </ul>
 * <p>Terms computed with a test circuit on the link are never stored. With -Dsnets.verifyPerf=true every hit is
 * recomputed and compared bit for bit.</p>
 */
final class QotLinkCache {

    private QotLinkCache() {
    }

    /** One cached link of one circuit. */
    static final class Entry {
        Link link;
        PhysicalLayer physicalLayer;
        long version;
        int core;
        int s0;
        int s1;
        Modulation modulation;
        long power;
        long bsi;
        long psd;
        long fi;
        double nli;
        double ase;
        double xt;
    }

    private static final int MAX = 16;

    static Entry find(Circuit c, PhysicalLayer pl, Link link, int core, int[] sa, Modulation mod, double power,
            double bsi, double psd, double fi) {
        final Entry[] es = c.qotCache;
        if (es == null) {
            return null;
        }
        final long version = link.getPhysicalVersion();
        for (int k = 0; k < es.length; k++) {
            final Entry e = es[k];
            if (e == null) {
                return null;
            }
            if (e.link == link && e.physicalLayer == pl) {
                if (e.version == version && e.core == core && e.s0 == sa[0] && e.s1 == sa[1] && e.modulation == mod
                        && e.power == Double.doubleToRawLongBits(power) && e.bsi == Double.doubleToRawLongBits(bsi)
                        && e.psd == Double.doubleToRawLongBits(psd) && e.fi == Double.doubleToRawLongBits(fi)) {
                    return e;
                }
                return null;
            }
        }
        return null;
    }

    static void store(Circuit c, PhysicalLayer pl, Link link, long version, int core, int[] sa, Modulation mod,
            double power, double bsi, double psd, double fi, double[] terms) {
        Entry[] es = c.qotCache;
        if (es == null) {
            es = new Entry[4];
            c.qotCache = es;
        }
        int slot = -1;
        for (int k = 0; k < es.length; k++) {
            if (es[k] == null || (es[k].link == link && es[k].physicalLayer == pl)) {
                slot = k;
                break;
            }
        }
        if (slot < 0) {
            if (es.length < MAX) {
                final Entry[] bigger = new Entry[Math.min(MAX, es.length * 2)];
                System.arraycopy(es, 0, bigger, 0, es.length);
                slot = es.length;
                es = bigger;
                c.qotCache = es;
            } else {
                // full: drop the oldest entry (index 0) and keep the others in order
                System.arraycopy(es, 1, es, 0, es.length - 1);
                slot = es.length - 1;
                es[slot] = null;
            }
        }
        Entry e = es[slot];
        if (e == null) {
            e = new Entry();
            es[slot] = e;
        }
        e.link = link;
        e.physicalLayer = pl;
        e.version = version;
        e.core = core;
        e.s0 = sa[0];
        e.s1 = sa[1];
        e.modulation = mod;
        e.power = Double.doubleToRawLongBits(power);
        e.bsi = Double.doubleToRawLongBits(bsi);
        e.psd = Double.doubleToRawLongBits(psd);
        e.fi = Double.doubleToRawLongBits(fi);
        e.nli = terms[0];
        e.ase = terms[1];
        e.xt = terms[2];
    }
}
