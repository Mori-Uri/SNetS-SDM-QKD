package simulator;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;

/**
 * Parte 66: probes of the internal attempts of the algorithms for the simulation viewer.
 *
 * <p>The shared functions that the algorithms use to test a candidate (QoT, crosstalk, QoT and crosstalk of the
 * other circuits, the QKD admission and the PICh opening) and the calls to the algorithm interfaces (routing,
 * modulation, core and spectrum assignment...) report what they just computed here. Nothing is computed again and no
 * decision changes: a probe only copies values that already exist. The probes record only while a traced request is
 * being handled in this thread (see SimulationTracer); otherwise each one costs a static boolean test.</p>
 */
public final class TraceProbe {

    private TraceProbe() {
    }

    /** True while some simulation of this JVM records internal attempts (fast path for the probes). */
    public static volatile boolean ON = false;

    private static final ThreadLocal<SimulationTracer> CURRENT = new ThreadLocal<SimulationTracer>();

    static void begin(SimulationTracer t) {
        ON = true;
        CURRENT.set(t);
    }

    static void end() {
        CURRENT.set(null);
    }

    private static SimulationTracer active() {
        return ON ? CURRENT.get() : null;
    }

    /** A check of the candidate (kind: QoT, XT, QoTO, XTO) with its result and value (dB), on the given candidate. */
    public static void check(String kind, Circuit c, Route route, Modulation mod, int core, int[] band, boolean ok,
            double value, Circuit victim) {
        final SimulationTracer t = active();
        if (t != null) t.probeCheck(kind, c, route, mod, core, band, ok, value, victim);
    }

    /** Same, with the route, modulation, core and band read from the circuit. */
    public static void check(String kind, Circuit c, boolean ok, double value, Circuit victim) {
        final SimulationTracer t = active();
        if (t != null) t.probeCheck(kind, c, c.getRoute(), c.getModulation(), qkd.metrics.BlockingDetailRecorder.rawCore(c),
                c.getSpectrumAssigned(), ok, value, victim);
    }

    /** A call to an algorithm (proxy of its interface) and what it left in the candidate. */
    static void call(String component, String method, Object result, Object[] args) {
        final SimulationTracer t = active();
        if (t != null) t.probeCall(component, method, result, args);
    }

    /** The blocking cause is being classified (the checks that follow only explain the block). */
    public static void diagnosis(boolean on) {
        final SimulationTracer t = active();
        if (t != null) t.probeDiagnosis(on);
    }

    /** QKD admission of a candidate (pre-commit): result and message. */
    public static void qkd(String what, Circuit c, boolean ok, String message) {
        final SimulationTracer t = active();
        if (t != null) t.probeQkd(what, c, ok, message);
    }

    /** One placement tried for a new PICh: link, core, band, modulation and verdict (0 = accepted). */
    public static void pich(network.Link link, int core, int[] band, Modulation mod, int verdict) {
        final SimulationTracer t = active();
        if (t != null) t.probePich(link, core, band, mod, verdict);
    }

    /** QCh bands evaluated for the request (link, core, band). */
    public static void qchTried(java.util.List<Object[]> tried) {
        final SimulationTracer t = active();
        if (t != null && tried != null) t.probeQchTried(tried);
    }
}
