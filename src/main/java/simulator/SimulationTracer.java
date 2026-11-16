package simulator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;
import network.Node;
import request.RequestForConnection;

/**
 * Parte 66: event trace of one simulation for the simulation viewer of the launcher ("Visualizador" tab).
 *
 * <p>Turned on by the block {@code "trace"} of the simulation file. Only the chosen load point and replication
 * record, and only the arrivals {@code firstRequest} to {@code firstRequest + maxRequests - 1} (counted from the start
 * of the simulation, transient included) and the departures between them. At the first recorded arrival the
 * requests already in the network are written as a snapshot, so the viewer starts from the real state.</p>
 *
 * <p>The tracer only reads the state after each decision (route, modulation, core, slots, SNR and XT already in the
 * circuit, cause of the block already classified by the blocking recorder, QKD session already admitted). Nothing is
 * computed again and nothing is changed: the results are the same with and without the trace. The file is written
 * when the window ends (or the simulation ends) in {@code <case>/visualizador/<case>_lp<L>_rep<R>.json}. The tracer is
 * not saved in the save states: a run resumed before the window ended writes no trace.</p>
 */
public final class SimulationTracer {

    private final File out;
    private final String caseName;
    private final int loadPoint;
    private final int replication;
    private final int first;
    private final int max;

    private int arrivals;
    private boolean started;
    private boolean done;
    private int nextId;
    private final IdentityHashMap<RequestForConnection, Integer> ids = new IdentityHashMap<RequestForConnection, Integer>();
    private final IdentityHashMap<RequestForConnection, Integer> attempts = new IdentityHashMap<RequestForConnection, Integer>();
    private final List<Object> events = new ArrayList<Object>();
    private List<Object> snapshot;
    private Map<String, Object> header;
    private double startTime = Double.NaN;
    private double endTime = Double.NaN;

    public SimulationTracer(File caseDir, String caseName, int loadPoint, int replication, int firstRequest, int maxRequests) {
        this(caseDir, caseName, loadPoint, replication, firstRequest, maxRequests, true, 300);
    }

    /** Parte 66: with the internal attempts of the algorithms (up to maxAttempts per request). */
    public SimulationTracer(File caseDir, String caseName, int loadPoint, int replication, int firstRequest, int maxRequests,
            boolean internalAttempts, int maxAttempts) {
        this.internal = internalAttempts;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.caseName = caseName;
        this.loadPoint = loadPoint;
        this.replication = replication;
        this.first = Math.max(0, firstRequest);
        this.max = Math.max(1, Math.min(maxRequests, 50000));
        this.out = new File(new File(caseDir, "visualizador"), caseName + "_lp" + loadPoint + "_rep" + replication + ".json");
    }

    /** True while the current arrival is inside the window. */
    private boolean recording() {
        return started && !done;
    }

    // ------------------------------------------------------------------ hooks (listeners)

    /** Before the control plane handles an arrival. */
    public void beforeArrival(RequestForConnection r, double timeHours, ControlPlane cp) {
        if (done) return;
        cp.clearTraceVictim();
        cp.getMesh().getPhysicalLayer().getCrosstalk().clearTraceVictim();
        if (!started && arrivals == first) {
            started = true;
            startTime = timeHours;
            header = header(cp);
            snapshot = snapshot(cp);
        }
        if (started && internal) {
            current = r;
            steps.clear();
            omitted = 0;
            diag = false;
            TraceProbe.begin(this);
        }
    }

    /** After the control plane handled an arrival. */
    public void afterArrival(RequestForConnection r, double timeHours, boolean success, ControlPlane cp) {
        TraceProbe.end();
        current = null;
        if (done) return;
        if (recording()) {
            final int id = idOf(r);
            final Map<String, Object> e = new LinkedHashMap<String, Object>();
            e.put("k", "req");
            e.put("t", round(timeHours, 6));
            e.put("n", Integer.valueOf(arrivals));
            e.put("ok", Boolean.valueOf(success));
            // a request handled again (versions with retrials): number of the attempt
            final Integer prev = attempts.get(r);
            final int attempt = prev == null ? 1 : prev.intValue() + 1;
            attempts.put(r, Integer.valueOf(attempt));
            if (attempt > 1) e.put("try", Integer.valueOf(attempt));
            request(e, r, id, cp, success);
            if (!success) victim(e, cp);
            if (!steps.isEmpty()) {
                e.put("a", new ArrayList<Object>(steps));
                if (omitted > 0) e.put("aMore", Integer.valueOf(omitted));
            }
            steps.clear();
            if (success) {
                // the request leaves the network at this time
                e.put("end", round(r.getTimeOfFinalizeHours(), 6));
            }
            // a blocked request keeps its id: a new attempt of the same request (versions with retrials) is linked to it
            events.add(e);
            endTime = timeHours;
        }
        arrivals++;
        if (started && arrivals >= first + max) finish("window");
    }

    /** A request leaves the network (before its resources are freed). */
    public void departure(RequestForConnection r, double timeHours) {
        if (!recording()) return;
        final Integer id = ids.remove(r);
        if (id == null) return;
        final Map<String, Object> e = new LinkedHashMap<String, Object>();
        e.put("k", "rel");
        e.put("t", round(timeHours, 6));
        e.put("r", id);
        events.add(e);
        endTime = timeHours;
    }

    /** End of the simulation: writes what was recorded (if the window was not complete yet). */
    public void simulationFinished() {
        if (started && !done) finish("simulation end");
    }

    // ------------------------------------------------------------------ internal attempts (TraceProbe)

    private final boolean internal;
    private final int maxAttempts;
    private RequestForConnection current;
    private final List<Object> steps = new ArrayList<Object>();
    private int omitted;
    private boolean diag;

    private boolean isCandidate(Circuit c) {
        if (c == null || current == null || c instanceof network.SectionedCircuit || c.getRequests() == null) return false;
        for (RequestForConnection x : c.getRequests()) if (x == current) return true;
        return false;
    }

    private Map<String, Object> step(String kind) {
        if (steps.size() >= maxAttempts) {
            omitted++;
            return null;
        }
        final Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("k", kind);
        if (diag) m.put("diag", Boolean.TRUE);
        steps.add(m);
        return m;
    }

    private static void candidate(Map<String, Object> m, Route route, Modulation mod, int core, int[] band) {
        if (route != null) m.put("route", nodes(route));
        if (mod != null) m.put("mod", mod.getName());
        if (core >= 0) m.put("core", Integer.valueOf(core));
        if (band != null) m.put("band", new int[] {band[0], band[1]});
    }

    void probeCheck(String kind, Circuit c, Route route, Modulation mod, int core, int[] band, boolean ok, double value,
            Circuit victim) {
        if (!isCandidate(c)) return;
        final Map<String, Object> m = step(kind);
        if (m == null) return;
        m.put("ok", Boolean.valueOf(ok));
        if (Double.isFinite(value)) m.put("v", round(value, 3));
        candidate(m, route, mod, core, band);
        if (victim != null && !ok) m.put("victim", victim.getId());
    }

    void probeCall(String component, String method, Object result, Object[] args) {
        Circuit c = null;
        if (args != null) for (Object a : args) if (a instanceof Circuit && isCandidate((Circuit) a)) { c = (Circuit) a; break; }
        if (c == null && !(result instanceof List)) return;
        final Map<String, Object> m = step("call");
        if (m == null) return;
        m.put("c", component);
        m.put("m", method);
        if (result instanceof Boolean) m.put("ok", result);
        else if (result instanceof Modulation) m.put("res", ((Modulation) result).getName());
        else if (result instanceof List) {
            final List<Object> routes = new ArrayList<Object>();
            for (Object o : (List<?>) result) {
                if (o instanceof Route && routes.size() < 8) routes.add(nodes((Route) o));
            }
            if (!routes.isEmpty()) m.put("routes", routes);
        }
        if (args != null) for (Object a : args) if (a instanceof Integer) { m.put("slots", a); break; }
        if (c != null) candidate(m, c.getRoute(), c.getModulation(), qkd.metrics.BlockingDetailRecorder.rawCore(c), c.getSpectrumAssigned());
    }

    void probeDiagnosis(boolean on) {
        diag = on;
    }

    void probeQkd(String what, Circuit c, boolean ok, String message) {
        if (c != null && !isCandidate(c)) return;
        final Map<String, Object> m = step(what);
        if (m == null) return;
        m.put("ok", Boolean.valueOf(ok));
        if (message != null) m.put("msg", message);
        if (c != null) candidate(m, c.getRoute(), c.getModulation(), qkd.metrics.BlockingDetailRecorder.rawCore(c), c.getSpectrumAssigned());
    }

    void probePich(Link link, int core, int[] band, Modulation mod, int verdict) {
        final Map<String, Object> m = step("PICh");
        if (m == null) return;
        m.put("ok", Boolean.valueOf(verdict == 0));
        m.put("link", linkId(link));
        m.put("core", Integer.valueOf(core));
        m.put("band", new int[] {band[0], band[1]});
        if (mod != null) m.put("mod", mod.getName());
        if (verdict != 0) m.put("why", verdict == 1 ? "QoT" : verdict == 2 ? "classical" : verdict == 3 ? "qch" : "spectrum");
    }

    void probeQchTried(List<Object[]> tried) {
        if (tried.isEmpty()) return;
        final Map<String, Object> m = step("QCh");
        if (m == null) return;
        final List<Object> bands = new ArrayList<Object>();
        for (Object[] t : tried) {
            if (t == null || t.length < 3 || !(t[0] instanceof Link) || !(t[2] instanceof int[])) continue;
            final int[] b = (int[]) t[2];
            bands.add(new Object[] {linkId((Link) t[0]), t[1], Integer.valueOf(b[0]), Integer.valueOf(b[1])});
            if (bands.size() >= 40) break;
        }
        m.put("bands", bands);
        m.put("n", Integer.valueOf(tried.size()));
    }

    // ------------------------------------------------------------------ records

    private int idOf(RequestForConnection r) {
        Integer id = ids.get(r);
        if (id == null) {
            id = Integer.valueOf(nextId++);
            ids.put(r, id);
        }
        return id.intValue();
    }

    private void request(Map<String, Object> e, RequestForConnection r, int id, ControlPlane cp, boolean success) {
        e.put("r", Integer.valueOf(id));
        if (r.getPair() != null) {
            e.put("s", r.getPair().getSource().getName());
            e.put("d", r.getPair().getDestination().getName());
        }
        e.put("gbps", round(r.getRequiredBitRate() / 1e9, 3));
        final boolean qkd = r.isQkdProtected();
        if (qkd) {
            e.put("q", Boolean.TRUE);
            e.put("keyMbps", round(r.getRequiredQkdKeyRateMbps(), 4));
        }
        final List<Object> cs = new ArrayList<Object>();
        if (r.getCircuits() != null) {
            for (Circuit c : r.getCircuits()) {
                if (!success && !c.isWasBlocked() && cs.size() > 0) continue;
                cs.add(circuit(c));
            }
        }
        e.put("c", cs);
        if (!success) {
            String cause = null;
            if (cp instanceof qkd.control.QKDControlPlane) cause = ((qkd.control.QKDControlPlane) cp).getLastBlockCause();
            else if (cp instanceof qkd.metrics.ObservedClassicalControlPlane) cause = ((qkd.metrics.ObservedClassicalControlPlane) cp).getLastBlockCause();
            if (cause == null && r.getCircuits() != null && !r.getCircuits().isEmpty()) cause = causeName(r.getCircuits().get(0).getBlockCause());
            e.put("cause", cause);
        }
        if (qkd && cp instanceof qkd.control.QKDControlPlane) {
            final qkd.control.QKDControlPlane q = (qkd.control.QKDControlPlane) cp;
            if (success) {
                final qkd.control.QKDActiveConnection conn = q.getActiveQkdConnection(r);
                if (conn != null) e.put("qkd", qkdRecord(conn));
            } else if (q.getLastQkdFailureMessage() != null) {
                e.put("qkdMsg", q.getLastQkdFailureMessage());
            }
        }
    }

    /** QoTO / XTO blocks: the active circuit that would lose its QoT or exceed its XT (first one found by the test). */
    private static void victim(Map<String, Object> e, ControlPlane cp) {
        final Object cause = e.get("cause");
        Circuit v = null;
        Double value = null;
        String kind = null;
        if ("QoTO".equals(cause) && cp.getTraceVictim() != null) {
            v = cp.getTraceVictim();
            value = round(cp.getTraceVictimSnr(), 3);
            kind = "snr";
        } else if ("Crosstalk in other".equals(cause) && cp.getMesh().getPhysicalLayer().getCrosstalk().getTraceVictim() != null) {
            v = cp.getMesh().getPhysicalLayer().getCrosstalk().getTraceVictim();
            value = round(cp.getMesh().getPhysicalLayer().getCrosstalk().getTraceVictimXt(), 3);
            kind = "xt";
        }
        if (v == null) return;
        final Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", v.getId());
        m.put(kind, value);
        if (v.getModulation() != null) {
            m.put("mod", v.getModulation().getName());
            m.put(kind + "Th", round("snr".equals(kind) ? v.getModulation().getSNRthreshold() : v.getModulation().getXTthreshold(), 3));
        }
        if (v instanceof network.SectionedCircuit) m.put("pich", Boolean.TRUE);
        e.put("victim", m);
    }

    private static String causeName(int code) {
        switch (code) {
            case Circuit.BY_LACK_TX: return "Lack of transmitters";
            case Circuit.BY_LACK_RX: return "Lack of receivers";
            case Circuit.BY_FRAGMENTATION: return "Fragmentation";
            case Circuit.BY_QOTN: return "QoTN";
            case Circuit.BY_QOTO: return "QoTO";
            case Circuit.BY_XTN: return "Crosstalk";
            case Circuit.BY_XTO: return "Crosstalk in other";
            default: return "Other";
        }
    }

    private static Map<String, Object> circuit(Circuit c) {
        final Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", c.getId());
        if (c.getRoute() != null) m.put("route", nodes(c.getRoute()));
        final int core = qkd.metrics.BlockingDetailRecorder.rawCore(c);
        if (core >= 0) m.put("core", Integer.valueOf(core));
        final int[] band = c.getSpectrumAssigned();
        if (band != null) m.put("band", new int[] {band[0], band[1]});
        final Modulation mod = c.getModulation();
        if (mod != null) {
            m.put("mod", mod.getName());
            m.put("snrTh", round(mod.getSNRthreshold(), 3));
            m.put("xtTh", round(mod.getXTthreshold(), 3));
        }
        if (band != null) {
            final double snr = c.getSNR();
            if (Double.isFinite(snr)) m.put("snr", round(snr, 3));
            final double xt = c.getXt();
            if (Double.isFinite(xt) && xt > -190.0) m.put("xt", round(xt, 3));
            final double p = c.getLaunchPowerLinear();
            if (Double.isFinite(p) && p > 0.0) m.put("dbm", round(10.0 * Math.log10(p * 1000.0), 3));
        }
        return m;
    }

    private static List<String> nodes(Route route) {
        final List<String> out = new ArrayList<String>();
        for (Node n : route.getNodeList()) out.add(n.getName());
        return out;
    }

    private static String linkId(Link l) {
        return l.getSource().getName() + "-" + l.getDestination().getName();
    }

    private static Map<String, Object> qkdRecord(qkd.control.QKDActiveConnection conn) {
        final Map<String, Object> m = new LinkedHashMap<String, Object>();
        if (conn.getQkdRoute() != null) m.put("route", nodes(conn.getQkdRoute()));
        if (conn.isAlternativeRoute()) m.put("alt", Boolean.TRUE);
        try {
            m.put("skrMbps", round(conn.getMinimumSecretKeyRateBps() / 1e6, 4));
            m.put("qber", round(conn.getMaximumQber(), 6));
        } catch (RuntimeException ignored) {
            // physical result not available
        }
        final qkd.resource.QKDSession s = conn.getQkdSession();
        if (s != null && s.getRoutePlan() != null && s.getRoutePlan().getSpans() != null) {
            final List<Object> spans = new ArrayList<Object>();
            for (qkd.network.QKDSpan sp : s.getRoutePlan().getSpans()) {
                spans.add(new Object[] {sp.getPhysicalLinkSource() + "-" + sp.getPhysicalLinkDestination(),
                        round(sp.getStartKm(), 3), round(sp.getEndKm(), 3)});
            }
            m.put("spans", spans);
        }
        final List<Object> qch = new ArrayList<Object>();
        if (s != null && s.getAllocations() != null) {
            for (qkd.resource.QKDChannelAllocation a : s.getAllocations()) {
                if (a == null || a.getLink() == null || a.getSpectrumBand() == null) continue;
                qch.add(new Object[] {linkId(a.getLink()), Integer.valueOf(a.getQuantumCoreId()),
                        Integer.valueOf(a.getSpectrumBand()[0]), Integer.valueOf(a.getSpectrumBand()[1])});
            }
        }
        m.put("qch", qch);
        final List<Object> pich = new ArrayList<Object>();
        if (conn.getPichShares() != null) {
            for (qkd.capacity.QKDPichPool.Channel ch : conn.getPichShares()) {
                if (ch == null || ch.link == null || ch.band == null) continue;
                final Map<String, Object> p = new LinkedHashMap<String, Object>();
                p.put("link", linkId(ch.link));
                p.put("core", Integer.valueOf(ch.coreId));
                p.put("band", new int[] {ch.band[0], ch.band[1]});
                if (ch.circuit != null) {
                    p.put("id", ch.circuit.getId());
                    if (ch.circuit.getModulation() != null) p.put("mod", ch.circuit.getModulation().getName());
                    p.put("sections", ch.circuit.getSectionsKm());
                    final double snr = ch.circuit.getSNR();
                    if (Double.isFinite(snr)) p.put("snr", round(snr, 3));
                }
                pich.add(p);
            }
        } else if (conn.getPich() != null && conn.getPich().links != null && conn.getPich().band != null) {
            for (Link l : conn.getPich().links) {
                final Map<String, Object> p = new LinkedHashMap<String, Object>();
                p.put("link", linkId(l));
                p.put("core", Integer.valueOf(conn.getPich().coreId));
                p.put("band", new int[] {conn.getPich().band[0], conn.getPich().band[1]});
                pich.add(p);
            }
        }
        m.put("pich", pich);
        return m;
    }

    /** Requests in the network at the start of the window, in the format of an accepted arrival. */
    private List<Object> snapshot(ControlPlane cp) {
        final Map<Integer, Circuit> circuits = new TreeMap<Integer, Circuit>();
        for (Link l : cp.getMesh().getLinkList()) {
            for (Core core : l.getCores()) {
                for (Circuit c : core.getCircuitList()) {
                    if (c instanceof network.SectionedCircuit) continue; // PICh: comes with its QKD session
                    circuits.put(c.getId(), c);
                }
            }
        }
        final List<RequestForConnection> requests = new ArrayList<RequestForConnection>();
        final IdentityHashMap<RequestForConnection, Boolean> seen = new IdentityHashMap<RequestForConnection, Boolean>();
        for (Circuit c : circuits.values()) {
            if (c.getRequests() == null) continue;
            for (RequestForConnection r : c.getRequests()) {
                if (r != null && seen.put(r, Boolean.TRUE) == null) requests.add(r);
            }
        }
        final List<Object> out = new ArrayList<Object>();
        for (RequestForConnection r : requests) {
            final Map<String, Object> e = new LinkedHashMap<String, Object>();
            e.put("k", "req");
            e.put("t", round(r.getTimeOfRequestHours(), 6));
            e.put("ok", Boolean.TRUE);
            request(e, r, idOf(r), cp, true);
            e.put("end", round(r.getTimeOfFinalizeHours(), 6));
            out.add(e);
        }
        return out;
    }

    private Map<String, Object> header(ControlPlane cp) {
        final Map<String, Object> h = new LinkedHashMap<String, Object>();
        h.put("version", Integer.valueOf(1));
        h.put("case", caseName);
        h.put("loadPoint", Integer.valueOf(loadPoint));
        h.put("replication", Integer.valueOf(replication));
        h.put("firstRequest", Integer.valueOf(first));
        h.put("maxRequests", Integer.valueOf(max));
        final List<Object> links = new ArrayList<Object>();
        for (Link l : cp.getMesh().getLinkList()) {
            final Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", linkId(l));
            m.put("a", l.getSource().getName());
            m.put("b", l.getDestination().getName());
            m.put("km", round(l.getDistance(), 3));
            final List<Object> cores = new ArrayList<Object>();
            for (Core c : l.getCores()) {
                final Map<String, Object> cm = new LinkedHashMap<String, Object>();
                cm.put("id", Integer.valueOf(c.getId()));
                cm.put("slots", Integer.valueOf(c.getNumOfSlots()));
                if (c.isQkdReserved()) cm.put("qkd", Boolean.TRUE);
                if (c.isDisabled()) cm.put("off", Boolean.TRUE);
                final List<Integer> adj = new ArrayList<Integer>();
                if (c.getAdjacentCores() != null) for (Core a : c.getAdjacentCores()) adj.add(Integer.valueOf(a.getId()));
                cm.put("adj", adj);
                cores.add(cm);
            }
            m.put("cores", cores);
            links.add(m);
        }
        h.put("links", links);
        final List<Object> mods = new ArrayList<Object>();
        for (Modulation mod : cp.getMesh().getAvaliableModulations()) {
            final Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("name", mod.getName());
            m.put("snrTh", round(mod.getSNRthreshold(), 3));
            m.put("xtTh", round(mod.getXTthreshold(), 3));
            mods.add(m);
        }
        h.put("modulations", mods);
        h.put("qkd", Boolean.valueOf(cp instanceof qkd.control.QKDControlPlane));
        return h;
    }

    private static Double round(double v, int digits) {
        if (!Double.isFinite(v)) return null;
        final double f = Math.pow(10, digits);
        return Double.valueOf(Math.round(v * f) / f);
    }

    // ------------------------------------------------------------------ file

    private void finish(String reason) {
        done = true;
        final Map<String, Object> root = new LinkedHashMap<String, Object>(header);
        root.put("endReason", reason);
        root.put("startTime", round(startTime, 6));
        root.put("endTime", round(endTime, 6));
        root.put("snapshot", snapshot);
        root.put("events", events);
        try {
            out.getParentFile().mkdirs();
            final Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
            final File tmp = new File(out.getParentFile(), out.getName() + ".tmp");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                gson.toJson(root, w);
            }
            if (out.exists() && !out.delete()) {
                System.out.println("[trace] nao foi possivel substituir " + out);
            }
            if (!tmp.renameTo(out)) {
                System.out.println("[trace] nao foi possivel gravar " + out);
                return;
            }
            System.out.println("[trace] registro do visualizador gravado: " + out + " (" + events.size() + " eventos, "
                    + (out.length() / 1024) + " KB)");
        } catch (Exception e) {
            System.out.println("[trace] nao foi possivel gravar o registro do visualizador: " + e);
        }
        events.clear();
        snapshot = null;
        ids.clear();
        attempts.clear();
    }
}
