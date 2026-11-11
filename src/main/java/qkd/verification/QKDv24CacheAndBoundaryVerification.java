package qkd.verification;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Vector;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Amplifier;
import network.Circuit;
import network.Link;
import network.Mesh;
import network.Node;
import network.Pair;
import network.PhysicalLayer;
import qkd.capacity.QKDPichCircuit;
import qkd.multiband.ClassicalPowerEvolution;
import qkd.network.QKDSpan;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;
import simulationControl.parsers.NetworkConfig.ModulationConfig;
import simulationControl.parsers.NetworkConfig.NodeConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.TrafficConfig;

/** Regression: MUX/booster and final fibre/DEMUX/preamp; invalidation for DCh/PICh. */
public final class QKDv24CacheAndBoundaryVerification {

	private QKDv24CacheAndBoundaryVerification() { }

	private static final class Case {
		Mesh mesh;
		Route route;
		Circuit victim;
		Modulation modulation;
		Link link;
	}

	private static void check(boolean value, String label) {
		if (!value) throw new AssertionError(label);
	}

	private static void near(double actual, double expected, String label) {
		double relative = Math.abs(actual - expected) / Math.max(1e-22, Math.abs(expected));
		check(Double.isFinite(actual) && relative < 1e-8,
				label + ": actual " + actual + ", expected " + expected);
	}

	private static Case scenario(int hops, double mux, double demux, boolean nli, boolean xt) {
		NetworkConfig config = new NetworkConfig();
		config.setNodes(Arrays.asList(new NodeConfig("A", 4, 4, 0),
				new NodeConfig("B", 4, 4, 0), new NodeConfig("C", 4, 4, 0)));
		config.setCores(Arrays.asList(new CoreConfig(0, Arrays.asList(1)),
				new CoreConfig(1, Arrays.asList(0))));
		config.setLinks(Arrays.asList(new LinkConfig("A", "B", 64, 12.5e9, 80.0),
				new LinkConfig("B", "C", 64, 12.5e9, 95.0)));
		config.setModulations(Arrays.asList(new ModulationConfig("QPSK", 4.0, 10000.0, -100.0, -10.0)));

		PhysicalLayerConfig p = new PhysicalLayerConfig();
		p.setPhysicalLayerModel(PhysicalLayer.MODEL_JOHANNISSON);
		p.setPower(0.0);
		p.setSpanLength(80.0);
		p.setFiberLoss(0.2);
		p.setFiberNonlinearity(0.0013);
		p.setFiberDispersion(16.7e-6);
		p.setCenterFrequency(193.4e12);
		p.setConstantOfPlanck(6.62607015e-34);
		p.setNoiseFigureOfOpticalAmplifier(5.0);
		p.setPowerSaturationOfOpticalAmplifier(18.0);
		p.setAmplificationFrequency(193.4e12);
		p.setTypeOfAmplifierGain(PhysicalLayer.AMP_GAIN_FIXED);
		p.setActiveASE(true);
		p.setActiveNLI(nli);
		p.setActiveXT(xt);
		p.setCrosstalkModel(PhysicalLayer.XT_TOGETHER);
		p.setCouplingCoefficient(1e-4);
		p.setBendingRadius(0.05);
		p.setPropagationConstant(5.9e6);
		p.setCorePitch(41e-6);
		p.setPolarizationModes(2.0);
		p.setMuxInsertionLoss(mux);
		p.setDemuxInsertionLoss(demux);

		Case s = new Case();
		s.mesh = new Mesh(config, new TrafficConfig(), p, new OthersConfig(), null, new Util());
		s.link = s.mesh.getLinkList().get(0);
		Vector<Node> routeNodes = new Vector<Node>();
		routeNodes.add(s.mesh.getNodeList().get(0));
		routeNodes.add(s.mesh.getNodeList().get(1));

		if (hops == 2) routeNodes.add(s.mesh.getNodeList().get(2));
		s.route = new Route(routeNodes);
		s.modulation = s.mesh.getAvaliableModulations().get(0);
		s.victim = new Circuit();
		s.victim.setRoute(s.route);
		s.victim.setPair(new Pair(s.route.getSource(), s.route.getDestination()));
		s.victim.setModulation(s.modulation);
		s.victim.setIndexCore(0);
		s.victim.setSpectrumAssigned(new int[] {12, 15});
		s.victim.setLaunchPowerLinear(1e-3);
		RequestForConnection r = new RequestForConnection();
		r.setPair(s.victim.getPair());
		r.setRequiredBitRate(s.modulation.potentialBitRate(4));
		s.victim.addRequest(r);

		return s;
	}

	private static double snr(Case s) {
		return s.mesh.getPhysicalLayer().computeOSNRSegment(s.victim, s.route,
				0, s.route.getNodeList().size() - 1, s.modulation, 0,
				s.victim.getSpectrumAssigned(), null, false);
	}

	private static void compareWithoutCache(Case s, String label) {
		double fast = snr(s);
		System.setProperty("snets.noPerfCache", "true");
		double reference;

		try {
			reference = snr(s);
		} finally {
			System.clearProperty("snets.noPerfCache");
		}

		check(Double.doubleToRawLongBits(fast) == Double.doubleToRawLongBits(reference),
				label + " cache differs from reference " + fast + " vs " + reference);
	}

	private static void boundary() throws Exception {
		Case s = scenario(2, 3.0, 5.0, false, false);
		PhysicalLayer pl = s.mesh.getPhysicalLayer();
		Field boosterField = PhysicalLayer.class.getDeclaredField("boosterAmp");
		boosterField.setAccessible(true);
		Field preampField = PhysicalLayer.class.getDeclaredField("preAmp");
		preampField.setAccessible(true);
		Amplifier booster = (Amplifier) boosterField.get(pl);
		Amplifier preamp = (Amplifier) preampField.get(pl);
		near(booster.getGainLinear(), Math.pow(10.0, 3.0 / 10.0), "booster compensates MUX only");
		near(preamp.getGainLinear(), Math.pow(10.0, (16.0 + 5.0) / 10.0),
				"preamplifier compensates final reference span and DEMUX");
		compareWithoutCache(s, "boundary no NLI");
		System.out.println("PASS v24 amplifier boundary, MUX 3dB, DEMUX 5dB");
	}

	private static void sourceInvalidation(boolean longitudinal) throws Exception {
		Case s = scenario(2, 3.0, 5.0, true, true);
		PhysicalLayer pl = s.mesh.getPhysicalLayer();

		if (longitudinal) {
			double f0 = 193.4e12 - 64 * 12.5e9 / 2.0;
			pl.enableLongitudinalAmplifierQoT(new ClassicalPowerEvolution(80.0, 16.0),
					f0, 0.2 * Math.log(10.0) / 10.0);
		}

		// First call populates one entry per physical link; second one must hit.
		compareWithoutCache(s, "initial");
		long hits = pl.getQotLinkCacheHits();
		double original = snr(s);
		check(pl.getQotLinkCacheHits() >= hits + 2, "two cached link evaluations should hit");

		s.victim.setLaunchPowerLinear(2e-3);
		compareWithoutCache(s, "victim power update");
		check(Double.doubleToRawLongBits(original) != Double.doubleToRawLongBits(snr(s)),
				"victim launch-power mutation changes QoT");

		s.victim.setSpectrumAssigned(new int[] {20, 23});
		compareWithoutCache(s, "victim spectrum update");

		// An auxiliary PICh does NOT enter the ordinary DCh circuit list.
		// Its insertion / power update / release must still invalidate the cache.
		QKDSpan span = new QKDSpan("A", "B", 0, 2, 0.0, 40.0, "A", "TN-A-40");
		QKDPichCircuit pich = new QKDPichCircuit(s.link, span, 1,
				new int[] {21, 24}, 0, 1e-3);
		pich.assignModulation(s.modulation);
		s.link.getCore(1).addAuxiliaryClassicalCircuit(pich);
		long misses = pl.getQotLinkCacheMisses();
		compareWithoutCache(s, "PICh opened");
		check(pl.getQotLinkCacheMisses() > misses, "PICh open must invalidate link");

		pich.setLaunchPowerLinear(4e-3);
		misses = pl.getQotLinkCacheMisses();
		compareWithoutCache(s, "PICh power updated");
		check(pl.getQotLinkCacheMisses() > misses, "PICh power must invalidate link");

		s.link.getCore(1).removeAuxiliaryClassicalCircuit(pich);
		// Restoring exactly the pre-PICh optical state may safely reuse the
		// corresponding historical entry; equality with uncached is decisive.
		compareWithoutCache(s, "PICh closed");

		// The physical longitudinal mode needs to survive Java save-state serialization.
		if (longitudinal) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
				out.writeObject(pl);
			}

			try (ObjectInputStream in = new ObjectInputStream(
					new ByteArrayInputStream(bytes.toByteArray()))) {
				PhysicalLayer restored = (PhysicalLayer) in.readObject();
				Field field = PhysicalLayer.class.getDeclaredField("longitudinalAmplifierTemplate");
				field.setAccessible(true);
				check(field.get(restored) != null, "shared longitudinal template lost on resume");
			}
		}

		System.out.println("PASS v24 cache, PIch invalidation, mode=" + (longitudinal ? "longitudinal" : "classic"));
	}

	public static void main(String[] args) throws Exception {
		boundary();
		sourceInvalidation(false);
		sourceInvalidation(true);
		System.out.println("PASS QKDv24CacheAndBoundaryVerification");
	}
}
