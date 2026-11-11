package gprmcsa.powerAssignment;

import java.util.Map;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;

/**
 * This class represents the Enough Power Assignment (EnPA) algorithm.
 * The EnPA strategy assigns to the circuit the power that reaches the OSNR threshold (OSNR_th) of the modulation selected for the circuit.
 *
 * Article: Power, routing, Modulation Level and Spectrum Assignment in all-optical and elastic networks (2019).
 *
 * The value of margin must be entered in the configuration file "others" as shown below.
 * {"variables":{
 *               "margin":"1.0"
 *              }
 * }
 *
 * @author Alexandre
 *
 */
public class EnPAAlgorithm implements PowerAssignmentAlgorithmInterface {

	private Double margin;
	private int maxAttemptsCounter = 100;

	/**
	 * Returns the assign launch power.
	 * @param circuit the circuit.
	 * @param route the route.
	 * @param modulation the modulation.
	 * @param core the core.
	 * @param spectrumAssigned the spectrumAssigned.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	@Override
	public double assignLaunchPower(Circuit circuit, Route route, Modulation modulation, int core, int[] spectrumAssigned, ControlPlane cp) {

		if(margin == null){
			Map<String, String> uv = cp.getMesh().getOthersConfig().getVariables();

			if(uv.get("margin") != null) {
				margin = Double.parseDouble((String)uv.get("margin"));
			}

			if(margin == null) {
				margin = 1.0;
			}
		}

		//For the EnPA algorithm to work correctly the PSD must be variable.
		if (cp.getMesh().getPhysicalLayer().getFixedPowerSpectralDensity()) {
			cp.getMesh().getPhysicalLayer().setFixedPowerSpectralDensity(false);
		}

		double launchPower = computePowerByLinearInterpolation(circuit, route, modulation, core, spectrumAssigned, margin, cp);
		circuit.setLaunchPowerLinear(launchPower);

		return launchPower;
	}

	/**
	 * This method uses linear interpolation to compute the launch power.
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param modulation Modulation
	 * @param core int
	 * @param spectrumAssigned int[]
	 * @param margin double
	 * @param cp ControlPlane
	 * @return double
	 */
	public double computePowerByLinearInterpolation(Circuit circuit, Route route, Modulation modulation, int core, int spectrumAssigned[], double margin, ControlPlane cp){

		double Pcurrent = 0.0;
		double OSNRcurrent = 0.0;

		double Pmin = 0.0;
		double OSNRmin = 0.0;

		double Pmax = 0.0;
		double OSNRmax = 0.0;

		double OSNRdif = 0.0;
		double error = 1e-3;

		int attemptsCounter = 0; // Count attempts to reach OSNRth

		double OSNRth = modulation.getOSNRthresholdLinear(); //Linear
		OSNRth = OSNRth + margin;

		Pmin = 1.0E-11; //W, -80 dBm
		circuit.setLaunchPowerLinear(Pmin);
		OSNRmin = cp.getMesh().getPhysicalLayer().computeOSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, modulation, core, spectrumAssigned, null, false);

		Pmax = cp.getMesh().getPhysicalLayer().computeMaximumPower(circuit, circuit.getRequiredBitRate(), route, 0, route.getNodeList().size() - 1, modulation, core, spectrumAssigned);
		circuit.setLaunchPowerLinear(Pmax);
		OSNRmax = cp.getMesh().getPhysicalLayer().computeOSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, modulation, core, spectrumAssigned, null, false);

		while (attemptsCounter <= maxAttemptsCounter) {

			if(OSNRmin - OSNRth > 0.0) {
				return Pmin;
			}

			if (OSNRmax - OSNRth < 0.0) {
				return Pmax;
			}

			// Avoid division by zero
			if (OSNRmax == OSNRmin) {
				// If OSNR barely varies, use binary search as fallback
				Pcurrent = (Pmax + Pmin) * 0.5;

			} else {
				// linear interpolation
				Pcurrent = Pmin + ((Pmax - Pmin) * ((OSNRth - OSNRmin) / (OSNRmax - OSNRmin)));
			}

			circuit.setLaunchPowerLinear(Pcurrent);
			OSNRcurrent = cp.getMesh().getPhysicalLayer().computeOSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, modulation, core, spectrumAssigned, null, false);

			OSNRdif = Math.abs(OSNRcurrent - OSNRth);

			if (OSNRdif <= error) {
				break;

			} else {
				if(OSNRcurrent > OSNRth) {
					Pmax = Pcurrent;
					OSNRmax = OSNRcurrent;

				} else {
					Pmin = Pcurrent;
					OSNRmin = OSNRcurrent;
				}
			}

			attemptsCounter++;
		}

//		if(attemptsCounter > 50) {
//			System.out.println("tentativas: " + attemptsCounter);
//		}

		return Pcurrent;
	}

}
