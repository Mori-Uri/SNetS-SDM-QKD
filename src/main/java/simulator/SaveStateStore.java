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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
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
 * counts the part run after the last resume.</p>
 *
 * <p>Parte 52:</p>
 * <ul>
 *   <li>save states are on by default (no {@code saveState} block = on, every 10 min); {@code "enabled": false}
 *       turns them off;</li>
 *   <li>stop on request: the file {@code savestate/STOP} (written by the launcher when "Cancelar" is pressed),
 *       Ctrl+C or closing the console make every simulation save its state between two events and stop; the
 *       process ends with exit code 3 and writes no results. A power cut cannot be caught: the last periodic save
 *       is kept;</li>
 *   <li>{@code savestate/progresso.json} tells the launcher what is saved (finished, partial or pending
 *       simulations, requests already processed);</li>
 *   <li>the inputs hash also covers the simulator jar, so a state of another build is not resumed;</li>
 *   <li>when the whole case finishes and the results are written, the folder {@code savestate} is deleted
 *       ({@code "keepAfterFinish": true} keeps it).</li>
 * </ul>
 */
public final class SaveStateStore {

	/** Folder of the states inside the case folder. */
	public static final String FOLDER = "savestate";

	/** Parte 52: progress summary read by the launcher. */
	public static final String PROGRESS_FILE = "progresso.json";

	/** Parte 52: stop request written by the launcher. */
	public static final String STOP_FILE = "STOP";

	/** Parte 52: exit code of a run stopped on request (the progress is saved). */
	public static final int EXIT_STOPPED = 3;

	// ------------------------------------------------------------------ Parte 52: stop on request

	/** Thrown between two events when a stop was requested (after the state is saved). */
	public static final class Stopped extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public Stopped() {
			super("stop requested");
		}

		public Stopped(String msg) {
			super(msg);
		}
	}

	private static volatile boolean stopRequested;
	private static volatile boolean exitHandled;
	private static final AtomicInteger ACTIVE = new AtomicInteger();
	private static volatile SaveStateStore current;
	private static boolean hookInstalled;

	public static boolean isStopRequested() {
		return stopRequested;
	}

	/** Asks every running simulation to save and stop between two events. */
	public static void requestStop(String why) {
		if (!stopRequested) {
			stopRequested = true;
			System.out.println("[snets] parada solicitada (" + why + "): gravando o estado das simulacoes...");
			System.out.flush();
		}
	}

	/** The process is ending on its own terms (results written, or the stop already handled). */
	public static void markExitHandled() {
		exitHandled = true;
	}

	static void enter() {
		ACTIVE.incrementAndGet();
	}

	static void leave() {
		ACTIVE.decrementAndGet();
	}

	private static synchronized void installHook() {
		if (hookInstalled) return;
		hookInstalled = true;
		// Ctrl+C, closing the console window, a normal kill: save and stop the running simulations (a few seconds)
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				if (exitHandled || ACTIVE.get() == 0) return;
				requestStop("encerramento do processo");
				final long limit = System.currentTimeMillis() + 25000L;
				while (ACTIVE.get() > 0 && System.currentTimeMillis() < limit) {
					try {
						Thread.sleep(100L);
					} catch (InterruptedException e) {
						return;
					}
				}

				final SaveStateStore s = current;

				if (s != null) s.writeProgress("stopped");
			}

		}, "snets-savestate-hook"));
	}

	private void startWatcher() {
		final File stop = new File(dir, STOP_FILE);

		if (stop.isFile() && !stop.delete()) {
			System.out.println("Nao foi possivel apagar " + stop + " (pedido de parada antigo)");
		}

		final Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				while (!stopRequested) {
					if (stop.isFile()) {
						requestStop("pedido do launcher");

						return;
					}

					try {
						Thread.sleep(500L);
					} catch (InterruptedException e) {
						return;
					}
				}
			}

		}, "snets-savestate-stop");
		t.setDaemon(true);
		t.start();
	}

	// ------------------------------------------------------------------ Parte 52: progress summary

	private final Map<String, Map<String, Object>> progress = new TreeMap<String, Map<String, Object>>();
	private int totalSimulations;
	private double requestsPerSimulation;
	private final Map<String, Object> inputFiles = new LinkedHashMap<String, Object>();
	private boolean keepAfterFinish;
	private long startedAt;

	private static String key(int loadPoint, int replication) {
		return "lp" + loadPoint + "_rep" + replication;
	}

	/** Called before the simulations start: every simulation is pending until a state is loaded or saved. */
	public synchronized void begin(List<List<Simulation>> simulations, double requests) {
		totalSimulations = 0;

		for (List<Simulation> lp : simulations) {
			for (Simulation s : lp) {
				totalSimulations++;
				final String k = key(s.getLoadPoint(), s.getReplication());

				if (!progress.containsKey(k)) {
					final Map<String, Object> e = new LinkedHashMap<String, Object>();
					e.put("loadPoint", s.getLoadPoint());
					e.put("replication", s.getReplication());
					e.put("state", "pending");
					e.put("requests", 0.0);
					progress.put(k, e);
				}
			}
		}

		requestsPerSimulation = requests;
		startedAt = System.currentTimeMillis();
		current = this;
		installHook();
		startWatcher();
		writeProgress("running");
	}

	private synchronized void record(int loadPoint, int replication, boolean finished, double requests) {
		final Map<String, Object> e = new LinkedHashMap<String, Object>();
		e.put("loadPoint", loadPoint);
		e.put("replication", replication);
		e.put("state", finished ? "finished" : "partial");
		e.put("requests", requests);
		e.put("savedAt", System.currentTimeMillis());
		progress.put(key(loadPoint, replication), e);
	}

	/** Writes savestate/progresso.json (status: running, stopped or finished). */
	public synchronized void writeProgress(String status) {
		try {
			if (!dir.isDirectory() && !dir.mkdirs()) return;
			int finished = 0, partial = 0;
			double reqs = 0.0;

			for (Map<String, Object> e : progress.values()) {
				final Object st = e.get("state");

				if ("finished".equals(st)) finished++;
				else if ("partial".equals(st)) partial++;
				final Object r = e.get("requests");

				if (r instanceof Number) reqs += "finished".equals(st) ? requestsPerSimulation : ((Number) r).doubleValue();
			}

			final Map<String, Object> m = new LinkedHashMap<String, Object>();
			m.put("version", 1);
			m.put("status", status);
			m.put("updated", System.currentTimeMillis());
			m.put("started", startedAt);
			m.put("totalSimulations", totalSimulations);
			m.put("finishedSimulations", finished);
			m.put("partialSimulations", partial);
			m.put("requestsPerSimulation", requestsPerSimulation);
			m.put("fraction", totalSimulations > 0 && requestsPerSimulation > 0
					? Math.min(1.0, reqs / (totalSimulations * requestsPerSimulation)) : 0.0);
			m.put("intervalMinutes", intervalNanos / 60e9);
			m.put("inputHash", inputHash);
			m.put("simulatorJar", simulatorJarName());
			m.put("simulatorBuild", simulatorBuild().length() > 12 ? simulatorBuild().substring(0, 12) : simulatorBuild());
			m.put("inputFiles", inputFiles);
			m.put("simulations", new java.util.ArrayList<Object>(progress.values()));
			final File tmp = new File(dir, PROGRESS_FILE + ".tmp");
			Files.write(tmp.toPath(), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(m)
					.getBytes(StandardCharsets.UTF_8));
			Files.move(tmp.toPath(), new File(dir, PROGRESS_FILE).toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (Throwable e) {
			System.out.println("Nao foi possivel gravar " + PROGRESS_FILE + ": " + e);
		}
	}

	private volatile boolean caseFinished;

	/**
     * Every simulation of the case finished. The results are written by Main after this call, so the states are
     * deleted when the process ends, and only if the result files were written during this run.
     */
	public void caseFinished() {
		caseFinished = true;
		exitHandled = true;
		writeProgress("finished");
		final SaveStateStore self = this;
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				final File caseDir = dir.getParentFile();
				final File[] res = caseDir == null ? null : caseDir.listFiles();
				boolean written = false;

				if (res != null) {
					for (File f : res) {
						if (f.isFile() && f.getName().endsWith("_BlockingProbability.csv") && f.lastModified() >= startedAt - 2000L) written = true;
					}
				}

				if (written) self.finishCase();
			}

		}, "snets-savestate-clean"));
	}

	/** The case finished and its results are written: the states are deleted (or kept, keepAfterFinish). */
	public synchronized void finishCase() {
		exitHandled = true;

		if (keepAfterFinish) {
			writeProgress("finished");

			return;
		}

		final File[] files = dir.listFiles();

		if (files != null) {
			for (File f : files) {
				if (f.isFile() && !f.delete()) System.out.println("Nao foi possivel apagar " + f);
			}
		}

		if (dir.isDirectory() && !dir.delete()) System.out.println("Nao foi possivel apagar " + dir);
	}

	public File getDir() {
		return dir;
	}

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

	/** Store of a simulation request, or {@code null} when save states are off (Parte 52: on by default). */
	public static SaveStateStore forRequest(SimulationRequest sr) {
		final simulationControl.parsers.SimulationConfig.SaveState cfg = sr.getSimulationConfig().getSaveState();

		if (cfg != null && !cfg.isEnabled()) {
			return null;
		}

		final File caseDir = new File(sr.getPath(), sr.getName());
		final double minutes = cfg == null ? 10.0 : cfg.getIntervalMinutes();
		final long interval = (long) (Math.max(0.0, minutes) * 60e9);
		final SaveStateStore st = new SaveStateStore(new File(caseDir, FOLDER), inputHash(sr), interval);
		st.keepAfterFinish = cfg != null && cfg.isKeepAfterFinish();
		final File[] files = caseDir.listFiles();

		if (files != null) {
			java.util.Arrays.sort(files);

			for (File f : files) {
				final String n = f.getName().toLowerCase();

				if (f.isFile() && !n.endsWith(".csv") && !n.endsWith(".txt")) st.inputFiles.put(f.getName(), f.lastModified());
			}
		}

		return st;
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
			record(loadPoint, replication, s.finished, s.simulation.getMeasurements().getNumGeneratedReq());

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
			record(simulation.getLoadPoint(), simulation.getReplication(), finished,
					simulation.getMeasurements().getNumGeneratedReq());
			writeProgress(stopRequested ? "stopping" : "running");
		} catch (Throwable e) {
			System.out.println("Nao foi possivel gravar o save state " + target.getName() + ": " + e);
		}
	}

	// ------------------------------------------------------------------ inputs hash and circuit ids

	/**
	 * SHA-256 of the input JSON files defining a simulation. Hash the parsed JSON
	 * trees, NOT a Gson reflective serialization of mutable config objects.
	 *
	 * Some SNetS configurations are enriched after parsing (for instance QKD
	 * physical layers and core reservations). Reflectively traversing the live
	 * object graph here is unnecessary and can enter a recursive object graph;
	 * the exact source files are the authoritative simulation inputs.
	 *
	 * The "threads" and "saveState" execution controls are deliberately excluded,
	 * so changing concurrency/checkpoint frequency still permits resuming a run.
	 * simulatorBuild() continues to bind a checkpoint to its compiled simulator.
	 */
	static String inputHash(SimulationRequest sr) {
		if (sr == null || sr.getPath() == null || sr.getName() == null)
			throw new IllegalArgumentException("inputHash requires a simulation request with a case folder");
		final File caseDir = new File(sr.getPath(), sr.getName());
		final Gson gson = new Gson();
		final StringBuilder sb = new StringBuilder();

		appendInputJson(sb, gson, caseDir, "network", false);
		appendInputJson(sb, gson, caseDir, "traffic", false);
		appendInputJson(sb, gson, caseDir, "physicalLayer", false);
		// The independent QKD physical file is a first-class physical input.
		appendInputJson(sb, gson, caseDir,
				qkd.physical.QKDPhysicalLayerConfig.FILE_NAME, false);
		appendInputJson(sb, gson, caseDir, "others", false);
		appendInputJson(sb, gson, caseDir, "simulation", true);
		sb.append("build|").append(simulatorBuild());
		try {
			final byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
			final StringBuilder hex = new StringBuilder();
			for (byte value : digest) hex.append(String.format("%02x", value));
			return hex.toString();
		} catch (Exception e) {
			throw new IllegalStateException("Cannot compute simulation input SHA-256", e);
		}
	}

	/** Match SimulationFileManager: .json wins over the extensionless filename. */
	private static void appendInputJson(StringBuilder sb, Gson gson, File dir,
			String filename, boolean executionSettings) {
		File file = new File(dir, filename + ".json");
		if (!file.isFile()) file = new File(dir, filename);
		sb.append(filename).append('|');
		if (!file.isFile()) {
			sb.append("<absent>|");
			return;
		}
		try {
			final String data = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			final JsonElement parsed = gson.fromJson(data, JsonElement.class);
			if (executionSettings && parsed != null && parsed.isJsonObject()) {
				final JsonObject sim = parsed.getAsJsonObject();
				sim.remove("threads");
				sim.remove("saveState");
			}
			sb.append(gson.toJson(parsed)).append('|');
		} catch (IOException e) {
			throw new IllegalStateException("Cannot read simulation input for checkpoint hash: " + file, e);
		} catch (RuntimeException e) {
			throw new IllegalStateException("Invalid simulation JSON for checkpoint hash: " + file, e);
		}
	}

	/** Parte 52: file name of the simulator jar (the launcher runs a copy named name_size_date.jar). */
	static String simulatorJarName() {
		try {
			return new File(SaveStateStore.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getName();
		} catch (Throwable e) {
			return "";
		}
	}

	private static String buildHash;

	/** Parte 52: SHA-256 of the simulator jar (empty when running from a classes folder). */
	static synchronized String simulatorBuild() {
		if (buildHash != null) return buildHash;
		buildHash = "";

		try {
			final File jar = new File(SaveStateStore.class.getProtectionDomain().getCodeSource().getLocation().toURI());

			if (jar.isFile()) {
				final MessageDigest md = MessageDigest.getInstance("SHA-256");
				try (java.io.InputStream in = new BufferedInputStream(new FileInputStream(jar))) {
					final byte[] buf = new byte[1 << 16];
					int n;
					while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
				}

				final StringBuilder hex = new StringBuilder();

				for (byte x : md.digest()) hex.append(String.format("%02x", x));
				buildHash = hex.toString();
			}

		} catch (Throwable e) {
			buildHash = "";
		}

		return buildHash;
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
