package simulator;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parte 45: computational cost of the algorithms of one simulation (one load point and replication), written to
 * {@code <caso>_AlgorithmCost.csv} when {@code simulation.activeMetrics.AlgorithmCost} is true.
 *
 * <p>Each algorithm object chosen in the simulation file (grooming, integrated RMLSA, routing, k-routing, spectrum
 * assignment, core and spectrum assignment, modulation selection, power assignment, reallocation) is wrapped in a
 * {@link java.lang.reflect.Proxy} of its interface. The proxy only forwards the call and adds the elapsed time
 * ({@link System#nanoTime()}) and one call to the component; it never changes arguments, results, exceptions or the
 * order of the calls, so the simulation results are the same with or without the metric (checked by the regression
 * test). Times are inclusive: when an integrated RMLSA calls the spectrum assignment through the control plane,
 * that time is counted in both components. QKD admission and the protection of active sessions are timed directly
 * in the QKD control plane ({@link #add}).</p>
 *
 * <p>Wall-clock times depend on the machine and on the other threads; compare algorithms in the same run or with
 * {@code threads = 1}. The number of calls does not depend on the machine.</p>
 *
 * <p>Not wrapped: PABS power assignment (the blocking metric reads its counters through {@code instanceof}).</p>
 */
public final class AlgorithmCostRecorder {

    /** Statistics of one component. */
    public static final class Stat {
        public final String component;
        public final String algorithm;
        public long calls;
        public long totalNanos;
        public long maxNanos;

        Stat(String component, String algorithm) {
            this.component = component;
            this.algorithm = algorithm;
        }
    }

    private final Map<String, Stat> stats = new LinkedHashMap<String, Stat>();
    private long wallNanos;

    /** Wraps {@code target} (an implementation of {@code iface}) so that its calls are timed. */
    @SuppressWarnings("unchecked")
    public <T> T wrap(final T target, Class<T> iface, String component) {
        if (target == null || !iface.isInterface()) {
            return target;
        }
        final String name = target.getClass().getName();
        if (name.contains("PABSAlgorithm")) {
            return target;
        }
        final Stat stat = stat(component, target.getClass().getSimpleName());
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getDeclaringClass() == Object.class) {
                    if ("equals".equals(method.getName())) return Boolean.valueOf(proxy == args[0]);
                    if ("hashCode".equals(method.getName())) return Integer.valueOf(System.identityHashCode(proxy));
                    if ("toString".equals(method.getName())) return target.toString();
                }
                final long t0 = System.nanoTime();
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                } finally {
                    record(stat, System.nanoTime() - t0);
                }
            }
        });
    }

    /** Adds one timed call of a component that is not an interface (e.g. QKD admission). */
    public synchronized void add(String component, String algorithm, long nanos) {
        record(stat(component, algorithm), nanos);
    }

    private synchronized Stat stat(String component, String algorithm) {
        Stat s = stats.get(component);
        if (s == null) {
            s = new Stat(component, algorithm);
            stats.put(component, s);
        }
        return s;
    }

    private static void record(Stat s, long nanos) {
        synchronized (s) {
            s.calls++;
            s.totalNanos += nanos;
            if (nanos > s.maxNanos) s.maxNanos = nanos;
        }
    }

    /** Wall-clock time of the whole simulation (set by the simulation manager). */
    public void setWallNanos(long nanos) {
        this.wallNanos = nanos;
    }

    public long getWallNanos() {
        return wallNanos;
    }

    public synchronized Iterable<Stat> getStats() {
        return new java.util.ArrayList<Stat>(stats.values());
    }

    /** CSV header of {@link #rows}. */
    public static String header() {
        return "LoadPoint,Replication,Component,Algorithm,Calls,CallsPerRequest,TotalMs,MeanUs,MaxUs,ShareOfSimulationPct\n";
    }

    /** CSV rows of this simulation (one per component plus the whole simulation). */
    public String rows(int loadPoint, int replication, long requests) {
        final StringBuilder sb = new StringBuilder();
        final double wallMs = wallNanos / 1e6;
        for (Stat s : getStats()) {
            sb.append(String.format(Locale.ROOT, "%d,%d,%s,%s,%d,%.4f,%.3f,%.3f,%.3f,%.2f%n", loadPoint, replication,
                    s.component, s.algorithm, s.calls, requests > 0 ? s.calls / (double) requests : Double.NaN,
                    s.totalNanos / 1e6, s.calls > 0 ? s.totalNanos / 1e3 / s.calls : 0.0, s.maxNanos / 1e3,
                    wallMs > 0 ? 100.0 * (s.totalNanos / 1e6) / wallMs : Double.NaN));
        }
        sb.append(String.format(Locale.ROOT, "%d,%d,Simulation,(total),%d,1.0000,%.3f,%.3f,%.3f,100.00%n", loadPoint,
                replication, requests, wallMs, requests > 0 ? wallNanos / 1e3 / requests : 0.0, 0.0));
        return sb.toString();
    }
}
