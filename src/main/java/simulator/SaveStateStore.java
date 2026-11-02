package simulator;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import simulationControl.parsers.SimulationRequest;
import simulator.eventListeners.ArriveRequestForConexionListener;

/**
 * Parte 45: save states of an experiment (option {@code simulation.saveState}).
 *
 * <pre>
 * "saveState": { "enabled": true, "intervalMinutes": 10 }
 * </pre>
 *
 * <p>Every simulation (load point x replication) writes {@code <caso>/savestate/lp<L>_rep<R>.state} every
 * {@code intervalMinutes} of execution and once more when it finishes. A state is the whole simulation (mesh,
 * spectrum, active circuits, QKD sessions, random generators, measurements) plus the pending events, written with
 * Java serialization (gzip) between two events. When the same case is run again:</p>
 * <ul>
 *   <li>a finished state is loaded and the simulation is not run again (its results are reused);</li>
 *   <li>an unfinished state is loaded and the simulation continues from the saved event;</li>
 *   <li>a state written with other input files (network, traffic, physical layers, others or simulation, except
 *       {@code threads} and {@code saveState}) is ignored, because the hash of the inputs is stored with it.</li>
 * </ul>
 * <p>The random generators, the event list and the counters are restored exactly, so a resumed experiment gives the
 * same results as an uninterrupted one (checked by the regression test). The cost metric (AlgorithmCost) only
 * counts the part run after the last resume. The folder {@code savestate} is not deleted automatically.</p>
 */
public final class SaveStateStore {

    /** Folder of the states inside the case folder. */
    public static final String FOLDER = "savestate";

    /** Contents of one state file. */
    public static final class State implements Serializable {
        private static final long serialVersionUID = 1L;
        String inputHash;
        boolean finished;
        int circuitCounter;
        Simulation simulation;
        EventMachine eventMachine;
        ArriveRequestForConexionListener arriveListener;

        public boolean isFinished() { return finished; }

        public Simulation getSimulation() { return simulation; }
    }

    /**
     * Stands for the {@link simulationControl.Util} shared by all simulations of the experiment. That object holds
     * the pairs of the mesh of simulation (0, 0), so writing it would copy another simulation into every state and,
     * with several threads, read sets that the other simulation is changing (ConcurrentModificationException). It is
     * written as this marker and read back as the live object of the current run.
     */
    private static final class SharedUtil implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private final File dir;
    private final String inputHash;
    private final long intervalNanos;

    private SaveStateStore(File dir, String inputHash, long intervalNanos) {
        this.dir = dir;
        this.inputHash = inputHash;
        this.intervalNanos = intervalNanos;
    }

    /** Store of a simulation request, or {@code null} when save states are off. */
    public static SaveStateStore forRequest(SimulationRequest sr) {
        final simulationControl.parsers.SimulationConfig.SaveState cfg = sr.getSimulationConfig().getSaveState();
        if (cfg == null || !cfg.isEnabled()) {
            return null;
        }
        final File caseDir = new File(sr.getPath(), sr.getName());
        final long interval = (long) (Math.max(0.0, cfg.getIntervalMinutes()) * 60e9);
        return new SaveStateStore(new File(caseDir, FOLDER), inputHash(sr), interval);
    }

    public long getIntervalNanos() {
        return intervalNanos;
    }

    private File file(int loadPoint, int replication) {
        return new File(dir, "lp" + loadPoint + "_rep" + replication + ".state");
    }

    /**
     * Stack of the thread that reads or writes a state. Java serialization is recursive and the object graph of a
     * large network (USA, many circuits) overflows the default stack of 512 KB-1 MB.
     */
    static final long STACK_BYTES = 1L << 30;

    /** Runs {@code task} in a thread with a large stack and waits for it. */
    private static <T> T withLargeStack(final java.util.concurrent.Callable<T> task) throws Exception {
        final Object[] result = new Object[1];
        final Throwable[] error = new Throwable[1];
        final Thread t = new Thread(null, new Runnable() {
            @Override
            public void run() {
                try {
                    result[0] = task.call();
                } catch (Throwable e) {
                    error[0] = e;
                }
            }
        }, "snets-savestate", STACK_BYTES);
        t.start();
        t.join();
        if (error[0] instanceof Exception) throw (Exception) error[0];
        if (error[0] != null) throw new IllegalStateException(error[0]);
        @SuppressWarnings("unchecked")
        final T r = (T) result[0];
        return r;
    }

    /** Saved state of a simulation, or {@code null} (absent, unreadable or written with other inputs). */
    public State load(final int loadPoint, final int replication, final simulationControl.Util sharedUtil) {
        try {
            return withLargeStack(new java.util.concurrent.Callable<State>() {
                @Override
                public State call() {
                    return loadInThisThread(loadPoint, replication, sharedUtil);
                }
            });
        } catch (Exception e) {
            System.out.println("Save state lp" + loadPoint + "_rep" + replication + " ignorado: " + e);
            return null;
        }
    }

    private State loadInThisThread(int loadPoint, int replication, final simulationControl.Util sharedUtil) {
        final File f = file(loadPoint, replication);
        if (!f.isFile()) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(
                new FileInputStream(f)))) {
            {
                enableResolveObject(true);
            }

            @Override
            protected Object resolveObject(Object o) {
                return o instanceof SharedUtil ? sharedUtil : o;
            }
        }) {
            final State s = (State) in.readObject();
            if (!inputHash.equals(s.inputHash)) {
                System.out.println("Save state " + f.getName() + " ignorado: arquivos de entrada diferentes.");
                return null;
            }
            ensureCircuitCounter(s.circuitCounter);
            return s;
        } catch (Throwable e) {
            System.out.println("Save state " + f.getName() + " ignorado: " + e);
            return null;
        }
    }

    /** Writes a state atomically (temporary file, then rename), in a thread with a large stack. */
    public void save(final Simulation simulation, final EventMachine eventMachine,
            final ArriveRequestForConexionListener listener, final boolean finished) {
        try {
            withLargeStack(new java.util.concurrent.Callable<Object>() {
                @Override
                public Object call() {
                    saveInThisThread(simulation, eventMachine, listener, finished);
                    return null;
                }
            });
        } catch (Exception e) {
            System.out.println("Nao foi possivel gravar o save state: " + e);
        }
    }

    private void saveInThisThread(Simulation simulation, EventMachine eventMachine,
            ArriveRequestForConexionListener listener, boolean finished) {
        final State s = new State();
        s.inputHash = inputHash;
        s.finished = finished;
        s.circuitCounter = circuitCounter();
        s.simulation = simulation;
        s.eventMachine = finished ? null : eventMachine;
        s.arriveListener = finished ? null : listener;
        final File target = file(simulation.getLoadPoint(), simulation.getReplication());
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("cannot create " + dir);
            }
            final File tmp = new File(dir, target.getName() + ".tmp");
            final simulationControl.Util sharedUtil = simulation.getUtil();
            final SharedUtil marker = new SharedUtil();
            try (ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(new BufferedOutputStream(
                    new FileOutputStream(tmp)))) {
                {
                    enableReplaceObject(true);
                }

                @Override
                protected Object replaceObject(Object o) {
                    return o != null && o == sharedUtil ? marker : o;
                }
            }) {
                out.writeObject(s);
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable e) {
            System.out.println("Nao foi possivel gravar o save state " + target.getName() + ": " + e);
        }
    }

    // ------------------------------------------------------------------ inputs hash and circuit ids

    /** SHA-256 of the input files that define the results (threads and saveState excluded). */
    static String inputHash(SimulationRequest sr) {
        final Gson gson = new Gson();
        final StringBuilder sb = new StringBuilder();
        sb.append(gson.toJson(sr.getNetworkConfig())).append('|');
        sb.append(gson.toJson(sr.getTrafficConfig())).append('|');
        sb.append(gson.toJson(sr.getPhysicalLayerConfig())).append('|');
        if (sr.getPhysicalLayerConfig() != null && sr.getPhysicalLayerConfig().getQkdPhysicalLayer() != null) {
            sb.append(gson.toJson(sr.getPhysicalLayerConfig().getQkdPhysicalLayer()));
        }
        sb.append('|').append(gson.toJson(sr.getOthersConfig())).append('|');
        final JsonElement sim = gson.toJsonTree(sr.getSimulationConfig());
        if (sim.isJsonObject()) {
            final JsonObject o = sim.getAsJsonObject();
            o.remove("threads");
            o.remove("saveState");
        }
        sb.append(gson.toJson(sim));
        try {
            final byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder();
            for (byte x : d) hex.append(String.format("%02x", x));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Current value of the global circuit-id counter (network.Circuit.quantity). */
    static int circuitCounter() {
        try {
            final java.lang.reflect.Field f = network.Circuit.class.getDeclaredField("quantity");
            f.setAccessible(true);
            return f.getInt(null);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Makes new circuit ids larger than every id of a loaded state (ids must stay unique). */
    static synchronized void ensureCircuitCounter(int atLeast) {
        try {
            final java.lang.reflect.Field f = network.Circuit.class.getDeclaredField("quantity");
            f.setAccessible(true);
            if (f.getInt(null) < atLeast) f.setInt(null, atLeast);
        } catch (Exception e) {
            // field not found: ids of new circuits could repeat ids of the loaded state
        }
    }
}
