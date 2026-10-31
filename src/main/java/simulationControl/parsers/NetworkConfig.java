package simulationControl.parsers;

import java.util.ArrayList;
import java.util.List;

/**
 * Network configuration read from the SNetS {@code network} JSON file.
 *
 * <p>QKD integration status through Part 4:</p>
 * <ul>
 *   <li>Part 3 introduced the optional top-level {@code qkd} object;</li>
 *   <li>Part 4 propagates the configured quantum core to every
 *       {@link CoreConfig} before links/cores are built by {@code Mesh}.</li>
 * </ul>
 *
 * <p>The propagation is deliberately performed in {@link #getCores()} rather
 * than relying on a JSON setter. SNetS uses Gson, which may populate fields
 * directly. {@code Mesh} already calls {@code networkConfig.getCores()} while
 * creating each physical link, so this is a deterministic point at which the
 * reservation can be applied without changing {@code Mesh}.</p>
 *
 * <p>Legacy scenarios without a {@code qkd} object remain fully compatible.</p>
 */
public class NetworkConfig {

    private List<NodeConfig> nodes;
    private List<LinkConfig> links;
    private List<ModulationConfig> modulations;
    private List<CoreConfig> cores;
    private int guardBand;
    private int bvtSpectralAmplitude;

    /** Optional QKD infrastructure configuration. */
    private QKDConfig qkd;

    public NetworkConfig() {
        nodes = new ArrayList<NodeConfig>();
        links = new ArrayList<LinkConfig>();
        modulations = new ArrayList<ModulationConfig>();
        cores = new ArrayList<CoreConfig>();
        guardBand = 1;
        bvtSpectralAmplitude = 1000;
        qkd = null;
    }

    public List<ModulationConfig> getModulations() {
        return modulations;
    }

    public void setModulations(List<ModulationConfig> modulations) {
        this.modulations = modulations;
    }

    public List<NodeConfig> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeConfig> nodes) {
        this.nodes = nodes;
    }

    public List<LinkConfig> getLinks() {
        return links;
    }

    public void setLinks(List<LinkConfig> links) {
        this.links = links;
    }

    /**
     * Returns core definitions after synchronizing the QKD reservation flag.
     *
     * <p>Exactly the core whose id equals {@code qkd.quantumCoreId} is marked
     * reserved when QKD is enabled. All flags are reset when QKD is disabled
     * or absent, which also keeps reused/programmatic configurations safe.</p>
     */
    public List<CoreConfig> getCores() {
        applyQkdCoreReservation();
        return cores;
    }

    public void setCores(List<CoreConfig> cores) {
        this.cores = cores;
        applyQkdCoreReservation();
    }

    public int getGuardBand() {
        return guardBand;
    }

    public void setGuardBand(int guardBand) {
        this.guardBand = guardBand;
    }

    public int getBvtSpectralAmplitude() {
        return bvtSpectralAmplitude;
    }

    public void setBvtSpectralAmplitude(int bvtSpectralAmplitude) {
        this.bvtSpectralAmplitude = bvtSpectralAmplitude;
    }

    public QKDConfig getQkd() {
        return qkd;
    }

    public void setQkd(QKDConfig qkd) {
        this.qkd = qkd;
        applyQkdCoreReservation();
    }

    /**
     * Propagates the top-level QKD configuration to individual core configs.
     * This flag is an internal Java-side property; it does not need to appear
     * inside each core object in the JSON file.
     */
    private void applyQkdCoreReservation() {
        if (cores == null) {
            return;
        }

        final boolean enabled = qkd != null && qkd.isEnabled();
        final int reservedId = enabled ? qkd.getQuantumCoreId() : -1;

        for (CoreConfig core : cores) {
            if (core != null) {
                core.setQkdReserved(enabled && core.getId() == reservedId);
                // Part 33: cores that the classical side must not use (the quantum core is never disabled)
                core.setDisabled(disabledCores != null && disabledCores.contains(Integer.valueOf(core.getId()))
                        && !(enabled && core.getId() == reservedId));
            }
        }
    }

    /**
     * Part 33: ids of the cores that no circuit may use, in every link (e.g. [3, 5]). Optional; absent or empty =
     * all cores usable (original behaviour). A disabled core looks full to the algorithms. Ignored for the QKD core.
     */
    private List<Integer> disabledCores;

    public List<Integer> getDisabledCores() {
        return disabledCores;
    }

    public void setDisabledCores(List<Integer> disabledCores) {
        this.disabledCores = disabledCores;
        applyQkdCoreReservation();
    }

    /** QKD-specific infrastructure parameters stored in the network file. */
    public static class QKDConfig {
        private boolean enabled;
        private double qtndKm;
        private int quantumCoreId;
        /**
         * QCh resource granularity: "route" (Part 23: one QCh per QKD session
         * occupying the same contiguous slots of the QKD core on every span of
         * the QKD route, chosen first-fit among the bands whose SKR meets the
         * key demand on all spans), "link" (one QCh spans every QTND segment
         * of a physical link; capacity = bottleneck segment) or "span" (one
         * independent QCh pool per QTND segment, Q-TRx counted per segment, as
         * in the trusted-node planning of Dibaj et al. 2024).
         */
        private String qchGranularity = "link";
        /** When true both directions of a fiber pair share the same QCh pools. */
        private boolean undirectedChannels = false;
        /** Discrete TDM slots per QCh (0 = continuous key-rate sharing). */
        private int tdmSlots = 0;
        /** "fixed" = every QCh at the reference wavelength; "grid" = k-th QCh of a span uses the k-th best wavelength of the QCh grid. */
        private String wavelengthPolicy = "fixed";
        /**
         * Part 23: number of alternative QKD routes (k shortest by length,
         * besides the route of the classical circuit) tried when the QCh
         * cannot be provisioned on the classical route. 0 = classical route only.
         */
        private int alternativeRoutes = 0;
        /**
         * Part 23: slots of the PICh (public interaction channel) reserved for
         * each QKD session on a classical core along the QKD route. 0 = PICh
         * not modelled (dedicated resources assumed).
         */
        private int pichSlots = 0;
        /**
         * Part 23 (route granularity): order in which the free QCh bands are
         * tried: "firstfit" (lowest slot first, as in the qualification) or
         * "lowestloss" (band closest to the attenuation minimum first).
         * Parte 40: "abne" (default) = the spectral rule ABNE/ACINE uses for the core: medium fit in the central core
         * (core 0, or 18 with 19 cores), last fit in even outer cores, first fit in odd outer cores. With the QKD core
         * on core 1 (odd) it is the same as "firstfit". "firstfit" and "abne" apply to the route, link and span modes
         * ("lowestloss" only to route; spanLanePlacement = "wavelength" wins in span/link lanes).
         */
        private String qchSlotPolicy = "abne";
        /** Parte 40: guard band (slots) between two QChs of the QKD core. 0 = none (behaviour up to Part 39). */
        private int qchGuardBand = 0;
        /**
         * Parte 40: classical core that receives a new PICh. "lowest" (default, as before) = lowest-id usable classical
         * core; "fixed" = pichCoreId (falls back to "lowest" when it is full); "leastUsed" = classical core with the
         * fewest used slots on the link. Disabled cores and the QKD core are never used.
         */
        private String pichCorePolicy = "lowest";
        private int pichCoreId = -1;
        /** Parte 40: slot of the PICh in its core: "firstfit" (default, as before), "lastfit" or "abne" (rule of the core). */
        private String pichSlotPolicy = "firstfit";
        /** Parte 40: guard band of the PICh; -1 (default) = the guardBand of the network, as the classical circuits. */
        private int pichGuardBand = -1;
        /**
         * Span/link granularity: where the k-th QCh lane sits in the QKD core.
         * "first" (default, article reproduction): lane k uses slot k+1, while
         * its SKR uses the k-th best wavelength of the grid. "wavelength": lane k
         * uses the slot whose frequency is that wavelength (mid-band first), so
         * the spectral overlap with classical channels in the neighbouring cores
         * (inter-core crosstalk) is consistent with the wavelength used in the SKR.
         */
        private String spanLanePlacement = "first";
        /**
         * Part 26: how the PICh is modelled when pichSlots &gt; 0. "segment" (default):
         * the PICh follows the QCh and ends at every node of the QKD route (one PICh
         * per physical link, shared by the sessions using that link, like the QCh
         * pool). "route": one dedicated PICh per session, continuous over the whole
         * route (Parts 23-25 behaviour).
         */
        private String pichMode = "segment";
        /** Part 26: sessions that may share one PICh in "segment" mode (0 = unlimited). */
        private int pichSessionsPerChannel = 0;
        /**
         * Part 26 (link/span granularity): "shared" (default) = a session reuses the
         * spare key rate of an active QCh before activating a new one; "dedicated" =
         * every session activates its own QCh (no sharing, no TDM).
         */
        private String qchSharing = "shared";
        /** Part 26: QKD transmitters (QTx) / receivers (QRx) per QKD node, 0 = unlimited. */
        private int qTransmittersPerNode = 0;
        private int qReceiversPerNode = 0;
        /** Part 26: separate limits for the in-line trusted nodes (-1 = same as the network nodes). */
        private int qTransmittersPerTrustedNode = -1;
        private int qReceiversPerTrustedNode = -1;

        /**
         * Parte 39: how the trusted nodes are placed. "global" (default) = every link is cut into spans of at most
         * qtndKm; "perLink" = the links listed in trustedNodesPerLink use their own rule (a number of trusted nodes,
         * evenly spaced, or their own QTND in km) and the other links keep qtndKm.
         */
        private String trustedNodeMode = "global";
        private List<LinkTrustedNodes> trustedNodesPerLink;

        public String getTrustedNodeMode() {
            return trustedNodeMode == null || trustedNodeMode.trim().isEmpty() ? "global" : trustedNodeMode.trim();
        }
        public void setTrustedNodeMode(String v) { this.trustedNodeMode = v; }
        public boolean isTrustedNodesPerLink() { return "perlink".equalsIgnoreCase(getTrustedNodeMode()); }
        public List<LinkTrustedNodes> getTrustedNodesPerLink() { return trustedNodesPerLink; }
        public void setTrustedNodesPerLink(List<LinkTrustedNodes> v) { this.trustedNodesPerLink = v; }

        /**
         * Parte 39: QTND of one link. Without per-link mode, or when the link is not listed, it is qtndKm. A listed
         * link ("A-B" matches both directions; an entry "B-A" written for the reverse direction wins for it) uses
         * trustedNodes (n nodes = n + 1 equal spans) or its own qtndKm.
         */
        public double qtndForLink(String source, String destination, double lengthKm) {
            if (!isTrustedNodesPerLink() || trustedNodesPerLink == null) return qtndKm;
            LinkTrustedNodes exact = null, reverse = null;
            for (LinkTrustedNodes e : trustedNodesPerLink) {
                if (e == null || e.link == null) continue;
                String[] ab = e.link.trim().split("\\s*-\\s*");
                if (ab.length != 2) continue;
                if (ab[0].equals(source) && ab[1].equals(destination)) exact = e;
                else if (ab[0].equals(destination) && ab[1].equals(source)) reverse = e;
            }
            LinkTrustedNodes e = exact != null ? exact : reverse;
            if (e == null) return qtndKm;
            if (e.trustedNodes != null && e.trustedNodes.intValue() >= 0 && lengthKm > 0) {
                return lengthKm / (e.trustedNodes.intValue() + 1);
            }
            if (e.qtndKm != null && e.qtndKm.doubleValue() > 0) return e.qtndKm.doubleValue();
            return qtndKm;
        }

        public String getPichMode() {
            return pichMode == null || pichMode.trim().isEmpty() ? "segment" : pichMode.trim().toLowerCase();
        }
        public void setPichMode(String v) { this.pichMode = v; }
        public int getPichSessionsPerChannel() { return pichSessionsPerChannel; }
        public void setPichSessionsPerChannel(int v) { this.pichSessionsPerChannel = v; }
        public String getQchSharing() {
            return qchSharing == null || qchSharing.trim().isEmpty() ? "shared" : qchSharing.trim().toLowerCase();
        }
        public void setQchSharing(String v) { this.qchSharing = v; }
        public int getQTransmittersPerNode() { return qTransmittersPerNode; }
        public void setQTransmittersPerNode(int v) { this.qTransmittersPerNode = v; }
        public int getQReceiversPerNode() { return qReceiversPerNode; }
        public void setQReceiversPerNode(int v) { this.qReceiversPerNode = v; }
        public int getQTransmittersPerTrustedNode() { return qTransmittersPerTrustedNode; }
        public void setQTransmittersPerTrustedNode(int v) { this.qTransmittersPerTrustedNode = v; }
        public int getQReceiversPerTrustedNode() { return qReceiversPerTrustedNode; }
        public void setQReceiversPerTrustedNode(int v) { this.qReceiversPerTrustedNode = v; }

        public String getSpanLanePlacement() {
            return spanLanePlacement == null || spanLanePlacement.trim().isEmpty() ? "first" : spanLanePlacement.trim().toLowerCase();
        }

        public void setSpanLanePlacement(String v) { this.spanLanePlacement = v; }

        public String getQchSlotPolicy() {
            return qchSlotPolicy == null || qchSlotPolicy.trim().isEmpty() ? "abne" : qchSlotPolicy.trim().toLowerCase();
        }

        public void setQchSlotPolicy(String v) { this.qchSlotPolicy = v; }
        public int getQchGuardBand() { return qchGuardBand; }
        public void setQchGuardBand(int v) { this.qchGuardBand = v; }
        public String getPichCorePolicy() {
            return pichCorePolicy == null || pichCorePolicy.trim().isEmpty() ? "lowest" : pichCorePolicy.trim().toLowerCase();
        }
        public void setPichCorePolicy(String v) { this.pichCorePolicy = v; }
        public int getPichCoreId() { return pichCoreId; }
        public void setPichCoreId(int v) { this.pichCoreId = v; }
        public String getPichSlotPolicy() {
            return pichSlotPolicy == null || pichSlotPolicy.trim().isEmpty() ? "firstfit" : pichSlotPolicy.trim().toLowerCase();
        }
        public void setPichSlotPolicy(String v) { this.pichSlotPolicy = v; }
        public int getPichGuardBand() { return pichGuardBand; }
        public void setPichGuardBand(int v) { this.pichGuardBand = v; }

        public int getAlternativeRoutes() { return alternativeRoutes; }

        public void setAlternativeRoutes(int alternativeRoutes) { this.alternativeRoutes = alternativeRoutes; }

        public int getPichSlots() { return pichSlots; }

        public void setPichSlots(int pichSlots) { this.pichSlots = pichSlots; }

        public QKDConfig() {
            this.enabled = false;
            this.qtndKm = 0.0;
            this.quantumCoreId = -1;
        }

        public String getQchGranularity() {
            return qchGranularity == null || qchGranularity.trim().isEmpty() ? "link" : qchGranularity.trim().toLowerCase();
        }

        public void setQchGranularity(String qchGranularity) { this.qchGranularity = qchGranularity; }

        public boolean isUndirectedChannels() { return undirectedChannels; }

        public void setUndirectedChannels(boolean undirectedChannels) { this.undirectedChannels = undirectedChannels; }

        public int getTdmSlots() { return tdmSlots; }

        public void setTdmSlots(int tdmSlots) { this.tdmSlots = tdmSlots; }

        public String getWavelengthPolicy() {
            return wavelengthPolicy == null || wavelengthPolicy.trim().isEmpty() ? "fixed" : wavelengthPolicy.trim().toLowerCase();
        }

        public void setWavelengthPolicy(String wavelengthPolicy) { this.wavelengthPolicy = wavelengthPolicy; }

        public QKDConfig(boolean enabled, double qtndKm, int quantumCoreId) {
            this.enabled = enabled;
            this.qtndKm = qtndKm;
            this.quantumCoreId = quantumCoreId;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getQtndKm() {
            return qtndKm;
        }

        public void setQtndKm(double qtndKm) {
            this.qtndKm = qtndKm;
        }

        public int getQuantumCoreId() {
            return quantumCoreId;
        }

        public void setQuantumCoreId(int quantumCoreId) {
            this.quantumCoreId = quantumCoreId;
        }

        @Override
        public String toString() {
            return "QKDConfig{" +
                    "enabled=" + enabled +
                    ", qtndKm=" + qtndKm +
                    ", quantumCoreId=" + quantumCoreId +
                    ", qchGranularity=" + getQchGranularity() +
                    ", undirectedChannels=" + undirectedChannels +
                    ", tdmSlots=" + tdmSlots +
                    ", wavelengthPolicy=" + getWavelengthPolicy() +
                    '}';
        }
    }

    /** Parte 39: trusted-node rule of one link (network.qkd.trustedNodesPerLink). */
    public static class LinkTrustedNodes {
        /** "A-B": the link between nodes A and B (both directions unless "B-A" is also listed). */
        private String link;
        /** Number of trusted nodes inside the link (evenly spaced); has priority over qtndKm. */
        private Integer trustedNodes;
        /** QTND of this link in km (used when trustedNodes is absent). */
        private Double qtndKm;

        public String getLink() { return link; }
        public void setLink(String v) { this.link = v; }
        public Integer getTrustedNodes() { return trustedNodes; }
        public void setTrustedNodes(Integer v) { this.trustedNodes = v; }
        public Double getQtndKm() { return qtndKm; }
        public void setQtndKm(Double v) { this.qtndKm = v; }
    }

    public static class ModulationConfig {
        private String name;
        private double maxRange;
        private double M;
        private double SNR;
        private double XT;

        public ModulationConfig(String name, double M, double maxRange,
                double SNR, double XT) {
            this.name = name;
            this.M = M;
            this.maxRange = maxRange;
            this.SNR = SNR;
            this.XT = XT;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public double getM() {
            return M;
        }

        public void setM(double m) {
            M = m;
        }

        public double getMaxRange() {
            return maxRange;
        }

        public void setMaxRange(double maxRange) {
            this.maxRange = maxRange;
        }

        public double getSNR() {
            return SNR;
        }

        public void setSNR(double sNR) {
            SNR = sNR;
        }

        public double getXT() {
            return XT;
        }

        public void setXT(double xT) {
            XT = xT;
        }
    }

    public static class LinkConfig {
        private String source;
        private String destination;
        private int slots;
        private double spectrum;
        private double size;

        public LinkConfig(String source, String destination, int slots,
                double spectrum, double size) {
            this.source = source;
            this.destination = destination;
            this.slots = slots;
            this.spectrum = spectrum;
            this.size = size;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public int getSlots() {
            return slots;
        }

        public void setSlots(int slots) {
            this.slots = slots;
        }

        public double getSpectrum() {
            return spectrum;
        }

        public void setSpectrum(double spectrum) {
            this.spectrum = spectrum;
        }

        public double getSize() {
            return size;
        }

        public void setSize(double size) {
            this.size = size;
        }
    }

    public static class NodeConfig {
        private String name;
        private int transmitters;
        private int receivers;
        private int regenerators;
        /** Part 26: QKD transmitters / receivers of this node (0 = network.qkd default). */
        private int qTransmitters;
        private int qReceivers;

        public int getQTransmitters() { return qTransmitters; }
        public void setQTransmitters(int v) { this.qTransmitters = v; }
        public int getQReceivers() { return qReceivers; }
        public void setQReceivers(int v) { this.qReceivers = v; }

        public NodeConfig(String name, int transmitters, int receivers,
                int regenerators) {
            this.name = name;
            this.transmitters = transmitters;
            this.receivers = receivers;
            this.regenerators = regenerators;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getTransmitters() {
            return transmitters;
        }

        public void setTransmitters(int transmitters) {
            this.transmitters = transmitters;
        }

        public int getReceivers() {
            return receivers;
        }

        public void setReceivers(int receivers) {
            this.receivers = receivers;
        }

        public int getRegenerators() {
            return regenerators;
        }

        public void setRegenerators(int regenerators) {
            this.regenerators = regenerators;
        }
    }

    /**
     * Configuration of one spatial core.
     *
     * <p>{@code qkdReserved} is populated internally from the top-level QKD
     * configuration. It intentionally does not need to be present in JSON.</p>
     */
    public static class CoreConfig {
        private int id;
        private List<Integer> adjacentCores;
        private boolean qkdReserved;

        public CoreConfig(int id, List<Integer> adjacentCores) {
            this.adjacentCores = new ArrayList<Integer>();
            this.id = id;
            this.adjacentCores = adjacentCores;
            this.qkdReserved = false;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public List<Integer> getAdjacentCores() {
            return adjacentCores;
        }

        public void setAdjacentCores(List<Integer> adjacentCores) {
            this.adjacentCores = adjacentCores;
        }

        public boolean isQkdReserved() {
            return qkdReserved;
        }

        /** Part 33: set from network.disabledCores (not read from the core object of the JSON). */
        private transient boolean disabled;

        public boolean isDisabled() {
            return disabled;
        }

        public void setDisabled(boolean disabled) {
            this.disabled = disabled;
        }

        public void setQkdReserved(boolean qkdReserved) {
            this.qkdReserved = qkdReserved;
        }
    }
}
