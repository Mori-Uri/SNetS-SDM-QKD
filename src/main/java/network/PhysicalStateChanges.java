package network;

/**
 * Parte 54: counters of the changes made to the classical state of the network, per thread (a simulation runs in a
 * single thread, so these are the counters of the simulation being run):
 * <ul>
 *   <li>{@link #mutations()}: changes made to circuits that are already in the network (spectrum, core, modulation,
 *       power, route or requests changed after the allocation: reallocation, spectrum expansion, power
 *       adjustment...);</li>
 *   <li>{@link #memberships()}: circuits added to or removed from the circuit list of a core (one per core list).</li>
 * </ul>
 * The QKD session refresh uses them to know that nothing but the circuit being allocated or released changed since
 * the last refresh.
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
    private static volatile boolean untracked;

    private PhysicalStateChanges() {
    }

    static void mutated() {
        COUNTERS.get()[0]++;
    }

    static void membershipChanged() {
        COUNTERS.get()[1]++;
    }

    /** Changes made to circuits already in the network (this thread). */
    public static long mutations() {
        return COUNTERS.get()[0];
    }

    /** Circuits added to or removed from core circuit lists (this thread). */
    public static long memberships() {
        return COUNTERS.get()[1];
    }

    static void markUntracked() {
        untracked = true;
    }

    /** True when the change tracking cannot be trusted (see {@link #markUntracked}). */
    public static boolean isUntracked() {
        return untracked;
    }
}
