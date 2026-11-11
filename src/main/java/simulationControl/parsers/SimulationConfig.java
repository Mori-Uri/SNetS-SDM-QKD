package simulationControl.parsers;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Simulation execution/algorithm configuration.
 *
 * <p>QKD-specific execution parameters are kept in the simulation
 * configuration so the legacy traffic matrix remains unchanged. Part 6 added
 * {@code qkdProtectedRequestProbability}; Part 11 adds the key-demand policy
 * ({@code qkdKeyDemand}) used only by QKD-protected requests.</p>
 */
public class SimulationConfig {

	private int requests;
	private int rmlsaType;
	private String routing;
	private String kRouting;
	private String spectrumAssignment;
	private String coreAndSpectrumAssignment;
	private String integratedRmlsa;
	private String modulationSelection;
	private String grooming;
	private String reallocation;
	private String powerAssignment;
	private int loadPoints;
	private int replications;
	private Metrics activeMetrics;

	/** Save states (simulator.SaveStateStore); null means enabled at the default 10-minute interval. */
	private SaveState saveState;

	/** Parte 45: "saveState": { "enabled": true, "intervalMinutes": 10 }. */
	public static class SaveState implements Serializable {
		private static final long serialVersionUID = 1L;
		private boolean enabled = true;
		private double intervalMinutes = 10.0;
		private boolean keepAfterFinish = false;

		public boolean isEnabled() { return enabled; }
		public double getIntervalMinutes() { return intervalMinutes; }
		public boolean isKeepAfterFinish() { return keepAfterFinish; }
	}

	/** Save-state options, or null when the defaults apply. */
	public SaveState getSaveState() { return saveState; }
	private String regeneratorAssignment;
	private int networkType;
	private int threads;

	/** Fraction of generated requests that require QKD protection. */
	private double qkdProtectedRequestProbability;

	/** Optional master seed for arrival/holding traffic. Null preserves legacy behavior. */
	private Long trafficRandomSeed;

	/** Independent stream for CLASSICAL/QKD_PROTECTED classification. */
	private long qkdProtectionRandomSeed;

	/**
     * Key-consumption policy used only by QKD-protected requests.
     * The reference values reproduce the dissertation/article-base setup:
     * AES-256 renewed every 64, 128 or 256 milliseconds by default (expressed in seconds).
     */
	private QKDKeyDemandConfig qkdKeyDemand;

	/**
     * Fraction of the arrivals of each replication whose QKD statistics are
     * discarded as transient (warm-up). null = same rule used by the SNetS
     * classical metrics (10%). Use 0 for semi-static (no-departure) studies.
     */
	private Double qkdWarmupFraction;

	/**
     * QKD scenarios only: process exactly {@code requests} arrivals per
     * replication (see qkd.traffic.QKDArrivalBudget). Default false keeps the
     * original SNetS behaviour.
     */
	private Boolean qkdExactRequestCount;

	public SimulationConfig() {
		activeMetrics = new Metrics();
		threads = 1;
		qkdProtectedRequestProbability = 0.0;
		trafficRandomSeed = null;
		qkdProtectionRandomSeed = 20241010L;
		qkdKeyDemand = new QKDKeyDemandConfig();
	}

	public int getRequests() {
		return requests;
	}

	public void setRequests(int requests) {
		this.requests = requests;
	}

	public int getRmlsaType() {
		return rmlsaType;
	}

	public void setRmlsaType(int rmlsaType) {
		this.rmlsaType = rmlsaType;
	}

	public String getRouting() {
		return routing;
	}

	public void setRouting(String routing) {
		this.routing = routing;
	}

	public String getkRouting() {
		return kRouting;
	}

	public void setkRouting(String kRouting) {
		this.kRouting = kRouting;
	}

	public String getSpectrumAssignment() {
		return spectrumAssignment;
	}

	public String getCoreAndSpectrumAssignment() {
		return coreAndSpectrumAssignment;
	}

	public void setSpectrumAssignment(String spectrumAssignment) {
		this.spectrumAssignment = spectrumAssignment;
	}

	public void setCoreAndSpectrumAssignment(String coreAndSpectrumAssignment) {
		this.coreAndSpectrumAssignment = coreAndSpectrumAssignment;
	}

	public String getIntegratedRmlsa() {
		return integratedRmlsa;
	}

	public void setIntegratedRmlsa(String integratedRmlsa) {
		this.integratedRmlsa = integratedRmlsa;
	}

	public String getModulationSelection() {
		return modulationSelection;
	}

	public void setModulationSelection(String modulationSelection) {
		this.modulationSelection = modulationSelection;
	}

	public String getGrooming() {
		return grooming;
	}

	public void setGrooming(String grooming) {
		this.grooming = grooming;
	}

	public int getLoadPoints() {
		return loadPoints;
	}

	public void setLoadPoints(int loadPoints) {
		this.loadPoints = loadPoints;
	}

	public int getReplications() {
		return replications;
	}

	public void setReplications(int replications) {
		this.replications = replications;
	}

	public Metrics getActiveMetrics() {
		return activeMetrics;
	}

	public void setActiveMetrics(Metrics activeMetrics) {
		this.activeMetrics = activeMetrics;
	}

	public String getRegeneratorAssignment() {
		return regeneratorAssignment;
	}

	public void setRegeneratorAssignment(String regeneratorAssignment) {
		this.regeneratorAssignment = regeneratorAssignment;
	}

	public int getNetworkType() {
		return networkType;
	}

	public void setNetworkType(int networkType) {
		this.networkType = networkType;
	}

	/**
     * Number of worker threads. The launcher can override the value of the
     * simulation file for one execution with -Dsnets.threads=N (used to split
     * the processors among simulations running in parallel); the file itself
     * is not changed.
     */
	public int getThreads() {
		Integer override = Integer.getInteger("snets.threads");

		if (override != null && override.intValue() > 0) {
			return override.intValue();
		}

		return threads;
	}

	public void setThreads(int threads) {
		this.threads = threads;
	}

	public String getReallocation() {
		return reallocation;
	}

	public void setReallocation(String reallocation) {
		this.reallocation = reallocation;
	}

	public String getPowerAssignment() {
		return powerAssignment;
	}

	public void setPowerAssignment(String powerAssignment) {
		this.powerAssignment = powerAssignment;
	}

	public double getQkdProtectedRequestProbability() {
		validateQkdProbability(qkdProtectedRequestProbability);

		return qkdProtectedRequestProbability;
	}

	public void setQkdProtectedRequestProbability(double probability) {
		validateQkdProbability(probability);
		this.qkdProtectedRequestProbability = probability;
	}

	private static void validateQkdProbability(double probability) {
		if (!Double.isFinite(probability)
				|| probability < 0.0
				|| probability > 1.0) {
			throw new IllegalArgumentException(
					"simulation.qkdProtectedRequestProbability must be in [0,1]");
		}
	}

	public Long getTrafficRandomSeed() {
		return trafficRandomSeed;
	}

	public void setTrafficRandomSeed(Long trafficRandomSeed) {
		this.trafficRandomSeed = trafficRandomSeed;
	}

	public long getQkdProtectionRandomSeed() {
		return qkdProtectionRandomSeed;
	}

	public void setQkdProtectionRandomSeed(long qkdProtectionRandomSeed) {
		this.qkdProtectionRandomSeed = qkdProtectionRandomSeed;
	}

	public QKDKeyDemandConfig getQkdKeyDemand() {
		if (qkdKeyDemand == null) {
			qkdKeyDemand = new QKDKeyDemandConfig();
		}

		qkdKeyDemand.validate();

		return qkdKeyDemand;
	}

	public void setQkdKeyDemand(QKDKeyDemandConfig qkdKeyDemand) {
		if (qkdKeyDemand == null) {
			throw new IllegalArgumentException("qkdKeyDemand cannot be null");
		}

		qkdKeyDemand.validate();
		this.qkdKeyDemand = qkdKeyDemand;
	}

	/**
     * QKD key-demand parameters kept in the simulation configuration rather
     * than in the legacy traffic matrix.
     */
	public static class QKDKeyDemandConfig implements Serializable {

		private static final long serialVersionUID = 1L;

		private int keySizeBits;
		/** Canonical field. null means that no unit-specific interval was explicitly supplied. */
		private double[] updateIntervalSeconds;
		/** Legacy microseconds, accepted only when it is the unique interval field. */
		private double[] updateIntervalsMicroseconds;
		/** Correctly spelled legacy milliseconds. */
		private double[] updateIntervalsMilliseconds;
		/** Legacy single-l spelling, interpreted as milliseconds. */
		private double[] updateIntervalsMiliseconds;
		/** Obsolete, rejected explicitly rather than silently ignored by Gson. */
		private double[] updateIntervalsMileseconds;
		private static final double[] DEFAULT_INTERVAL_SECONDS = {0.064, 0.128, 0.256};
		private long randomSeed;

		/**
         * Part 34: how the demand of a protected request is chosen when its generator has no keyRateMbps.
         * "uniform" (default, also when absent): one of the intervals at random. "byBitRate": the demand follows
         * the bit rate of the request (the smallest classical rate gets the smallest key rate, and so on), so the
         * key demands keep the same proportions as the classical bit rates.
         */
		private String mode;
		/** Named literature demand model; explicit values are used only in custom mode. */
		private String profile = "custom";
		/** Keep existing traffic.keyRateMbps override behavior unless explicitly disabled. */
		private boolean preferGeneratorFixedRate = true;
		public String getProfile() { return profile == null ? "custom" : profile.trim(); }
		public void setProfile(String value) { profile = value; }
		public boolean isPreferGeneratorFixedRate() { return preferGeneratorFixedRate; }
		public void setPreferGeneratorFixedRate(boolean value) { preferGeneratorFixedRate = value; }

		/** Part 34, mode "byBitRate": optional explicit table classical bit rate (Gb/s, as text) -> key rate (Mbit/s). */
		private java.util.Map<String, Double> keyRateMbpsByBitRate;

		public QKDKeyDemandConfig() {
			keySizeBits = 256;
			// Referência Moghaddam (2021): 64, 128 e 256 ms em segundos.
			// Keep null: Gson can distinguish an omitted field from an explicitly set one.
			updateIntervalSeconds = null;
			randomSeed = 2024L;
		}

		public int getKeySizeBits() {
			validate();

			return keySizeBits;
		}

		public void setKeySizeBits(int keySizeBits) {
			this.keySizeBits = keySizeBits;
			validate();
		}

		/** Configura diretamente os períodos de renovação em segundos. */
		public double[] getUpdateIntervalSeconds() {
			validate();

			if ("moghaddam2021".equalsIgnoreCase(getProfile()))
				return new double[] {0.064, 0.128, 0.256};
			if ("dibaj2024".equalsIgnoreCase(getProfile()))
				return new double[] {0.000064, 0.000128, 0.000256};
			if (updateIntervalSeconds != null)
				return Arrays.copyOf(updateIntervalSeconds, updateIntervalSeconds.length);
			if (updateIntervalsMicroseconds != null)
				return converted(updateIntervalsMicroseconds, 1.0e-6);
			if (updateIntervalsMilliseconds != null)
				return converted(updateIntervalsMilliseconds, 1.0e-3);
			if (updateIntervalsMiliseconds != null)
				return converted(updateIntervalsMiliseconds, 1.0e-3);
			return Arrays.copyOf(DEFAULT_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS.length);
		}

		private double[] getIntervalsWithoutValidation() {
			if (updateIntervalSeconds != null) return updateIntervalSeconds;
			if (updateIntervalsMicroseconds != null) return converted(updateIntervalsMicroseconds, 1.0e-6);
			if (updateIntervalsMilliseconds != null) return converted(updateIntervalsMilliseconds, 1.0e-3);
			if (updateIntervalsMiliseconds != null) return converted(updateIntervalsMiliseconds, 1.0e-3);
			return DEFAULT_INTERVAL_SECONDS;
		}

		private static double[] converted(double[] values, double scale) {
			double[] seconds = new double[values.length];
			for (int i = 0; i < values.length; i++) seconds[i] = values[i] * scale;
			return seconds;
		}

		/** Named fields are serialized as supplied. Normalization happens at the getter. */
		public void setUpdateIntervalsMicroseconds(double[] values) {
			checkIntervals(values, "updateIntervalsMicroseconds");
			this.updateIntervalsMicroseconds = Arrays.copyOf(values, values.length);
		}
		public void setUpdateIntervalsMilliseconds(double[] values) {
			checkIntervals(values, "updateIntervalsMilliseconds");
			this.updateIntervalsMilliseconds = Arrays.copyOf(values, values.length);
		}
		public void setUpdateIntervalsMiliseconds(double[] values) {
			checkIntervals(values, "updateIntervalsMiliseconds");
			this.updateIntervalsMiliseconds = Arrays.copyOf(values, values.length);
		}

		private static void checkIntervals(double[] values, String field) {
			if (values == null || values.length == 0)
				throw new IllegalArgumentException("simulation.qkdKeyDemand." + field + " must contain positive intervals");
			for (double v : values)
				if (!Double.isFinite(v) || !(v > 0.0))
					throw new IllegalArgumentException("simulation.qkdKeyDemand." + field + " must be finite and > 0");
		}

		public void setUpdateIntervalSeconds(double[] values) {
			if (values == null || values.length == 0) {
				throw new IllegalArgumentException("simulation.qkdKeyDemand.updateIntervalSeconds cannot be null/empty");
			}

			for (double value : values) {
				if (!Double.isFinite(value) || value <= 0.0) {
					throw new IllegalArgumentException("simulation.qkdKeyDemand.updateIntervalSeconds values must be finite and > 0");
				}
			}

			updateIntervalSeconds = Arrays.copyOf(values, values.length);
		}

		public long getRandomSeed() {
			return randomSeed;
		}

		public String getMode() {
			return mode == null || mode.trim().isEmpty() ? "uniform" : mode.trim();
		}

		public void setMode(String mode) {
			this.mode = mode;
		}

		public boolean isByBitRate() {
			return "byBitRate".equalsIgnoreCase(getMode());
		}

		public java.util.Map<String, Double> getKeyRateMbpsByBitRate() {
			return keyRateMbpsByBitRate;
		}

		public void setKeyRateMbpsByBitRate(java.util.Map<String, Double> m) {
			this.keyRateMbpsByBitRate = m;
		}

		public void setRandomSeed(long randomSeed) {
			this.randomSeed = randomSeed;
		}

		public void validate() {
			if (updateIntervalsMileseconds != null)
				throw new IllegalArgumentException(
						"Obsolete QKD interval field: use updateIntervalsMiliseconds instead");
			final String profileName = getProfile();
			if (!"custom".equalsIgnoreCase(profileName)
					&& !"moghaddam2021".equalsIgnoreCase(profileName)
					&& !"dibaj2024".equalsIgnoreCase(profileName))
				throw new IllegalArgumentException("unknown simulation.qkdKeyDemand.profile: " + profileName);
			if (!"custom".equalsIgnoreCase(profileName) && keySizeBits != 256)
				throw new IllegalArgumentException("named QKD key-demand references require keySizeBits=256");
			int supplied = (updateIntervalSeconds == null ? 0 : 1)
					+ (updateIntervalsMicroseconds == null ? 0 : 1)
					+ (updateIntervalsMilliseconds == null ? 0 : 1)
					+ (updateIntervalsMiliseconds == null ? 0 : 1);
			if (supplied > 1) {
				throw new IllegalArgumentException("Ambiguous QKD intervals: supply only one of "
						+ "updateIntervalSeconds, updateIntervalsMicroseconds, "
						+ "updateIntervalsMilliseconds, updateIntervalsMiliseconds");
			}
			if (!"custom".equalsIgnoreCase(profileName) && supplied > 0) {
				// Literature profiles determine their intervals, not the user-supplied list.
				// Retain the historic behaviour for seconds with named profiles, but
				// never silently ignore a newly accepted legacy field.
				if (updateIntervalSeconds == null)
					throw new IllegalArgumentException("Named QKD profiles cannot use legacy interval fields");
			}
			if (updateIntervalsMicroseconds != null) checkIntervals(updateIntervalsMicroseconds, "updateIntervalsMicroseconds");
			if (updateIntervalsMilliseconds != null) checkIntervals(updateIntervalsMilliseconds, "updateIntervalsMilliseconds");
			if (updateIntervalsMiliseconds != null) checkIntervals(updateIntervalsMiliseconds, "updateIntervalsMiliseconds");

			if (keySizeBits <= 0) {
				throw new IllegalArgumentException(
						"simulation.qkdKeyDemand.keySizeBits must be > 0");
			}

			if (updateIntervalSeconds != null) checkIntervals(updateIntervalSeconds, "updateIntervalSeconds");
			// Recheck the converted SI values for underflow to zero.
			final double[] seconds = getIntervalsWithoutValidation();
			checkIntervals(seconds, "effective interval in seconds");

			String m = getMode();

			if (!"uniform".equalsIgnoreCase(m) && !"byBitRate".equalsIgnoreCase(m)) {
				throw new IllegalArgumentException(
						"simulation.qkdKeyDemand.mode must be \"uniform\" or \"byBitRate\" (found \"" + m + "\")");
			}

			if (keyRateMbpsByBitRate != null) {
				for (java.util.Map.Entry<String, Double> e : keyRateMbpsByBitRate.entrySet()) {
					double gb;

					try { gb = Double.parseDouble(e.getKey().trim().replace(',', '.')); }
					catch (NumberFormatException ex) { gb = Double.NaN; }
					if (!(gb > 0) || e.getValue() == null || !(e.getValue() > 0) || e.getValue().isInfinite()) {
						throw new IllegalArgumentException(
								"simulation.qkdKeyDemand.keyRateMbpsByBitRate: \"" + e.getKey() + "\": " + e.getValue()
								+ " (the key must be a bit rate in Gb/s and the value a key rate in Mbit/s, both > 0)");
					}
				}
			}
		}
	}

	public static class Metrics implements Serializable {
		private static final long serialVersionUID = 1L;

		public boolean BlockingProbability;
		public boolean BitRateBlockingProbability;
		public boolean SpectrumUtilization;
		public boolean SpectrumSizeStatistics;
		public boolean ExternalFragmentation;
		public boolean RelativeFragmentation;
		public boolean TransmittersReceiversRegeneratorsUtilization;
		public boolean ModulationUtilization;
		public boolean ConsumedEnergy;
		public boolean GroomingStatistics;
		public boolean DataSetInformation;
		public boolean PhysicalLayerStatistics;

		/** QKD addon (Part 23): QKDMetrics.csv, written only when network.qkd.enabled = true. */
		public boolean QKDMetrics;

		/**
         * Part 27: SlotUtilization.csv, the occupancy of every slot of every core of every link
         * (needs SpectrumUtilization). Turn it off to save disk space in large topologies.
         */
		public boolean SlotUtilization;

		/**
         * Part 29: SlotBlocking.csv, attempts and blocks (by cause) of each slot of each core of each link
         * (needs BlockingProbability).
         */
		public boolean SlotBlocking;

		/**
         * Parte 45: AlgorithmCost.csv, number of calls and time of each algorithm of the simulation (routing,
         * spectrum/core assignment, integrated RMLSA, QKD admission...). Off by default; it does not change any
         * result (simulator.AlgorithmCostRecorder).
         */
		public boolean AlgorithmCost;

		public Metrics() {
			QKDMetrics = true;
			SlotUtilization = true;
			SlotBlocking = true;
			BlockingProbability = true;
			BitRateBlockingProbability = true;
			SpectrumUtilization = true;
			SpectrumSizeStatistics = true;
			ExternalFragmentation = true;
			RelativeFragmentation = true;
			TransmittersReceiversRegeneratorsUtilization = true;
			ModulationUtilization = true;
			ConsumedEnergy = true;
			GroomingStatistics = true;
			DataSetInformation = true;
			PhysicalLayerStatistics = true;
		}
	}

	public double getQkdWarmupFraction() {
		double v = qkdWarmupFraction == null ? 0.1 : qkdWarmupFraction.doubleValue();

		if (!Double.isFinite(v) || v < 0.0 || v >= 1.0) {
			throw new IllegalArgumentException("simulation.qkdWarmupFraction must be in [0,1)");
		}

		return v;
	}

	public void setQkdWarmupFraction(Double qkdWarmupFraction) {
		this.qkdWarmupFraction = qkdWarmupFraction;
	}

	public boolean isQkdExactRequestCount() {
		return qkdExactRequestCount != null && qkdExactRequestCount.booleanValue();
	}

	public void setQkdExactRequestCount(Boolean qkdExactRequestCount) {
		this.qkdExactRequestCount = qkdExactRequestCount;
	}

}
