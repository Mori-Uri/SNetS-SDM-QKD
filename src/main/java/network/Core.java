package network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

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

	/** Physical auxiliary classical channels (currently PICh). They are visible to the QKD physical layer,
     * and considered separately in XT_TOGETHER classical QoT calculations. */
	private HashSet<Circuit> auxiliaryClassicalCircuitList;
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
		this.auxiliaryClassicalCircuitList = new HashSet<Circuit>();
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

		return spectrum.useSpectrum(spectrumBand, guardBand);
	}

	/** Classical release entry point retained unchanged for compatibility. */
	public void liberateSpectrum(int[] spectrumBand, int guardBand)
			throws Exception {
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

		return spectrum.useSpectrum(spectrumBand, guardBand);
	}

	/** Releases spectrum previously allocated through the QKD-only API. */
	public void liberateSpectrumForQKD(int[] spectrumBand, int guardBand)
			throws Exception {
		if (!qkdReserved) {
			throw new IllegalStateException(
					"Core " + id + " is not reserved for QKD");
		}

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
		PhysicalStateChanges.membershipChanged();
	}

	public void addCircuit(Circuit circuit) {
		if (!circuitList.contains(circuit)) {
			circuitList.add(circuit);
			PhysicalStateChanges.membershipChanged();
		}
	}

	public void removeCircuit(Circuit circuit) {
		if (circuitList.remove(circuit)) PhysicalStateChanges.membershipChanged();
	}

	/**
     * Physical auxiliary classical optical sources, currently used by the PICh.
     * They consume spectrum in this core and are included by the QKD physical
     * layer as Raman/NLI interferers, without changing the legacy DCh QoT
     * calculation in this first stage.
     */
	public HashSet<Circuit> getAuxiliaryClassicalCircuitList() {
		return auxiliaryClassicalCircuitList;
	}

	public void addAuxiliaryClassicalCircuit(Circuit circuit) {
		if (circuit != null && auxiliaryClassicalCircuitList.add(circuit))
			PhysicalStateChanges.membershipChanged();
	}

	public void removeAuxiliaryClassicalCircuit(Circuit circuit) {
		if (auxiliaryClassicalCircuitList.remove(circuit)) PhysicalStateChanges.membershipChanged();
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
