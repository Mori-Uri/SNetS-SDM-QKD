package network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

/**
 * One spatial core of an SNetS multi-core fiber.
 *
 * <p>Part 4 adds an explicit QKD reservation flag. The classical SNetS API is
 * preserved, but a reserved core exposes no free spectrum to classical
 * resource-allocation algorithms. This provides a central protection layer:
 * algorithms that obtain availability through {@link #getFreeSpectrumBands(int)}
 * automatically treat the QKD core as unavailable.</p>
 *
 * <p>QKD-specific access methods are provided separately so later stages can
 * allocate quantum spectrum without weakening the classical reservation.</p>
 */
public class Core implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private Spectrum spectrum;
    private int peso;
    private HashSet<Circuit> circuitList;
    private ArrayList<Core> adjacentCores;

    /** True when this physical core is dedicated to QKD. */
    private boolean qkdReserved;

    /** Original SNetS constructor: creates a normal classical core. */
    public Core(int id, int numOfSlots, double slotSpectrumBand) {
        this(id, numOfSlots, slotSpectrumBand, false);
    }

    /** Constructor used by {@link Link} when building QKD-enabled networks. */
    public Core(int id, int numOfSlots, double slotSpectrumBand,
            boolean qkdReserved) {
        this.id = id;
        this.spectrum = new Spectrum(numOfSlots, slotSpectrumBand);
        this.peso = 0;
        this.circuitList = new HashSet<Circuit>();
        this.adjacentCores = new ArrayList<Core>();
        this.qkdReserved = qkdReserved;
    }

    public ArrayList<Core> getAdjacentCores() {
        return adjacentCores;
    }

    public void setAdjacentCores(ArrayList<Core> adjacentCores) {
        this.adjacentCores = adjacentCores;
    }

    /**
     * Classical allocation entry point.
     *
     * <p>A QKD-reserved core rejects classical allocation even if a caller
     * bypassed the normal free-band discovery stage.</p>
     */
    public boolean useSpectrum(int[] spectrumBand, int guardBand)
            throws Exception {
        if (qkdReserved || disabled) {
            return false;
        }
        touch(); // Parte 54
        return spectrum.useSpectrum(spectrumBand, guardBand);
    }

    /** Classical release entry point retained unchanged for compatibility. */
    public void liberateSpectrum(int[] spectrumBand, int guardBand)
            throws Exception {
        touch(); // Parte 54
        spectrum.freeSpectrum(spectrumBand, guardBand);
    }

    /**
     * Classical free-spectrum view. Reserved QKD cores intentionally appear
     * to have no available spectrum.
     */
    public List<int[]> getFreeSpectrumBands(int guardBand) {
        if (qkdReserved || disabled) {
            return Collections.emptyList();
        }
        return spectrum.getFreeSpectrumBands(guardBand);
    }

    /*
     * Part 33: a core disabled in the network file (network.disabledCores) behaves like the QKD core for the
     * classical side: no free spectrum and no allocation, so every algorithm skips it. Default false.
     */
    private boolean disabled;

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    /** Part 33: the real free bands, whatever the flags (used by the occupancy metrics). */
    public List<int[]> getFreeSpectrumBandsIgnoringReservation(int guardBand) {
        return spectrum.getFreeSpectrumBands(guardBand);
    }

    /**
     * QKD-only free-spectrum view prepared for later quantum-channel stages.
     * Non-reserved cores are not exposed through this method.
     */
    public List<int[]> getFreeSpectrumBandsForQKD(int guardBand) {
        if (!qkdReserved) {
            return Collections.emptyList();
        }
        return spectrum.getFreeSpectrumBands(guardBand);
    }

    /**
     * QKD-only spectrum allocation. This is deliberately separate from the
     * classical {@link #useSpectrum(int[], int)} method.
     */
    public boolean useSpectrumForQKD(int[] spectrumBand, int guardBand)
            throws Exception {
        if (!qkdReserved) {
            return false;
        }
        touch(); // Parte 54
        return spectrum.useSpectrum(spectrumBand, guardBand);
    }

    /** Releases spectrum previously allocated through the QKD-only API. */
    public void liberateSpectrumForQKD(int[] spectrumBand, int guardBand)
            throws Exception {
        if (!qkdReserved) {
            throw new IllegalStateException(
                    "Core " + id + " is not reserved for QKD");
        }
        touch(); // Parte 54
        spectrum.freeSpectrum(spectrumBand, guardBand);
    }

    public double getSlotSpectrumBand() {
        return spectrum.getSlotSpectrumBand();
    }

    public int getNumOfSlots() {
        return spectrum.getNumOfSlots();
    }

    public int getUsedSlots() {
        return spectrum.getUsedSlots();
    }

    public Double getUtilization() {
        return Double.valueOf(spectrum.utilization());
    }

    public HashSet<Circuit> getCircuitList() {
        return circuitList;
    }

    public void setCircuitList(HashSet<Circuit> circuitList) {
        this.circuitList = circuitList;
        this.sortedCircuits = null; // Parte 54
        touch();
    }

    public void addCircuit(Circuit circuit) {
        if (!circuitList.contains(circuit)) {
            circuitList.add(circuit);
            if (sortedCircuits != null) sortedCircuits.add(circuit); // Parte 54
            circuit.enteredCoreList(); // Parte 54
            PhysicalStateChanges.membershipChanged();
        }
        touch();
    }

    public void removeCircuit(Circuit circuit) {
        if (circuitList.remove(circuit)) {
            circuit.leftCoreList(); // Parte 54
            PhysicalStateChanges.membershipChanged();
        }
        if (sortedCircuits != null) sortedCircuits.remove(circuit); // Parte 54
        touch();
    }

    /*
     * Parte 54 (desempenho): the QoT and crosstalk code iterates the circuits of a core in id order, and used to build
     * a new TreeSet from the HashSet above at every evaluation. The same ordered set is now kept up to date by
     * addCircuit/removeCircuit. Callers must only read it.
     */
    private transient TreeSet<Circuit> sortedCircuits;

    /** Parte 54: the circuits of this core in id order (read only; the same order as new TreeSet(getCircuitList())). */
    public TreeSet<Circuit> getSortedCircuitList() {
        if (sortedCircuits == null || !simulator.PerfChecks.ENABLED) {
            final TreeSet<Circuit> s = new TreeSet<Circuit>(circuitList);
            if (!simulator.PerfChecks.ENABLED) return s;
            sortedCircuits = s;
        } else if (simulator.PerfChecks.VERIFY) {
            if (!sortedCircuits.equals(new TreeSet<Circuit>(circuitList)) || sortedCircuits.size() != circuitList.size()) {
                throw new IllegalStateException("snets.verifyPerf: lista ordenada de circuitos do nucleo difere");
            }
        }
        return sortedCircuits;
    }

    /*
     * Parte 54: version of the physical state of this core (its circuits). The link that owns the core is told, so the
     * link version changes whenever a circuit is added to or removed from any of its cores.
     */
    private Link owner;

    /** Parte 54: set by the link that creates this core. */
    void setOwner(Link link) {
        this.owner = link;
    }

    /** Parte 54: the physical state of this core changed. */
    public void touch() {
        if (owner != null) owner.touch();
    }

    public int getId() {
        return id;
    }

    public Spectrum getSpectrum() {
        return spectrum;
    }

    public int getPeso() {
        return peso;
    }

    public void setPeso(int peso) {
        this.peso = peso;
    }

    public void incrementPeso() {
        this.peso++;
    }

    public void renewPeso() {
        this.peso = 0;
    }

    public boolean isQkdReserved() {
        return qkdReserved;
    }

    /**
     * Intended for network construction/configuration only. Dynamic toggling
     * during a simulation is not part of the current model.
     */
    public void setQkdReserved(boolean qkdReserved) {
        this.qkdReserved = qkdReserved;
    }
}
