package measurement;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import network.ControlPlane;
import network.Core;
import network.Link;
import network.Mesh;
import request.RequestForConnection;
import simulationControl.parsers.SimulationRequest;
import simulationControl.resultManagers.SpectrumUtilizationResultManager;

/**
 * This class stored the metrics related to the use of spectrum.
 *
 * @author Iallen
 */
public class SpectrumUtilization extends Measurement {
    public final static String SEP = "-";

    private Mesh mesh;

    private double utilizationGen;
    private int numberObservations;

    private HashMap<String, Double> utilizationPerLink;
    private int[] desUtilizationPerSlot;
    private Integer maxSlotsByLinks;

    private int maxCoresByLinks;
    private HashMap<Integer, Double> utilizationPerCore;
    private HashMap<String, HashMap<Integer, Double>> utilizationPerLinkCore;

    /** Part 27: count of observations in which [link][core][slot] was free (link order of the mesh). */
    private int[][][] freePerLinkCoreSlot;
    private String[] linkNames;

    /**
     * Creates a new instance of SpectrumUtilization
     *
     * @param loadPoint int
     * @param replication int
     * @param mesh Mesh
     */
    public SpectrumUtilization(int loadPoint, int replication, Mesh mesh) {
        super(loadPoint, replication);

        this.mesh = mesh;
        utilizationGen = 0.0;
        numberObservations = 0;
        utilizationPerLink = new HashMap<String, Double>();

        maxSlotsByLinks = mesh.maximumSlotsByLinks();
        desUtilizationPerSlot = new int[maxSlotsByLinks];

        this.maxCoresByLinks = 0;
        this.utilizationPerCore = new HashMap<>();
        this.utilizationPerLinkCore = new HashMap<>();

		resultManager = new SpectrumUtilizationResultManager();
    }

    /**
     * Adds a new usage observation of spectrum utilization
     *
     * @param cp ControlPlane
     * @param success boolean
     * @param request RequestForConnection
     */
    public void addNewObservation(ControlPlane cp, boolean success, RequestForConnection request) {
    	if(maxCoresByLinks == 0) {
        	maxCoresByLinks = cp.getMesh().maximumCoresByLinks();

        	for(Integer core = 0; core < maxCoresByLinks; core++) {
        		this.utilizationPerCore.put(core, 0.0);
        	}
        }

    	this.newObsUtilization();
    }

    /**
     * Returns the file name.
     * @return the file name.
     */
    @Override
    public String getFileName() {
        return SimulationRequest.Result.FILE_SPECTRUM_UTILIZATION;
    }

    /**
     * Observation of the use of the spectrum resource of the network
     */
    private void newObsUtilization() {
        // Parte 57 (desempenho): the sums are kept in arrays indexed by the position of the link in the mesh and by
        // the core id, and copied to the maps only when they are read (syncMaps). The arithmetic is the same, in the
        // same order, so the values are the same bit for bit. The maps get their keys in the same order as before, at
        // the first observation. With -Dsnets.verifyPerf=true both paths run and are compared at every observation.
        if (simulator.PerfChecks.ENABLED && fastReady()) {
            if (simulator.PerfChecks.VERIFY) {
                newObsUtilizationOriginal();
                newObsUtilizationFast(false);
                verifyFast();
                return;
            }
            newObsUtilizationFast(true);
            return;
        }
        newObsUtilizationOriginal();
    }

    // ---------------------------------------------------------------- Parte 57: arrays of the sums

    /** Sum of the link utilization (average of its cores) per link, in the order of mesh.getLinkList(). */
    private double[] fastLink;
    /** Sum of the core utilization per link and core id. */
    private double[][] fastLinkCore;
    /** Sum of the core utilization divided by the number of links, per core id. */
    private double[] fastCore;
    /** Core ids of each link, in the order of link.getCores(). */
    private int[][] fastCoreIds;
    /** Name of each link (key of the maps). */
    private String[] fastLinkName;
    /** 0 = not prepared, 1 = arrays in use, -1 = the network does not fit the arrays (original path). */
    private int fastState;
    /** The maps are behind the arrays. */
    private boolean mapsStale;

    /** Prepares the arrays at the first observation; false when the original path must be used. */
    private boolean fastReady() {
        if (fastState != 0) {
            return fastState > 0;
        }
        final List<Link> links = mesh.getLinkList();
        final int n = links.size();
        final java.util.HashSet<String> names = new java.util.HashSet<String>();
        int maxCore = maxCoresByLinks;
        fastCoreIds = new int[n][];
        fastLinkName = new String[n];
        for (int l = 0; l < n; l++) {
            final Link link = links.get(l);
            final String name = link.getSource().getName() + SEP + link.getDestination().getName();
            if (!names.add(name)) {
                fastState = -1; // two links with the same name share one entry of the maps
                return false;
            }
            fastLinkName[l] = name;
            final List<Core> cores = link.getCores();
            fastCoreIds[l] = new int[cores.size()];
            final java.util.HashSet<Integer> ids = new java.util.HashSet<Integer>();
            for (int k = 0; k < cores.size(); k++) {
                final int id = cores.get(k).getId();
                if (id < 0 || !ids.add(id)) {
                    fastState = -1;
                    return false;
                }
                fastCoreIds[l][k] = id;
                maxCore = Math.max(maxCore, id + 1);
            }
        }
        if (n == 0) {
            fastState = -1;
            return false;
        }
        fastLink = new double[n];
        fastLinkCore = new double[n][maxCore];
        fastCore = new double[maxCore];
        // keys of the maps in the same order as the original first observation
        for (int l = 0; l < n; l++) {
            final int[] ids = fastCoreIds[l];
            for (int k = 0; k < ids.length; k++) {
                if (!utilizationPerCore.containsKey(ids[k])) utilizationPerCore.put(ids[k], 0.0);
                HashMap<Integer, Double> uplc = utilizationPerLinkCore.get(fastLinkName[l]);
                if (uplc == null) {
                    uplc = new HashMap<>();
                    utilizationPerLinkCore.put(fastLinkName[l], uplc);
                }
                if (!uplc.containsKey(ids[k])) uplc.put(ids[k], 0.0);
            }
            if (!utilizationPerLink.containsKey(fastLinkName[l])) utilizationPerLink.put(fastLinkName[l], 0.0);
        }
        // a map may hold values already (save state of a run with the original path): continue from them
        for (int l = 0; l < n; l++) {
            fastLink[l] = utilizationPerLink.get(fastLinkName[l]);
            final HashMap<Integer, Double> uplc = utilizationPerLinkCore.get(fastLinkName[l]);
            for (int id : fastCoreIds[l]) fastLinkCore[l][id] = uplc.get(id);
        }
        for (int id = 0; id < maxCore; id++) {
            final Double v = utilizationPerCore.get(id);
            fastCore[id] = v == null ? 0.0 : v;
        }
        fastState = 1;
        return true;
    }

    /** The loop of the original observation with the sums in the arrays. */
    private void newObsUtilizationFast(boolean countObservation) {
        final List<Link> links = mesh.getLinkList();
        final int n = links.size();
        double utGeral = 0.0;
        for (int l = 0; l < n; l++) {
            final Link link = links.get(l);
            final List<Core> cores = link.getCores();
            double utLinkAllCores = 0.0;
            for (int k = 0; k < cores.size(); k++) {
                final Core core = cores.get(k);
                final int coreId = fastCoreIds[l][k];
                final double coreUtilization = core.getUtilization();

                utLinkAllCores += coreUtilization;

                if (countObservation) {
                    final List<int[]> classicalFree = core.getFreeSpectrumBands(0);
                    for (int[] faixa : classicalFree) {
                        incrementarDesUtFaixa(faixa);
                    }
                    int[] freeSlot = freeSlotsOf(l, link, coreId);
                    if (freeSlot != null) {
                        List<int[]> bands = core.isQkdReserved() ? core.getFreeSpectrumBandsForQKD(0)
                                : core.isDisabled() ? core.getFreeSpectrumBandsIgnoringReservation(0) : classicalFree;
                        for (int[] faixa : bands) {
                            for (int i = faixa[0] - 1; i < faixa[1] && i < freeSlot.length; i++) freeSlot[i]++;
                        }
                    }
                }

                fastCore[coreId] += coreUtilization / (double) n;
                fastLinkCore[l][coreId] += coreUtilization;
            }
            utLinkAllCores = utLinkAllCores / (double) cores.size();
            utGeral += utLinkAllCores;
            fastLink[l] += utLinkAllCores;
        }
        if (countObservation) {
            utGeral = utGeral / (double) n;
            this.utilizationGen += utGeral;
            this.numberObservations++;
        }
        mapsStale = true;
    }

    /** -Dsnets.verifyPerf=true: the arrays must hold exactly the values of the maps of the original path. */
    private void verifyFast() {
        for (int l = 0; l < fastLink.length; l++) {
            simulator.PerfChecks.same(fastLink[l], utilizationPerLink.get(fastLinkName[l]), "utilizacao do enlace");
            final HashMap<Integer, Double> uplc = utilizationPerLinkCore.get(fastLinkName[l]);
            for (int id : fastCoreIds[l]) simulator.PerfChecks.same(fastLinkCore[l][id], uplc.get(id), "utilizacao do nucleo no enlace");
        }
        for (int id = 0; id < fastCore.length; id++) {
            final Double v = utilizationPerCore.get(id);
            if (v != null) simulator.PerfChecks.same(fastCore[id], v, "utilizacao do nucleo");
        }
        mapsStale = false; // the maps are the original ones in this mode
    }

    /** Copies the sums of the arrays to the maps before they are read. */
    private void syncMaps() {
        if (!mapsStale || fastState <= 0) {
            return;
        }
        mapsStale = false;
        for (int l = 0; l < fastLink.length; l++) {
            utilizationPerLink.put(fastLinkName[l], fastLink[l]);
            final HashMap<Integer, Double> uplc = utilizationPerLinkCore.get(fastLinkName[l]);
            for (int id : fastCoreIds[l]) uplc.put(id, fastLinkCore[l][id]);
        }
        for (int id = 0; id < fastCore.length; id++) {
            if (utilizationPerCore.containsKey(id)) utilizationPerCore.put(id, fastCore[id]);
        }
    }

    /** The original observation (Parte 57: kept for -Dsnets.noPerfCache and -Dsnets.verifyPerf). */
    private void newObsUtilizationOriginal() {
        // General use and per link
        Double utGeral = 0.0;
        int linkIndex = -1;

        for (Link link : mesh.getLinkList()) {
        	linkIndex++;
        	String linkName = link.getSource().getName() + SEP + link.getDestination().getName();
        	Double utLinkAllCores = 0.0;

        	for (Core core : link.getCores()) {
        		Integer coreId = core.getId();
        		Double coreUtilization = core.getUtilization();

        		utLinkAllCores += coreUtilization;

	            // Calculates the non-utilization of slots
	            final List<int[]> classicalFree = core.getFreeSpectrumBands(0); // Parte 45: read once, reused below
	            for (int[] faixa : classicalFree) {
	                incrementarDesUtFaixa(faixa);
	            }
	            // Part 27: real occupancy of each slot (a QKD-reserved core is read through its QKD view,
	            // since the classical view shows it as fully occupied); guard bands count as occupied
	            int[] freeSlot = freeSlotsOf(linkIndex, link, coreId);
	            if (freeSlot != null) {
	                List<int[]> bands = core.isQkdReserved() ? core.getFreeSpectrumBandsForQKD(0)
	                        : core.isDisabled() ? core.getFreeSpectrumBandsIgnoringReservation(0) : classicalFree;
	                for (int[] faixa : bands) {
	                    for (int i = faixa[0] - 1; i < faixa[1] && i < freeSlot.length; i++) freeSlot[i]++;
	                }
	            }

	            // Increment the utilization per core
	            Double utCore = utilizationPerCore.get(coreId);
	            if(utCore == null) utCore = 0.0;
	            utCore += coreUtilization / (double) mesh.getLinkList().size(); //There is the same core index for all links
	            utilizationPerCore.put(coreId, utCore);

	            // Increment the utilization for link and core
	            HashMap<Integer, Double> uplc = this.utilizationPerLinkCore.get(linkName);
	            if (uplc == null) {
	            	uplc = new HashMap<>();
	                this.utilizationPerLinkCore.put(linkName, uplc);
	            }
	            Double utLinkCore = uplc.get(coreId);
	            if (utLinkCore == null) utLinkCore = 0.0;
	            uplc.put(coreId, utLinkCore + coreUtilization);
        	}

        	utLinkAllCores = utLinkAllCores / (double) link.getCores().size();
        	utGeral += utLinkAllCores;

        	// Increment the utilization per link
            Double utLink = this.utilizationPerLink.get(linkName);
            if (utLink == null) utLink = 0.0;
            this.utilizationPerLink.put(linkName, utLink + utLinkAllCores);
        }

        utGeral = utGeral / (double) mesh.getLinkList().size();

        this.utilizationGen += utGeral;
        this.numberObservations++;
    }

    /** Part 27: the per-slot free counters of a link and core, created on the first observation. */
    private int[] freeSlotsOf(int linkIndex, Link link, int coreId) {
        if (freePerLinkCoreSlot == null) {
            int n = mesh.getLinkList().size();
            freePerLinkCoreSlot = new int[n][][];
            linkNames = new String[n];
        }
        if (linkIndex < 0 || linkIndex >= freePerLinkCoreSlot.length || coreId < 0) return null;
        if (freePerLinkCoreSlot[linkIndex] == null) {
            int cores = 0;
            for (Core c : link.getCores()) cores = Math.max(cores, c.getId() + 1);
            freePerLinkCoreSlot[linkIndex] = new int[cores][];
            linkNames[linkIndex] = link.getSource().getName() + SEP + link.getDestination().getName();
        }
        int[][] perCore = freePerLinkCoreSlot[linkIndex];
        if (coreId >= perCore.length) return null;
        if (perCore[coreId] == null) perCore[coreId] = new int[maxSlotsByLinks];
        return perCore[coreId];
    }

    /**
     * Part 27: appends the rows of SlotUtilization.csv of this load point and replication:
     * metric, load point, &lt;link&gt;, core, replication and the occupancy (1 - free fraction) of each slot.
     */
    public void appendSlotUtilizationRows(StringBuilder sb, int slots) {
        if (freePerLinkCoreSlot == null || numberObservations == 0) return;
        for (int l = 0; l < freePerLinkCoreSlot.length; l++) {
            int[][] perCore = freePerLinkCoreSlot[l];
            if (perCore == null) continue;
            for (int c = 0; c < perCore.length; c++) {
                int[] free = perCore[c];
                if (free == null) continue;
                sb.append("Slot utilization").append(',').append(getLoadPoint()).append(",<").append(linkNames[l]).append(">,")
                  .append(c).append(',').append(getReplication());
                for (int s = 0; s < slots; s++) {
                    sb.append(',');
                    if (s >= free.length) { sb.append('0'); continue; }
                    double u = 1.0 - (double) free[s] / (double) numberObservations;
                    sb.append(compact(u));
                }
                sb.append('\n');
            }
        }
    }

    private static String compact(double v) {
        if (v <= 0.0) return "0";
        if (v >= 1.0) return "1";
        String s = String.format(java.util.Locale.US, "%.4f", v);
        int e = s.length();
        while (e > 0 && s.charAt(e - 1) == '0') e--;
        if (e > 0 && s.charAt(e - 1) == '.') e--;
        return e == 0 ? "0" : s.substring(0, e);
    }

    /**
	 * This method increases slot non-utilization
	 *
	 * @param band int[]
	 */
    private void incrementarDesUtFaixa(int faixa[]) {
        for (int i = faixa[0] - 1; i < faixa[1]; i++) {
            desUtilizationPerSlot[i]++;
        }
    }

    /**
     * Returns the HashMap key set
     * The key set corresponds to the links that were analyzed by the metric
     *
     * @return
     */
    public Set<String> getLinkSet() {
        syncMaps(); // Parte 57
        return this.utilizationPerLink.keySet();
    }

    /**
     * Returns the utilization
     *
     * @return
     */
    public double getUtilizationGen() {
        return this.utilizationGen / (double) this.numberObservations;
    }

    /**
     * Returns the utilization for a given link passed by parameter
     *
     * @param link
     * @return
     */
    public double getUtilizationPerLink(String link) {
        syncMaps(); // Parte 57
        Double utLink = this.utilizationPerLink.get(link);
        if (utLink == null) {
        	return 0.0;
        }
        return (utLink / (double) this.numberObservations);
    }

    /**
     * Returns the utilization for a given slot passed by parameter
     *
     * @param Slot
     * @return double
     */
    public double getUtilizationPerSlot(int Slot) {
        double desUt = (double) desUtilizationPerSlot[Slot - 1] / ((double) this.numberObservations * mesh.getLinkList().size() * maxCoresByLinks);

        return 1 - desUt;
    }

    /**
     * Returns the maximum slots by links
     *
     * @return int
     */
    public int getMaxSlotsByLinks(){
    	return maxSlotsByLinks;
    }

    /**
     * Returns the maximum number of cores between all links
     *
     * @return int
     */
    public int getMaxCoresByLinks() {
    	return maxCoresByLinks;
    }

    /**
     * Returns the utilization for a specific core
     *
     * @param core Integer
     * @return double
     */
    public double getUtilizationPerCore(Integer core) {
        syncMaps(); // Parte 57
        Double upc = this.utilizationPerCore.get(core);
        if(upc == null) {
        	return 0.0;
        }
        return (upc / (double) this.numberObservations);
	}

    /**
     * Returns the utilization per link and core
     *
     * @return double
     */
    public double getUtilizationForLinkAndCore(String link, Integer core) {
        syncMaps(); // Parte 57
    	HashMap<Integer, Double> utLinkCore = utilizationPerLinkCore.get(link);

    	if(utLinkCore == null || utLinkCore.get(core) == null) { // No utilization for this link and core
    		return 0.0;
    	}

    	Double utCore = utLinkCore.get(core);

        return (utCore / (double) this.numberObservations);
	}

}
