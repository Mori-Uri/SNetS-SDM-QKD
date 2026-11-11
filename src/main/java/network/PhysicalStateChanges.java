package network;

/**
 * Trace-only mutation/membership counters adapted from the experimental branch.
 * In the v1 they deliberately DO NOT authorize skipping re-evaluation of QKD
 * sessions, which may depend on non-local Raman, longitudinal PIChs, NLI and ASE.
 * Tracing is enabled only by -Dsnets.perfStats=true and is not complete for
 * direct mutations of collection views or per-link translucent configuration.
 */
public final class PhysicalStateChanges {

    private static final ThreadLocal<long[]> COUNTERS = new ThreadLocal<long[]>() {
        @Override
        protected long[] initialValue() {
            return new long[2];
        }
    };

    /**
     * True once a translucent circuit (with per-link spectrum, modulation and core maps that are changed directly)
     * entered the network, or a circuit received a request list that cannot be tracked: the caches that depend on the
     * change tracking are then turned off for the process.
     */
    private static volatile boolean untracked = true; // v1 QKD/PICh changes are not exhaustively tracked
    private static final java.util.concurrent.atomic.AtomicLong MUTATIONS = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong MEMBERSHIPS = new java.util.concurrent.atomic.AtomicLong();

    private PhysicalStateChanges() {
    }

    static void mutated() {
        if (simulator.PerfChecks.STATS) { COUNTERS.get()[0]++; MUTATIONS.incrementAndGet(); }
    }

    static void membershipChanged() {
        if (simulator.PerfChecks.STATS) { COUNTERS.get()[1]++; MEMBERSHIPS.incrementAndGet(); }
    }

    /** Changes made to circuits already in the network (this thread). */
    public static long mutations() {
        return COUNTERS.get()[0];
    }

    /** Circuits added to or removed from core circuit lists (this thread). */
    public static long memberships() {
        return COUNTERS.get()[1];
    }

    public static long totalMutations() { return MUTATIONS.get(); }
    public static long totalMembershipChanges() { return MEMBERSHIPS.get(); }

    /** No cache skips are based on this telemetry in v1 (PICh and per-span state are retained). */
    public static boolean maySkipPhysicalRefresh() { return false; }

    static void markUntracked() {
        untracked = true;
    }

    /** True when the change tracking cannot be trusted (see {@link #markUntracked}). */
    public static boolean isUntracked() {
        return untracked;
    }
}
