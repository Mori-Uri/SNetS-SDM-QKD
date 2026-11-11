package qkd.verification;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.Link;
import network.Mesh;
import network.Node;
import network.Pair;
import network.PhysicalLayer;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;
import simulationControl.parsers.NetworkConfig.ModulationConfig;
import simulationControl.parsers.NetworkConfig.NodeConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.TrafficConfig;

import network.Crosstalk;
import network.Spectrum;
import simulationControl.parsers.PhysicalLayerConfig;

/**
 * Checks Lobato XT equations 3.33-3.38 and verifies guard-band separation
 * in the actual SNetS Spectrum allocation (Lacerda Jr., 2024, Figure 23).
 */
public final class QKDThesisCrosstalkVerification {

	private QKDThesisCrosstalkVerification() {
	}

	private static void close(double actual, double expected, double tolerance, String name) {
		if (Math.abs(actual - expected) > tolerance) {
			throw new AssertionError(name + ": " + actual + " != " + expected);
		}
	}

	public static void main(String[] args) throws Exception {
		double coupling = 1e-4;
		double radius = 0.05;
		double propagation = 5.9e6;
		double pitch = 41e-6;
		double expectedH = 2.0 * coupling * coupling * radius / (propagation * pitch);

		PhysicalLayerConfig config = new PhysicalLayerConfig();
		config.setCouplingCoefficient(coupling);
		config.setBendingRadius(radius);
		config.setPropagationConstant(propagation);
		config.setCorePitch(pitch);

		Crosstalk implementation = new Crosstalk(null, config);
		close(implementation.getH(), expectedH, 1e-20, "Eq 3.36 coupling");

		Method overlapMethod = Crosstalk.class.getDeclaredMethod(
				"numberOfOverlapping", int[].class, int[].class);
		overlapMethod.setAccessible(true);

		// The SNetS reserves guards in Spectrum separately from the actual
		// signal band. The assigned spectrum of each Circuit is signal-only.
		Spectrum victimSpectrum = new Spectrum(12, 12.5e9);
		Spectrum interfererSpectrum = new Spectrum(12, 12.5e9);
		int[] victimSignal = new int[]{1, 4};
		int[] interferingSignal = new int[]{3, 7};

		if (!victimSpectrum.useSpectrum(victimSignal, 1)
				|| !interfererSpectrum.useSpectrum(interferingSignal, 1)) {
			throw new AssertionError("SNetS spectrum allocation failed");
		}

		close(victimSpectrum.getUsedSlots(), 4, 0.0, "victim signal slots");
		close(interfererSpectrum.getUsedSlots(), 5, 0.0, "interferer signal slots");

		int signalOverlap = (Integer) overlapMethod.invoke(
				implementation, victimSignal, interferingSignal);
		close(signalOverlap / 5.0, 0.40, 1e-12, "Fig 23 ISO without guards");

		double lengthMeters = 100_000.0;
		double power = 1e-3;
		double xtPower = (signalOverlap / 5.0) * power * expectedH * lengthMeters;
		double normalizedXt = xtPower / power;

		close(normalizedXt, 0.40 * expectedH * lengthMeters, 1e-12, "Eq 3.34-3.35");
		System.out.println("PASS: Eq 3.36 and arithmetic of Eqs 3.33-3.35");
		System.out.println("ISO thesis Fig 23, guard excluded: 0.40");
		System.out.println("SNetS Spectrum keeps guard slots separate: confirmed");

		// Evaluate the actual SNetS Crosstalk engine, not only the formula.
		NetworkConfig physicalNetwork = new NetworkConfig();
		physicalNetwork.setNodes(Arrays.asList(new NodeConfig("A", 4, 4, 0),
				new NodeConfig("B", 4, 4, 0)));
		physicalNetwork.setCores(Arrays.asList(
				new CoreConfig(0, Arrays.asList(1)),
				new CoreConfig(1, Arrays.asList(0))));
		physicalNetwork.setLinks(Arrays.asList(new LinkConfig("A", "B", 12, 12.5e9, 100.0)));
		physicalNetwork.setModulations(Arrays.asList(
				new ModulationConfig("QPSK", 4.0, 1000.0, -100.0, -10.0)));
		physicalNetwork.setGuardBand(1);

		config.setPhysicalLayerModel(PhysicalLayer.MODEL_JOHANNISSON);
		config.setActiveXT(true);
		config.setPower(0.0);
		config.setSpanLength(100.0);
		config.setFiberLoss(0.2);
		config.setFiberNonlinearity(1.3e-3);
		config.setFiberDispersion(16.7e-6);
		config.setCenterFrequency(193.4e12);
		config.setConstantOfPlanck(6.626e-34);
		config.setNoiseFigureOfOpticalAmplifier(5.0);
		config.setPowerSaturationOfOpticalAmplifier(20.0);
		config.setAmplificationFrequency(193.4e12);
		config.setSwitchInsertionLoss(0.0);
		config.setPolarizationModes(2.0);

		Mesh mesh = new Mesh(physicalNetwork, new TrafficConfig(), config,
				new OthersConfig(), null, new Util());
		Link link = mesh.getLinkList().get(0);
		Route route = new Route(new Vector<Node>(Arrays.asList(
				mesh.getNodeList().get(0), mesh.getNodeList().get(1))));
		Modulation modulation = mesh.getAvaliableModulations().get(0);

		Circuit victim = new Circuit();
		victim.setRoute(route);
		victim.setPair(new Pair(route.getSource(), route.getDestination()));
		victim.setModulation(modulation);
		victim.setIndexCore(0);
		victim.setSpectrumAssigned(new int[]{1, 4});
		victim.setGuardBand(1);
		victim.setLaunchPowerLinear(power);

		Circuit aggressor = new Circuit();
		aggressor.setRoute(route);
		aggressor.setPair(new Pair(route.getSource(), route.getDestination()));
		aggressor.setModulation(modulation);
		aggressor.setIndexCore(1);
		aggressor.setSpectrumAssigned(new int[]{3, 7});
		aggressor.setGuardBand(1);
		aggressor.setLaunchPowerLinear(power);
		link.getCore(1).addCircuit(aggressor);

		double engineXtDb = mesh.getPhysicalLayer().getCrosstalk()
				.calculateCrosstalk(victim, null, false);
		double expectedDb = 10.0 * Math.log10(normalizedXt);
		close(engineXtDb, expectedDb, 1e-7, "SNetS Lobato XT engine");

		System.out.println("PASS: SNetS Crosstalk engine == thesis Eq 3.33-3.38");
		System.out.println("SNetS engine XT (dB): " + engineXtDb);
		System.out.println("NOTE: per-link spectra and PICh spans validated by QKDLobatoPerLinkPichVerification");
	}
}
