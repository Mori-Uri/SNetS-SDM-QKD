package qkd.network;

import java.io.Serializable;
import java.util.List;

import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.QKDConfig;

/**
 * Immutable, validated view of the QKD infrastructure parameters declared in
 * the SNetS {@code network} file.
 *
 * <p>This class separates JSON/parser concerns from the QKD network model. The
 * parser may contain an absent or disabled QKD section, while the rest of the
 * QKD code receives a small object with explicit semantics.</p>
 *
 * <p>Refer\u00eancias: [DIBAJ24] (QTND, n\u00facleo qu\u00e2ntico, TDM de 256 slots), [MORI26] (modos route/link/span, PICh, rotas alternativas), [MOGH21] (CCh/PICh na mesma rota do QCh).</p>
 */
public final class QKDNetworkParameters implements Serializable {

	private static final long serialVersionUID = 1L;

	private final boolean enabled;
	private final double qtndKm;
	private final int quantumCoreId;

	/** true = one QCh pool per QTND span (Dibaj et al.); false = one QCh per physical link. */
	private final boolean spanGranularity;
	private final boolean undirectedChannels;
	private final int tdmSlots;
	private final boolean wavelengthGrid;

	/** Part 23: one dedicated, route-continuous QCh per session. */
	private final boolean routeGranularity;
	private final int alternativeRoutes;
	private final int pichSlots;
	private boolean lowestLossSlots;
	private boolean wavelengthLanes;

	/** Parte 40: QCh slot rule ("firstfit", "lowestloss" or "abne") and guard band between QChs. */
	private String qchSlotPolicy = "firstfit";
	private boolean qchSlotContinuityAtNetworkNodes = true;
	private boolean pichSlotContinuityAtNetworkNodes = true;
	private int qchGuardBand = 0;

	/** Parte 40: PICh placement (core rule, fixed core, slot rule, guard band; -1 = network guard band). */
	private String pichCorePolicy = "lowest";
	private int pichCoreId = -1;
	private String pichSlotPolicy = "firstfit";
	private int pichGuardBand = -1;
	private int pichFirstSlot = -1, pichLastSlot = -1;
	private String pichModulation;
	private boolean pichRxPowerCheckEnabled;
	private boolean pichOsnrCheckEnabled;
	/** Enables an additional QBER rejection criterion, independent of the BB84 SKR. */
	private boolean qberThresholdEnabled = false;
	/** Configured QBER cap, retained even when the additional gate is disabled. */
	private double maximumQber = 0.11;
	private boolean sameBandAlignedGrids;
	private int sameBandGuardSlots;

	// Receiver sensitivity is equipment-specific; all fibre, XT, GN and ASE
	// parameters now belong exclusively to SNetS PhysicalLayer.
	private double pichReceiverSensitivityDbm;

	/** Legacy PICh placement flag. Stage-1 physical provisioning always creates one PICh per QKDSpan; retained for config compatibility. */
	private boolean pichSegmented = true;
	private int pichSessionsPerChannel = 0;
	private boolean pichTdmSharing;
	private boolean pichOverIp; // IP control transport has no optical PICh or associated noise
	private boolean pichLongitudinalReuse;
	private boolean longitudinalXtEnabled;
	private boolean dedicatedQch = false;
	private int qTxPerNode = 0, qRxPerNode = 0, qTxPerTrustedNode = -1, qRxPerTrustedNode = -1;
	private java.util.Map<String, int[]> nodeQEquipment = java.util.Collections.emptyMap();

	private QKDNetworkParameters(boolean enabled, double qtndKm,
			int quantumCoreId) {
		this(enabled, qtndKm, quantumCoreId, false, false, 0, false, false, 0, 0);
	}

	private QKDNetworkParameters(boolean enabled, double qtndKm,
			int quantumCoreId, boolean spanGranularity,
			boolean undirectedChannels, int tdmSlots, boolean wavelengthGrid,
			boolean routeGranularity, int alternativeRoutes, int pichSlots) {
		this.routeGranularity = routeGranularity;
		this.alternativeRoutes = alternativeRoutes;
		this.pichSlots = pichSlots;
		this.enabled = enabled;
		this.qtndKm = qtndKm;
		this.quantumCoreId = quantumCoreId;
		this.spanGranularity = spanGranularity;
		this.undirectedChannels = undirectedChannels;
		this.tdmSlots = tdmSlots;
		this.wavelengthGrid = wavelengthGrid;
	}

	/**
     * Builds and validates QKD parameters from the network configuration.
     *
     * <p>If the legacy network file has no {@code qkd} section, or if
     * {@code qkd.enabled=false}, a disabled parameter object is returned and
     * no QKD topology is created.</p>
     */
	public static QKDNetworkParameters from(NetworkConfig networkConfig) {
		if (networkConfig == null) {
			throw new IllegalArgumentException("networkConfig cannot be null");
		}

		final QKDConfig config = networkConfig.getQkd();

		if (config == null || !config.isEnabled()) {
			return disabled();
		}

		final double qtndKm = config.getQtndKm();
		if (!Double.isFinite(config.getMaximumQber()) || config.getMaximumQber() < 0.0
				|| config.getMaximumQber() > 1.0)
			throw new IllegalArgumentException("network.qkd.maximumQber must be in [0,1]");

		if (!Double.isFinite(qtndKm) || qtndKm <= 0.0) {
			throw new IllegalArgumentException(
					"network.qkd.qtndKm must be finite and > 0 when QKD is enabled");
		}

		final int quantumCoreId = config.getQuantumCoreId();

		if (quantumCoreId < 0) {
			throw new IllegalArgumentException(
					"network.qkd.quantumCoreId must be >= 0 when QKD is enabled");
		}

		if (!containsCore(networkConfig.getCores(), quantumCoreId)) {
			throw new IllegalArgumentException(
					"network.qkd.quantumCoreId=" + quantumCoreId
					+ " does not exist in network.cores");
		}

		final String granularity = config.getQchGranularity();

		if (!"link".equals(granularity) && !"span".equals(granularity) && !"route".equals(granularity)) {
			throw new IllegalArgumentException(
					"network.qkd.qchGranularity must be \"route\", \"link\" or \"span\"");
		}

		if (config.getAlternativeRoutes() < 0) {
			throw new IllegalArgumentException("network.qkd.alternativeRoutes must be >= 0");
		}

		final String slotPolicy = config.getQchSlotPolicy();

		if (!"firstfit".equals(slotPolicy) && !"lowestloss".equals(slotPolicy) && !"abne".equals(slotPolicy) && !"maxskr".equals(slotPolicy)) {
			throw new IllegalArgumentException("network.qkd.qchSlotPolicy must be \"abne\", \"firstfit\", \"lowestloss\" or \"maxskr\"");
		}

		if ("maxskr".equals(slotPolicy) && !"route".equals(granularity)) {
			throw new IllegalArgumentException("network.qkd.qchSlotPolicy=maxskr requires qchGranularity=route");
		}

		if (config.getQchGuardBand() < 0) {
			throw new IllegalArgumentException("network.qkd.qchGuardBand must be >= 0");
		}

		final String pcp = config.getPichCorePolicy();

		if (!"lowest".equals(pcp) && !"fixed".equals(pcp) && !"leastused".equals(pcp)) {
			throw new IllegalArgumentException("network.qkd.pichCorePolicy must be \"lowest\", \"fixed\" or \"leastUsed\"");
		}

		if ("fixed".equals(pcp) && config.getPichSlots() > 0 && (config.getPichCoreId() == quantumCoreId || !containsCore(networkConfig.getCores(), config.getPichCoreId()))) {
			throw new IllegalArgumentException("network.qkd.pichCoreId must be an existing classical core when pichCorePolicy = \"fixed\"");
		}

		final String psp = config.getPichSlotPolicy();

		if (!"firstfit".equals(psp) && !"lastfit".equals(psp) && !"abne".equals(psp)) {
			throw new IllegalArgumentException("network.qkd.pichSlotPolicy must be \"firstfit\", \"lastfit\" or \"abne\"");
		}

		if (config.getPichGuardBand() < -1) {
			throw new IllegalArgumentException("network.qkd.pichGuardBand must be >= 0 (or -1 = network guardBand)");
		}

		final int pichFirst = config.getPichFirstSlot(), pichLast = config.getPichLastSlot();

		if (!((pichFirst == -1 && pichLast == -1)
				|| (pichFirst >= 1 && pichLast >= pichFirst))) {
			throw new IllegalArgumentException("network.qkd.pichFirstSlot and pichLastSlot must both be -1 or a valid 1-based interval");
		}

		if (pichFirst >= 1 && config.getPichSlots() > pichLast - pichFirst + 1)
			throw new IllegalArgumentException("PICh slot window smaller than configured pichSlots");

		if (config.isPichRxPowerCheckEnabled()) {
			if (!Double.isFinite(config.getPichReceiverSensitivityDbm())) {
				throw new IllegalArgumentException("Invalid PICh receiver-power budget parameters");
			}

			if (config.getPichSlots() == 0) {
				throw new IllegalArgumentException("PICh receiver-power check requires pichSlots > 0");
			}
		}

		if (config.getPichSlots() < 0) {
			throw new IllegalArgumentException("network.qkd.pichSlots must be >= 0");
		}

		if (config.getTdmSlots() < 0) {
			throw new IllegalArgumentException("network.qkd.tdmSlots must be >= 0");
		}

		final String policy = config.getWavelengthPolicy();

		if (!"fixed".equals(policy) && !"grid".equals(policy)) {
			throw new IllegalArgumentException(
					"network.qkd.wavelengthPolicy must be \"fixed\" or \"grid\"");
		}

		QKDNetworkParameters out = new QKDNetworkParameters(true, qtndKm, quantumCoreId,
				"span".equals(granularity), config.isUndirectedChannels(),
				config.getTdmSlots(), "grid".equals(policy), "route".equals(granularity),
				config.getAlternativeRoutes(), config.getPichSlots());
		out.lowestLossSlots = "lowestloss".equals(slotPolicy);
		out.qchSlotPolicy = slotPolicy;
		out.qchSlotContinuityAtNetworkNodes = config.isQchSlotContinuityAtNetworkNodes();
		out.pichSlotContinuityAtNetworkNodes = config.isPichSlotContinuityAtNetworkNodes();
		out.qchGuardBand = config.getQchGuardBand();
		out.pichCorePolicy = pcp;
		out.pichCoreId = config.getPichCoreId();
		out.pichSlotPolicy = psp;
		out.pichGuardBand = config.getPichGuardBand();
		out.pichModulation = config.getPichModulation();
		out.pichFirstSlot = pichFirst;
		out.pichLastSlot = pichLast;
		out.pichRxPowerCheckEnabled = config.isPichRxPowerCheckEnabled();
		out.pichOsnrCheckEnabled = config.isPichOsnrCheckEnabled();
		out.qberThresholdEnabled = config.isQberThresholdEnabled();
		out.maximumQber = config.getMaximumQber();
		out.sameBandAlignedGrids = config.isSameBandAlignedGrids();
		out.sameBandGuardSlots = config.getSameBandGuardSlots();
		out.pichLongitudinalReuse = config.isPichLongitudinalReuse();
		out.longitudinalXtEnabled = config.isLongitudinalXtEnabled();

		if (out.sameBandGuardSlots < 0)
			throw new IllegalArgumentException("sameBandGuardSlots must be >= 0");

		// Reject obsolete PICh-only physical parameters instead of silently
		// ignoring scientifically relevant values. They must now come from
		// physicalLayer and the selected network.modulations entry.
		if (out.pichOsnrCheckEnabled && (
				Double.isFinite(config.getPichMinimumSnrDb())
				|| Double.isFinite(config.getPichReceiverNoiseW())
				|| Double.isFinite(config.getPichXtCouplingPerKm())
				|| Double.isFinite(config.getPichGnCoefficientPerW2Km())))
			throw new IllegalArgumentException("Remove legacy PICh-only SNR/XT/GN parameters; "
					+ "QoT now uses physicalLayer and network.modulations");

		if (out.pichRxPowerCheckEnabled && (
				config.getPichFiberLossDbPerKm() != 0.0
				|| config.getPichInsertionLossDb() != 0.0))
			throw new IllegalArgumentException("Remove legacy PICh fibre/insertion losses; "
					+ "receiver budget uses SNetS PhysicalLayer");
		out.pichReceiverSensitivityDbm = config.getPichReceiverSensitivityDbm();
		final String lanes = config.getSpanLanePlacement();

		if (!"first".equals(lanes) && !"wavelength".equals(lanes)) {
			throw new IllegalArgumentException("network.qkd.spanLanePlacement must be \"first\" or \"wavelength\"");
		}

		out.wavelengthLanes = "wavelength".equals(lanes);
		final String pm = config.getPichMode();

		if (!"segment".equals(pm) && !"route".equals(pm)) {
			throw new IllegalArgumentException("network.qkd.pichMode must be \"segment\" or \"route\"");
		}

		out.pichSegmented = "segment".equals(pm);

		if (config.getPichSessionsPerChannel() < 0) {
			throw new IllegalArgumentException("network.qkd.pichSessionsPerChannel must be >= 0");
		}

		final String pichAllocation = config.getPichAllocation();
		if (!"dedicated".equals(pichAllocation) && !"tdm".equals(pichAllocation))
			throw new IllegalArgumentException("network.qkd.pichAllocation must be dedicated or tdm");
		final String pichTransport = config.getPichTransport();
		if (!"optical".equals(pichTransport) && !"ip".equals(pichTransport))
			throw new IllegalArgumentException("network.qkd.pichTransport must be optical or ip");
		out.pichTdmSharing = "tdm".equals(pichAllocation);
		out.pichOverIp = "ip".equals(pichTransport);
		if (!out.pichOverIp && config.getPichSlots() == 0)
			throw new IllegalArgumentException("Optical PICh requires pichSlots > 0; use pichTransport=ip to model an IP-based PICh");
		if (out.pichTdmSharing && !out.pichOverIp && config.getPichSessionsPerChannel() <= 0)
			throw new IllegalArgumentException("pichAllocation=tdm requires pichSessionsPerChannel > 0 as the maximum concurrently shared sessions");
		out.pichSessionsPerChannel = config.getPichSessionsPerChannel();
		final String sharing = config.getQchSharing();

		if (!"shared".equals(sharing) && !"dedicated".equals(sharing)) {
			throw new IllegalArgumentException("network.qkd.qchSharing must be \"shared\" or \"dedicated\"");
		}

		out.dedicatedQch = "dedicated".equals(sharing);

		if (config.getQTransmittersPerNode() < 0 || config.getQReceiversPerNode() < 0) {
			throw new IllegalArgumentException("network.qkd.qTransmittersPerNode/qReceiversPerNode must be >= 0");
		}

		out.qTxPerNode = config.getQTransmittersPerNode();
		out.qRxPerNode = config.getQReceiversPerNode();
		out.qTxPerTrustedNode = config.getQTransmittersPerTrustedNode();
		out.qRxPerTrustedNode = config.getQReceiversPerTrustedNode();
		java.util.Map<String, int[]> per = new java.util.HashMap<String, int[]>();

		if (networkConfig.getNodes() != null) {
			for (NetworkConfig.NodeConfig n : networkConfig.getNodes()) {
				if (n.getQTransmitters() < 0 || n.getQReceivers() < 0) {
					throw new IllegalArgumentException("nodes[" + n.getName() + "].qTransmitters/qReceivers must be >= 0");
				}

				if (n.getQTransmitters() > 0 || n.getQReceivers() > 0) {
					per.put(n.getName(), new int[] {n.getQTransmitters(), n.getQReceivers()});
				}
			}
		}

		out.nodeQEquipment = per;

		return out;
	}

	public static QKDNetworkParameters disabled() {
		return new QKDNetworkParameters(false, 0.0, -1);
	}

	private static boolean containsCore(List<CoreConfig> cores, int id) {
		if (cores == null) {
			return false;
		}

		for (CoreConfig core : cores) {
			if (core != null && core.getId() == id) {
				return true;
			}
		}

		return false;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public double getQtndKm() {
		return qtndKm;
	}

	public int getQuantumCoreId() {
		return quantumCoreId;
	}

	public boolean isSpanGranularity() {
		return spanGranularity;
	}

	public boolean isUndirectedChannels() {
		return undirectedChannels;
	}

	/** Discrete TDM slots per QCh; 0 means continuous key-rate sharing. */
	public int getTdmSlots() {
		return tdmSlots;
	}

	public boolean isWavelengthGrid() {
		return wavelengthGrid;
	}

	/** Part 23: dedicated QCh with the same slots on every span of the QKD route. */
	public boolean isRouteGranularity() {
		return routeGranularity;
	}

	/** Part 23: alternative QKD routes tried after the classical route (0 = none). */
	public int getAlternativeRoutes() {
		return alternativeRoutes;
	}

	/** Span lanes placed at the slot of their grid wavelength (mid-band first) instead of slot k+1. */
	public boolean isWavelengthLanes() {
		return wavelengthLanes;
	}

	/** Part 23: route-granularity QCh bands tried closest-to-attenuation-minimum first. */
	/** True only for route-continuous QCh: maximize the minimum SKR of all physical spans. */
	public boolean isMaxSkrSlots() { return "maxskr".equals(qchSlotPolicy); }

	public boolean isLowestLossSlots() {
		return lowestLossSlots;
	}

	/** Part 23: PICh slots reserved on a classical core per QKD session (0 = not modelled). */
	public int getPichSlots() {
		return pichSlots;
	}

	/** Parte 40: "firstfit", "lowestloss" or "abne" (spectral rule of the QKD core, as ABNE/ACINE). */
	public String getQchSlotPolicy() { return qchSlotPolicy; }
	/** Spectrum is always contiguous on each physical link; only SNetS nodes may retune. */
	public boolean isQchSlotContinuityAtNetworkNodes() { return qchSlotContinuityAtNetworkNodes; }
	public boolean isPichSlotContinuityAtNetworkNodes() { return pichSlotContinuityAtNetworkNodes; }

	/** Parte 40: guard band (slots) between QChs in the QKD core. */
	public int getQchGuardBand() { return qchGuardBand; }

	/** Parte 40: "lowest", "fixed" or "leastused". */
	public String getPichCorePolicy() { return pichCorePolicy; }
	public int getPichCoreId() { return pichCoreId; }

	/** Parte 40: "firstfit", "lastfit" or "abne". */
	public String getPichSlotPolicy() { return pichSlotPolicy; }

	/** Parte 40: PICh guard band; the network guard band when the file says -1. */
	public int pichGuardBandOr(int networkGuardBand) { return pichGuardBand >= 0 ? pichGuardBand : networkGuardBand; }

	/** Inclusive optical grid slot window. -1/-1 means full classical grid. */
	public String getPichModulation() { return pichModulation; }
	public int getPichFirstSlot() { return pichFirstSlot; }
	public int getPichLastSlot() { return pichLastSlot; }
	public boolean isSameBandAlignedGrids() { return sameBandAlignedGrids; }
	public int getSameBandGuardSlots() { return sameBandGuardSlots; }
	public boolean isPichOsnrCheckEnabled() { return pichOsnrCheckEnabled; }
	/** @deprecated Legacy API alias, replaced by isPichOsnrCheckEnabled. */
	@Deprecated public boolean isPichSnrCheckEnabled() { return isPichOsnrCheckEnabled(); }
	/** The numerical cap from the network configuration (does not imply admission is enabled). */
	public double getMaximumQber() { return maximumQber; }
	public boolean isQberThresholdEnabled() { return qberThresholdEnabled; }
	/**
	 * Gate disabled: pass 1.0 to legacy physical/result APIs so that their
	 * viability checks depend only on SKR, while the actual observed QBER
	 * remains available in telemetry. Gate enabled: use the configured cap.
	 */
	public double getEffectiveMaximumQber() { return qberThresholdEnabled ? maximumQber : 1.0; }
	public boolean isPichRxPowerCheckEnabled() { return pichRxPowerCheckEnabled; }
	public double getPichReceiverSensitivityDbm() { return pichReceiverSensitivityDbm; }

	/**
     * Parte 40: spectral rule of ABNE/ACINE (CSBASDM) for a core: 0 = medium fit (central core: 18 with 19 cores,
     * otherwise 0), 1 = first fit (odd outer cores), 2 = last fit (even outer cores).
     */
	public static int abneRule(int coreId, int numberOfCores) {
		int central = numberOfCores == 19 ? 18 : 0;

		if (coreId == central) return 0;

		return coreId % 2 == 0 ? 2 : 1;
	}

	/** Part 26: PICh per link of the QKD route, shared by sessions (true) or route-continuous and dedicated (false). */
	public boolean isPichSegmented() { return pichSegmented; }

	/** Part 26: sessions per PICh in segment mode (0 = unlimited). */
	/** Dedicated physical PICh per QKDSpan unless TDM was explicitly configured. */
	public boolean isPichTdmSharing() { return pichTdmSharing; }
	/** IP public discussion: no classical optical carrier or slot reservation. */
	public boolean isPichOverIp() { return pichOverIp; }
	/** Permit PICh-to-PICh spectrum reuse across non-overlapping QKDSpans. */
	public boolean isPichLongitudinalReuse() { return pichLongitudinalReuse; }
	public boolean isLongitudinalXtEnabled() { return longitudinalXtEnabled; }
	public int getPichSessionsPerChannel() { return pichSessionsPerChannel; }

	/** Part 26: one QCh per session in link/span granularity (no rate sharing). */
	public boolean isDedicatedQch() { return dedicatedQch; }

	/**
     * Part 26: QTx limit of a QKD node (0 = unlimited). {@code networkNode} is false
     * for the in-line trusted nodes created by the QTND planning.
     */
	public int getQTransmitterLimit(String node, boolean networkNode) {
		int[] v = nodeQEquipment.get(node);

		if (v != null && v[0] > 0) return v[0];

		if (!networkNode && qTxPerTrustedNode >= 0) return qTxPerTrustedNode;

		return qTxPerNode;
	}

	/** Part 26: QRx limit of a QKD node (0 = unlimited). */
	public int getQReceiverLimit(String node, boolean networkNode) {
		int[] v = nodeQEquipment.get(node);

		if (v != null && v[1] > 0) return v[1];

		if (!networkNode && qRxPerTrustedNode >= 0) return qRxPerTrustedNode;

		return qRxPerNode;
	}

	/** True when some QTx/QRx limit is configured. */
	public boolean hasQEquipmentLimits() {
		return qTxPerNode > 0 || qRxPerNode > 0 || qTxPerTrustedNode > 0 || qRxPerTrustedNode > 0 || !nodeQEquipment.isEmpty();
	}

	@Override
	public String toString() {
		return "QKDNetworkParameters{" +
				"enabled=" + enabled +
				", qtndKm=" + qtndKm +
				", quantumCoreId=" + quantumCoreId +
				", granularity=" + (routeGranularity ? "route" : spanGranularity ? "span" : "link") +
				", alternativeRoutes=" + alternativeRoutes +
				", pichSlots=" + pichSlots +
				", undirectedChannels=" + undirectedChannels +
				", tdmSlots=" + tdmSlots +
				", wavelengthPolicy=" + (wavelengthGrid ? "grid" : "fixed") +
				", qchSlotPolicy=" + qchSlotPolicy + ", qchContinuityAtNetworkNodes=" + qchSlotContinuityAtNetworkNodes + ", pichContinuityAtNetworkNodes=" + pichSlotContinuityAtNetworkNodes + ", qchGuardBand=" + qchGuardBand +
				", pich=" + pichCorePolicy + "/" + pichSlotPolicy +
				'}';
	}
}
