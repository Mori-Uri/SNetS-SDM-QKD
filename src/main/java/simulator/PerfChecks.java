package simulator;

/**
 * Selective performance instrumentation from the experimental branch.
 * All v1 physical computations remain authoritative; this utility observes
 * guarded-spectrum cache, full-source QoT cache and the optional Habibi pair cache.
 * -Dsnets.noPerfCache=true runs the un-memoized reference path.
 * -Dsnets.gnPairCache=true opts into GN pair memoization.
 * -Dsnets.verifyPerf=true checks cached pairs against fresh calculations.
 * -Dsnets.perfStats=true prints counters and sampled physical mutations.
 */
public final class PerfChecks {

    /** Compare every cached value with a fresh computation (tests only). */
    public static final boolean VERIFY = Boolean.getBoolean("snets.verifyPerf");

    /** Caches off: original code path. */
    public static final boolean DISABLED = Boolean.getBoolean("snets.noPerfCache");

    /** Caches on (default). */
    public static final boolean ENABLED = !DISABLED;

    /** Count the use of each cache and print it at the end (-Dsnets.perfStats=true). */
    public static final boolean STATS = Boolean.getBoolean("snets.perfStats");

    // Keep experimental IDs for safe interaction with precompiled classes.
    public static final int ARCSINH_HIT = 0, ARCSINH_MISS = 1,
            BANDS_HIT = 2, BANDS_MISS = 3, QOT_HIT = 4, QOT_MISS = 5,
            INTERF_HIT = 6, INTERF_MISS = 7, REFRESH_SKIP = 8, REFRESH_DONE = 9,
            GN_PAIR_HIT = 10, GN_PAIR_MISS = 11;
    private static final String[] NAMES = {
        "", "", "faixas livres", "", "QoT por enlace", "", "", "", "", "", "par GN/EGN Habibi", ""
    };
    private static final java.util.concurrent.atomic.AtomicLongArray COUNTS =
            new java.util.concurrent.atomic.AtomicLongArray(12);

    static {
        if (STATS) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    System.out.println("[snets.perfStats] uso dos caches (aproveitado / calculado):");
                    for (int i : new int[]{BANDS_HIT, QOT_HIT, GN_PAIR_HIT}) {
                        final long h = COUNTS.get(i), m = COUNTS.get(i + 1);
                        System.out.println(String.format("  %-36s %,14d / %,14d  (%.1f %%)", NAMES[i], h, m,
                                h + m == 0 ? 0.0 : 100.0 * h / (h + m)));
                    }
                    System.out.println("[snets.perfStats] mudancas fisicas observadas (telemetria, sem salto de calculos): "
                            + network.PhysicalStateChanges.totalMutations() + " mutacoes, "
                            + network.PhysicalStateChanges.totalMembershipChanges() + " alteracoes de membros");
                }
            });
        }
    }

    /** Counts only when requested, avoiding hot-loop AtomicLong overhead by default. */
    public static void count(int what) {
        if (STATS) COUNTS.incrementAndGet(what);
    }

    public static long countValue(int what) { return COUNTS.get(what); }

    private PerfChecks() {
    }

    /** Throws when two doubles differ in any bit. */
    public static void same(double cached, double fresh, String what) {
        if (Double.doubleToRawLongBits(cached) != Double.doubleToRawLongBits(fresh)) {
            throw new IllegalStateException("snets.verifyPerf: cache de " + what + " difere: " + cached + " != " + fresh);
        }
    }
}
