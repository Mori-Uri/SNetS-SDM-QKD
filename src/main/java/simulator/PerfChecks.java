package simulator;

/**
 * Parte 54: switches of the performance caches.
 *
 * <p>Every cache added in Parte 54 returns exactly the value the original code computes (same function, same inputs,
 * same order of the floating-point sums), so the results are identical bit for bit. Two system properties help to
 * check this:</p>
 * <ul>
 *   <li>{@code -Dsnets.verifyPerf=true}: every value served by a cache is also computed the original way and compared
 *       bit for bit; a difference stops the simulation with an {@link IllegalStateException} (slow, for tests);</li>
 *   <li>{@code -Dsnets.noPerfCache=true}: turns the caches off (original code path);</li>
 *   <li>{@code -Dsnets.perfStats=true}: prints, at the end, how often each cache was used.</li>
 * </ul>
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

    public static final int ARCSINH_HIT = 0, ARCSINH_MISS = 1, BANDS_HIT = 2, BANDS_MISS = 3, QOT_HIT = 4,
            QOT_MISS = 5, INTERF_HIT = 6, INTERF_MISS = 7, REFRESH_SKIP = 8, REFRESH_DONE = 9;

    private static final String[] NAMES = { "arcsinh (memoria)", "", "faixas livres", "", "QoT por enlace", "",
            "interferentes do QCh", "", "sessoes QKD puladas / reavaliadas", "" };

    private static final java.util.concurrent.atomic.AtomicLongArray COUNTS =
            new java.util.concurrent.atomic.AtomicLongArray(10);

    static {
        if (STATS) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    System.out.println("[snets.perfStats] uso dos caches (aproveitado / calculado):");
                    for (int i = 0; i < 10; i += 2) {
                        final long h = COUNTS.get(i), m = COUNTS.get(i + 1);
                        System.out.println(String.format("  %-36s %,14d / %,14d  (%.1f %%)", NAMES[i], h, m,
                                h + m == 0 ? 0.0 : 100.0 * h / (h + m)));
                    }
                }
            });
        }
    }

    /** Counts one use of a cache (only with -Dsnets.perfStats=true). */
    public static void count(int what) {
        COUNTS.incrementAndGet(what);
    }

    private PerfChecks() {
    }

    /** Throws when two doubles differ in any bit. */
    public static void same(double cached, double fresh, String what) {
        if (Double.doubleToRawLongBits(cached) != Double.doubleToRawLongBits(fresh)) {
            throw new IllegalStateException("snets.verifyPerf: cache de " + what + " difere: " + cached + " != " + fresh);
        }
    }
}
