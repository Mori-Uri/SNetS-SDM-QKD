package network;

import java.util.Vector;

import gprmcsa.routing.Route;

/**
 * Parte 65: a classical channel that occupies one band of one core along a whole physical link, but whose signal
 * is received and regenerated at intermediate points of the link (the sections). The PICh of the QKD sessions is
 * the only use: it follows the QCh, so each section goes from one trusted node to the next.
 *
 * <ul>
 *   <li>As an interferer it is a normal circuit of the core: it is in the circuit list of the core for the whole
 *       link (the sections cover the link), with its own modulation, bandwidth and launch power, and enters the
 *       NLI, crosstalk, Raman and saturated-gain computations of the other channels.</li>
 *   <li>As a victim its QoT and crosstalk are computed for each section, with the length of the section in place of
 *       the length of the link (PhysicalLayer.computeSNRSegment and Crosstalk.calculateCrosstalk). The worst section
 *       decides: every section must reach the threshold of the modulation.</li>
 * </ul>
 *
 * It does not carry requests: its bit rate is fixed.
 */
public abstract class SectionedCircuit extends Circuit {

    private static final long serialVersionUID = 1L;

    private final Link link;
    private final double[] sectionsKm;
    private final double bitRate;

    /**
     * @param id         circuit id (PICh: negative, see Circuit(Integer))
     * @param from       node at the start of the link
     * @param to         node at the end of the link
     * @param sectionsKm length of each section, in km; they add up to the length of the link
     * @param bitRate    bit rate of the channel, in bit/s
     */
    protected SectionedCircuit(Integer id, Node from, Node to, double[] sectionsKm, double bitRate) {
        super(id);
        if (sectionsKm == null || sectionsKm.length == 0) {
            throw new IllegalArgumentException("a sectioned circuit needs at least one section");
        }
        for (double km : sectionsKm) {
            if (!(km > 0.0)) throw new IllegalArgumentException("section length must be positive: " + km);
        }
        if (!(bitRate > 0.0)) throw new IllegalArgumentException("bit rate must be positive: " + bitRate);
        Vector<Node> nodes = new Vector<Node>();
        nodes.add(from);
        nodes.add(to);
        Route r = new Route(nodes);
        this.route = r;
        this.pair = new Pair(from, to);
        this.link = r.getLink(0);
        this.sectionsKm = sectionsKm.clone();
        this.bitRate = bitRate;
    }

    /** The physical link of the channel. */
    public Link getLink() {
        return link;
    }

    /** Length of each section, in km (copy). */
    public double[] getSectionsKm() {
        return sectionsKm.clone();
    }

    int numberOfSections() {
        return sectionsKm.length;
    }

    double sectionKm(int i) {
        return sectionsKm[i];
    }

    /** True when an earlier section has exactly the same length (its SNR and crosstalk are the same). */
    boolean sameLengthBefore(int i) {
        for (int k = 0; k < i; k++) if (Double.doubleToLongBits(sectionsKm[k]) == Double.doubleToLongBits(sectionsKm[i])) return true;
        return false;
    }

    @Override
    public double getRequiredBitRate() {
        return bitRate;
    }
}
