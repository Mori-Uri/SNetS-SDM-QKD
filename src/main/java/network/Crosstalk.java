package network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import simulationControl.parsers.PhysicalLayerConfig;

/**
 * This class calculates crosstalk
 * Reference: [LOBATO et al 2019], [OLIVEIRA 2018], [Ehsani Moghaddam 2019]
 *
 * @author Jurandir
 *
 */
public class Crosstalk implements Serializable {

	private PhysicalLayer physicalLayer;

	private double propagationConstant; // Beta, propagation constant, 1/m
	private double bendingRadius;       // R, Bending radius, m
	private double couplingCoefficient; // k, Coupling coefficient, m^-1
	private double corePitch;           // Lambda, Core pitch, m
	private double h;  // Power-coupling coefficient (he)
	private double K;  // Coupling coefficient in Klinkowski Model
	private int tipoEstimativa;  //1 for Lobato, 2 for Klinkowski, 3 for Klinkowski WCC-XT, 4 for Klinkowski WCF-XT

	public static final double lowestXT = 1.0E-20; //The lowest value, used to represent zero (-200 dB)

	public static final double SIGNALPOWER = 1.259E-4; //1.259E-4 W = -9 dBm.  The scenario that considers the same power for all lightpaths.

	/**
     * Instantiates a Crosstalk
     */
	public Crosstalk(PhysicalLayer physicalLayer, PhysicalLayerConfig plc) {
		this.physicalLayer = physicalLayer;

		this.propagationConstant = plc.getPropagationConstant();
		this.bendingRadius = plc.getBendingRadius();
		this.couplingCoefficient = plc.getCouplingCoefficient();
		this.corePitch = plc.getCorePitch();

		this.h = calculateH();
		this.K = calculateK();
		this.tipoEstimativa = 1; //1 for Lobato, 2 for Klinkowski, 3 for Klinkowski WCC-XT, 4 for Klinkowski WCF-XT
	}

	/**
	 * This method returns the value of the coupling coefficient (k)
	 *
	 * @return double
	 */
	private double calculateK() {
		if(this.tipoEstimativa==1) {
			return 0; //don't use K
		}

		if(this.tipoEstimativa==2) {
			return 1; //to update
		}

		if(this.tipoEstimativa==3) {
			return 3; //to update
		}

		if(this.tipoEstimativa==4) {
			return 6;
		}

		return 0;
	}

	/**
	 * This method calculates h (power-coupling coefficient), from [LOBATO et al 2019]
	 *
	 * @return double h
	 */
	private double calculateH() {
		return ((2.0 * couplingCoefficient * couplingCoefficient * bendingRadius) / (propagationConstant * corePitch));
	}

	/**
	 * This method returns the value of the fiber power coupling coefficient
	 *
	 * @return double
	 */
	public double getH() {
		return h;
	}

	/**
	 * OLIVEIRA 2018 and [Ehsani Moghaddam 2019]
	 * This method returns the crosstalk threshold value for the informed modulation
	 *
	 * @param modulation Modulation
	 * @return double (value in dB)
	 */
//	public double getXtThreshold(Modulation modulation) {
//		double XT_threshold = 0.0;
//
//		if (modulation.getM() == 2.0) { //BPSK
//			XT_threshold = XTBPSK;
//		} else if (modulation.getM() == 4.0) { //QPSK
//			XT_threshold = XTQPSK;
//		} else if (modulation.getM() == 8.0) { //8QAM
//			XT_threshold = XT8QAM;
//		} else if (modulation.getM() == 16.0) { //16QAM
//			XT_threshold = XT16QAM;
//		} else if (modulation.getM() == 32.0) { //32QAM
//			XT_threshold = XT32QAM;
//		} else if (modulation.getM() == 64.0) { //64QAM
//			XT_threshold = XT64QAM;
//		}
//
//		return XT_threshold;
//	}

	/**
	 * OLIVEIRA 2018 and [Ehsani Moghaddam 2019]
	 * This method returns the crosstalk threshold value for the informed modulation
	 *
	 * @param modulation Modulation
	 * @return double (value in dB)
	 */
//	public double getXtThreshold2(Modulation modulation) {
//		switch(modulation.getName()) {
//			case "BPSK":
//				return XTBPSK;
//			case "QPSK":
//				return XTQPSK;
//			case "8QAM":
//				return XT8QAM;
//			case "16QAM":
//				return XT16QAM;
//			case "32QAM":
//				return XT32QAM;
//			case "64QAM":
//				return XT64QAM;
//			default:
//				return 0.0;
//		}
//	}

	/**
	 * This method calculates the crosstalk on a given circuit
	 *
	 * @param circuit Circuit
	 * @param testCircuit Circuit
	 * @param addTestCircuit boolean
	 * @return double (value in dB)
	 */
	public double calculateCrosstalk(Circuit circuit, Circuit testCircuit, boolean addTestCircuit) {
		Route rota = circuit.getRoute();
		double totalXT = 0.0;

		//Modelo Lobato
		if(this.tipoEstimativa==1) {
			for(Link link: rota.getLinkList()) {
				totalXT = totalXT + calculateCrosstalkInLink(circuit, link, testCircuit, addTestCircuit);
			}
		}

		// Modelo Klinkowski precise method
		if(this.tipoEstimativa==2) {
			for(Link link: rota.getLinkList()) {
				totalXT = totalXT + calculateCrosstalkInLink2(circuit, link);
			}
		}

		// Modelo Klinkowski WCC-XT
		if(this.tipoEstimativa==3) {
			for(Link link: rota.getLinkList()) {
				totalXT = totalXT + calculateCrosstalkInLink2(circuit, link);
			}
		}

		// Modelo Klinkowski WCF-XT
		if(this.tipoEstimativa==4) {
			for(Link link: rota.getLinkList()) {
				totalXT = totalXT + calculateCrosstalkInLink2(circuit, link);
			}
		}

		// PICh sources are registered as auxiliary classical optical sources,
		// so none of the legacy DCh neighbour searches above sees them.
		// Convert PICh interference power to a linear XT ratio before the
		// conversion to dB. This also enables the XT_SEPARATE admission path.
		if (rota != null) {
			for (Link link : rota.getLinkList()) {
				final Integer victimCore = circuit.getIndexCoreByLink(link);

				if (victimCore == null || victimCore < 0) continue;

				final Core core = link.getCore(victimCore);

				if (core == null) continue;

				final int[] linkSpectrum = circuit.getSpectrumAssignedByLink(link);

				if (Crosstalk.overlappingSlots(linkSpectrum, linkSpectrum) == 0)
					continue;

				// Modulation and launch power may vary from one SNetS link
				// to the next. Never normalize all PICh XT by a global value.
				final double signalPower = physicalLayer.getCircuitLaunchPower(
						circuit, circuit.getModulationByLink(link));

				if (!(signalPower > 0.0) || !Double.isFinite(signalPower)) continue;

				final double bandwidth = (linkSpectrum[1] - linkSpectrum[0] + 1)
						* core.getSlotSpectrumBand();
				final double pichPsd = qkd.capacity.PichClassicalCrosstalk.noisePsd(
						link, victimCore, linkSpectrum, bandwidth, h, 2.0, false);

				totalXT += pichPsd * bandwidth / signalPower;
			}
		}

		if(totalXT == 0.0) {
			totalXT = lowestXT;
		}

		return PhysicalLayer.ratioForDB(totalXT);
	}

	/**
	 * This method calculates the crosstalk on a given circuit
	 *
	 * @param circuit Circuit
	 * @param testCircuit Circuit
	 * @param addTestCircuit boolean
	 * @return double (value in linear)
	 */
	public double calculateCrosstalk2(Circuit circuit, Circuit testCircuit, boolean addTestCircuit) {
		Route rota = circuit.getRoute();
		double totalXT = 0.0;

		//Modelo Lobato
		if(this.tipoEstimativa==1) {
			for(Link link: rota.getLinkList()) {
				totalXT = totalXT + calculateCrosstalkInLink3(circuit, link, testCircuit, addTestCircuit);
			}
		}

		return totalXT;
	}

	/**
	 * This method verifies if the crosstalk is acceptable in the informed circuit
	 *
	 * @param circuit Circuit
	 * @return boolean
	 */
	public boolean isAdmissible(Circuit circuit) {
		double xtDB = calculateCrosstalk(circuit, null, false);
		double xtThreshold = circuit.getModulation().getXTthreshold();

		if(xtDB > xtThreshold) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * This method checks if the informed crosstalk is acceptable for the circuit
	 *
	 * @param circuit Circuit
	 * @param xtDB double (in dB)
	 * @return boolean
	 */
	public boolean isAdmissible(Circuit circuit, double xtDB) {
		double xtThreshold = circuit.getModulation().getXTthreshold();

		if(xtDB > xtThreshold) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Method that returns the lowest delta XT value of the adjacent circuits of the informed circuit.
	 *
	 * @param circuit Circuit
	 * @return Double lowest delta XT (Linear)
	 */
	public Double getMinDeltaXTofAdjacents(Circuit circuit) {
		TreeSet<Circuit> circuitosAdj = new TreeSet<Circuit>();
		Route rota = circuit.getRoute();

		for (Link link: rota.getLinkList()) {
			ArrayList<Core> adjacentsCores = link.getAdjacentCores(circuit.getIndexCoreByLink(link));

			for (Core core : adjacentsCores) {
				for (Circuit adjacentCircuit : core.getCircuitList()) {
					if (isIntersection(circuit.getSpectrumAssignedByLink(link), adjacentCircuit.getSpectrumAssignedByLink(link))) {
						if (!circuitosAdj.contains(adjacentCircuit)) {
							circuitosAdj.add(adjacentCircuit);
						}
					}
				}
			}
		}

		double xtThreshold = 0.0;
		double xt = 0.0;
		double deltaXT = 0.0;
		double minDeltaXT = Double.MAX_VALUE;

		for (Circuit circuitNeighbor : circuitosAdj) {
			xtThreshold = circuitNeighbor.getModulation().getXTthresholdLinear();
			xt = calculateCrosstalk(circuitNeighbor, circuit, true);

			deltaXT = xtThreshold - PhysicalLayer.ratioOfDB(xt);

			if(deltaXT < minDeltaXT) {
				minDeltaXT = deltaXT;
			}
		}

		return minDeltaXT;
	}

	/**
	 * This method check the crosstalk (XT) on others circuits
	 *
	 * @param circuit Circuit
	 * @return boolean Return true if the XT of other circuits is less than threshold
	 */
	public boolean isAdmissibleInOthers(Circuit circuit) {
		TreeSet<Circuit> circuitosAdj = new TreeSet<Circuit>();
		Route rota = circuit.getRoute();

		for(Link link: rota.getLinkList()) {
			ArrayList<Core> adjacentsCores = link.getAdjacentCores(circuit.getIndexCoreByLink(link));

			for(Core core : adjacentsCores) {
				for(Circuit adjacentCircuit : core.getCircuitList()) {
					if (isIntersection(circuit.getSpectrumAssignedByLink(link), adjacentCircuit.getSpectrumAssignedByLink(link))) {
						if(!circuitosAdj.contains(adjacentCircuit)) {
							circuitosAdj.add(adjacentCircuit);
						}
					}
				}
			}
		}

		//If the list of adjacent circuits is empty
		if (circuitosAdj.isEmpty()) { return true; }

		double xtJThreshold = 0.0;
		double newXt = 0.0;

		for (Circuit circuitNeighbor : circuitosAdj) {
			xtJThreshold = circuitNeighbor.getModulation().getXTthreshold();
			newXt = calculateCrosstalk(circuitNeighbor, circuit, true);

			if(newXt > xtJThreshold) {
				return false;
			}
		}

		return true;
	}

	/**
	 * This method check the crosstalk (XT) on others circuits
	 *
	 * @param circuit Circuit
	 * @return Return true if the XT of other circuits is less than threshold
	 */
	public boolean isAdmissibleInOthers2(Circuit circuit) {
		double xtJThreshold = 0.0;
		double xtJ = 0.0;
		double nsoij = 0.0; // number of overlapping slots between i and j
		double nsj = 0.0; //number of slots of the connection j
		double newXt = 0.0;
		double Isoij = 0.0;

		TreeSet<Circuit> circuitosAdj = new TreeSet<Circuit>();
		Route rota = circuit.getRoute();

		for(Link link: rota.getLinkList()) {
			ArrayList<Core> adjacentsCores = link.getAdjacentCores(circuit.getIndexCoreByLink(link));

			for(Core core : adjacentsCores) {
				for(Circuit adjacentCircuit : core.getCircuitList()) {
					if(isIntersection(circuit.getSpectrumAssignedByLink(link), adjacentCircuit.getSpectrumAssignedByLink(link)) ) {
						if(!circuitosAdj.contains(adjacentCircuit)) {
							circuitosAdj.add(adjacentCircuit);
						}
					}
				}
			}
		}

		//If the list of adjacent circuits is empty
		if (circuitosAdj.isEmpty()) { return true; }

		for (Circuit circuitNeighbor : circuitosAdj) {
			xtJ = circuitNeighbor.getXt();
			xtJThreshold = circuitNeighbor.getModulation().getXTthreshold();

			//System.out.println(circuitNeighbor.getId()+"xt atual="+xtJ+"  limiar="+xtJThreshold);

			newXt = 0.0;
			nsoij = 0.0;
			nsj = 0.0;

			for (Link link : circuitNeighbor.route.getLinkList()) {
				if (circuit.getRoute().getLinkList().contains(link)) {
					if(isIntersection(circuit.getSpectrumAssignedByLink(link), circuitNeighbor.getSpectrumAssignedByLink(link))) {
						nsj = sizeSpectrumAllocate(circuit.getSpectrumAssignedByLink(link));
						nsoij = numberOfOverlapping(circuitNeighbor.getSpectrumAssignedByLink(link), circuit.getSpectrumAssignedByLink(link));

						if(nsj != 0.0){
							Isoij = nsoij / nsj;
						}else {
							Isoij = 0.0;
						}

						newXt += ((Isoij*SIGNALPOWER*h*link.getDistance()*1000)/SIGNALPOWER);
					}
				}
			}

			//System.out.println(newXt);
			//newXt = xtJ - newXt;
			xtJ = PhysicalLayer.ratioOfDB(xtJ);
			newXt = xtJ + newXt;
			newXt = PhysicalLayer.ratioForDB(newXt);

			if(newXt > xtJThreshold) {
				//System.out.println("blocking");

				return false;
			}
		}

		//System.out.println("No blocking");

		return true;
	}

	/**
	 * Test print method
	 *
	 * @param circuit Circuit
	 * @return boolean
	 */
	public boolean isAdmissibleInOthersImprimirLog(Circuit circuit) {
		//double xtJThreshold = xtThreshold(circuit.getModulation());
		double xtJ = 0.0;
		double nsoij = 0.0; // number of overlapping slots between i and j
		double nsj = 0.0; //number of slots of the connection j
		double newXt = 0.0;

		ArrayList<Circuit> circuitosAdj = new ArrayList<Circuit>();
		Route rota = circuit.getRoute();

		for(Link link: rota.getLinkList()) {
			ArrayList<Core> adjacentsCores = link.getAdjacentCores(circuit.getIndexCoreByLink(link));

			for(Core core : adjacentsCores) {
				for(Circuit circuit2 : core.getCircuitList()) {
					if(isIntersection(circuit.getSpectrumAssignedByLink(link), circuit2.getSpectrumAssignedByLink(link))) {
						if(!circuitosAdj.contains(circuit2)) {
							circuitosAdj.add(circuit2);
						}
					}
				}
			}
		}

		if (circuitosAdj.isEmpty()) {return true;}

		for (Circuit circuitNeighbor : circuitosAdj) {
			//System.out.println(circuitNeighbor.getId());
			xtJ = circuitNeighbor.getXt();
			//xtJThreshold = xtThreshold(circuitNeighbor.getModulation());

			//System.out.println(circuitNeighbor.getId()+"xt atual="+xtJ+"  limiar="+xtJThreshold);

			newXt = 0.0;
			nsoij = 0.0;
			nsj = 0.0;

			for (Link link : circuitNeighbor.route.getLinkList()) {
				if (circuit.getRoute().getLinkList().contains(link)) {
					if(isIntersection(circuit.getSpectrumAssignedByLink(link), circuitNeighbor.getSpectrumAssignedByLink(link))) {
						nsj = sizeSpectrumAllocate(circuit.getSpectrumAssignedByLink(link));
						nsoij = numberOfOverlapping(circuit.getSpectrumAssignedByLink(link), circuitNeighbor.getSpectrumAssignedByLink(link));
						newXt += (((nsoij/nsj)*SIGNALPOWER*h*link.getDistance()*1000)/SIGNALPOWER);
					}
				}
			}

			//System.out.println(newXt);
			//newXt = xtJ - newXt;
			xtJ = PhysicalLayer.ratioOfDB(xtJ);
			newXt = xtJ + newXt;
			newXt = PhysicalLayer.ratioForDB(newXt);

			//System.out.println(circuitNeighbor.getId()+"New xt ="+newXt+"  limiar="+xtJThreshold+"\n");
			//System.out.println("--The crosstalk on optical path "+circuitNeighbor.getId()+", already active on core "+circuitNeighbor.getIndexCore()+", after allocating optical path "+circuit.getId()+" will be: "+newXt);

			System.out.println("--The crosstalk on optical path "+circuitNeighbor.getId()+", already active on core "+circuitNeighbor.getIndexCore()+", after allocating optical path "+circuit.getId()+" will be: "+newXt+" (threshold: "+circuitNeighbor.getModulation().getXTthreshold()+")");

			//if(newXt > xtJThreshold) {
			//	return false;
			//}
		}

		return true;
	}

	/**
	 * This method updates the crosstalk in circuits adjacent to the reported circuit.
	 *
	 * @param circuit Circuit
	 * @param addCircuit boolean - Adding or removing the circuit of the crosstalk calculation
	 * @param printLog boolean - Whether or not to print the Log
	 */
	public void updateXTinOthers(Circuit circuit, boolean addCircuit, boolean printLog) {
		TreeSet<Circuit> circuitosAdj = new TreeSet<Circuit>();
		Route rota = circuit.getRoute();

		for(Link link: rota.getLinkList()) {
			ArrayList<Core> adjacentsCores = link.getAdjacentCores(circuit.getIndexCoreByLink(link));

			for(Core core : adjacentsCores) {
				for(Circuit adjacentCircuit : core.getCircuitList()) {
					if(isIntersection(circuit.getSpectrumAssignedByLink(link), adjacentCircuit.getSpectrumAssignedByLink(link)) ) {
						if(!circuitosAdj.contains(adjacentCircuit)) {
							circuitosAdj.add(adjacentCircuit);
						}
					}
				}
			}
		}

		for (Circuit circuitNeighbor : circuitosAdj) {
			double newXt = calculateCrosstalk(circuitNeighbor, circuit, addCircuit);
			circuitNeighbor.setXt(newXt);

			if(printLog){
				System.out.println("--The crosstalk on optical path "+circuitNeighbor.getId()+", already active on core "+circuitNeighbor.getIndexCore()+", after allocating optical path "+circuit.getId()+" will be: "+newXt+" (threshold: "+circuitNeighbor.getModulation().getXTthreshold()+")");
			}
		}
	}

	/**
	 * Method to update the value of XT in the neighbors of the new optical path
	 *
	 * @param circuit Circuit
	 */
	public void updateXTinOthers2(Circuit circuit) {
		//double xtJThreshold = xtThreshold(circuit.getModulation());
		double xtJ = 0.0;
		double nsoij = 0.0; // number of overlapping slots between i and j
		double nsj = 0.0; //number of slots of the connection j
		double newXt = 0.0;

		ArrayList<Circuit> circuitosAdj = new ArrayList<Circuit>();
		Route rota = circuit.getRoute();

		for(Link link: rota.getLinkList()) {
			ArrayList<Core> adjacentsCores = link.getAdjacentCores(circuit.getIndexCoreByLink(link));

			for(Core core : adjacentsCores) {
				for(Circuit circuit2 : core.getCircuitList()) {
					if(isIntersection(circuit.getSpectrumAssignedByLink(link), circuit2.getSpectrumAssignedByLink(link))) {
						if(!circuitosAdj.contains(circuit2)) {
							circuitosAdj.add(circuit2);
						}
					}
				}
			}
		}

		//if (circuitosAdj.isEmpty()) {return void;}

		for (Circuit circuitNeighbor2 : circuitosAdj) {
			xtJ = circuitNeighbor2.getXt();
			//xtJThreshold = xtThreshold(circuitNeighbor2.getModulation());

			//System.out.println(circuitNeighbor.getId()+"xt atual="+xtJ+"  limiar="+xtJThreshold);

			newXt = 0.0;
			nsoij = 0.0;
			nsj = 0.0;

			for (Link link : circuitNeighbor2.route.getLinkList()) {
				if (circuit.getRoute().getLinkList().contains(link)) {
					if(isIntersection(circuit.getSpectrumAssignedByLink(link), circuitNeighbor2.getSpectrumAssignedByLink(link))) {
						nsj = sizeSpectrumAllocate(circuit.getSpectrumAssignedByLink(link));
						nsoij = numberOfOverlapping(circuit.getSpectrumAssignedByLink(link), circuitNeighbor2.getSpectrumAssignedByLink(link));
						newXt += (((nsoij/nsj)*SIGNALPOWER*h*link.getDistance()*1000)/SIGNALPOWER);
					}
				}
			}

			xtJ = PhysicalLayer.ratioOfDB(xtJ);
			newXt = xtJ + newXt;
			newXt = PhysicalLayer.ratioForDB(newXt);

			circuitNeighbor2.setXt(newXt);
		}
	}

	/**
	 * Method to update the value of XT in the neighbors of the new optical path
	 *
	 * @param circuit Circuit
	 */
	public void updateXTinOthersUnderRemove(Circuit circuit) {
		//double xtJThreshold = xtThreshold(circuit.getModulation());
		double xtJ = 0.0;
		double nsoij = 0.0; // number of overlapping slots between i and j
		double nsj = 0.0; //number of slots of the connection j
		double newXt = 0.0;

		ArrayList<Circuit> circuitosAdj = new ArrayList<Circuit>();
		Route rota = circuit.getRoute();

		for(Link link: rota.getLinkList()) {
			ArrayList<Core> adjacentsCores = link.getAdjacentCores(circuit.getIndexCoreByLink(link));

			for(Core core : adjacentsCores) {
				for(Circuit adjacentCircuit : core.getCircuitList()) {
					if (isIntersection(circuit.getSpectrumAssignedByLink(link), adjacentCircuit.getSpectrumAssignedByLink(link))) {
						if(!circuitosAdj.contains(adjacentCircuit)) {
							circuitosAdj.add(adjacentCircuit);
						}
					}
				}
			}
		}

		//if (circuitosAdj.isEmpty()) {return void;}

		//System.out.println("\n----------------");

		for (Circuit circuitNeighbor2 : circuitosAdj) {
			xtJ = circuitNeighbor2.getXt();
			//xtJThreshold = xtThreshold(circuitNeighbor2.getModulation());

			//System.out.println(circuitNeighbor.getId()+"xt atual="+xtJ+"  limiar="+xtJThreshold);

			newXt = 0.0;
			nsoij = 0.0;
			nsj = 0.0;

			for (Link link : circuitNeighbor2.route.getLinkList()) {
				if (circuit.getRoute().getLinkList().contains(link)) {
					if(isIntersection(circuit.getSpectrumAssignedByLink(link), circuitNeighbor2.getSpectrumAssignedByLink(link))) {
						nsj = sizeSpectrumAllocate(circuit.getSpectrumAssignedByLink(link));
						nsoij = numberOfOverlapping(circuit.getSpectrumAssignedByLink(link), circuitNeighbor2.getSpectrumAssignedByLink(link));
						newXt += (((nsoij/nsj)*SIGNALPOWER*h*link.getDistance()*1000)/SIGNALPOWER);
					}
				}
			}

			//System.out.println("XT on optical path "+circuitNeighbor2.getId()+"  is equal to "+circuitNeighbor2.getXt() );

			xtJ = PhysicalLayer.ratioOfDB(xtJ);
			newXt = xtJ - newXt;

			//System.out.println("\n\n\n\n"+newXt);

			if(newXt <= Crosstalk.lowestXT) {
				circuitNeighbor2.setXt(Crosstalk.lowestXT);
			}else {
				newXt = PhysicalLayer.ratioForDB(newXt);
				circuitNeighbor2.setXt(newXt);
			}

			//System.out.println("After removing optical path "+circuit.getId()+", o XT on optical path "+circuitNeighbor2.getId()+" becomes equal to "+circuitNeighbor2.getXt() );
		}
	}

	/**
	 * Modelo de Lobato et al 2019
	 *
	 * This method returns the value of the crosstlak in the given link
	 *
	 * @param circuit Circuit
	 * @param link Link
	 * @param testCircuit Circuit
	 * @param addTestCircuit boolean
	 * @return double
	 */
	private double calculateCrosstalkInLink(Circuit circuit, Link link, Circuit testCircuit, boolean addTestCircuit) {
		double xtInLink = 0.0;
		double Pxt = 0.0;

		double Pm = physicalLayer.getCircuitLaunchPower(circuit, circuit.getModulation());

		final Integer victimCore = circuit.getIndexCoreByLink(link);

		if (victimCore == null || victimCore < 0) return 0.0;

		final int[] victimBand = circuit.getSpectrumAssignedByLink(link);

		if (victimBand == null) return 0.0;

		ArrayList<Core> adjacentsCores = link.getAdjacentCores(victimCore);

		for (Core core : adjacentsCores) {

			TreeSet<Circuit> adjacentCircuitList = new TreeSet<Circuit>();

			for (Circuit circtuiTemp : core.getCircuitList()) {
				adjacentCircuitList.add(circtuiTemp);
			}

			if (testCircuit != null && testCircuit.getRoute() != null
					&& testCircuit.getRoute().getLinkList().contains(link)
					&& testCircuit.getIndexCoreByLink(link) != null
					&& link.getCore(testCircuit.getIndexCoreByLink(link)) == core) {
				if(!adjacentCircuitList.contains(testCircuit) && addTestCircuit) {
					adjacentCircuitList.add(testCircuit);
				}

				if(adjacentCircuitList.contains(testCircuit) && !addTestCircuit) {
					adjacentCircuitList.remove(testCircuit);
				}
			}

			Pxt = calculatePxt(circuit, victimBand, link, adjacentCircuitList);

			if (Pm > 0.0) {
				xtInLink += Pxt / Pm; //XT power normalized
			}
		}

		return xtInLink;
	}

	/**
	 * Modelo de Lobato et al 2019
	 *
	 * This method returns the value of the crosstlak in the given link
	 *
	 * @param circuit Circuit
	 * @param link Link
	 * @param testCircuit Circuit
	 * @param addTestCircuit boolean
	 * @return double
	 */
	private double calculateCrosstalkInLink3(Circuit circuit, Link link, Circuit testCircuit, boolean addTestCircuit) {
		double xtInLink = 0.0;
		double Pxt = 0.0;

		final Integer victimCore = circuit.getIndexCoreByLink(link);

		if (victimCore == null || victimCore < 0) return 0.0;

		final int[] victimBand = circuit.getSpectrumAssignedByLink(link);

		if (victimBand == null) return 0.0;

		ArrayList<Core> adjacentsCores = link.getAdjacentCores(victimCore);

		for (Core core : adjacentsCores) {

			TreeSet<Circuit> adjacentCircuitList = new TreeSet<Circuit>();

			for (Circuit circtuiTemp : core.getCircuitList()) {
				adjacentCircuitList.add(circtuiTemp);
			}

			if (testCircuit != null && testCircuit.getRoute() != null
					&& testCircuit.getRoute().getLinkList().contains(link)
					&& testCircuit.getIndexCoreByLink(link) != null
					&& link.getCore(testCircuit.getIndexCoreByLink(link)) == core) {
				if(!adjacentCircuitList.contains(testCircuit) && addTestCircuit) {
					adjacentCircuitList.add(testCircuit);
				}

				if(adjacentCircuitList.contains(testCircuit) && !addTestCircuit) {
					adjacentCircuitList.remove(testCircuit);
				}
			}

			Pxt = calculatePxt(circuit, victimBand, link, adjacentCircuitList);

			xtInLink += Pxt;
		}

		return xtInLink;
	}

	/**
	 * Modelo de Lobato et al 2019
	 *
	 * This method returns the value of the crosstlak in the given link
	 *
	 * @param circuit Circuit
	 * @param link Link
	 * @param coreIndex int
	 * @param spectrumAssigned int[]
	 * @param testCircuit Circuit
	 * @param addTestCircuit boolean
	 * @return double
	 */
	public double calculateCrosstalkPowerInLink(Circuit circuit, Link link, int coreIndex, int[] spectrumAssigned, Circuit testCircuit, boolean addTestCircuit) {

		double pxtInLink = 0.0;

		ArrayList<Core> adjacentCores = link.getAdjacentCores(coreIndex);

		for (Core adjacentCore : adjacentCores) {

			TreeSet<Circuit> adjacentCircuitList = new TreeSet<>();

			adjacentCircuitList.addAll(adjacentCore.getCircuitList());

			if (testCircuit != null && testCircuit.getRoute() != null
					&& testCircuit.getRoute().getLinkList().contains(link)
					&& testCircuit.getIndexCoreByLink(link) != null
					&& link.getCore(testCircuit.getIndexCoreByLink(link)) == adjacentCore) {

				if (addTestCircuit) {
					adjacentCircuitList.add(testCircuit);
				} else {
					adjacentCircuitList.remove(testCircuit);
				}
			}

			pxtInLink += calculatePxt(circuit, spectrumAssigned, link, adjacentCircuitList);
		}

		return pxtInLink;
	}

	/*
	 * This method calculates the crosstalk power in a given circuit
	 *
	 * @param circuit Circuit
	 * @param spectrumAssigned int[]
	 * @param link Link
	 * @param adjacentCircuitList TreeSet<Circuit>
	 * @return double
	 */
	private double calculatePxt(Circuit victim, int[] victimBand,
			Link link, TreeSet<Circuit> adjacentCircuitList) {
		double interferingPowerW = 0.0;

		for (Circuit interferer : adjacentCircuitList) {
			// Circuit may choose a different signal band on each physical link.
			// Guard reservations remain separate and are NOT passed here.
			final int[] interfererBand = interferer.getSpectrumAssignedByLink(link);
			final double iso = Crosstalk.interfererFraction(
					victimBand, interfererBand);

			if (iso == 0.0) continue;

			final double powerW = physicalLayer.getCircuitLaunchPower(
					interferer, interferer.getModulationByLink(link));

			// Lacerda Jr., thesis Eq. (3.35), link distance in metres.
			interferingPowerW += powerW * iso * h * link.getDistance() * 1000.0;
		}

		return interferingPowerW;
	}

	/**
	 * This method uses the traditional Klinkowski Model 2019 to calculate the crosstalk
	 *
	 * @param circuit Circuit
	 * @param link Link
	 * @return double
	 */
	private double calculateCrosstalkInLink2(Circuit circuit, Link link) {

		double xtInLink = 0.0;
		ArrayList<Core> adjacentsCores = link.getAdjacentCores(circuit.getIndexCoreByLink(link));

		// Klinkowski 2019 accurate estimate
		if(this.tipoEstimativa==2) {
			this.K = 1;
			boolean flag = false;

			//System.out.println("core: "+circuit.getIndexCore());

			for(Core core : adjacentsCores) {
				List<int[]> espectrosLivres = new ArrayList<>();
				espectrosLivres = core.getSpectrum().getFreeSpectrumBands();

				for(int[] i : espectrosLivres) {
					if(numberOfOverlapping(i, circuit.getSpectrumAssigned()) != (circuit.getSpectrumAssigned()[1] - circuit.getSpectrumAssigned()[0])+1) {
						flag = true;
						break;
					}
				}

				if(flag) {
					//System.out.println(core.getId());
					xtInLink = xtInLink + ((SIGNALPOWER*h*link.getDistance()*1000)/SIGNALPOWER);
				}

				//System.out.println(core.getId());
				//xtInLink = xtInLink + ((SIGNALPOWER*h*link.getDistance()*1000)/SIGNALPOWER);

			}

			//System.out.println("-------------");
		}

		// Klinkowski 2019 WCC-XT
		if(this.tipoEstimativa==3) {
			if(circuit.getIndexCore()==0) {
				this.K = 6;
			}else {
				this.K = 3;
			}

			for(int i=1; i<=K; i++) {
				//xtInLink = xtInLink + ((SIGNALPOWER*h*link.getDistance()*1000)/SIGNALPOWER);
				xtInLink = xtInLink + ((SIGNALPOWER*h*link.getDistance()*1000)/SIGNALPOWER);
			}
		}

		// Klinkowski 2019 WCF-XT
		if(this.tipoEstimativa==4) {
			this.K = 6;

			for(int i=1; i<=K; i++) {
				//xtInLink = xtInLink + ((SIGNALPOWER*h*link.getDistance()*1000)/SIGNALPOWER);
				xtInLink = xtInLink + ((SIGNALPOWER*h*link.getDistance()*1000)/SIGNALPOWER);
			}
		}

		return xtInLink;
	}

	/**
	 * This method reports the number of slots in a range of slots
	 *
	 * @param spectrum int[]
	 * @return int
	 */
	private int sizeSpectrumAllocate(int[] spectrum) {
		return spectrum[1] - spectrum[0] + 1;
	}

	/**
	 * This method calculates the spectral overlap index
	 *
	 * @param circuit Circuit
	 * @param adjacentCircuitList TreeSet<Circuit>
	 * @return double
	 */
	private double calculateIsoij(Circuit circuit, TreeSet<Circuit> adjacentCircuitList) {
		//int[] spectrum1 = circuit.getSpectrumAssigned();
		double nsoij = 0.0; // number of overlapping slots between i and j
		double nsj = 0.0; //number of slots of the connection j
		double Isoij = 0.0;
		//int quantInter = 0;

		for(Circuit adjacentCircuit : adjacentCircuitList) {
			if(isIntersection(circuit.getSpectrumAssigned(), adjacentCircuit.getSpectrumAssigned())) {
				nsj = sizeSpectrumAllocate(adjacentCircuit.getSpectrumAssigned());
				nsoij = numberOfOverlapping(circuit.getSpectrumAssigned(), adjacentCircuit.getSpectrumAssigned());
				//quantInter++;
				//nsoij = nsoij + numberOfOverlapping(spectrum1, circuit2.getSpectrumAssigned());

				if(nsj != 0.0){
					Isoij += nsoij / nsj;
				}else {
					Isoij += 0.0;
				}
			}
		}

		return Isoij;
	}

	/**
	 * This method checks the intersection between two spectrum bands
	 *
	 * @param spectrum1 int[]
	 * @param spectrum2 int[]
	 * @return boolean
	 */
	public boolean isIntersection(int[] spectrum1, int[] spectrum2) {
		return Crosstalk.overlappingSlots(spectrum1, spectrum2) > 0;
	}

	/**
	 * This method retrofits the amount of overlapping slots between two spectrum bands
	 *
	 * @param spectrum1 int[]
	 * @param spectrum2 int[]
	 * @return int
	 */
	private int numberOfOverlapping(int[] spectrum1, int[] spectrum2) {
		return Crosstalk.overlappingSlots(spectrum1, spectrum2);
	}

	/**
	 * This method computes the Maximum Power Allowed by the XT threshold of a given modulation.
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param modulation Modulation
	 * @param core int
	 * @param spectrumAssigned int[]
	 * @return double (linear value)
	 */
	public double computeMaximumPowerAllowedByXT(Circuit circuit, Route route, Modulation modulation, int core, int spectrumAssigned[]) {

		double PmaxXT = 0.0;
		double XTcoefficient = 0.0;

		double nsoij = 0.0; // number of overlapping slots between i and j
		double nsj = 0.0; // number of slots of the connection j
		double Isoij = 0.0;

		//Modelo Lobato
		if (this.tipoEstimativa==1) {
			for (Link link: route.getLinkList()) {

				ArrayList<Core> adjacentsCores = link.getAdjacentCores(core);

				for (Core adjCore : adjacentsCores) {

					TreeSet<Circuit> adjacentCircuitList = new TreeSet<Circuit>();

					for (Circuit circtuiTemp : adjCore.getCircuitList()) {
						adjacentCircuitList.add(circtuiTemp);
					}

					for (Circuit adjacentCircuit : adjacentCircuitList) {
						if (isIntersection(spectrumAssigned, adjacentCircuit.getSpectrumAssignedByLink(link))) {
							nsj = sizeSpectrumAllocate(adjacentCircuit.getSpectrumAssignedByLink(link));
							nsoij = numberOfOverlapping(spectrumAssigned, adjacentCircuit.getSpectrumAssignedByLink(link));

							if (nsj != 0.0){
								Isoij = nsoij / nsj;
							} else {
								Isoij = 0.0;
							}

							XTcoefficient += Isoij * h * link.getDistance()*1000.0; //Multiply by 1000 to convert kilometers to meters.
						}
					}
				}
			}
		}

		if(XTcoefficient == 0.0) {
			XTcoefficient =  lowestXT;
		}

		double xt_threshold = modulation.getXTthresholdLinear(); //linear value
		PmaxXT = xt_threshold / XTcoefficient;

		return PmaxXT;
	}

	/**
	 * This method calculates the amount of spectrum overlap in a given circuit.
	 *
	 * @param circuit
	 * @param route
	 * @param modulation
	 * @param core
	 * @param spectrumAssigned
	 * @return int
	 */
	public int numberSlotsOverlapping(Circuit circuit, Route route, Modulation modulation, int core, int spectrumAssigned[]) {

		int overlap = 0; // number of overlapping slots between i and j
		int totalOverlaps = 0;

		//Modelo Lobato
		if (this.tipoEstimativa==1) {
			for (Link link: route.getLinkList()) {

				ArrayList<Core> adjacentsCores = link.getAdjacentCores(core);

				for (Core adjCore : adjacentsCores) {

					TreeSet<Circuit> adjacentCircuitList = new TreeSet<Circuit>();

					for (Circuit circtuiTemp : adjCore.getCircuitList()) {
						adjacentCircuitList.add(circtuiTemp);
					}

					for (Circuit adjacentCircuit : adjacentCircuitList) {
						if (isIntersection(spectrumAssigned, adjacentCircuit.getSpectrumAssignedByLink(link))) {
							overlap  = numberOfOverlapping(spectrumAssigned, adjacentCircuit.getSpectrumAssignedByLink(link));
							totalOverlaps += overlap;
						}
					}
				}
			}
		}

		return totalOverlaps;
	}

	/**
	 * Method used to print a test log
	 *
	 * @param circuit Circuit
	 * @param origem the source node name.
	 * @param destino the destination node name.
	 * @param totalXT double
	 */
	private void printTest(Circuit circuit, String origem, String destino, double totalXT) {
		if (circuit.getSource().getName().equals(origem) && circuit.getDestination().getName().equals(destino)) {
			System.out.println("\n\n");

			for(Link link : circuit.getRoute().getLinkList()) {
				System.out.println("Link :"+link.getSource().getName()+"-"+link.getDestination().getName());

				for(Core core : link.getCores()) {
					System.out.print("Core: "+core.getId());

					for(int[] i : core.getFreeSpectrumBands(circuit.getGuardBand())) {
						System.out.print("||"+i[0]+"-"+i[1]);
					}

					System.out.println("");
				}
			}

			System.out.println("Crosstalk for the circuit using core: "+circuit.getIndexCore()+" and slots: "+circuit.getSpectrumAssigned()[0]+"-"+circuit.getSpectrumAssigned()[1]+" is "+PhysicalLayer.ratioForDB(totalXT)+" With modulation: "+circuit.getModulation().getName()+" therefore: "+isAdmissible(circuit, PhysicalLayer.ratioForDB(totalXT)));
			//System.out.println("Crosstalk for the circuit: "+circuit.getSource().getName()+"-"+circuit.getDestination().getName()+" using core: "+circuit.getIndexCore()+" and slots: "+circuit.getSpectrumAssigned()[0]+"-"+circuit.getSpectrumAssigned()[1]+" is "+calculeLog(totalXT)+" With modulation: "+circuit.getModulation().getName()+" therefore: "+isAdmissible(circuit, calculeLog(totalXT)));
		}
	}


	/** Lobato spectral slot-overlap rule; denominator is the interferer signal-slot count. */

public static int overlappingSlots(int[] victim, int[] interferer) {
		if (!valid(victim) || !valid(interferer)) return 0;

		return Math.max(0, Math.min(victim[1], interferer[1])
				- Math.max(victim[0], interferer[0]) + 1);
	}

	public static double interfererFraction(int[] victim, int[] interferer) {
		if (!valid(interferer)) return 0.0;

		return overlappingSlots(victim, interferer)
				/ (double)(interferer[1] - interferer[0] + 1);
	}

	private static boolean valid(int[] band) {
		return band != null && band.length == 2
				&& band[0] >= 1 && band[1] >= band[0];
	}
}
