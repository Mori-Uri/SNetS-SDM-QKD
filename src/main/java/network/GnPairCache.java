package network;

/**
 * Parte 59 (desempenho): the two terms that a circuit j adds to the NLI of a circuit i in the GN model
 * (PhysicalLayer.getGnli2): pnm = E (arcsinh(nm1) - arcsinh(nm2)) and the correction coefficient
 * w = E 5 Phi_j B_j / (6 alpha L_s delta_f). Both depend only on the circuit i (centre frequency, bandwidth and the
 * fibre parameters of its band) and on the circuit j (centre frequency, bandwidth and modulation). They are kept in
 * circuit i, by the id of j, together with these inputs, and reused while none of them changes. The sums of the GN
 * model keep their order, so the NLI is the same bit for bit. Not saved in save states. Activated only with -Dsnets.gnPairCache=true; disabled via -Dsnets.noPerfCache=true; verified with -Dsnets.verifyPerf=true.
 */
final class GnPairCache {

    private static final int MAX_CAPACITY = 1 << 14;

    // inputs of circuit i (the context); a change clears the table
    private long fI, bsI, beta, alpha, e, ls;
    private boolean hasContext;

    private int[] ids;
    private long[] fJ, bsJ;
    private Object[] mod;
    private double[] pnm, w;
    /** An entry is in use when its generation is the current one; clearing the table only moves the generation. */
    private int[] gen;
    private int current = 1;
    private int size;

    GnPairCache() {
        alloc(64);
    }

    private void alloc(int capacity) {
        ids = new int[capacity];
        fJ = new long[capacity];
        bsJ = new long[capacity];
        mod = new Object[capacity];
        pnm = new double[capacity];
        w = new double[capacity];
        gen = new int[capacity];
        current = 1;
        size = 0;
    }

    private void clear() {
        if (size == 0) return;
        current++;
        size = 0;
        if (current == Integer.MAX_VALUE) alloc(ids.length);
    }

    private boolean used(int i) {
        return gen[i] == current;
    }

    /** The table of circuit i for these inputs (cleared when they differ from the ones it was built with). */
    static GnPairCache of(Circuit circuitI, double fI, double bsI, double beta, double alpha, double e, double ls) {
        ThreadLocal<GnPairCache> local = circuitI.gnPairs;
        if (local == null) {
            synchronized (circuitI) {
                local = circuitI.gnPairs;
                if (local == null) {
                    local = new ThreadLocal<GnPairCache>() {
                        @Override protected GnPairCache initialValue() { return new GnPairCache(); }
                    };
                    circuitI.gnPairs = local;
                }
            }
        }
        GnPairCache p = local.get();
        final long a = Double.doubleToRawLongBits(fI), b = Double.doubleToRawLongBits(bsI),
                c = Double.doubleToRawLongBits(beta), d = Double.doubleToRawLongBits(alpha),
                f = Double.doubleToRawLongBits(e), g = Double.doubleToRawLongBits(ls);
        if (!p.hasContext || p.fI != a || p.bsI != b || p.beta != c || p.alpha != d || p.e != f || p.ls != g) {
            p.clear();
            p.fI = a;
            p.bsI = b;
            p.beta = c;
            p.alpha = d;
            p.e = f;
            p.ls = g;
            p.hasContext = true;
        }
        return p;
    }

    private int slotOf(int id) {
        final int mask = ids.length - 1;
        int h = id * 0x9E3779B9;
        h ^= (h >>> 15);
        int i = h & mask;
        while (used(i) && ids[i] != id) {
            i = (i + 1) & mask;
        }
        return i;
    }

    /** Index of the entry of circuit j when it was stored with the same inputs, or -1. */
    int find(int id, double fj, double bsj, Object modulation) {
        final int i = slotOf(id);
        if (used(i) && fJ[i] == Double.doubleToRawLongBits(fj) && bsJ[i] == Double.doubleToRawLongBits(bsj)
                && mod[i] == modulation) {
            return i;
        }
        return -1;
    }

    double pnm(int i) {
        return pnm[i];
    }

    double w(int i) {
        return w[i];
    }

    /** Stores (or replaces) the terms of circuit j. */
    void store(int id, double fj, double bsj, Object modulation, double pnmValue, double wValue) {
        int i = slotOf(id);
        if (!used(i)) {
            if ((size + 1) * 2 > ids.length) {
                if (ids.length >= MAX_CAPACITY) {
                    clear(); // bound the memory: start again
                } else {
                    grow();
                }
                i = slotOf(id);
            }
            gen[i] = current;
            ids[i] = id;
            size++;
        }
        fJ[i] = Double.doubleToRawLongBits(fj);
        bsJ[i] = Double.doubleToRawLongBits(bsj);
        mod[i] = modulation;
        pnm[i] = pnmValue;
        w[i] = wValue;
    }

    private void grow() {
        final int[] oIds = ids;
        final long[] oF = fJ, oB = bsJ;
        final Object[] oM = mod;
        final double[] oP = pnm, oW = w;
        final int[] oG = gen;
        final int oCur = current;
        alloc(oIds.length * 2);
        for (int k = 0; k < oIds.length; k++) {
            if (oG[k] != oCur) continue;
            final int i = slotOf(oIds[k]);
            gen[i] = current;
            ids[i] = oIds[k];
            fJ[i] = oF[k];
            bsJ[i] = oB[k];
            mod[i] = oM[k];
            pnm[i] = oP[k];
            w[i] = oW[k];
            size++;
        }
    }
}
