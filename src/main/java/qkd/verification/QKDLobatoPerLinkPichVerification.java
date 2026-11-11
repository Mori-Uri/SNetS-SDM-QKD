package qkd.verification;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Vector;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.Crosstalk;
import network.Link;
import network.Crosstalk;
import network.Mesh;
import network.Node;
import network.Pair;
import network.PhysicalLayer;
import qkd.capacity.PichClassicalCrosstalk;
import qkd.capacity.PichClassicalNonlinearity;
import qkd.capacity.QKDPichCircuit;
import qkd.network.QKDSpan;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;
import simulationControl.parsers.NetworkConfig.ModulationConfig;
import simulationControl.parsers.NetworkConfig.NodeConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.TrafficConfig;

/**
 * Lobato Eqs. (3.33)-(3.38) with per-link spectrum and independent PICh spans.
 * Test the actual classical Crosstalk and PICh noisePsd against hand arithmetic.
 */
public final class QKDLobatoPerLinkPichVerification {

	private QKDLobatoPerLinkPichVerification() { }

	private static final class PerLinkCircuit extends Circuit {
		private final IdentityHashMap<Link, int[]> byLink = new IdentityHashMap<Link, int[]>();

		void on(Link link, int[] band) {
			byLink.put(link, band);
		}

		@Override
		public int[] getSpectrumAssignedByLink(Link link) {
			return byLink.containsKey(link) ? byLink.get(link) : getSpectrumAssigned();
		}
	}

	public static void main(String[] args) throws Exception {
		NetworkConfig net = new NetworkConfig();
		net.setNodes(Arrays.asList(new NodeConfig("A", 4, 4, 0),
				new NodeConfig("B", 4, 4, 0), new NodeConfig("C", 4, 4, 0)));
		net.setCores(Arrays.asList(new CoreConfig(0, Arrays.asList(1)),
				new CoreConfig(1, Arrays.asList(0))));
		net.setLinks(Arrays.asList(
				new LinkConfig("A", "B", 40, 12.5e9, 40.0),
				new LinkConfig("B", "C", 40, 12.5e9, 60.0)));
		net.setModulations(Arrays.asList(
				new ModulationConfig("QPSK", 4.0, 1000.0, -100.0, -10.0)));
		net.setGuardBand(1);

		PhysicalLayerConfig plc = new PhysicalLayerConfig();
		plc.setCouplingCoefficient(1e-4);
		plc.setBendingRadius(0.05);
		plc.setPropagationConstant(5.9e6);
		plc.setCorePitch(41e-6);
		plc.setPhysicalLayerModel(PhysicalLayer.MODEL_JOHANNISSON);
		plc.setActiveXT(true);
		plc.setPower(0.0);
		plc.setSpanLength(80.0);
		plc.setFiberLoss(0.2);
		plc.setFiberNonlinearity(0.0013);
		plc.setFiberDispersion(16.7e-6);
		plc.setCenterFrequency(193.4e12);
		plc.setConstantOfPlanck(6.626e-34);
		plc.setNoiseFigureOfOpticalAmplifier(5.0);
		plc.setPowerSaturationOfOpticalAmplifier(20.0);
		plc.setAmplificationFrequency(193.4e12);
		plc.setSwitchInsertionLoss(0.0);
		plc.setPolarizationModes(2.0);

		Mesh mesh = new Mesh(net, new TrafficConfig(), plc,
				new OthersConfig(), null, new Util());
		final Link ab = mesh.getLinkList().get(0);
		final Link bc = mesh.getLinkList().get(1);
		final Route route = new Route(new Vector<Node>(mesh.getNodeList()));
		final Modulation modulation = mesh.getAvaliableModulations().get(0);
		final double powerW = 1e-3;

		PerLinkCircuit victim = new PerLinkCircuit();
		victim.setRoute(route);
		victim.setPair(new Pair(route.getSource(), route.getDestination()));
		victim.setModulation(modulation);
		victim.setIndexCore(0);
		victim.setSpectrumAssigned(new int[] {1, 4});
		victim.on(ab, new int[] {1, 4});
		victim.on(bc, new int[] {8, 11});
		victim.setLaunchPowerLinear(powerW);

		PerLinkCircuit interferer = new PerLinkCircuit();
		interferer.setRoute(route);
		interferer.setPair(new Pair(route.getSource(), route.getDestination()));
		interferer.setModulation(modulation);
		interferer.setIndexCore(1);
		interferer.setSpectrumAssigned(new int[] {3, 7});
		interferer.on(ab, new int[] {3, 7});
		interferer.on(bc, new int[] {9, 13});
		interferer.setLaunchPowerLinear(powerW);
		ab.getCore(1).addCircuit(interferer);
		bc.getCore(1).addCircuit(interferer);

		final Crosstalk xt = mesh.getPhysicalLayer().getCrosstalk();
		final double h = xt.getH();
		final double isoAB = Crosstalk.interfererFraction(
				victim.getSpectrumAssignedByLink(ab), interferer.getSpectrumAssignedByLink(ab));
		final double isoBC = Crosstalk.interfererFraction(
				victim.getSpectrumAssignedByLink(bc), interferer.getSpectrumAssignedByLink(bc));
		close(isoAB, 2.0/5.0, 1e-14, "ISO AB");
		close(isoBC, 3.0/5.0, 1e-14, "ISO BC");

		double referenceRatio = h * (isoAB * 40_000.0 + isoBC * 60_000.0);
		double actualRatio = Math.pow(10.0, xt.calculateCrosstalk(victim, null, false)/10.0);
		close(actualRatio, referenceRatio, 1e-10, "Lobato total per-link XT");
		double expectedBC = powerW * h * 60_000.0 * isoBC;
		close(xt.calculateCrosstalkPowerInLink(victim, bc, 0,
				victim.getSpectrumAssignedByLink(bc), null, false),
				expectedBC, 1e-10, "per-link absolute Pxt");

		// Remove existing DCh to isolate PICh as an AUXILIARY physical source.
		ab.getCore(1).removeCircuit(interferer);
		bc.getCore(1).removeCircuit(interferer);
		final QKDSpan span = new QKDSpan("A", "B", 0, 2, 0.0, 15.0,
				"A", "TN-A-15");
		QKDPichCircuit pich = new QKDPichCircuit(ab, span, 1,
				new int[] {3, 7}, 1, powerW);
		pich.assignModulation(modulation);
		ab.getCore(1).addAuxiliaryClassicalCircuit(pich);

		double slotBandwidth = ab.getCore(0).getSlotSpectrumBand();

		// The same PICh is an XCI source only on its physical 15-km span.
		// This also exercises the Eq.15 kernel through the PICh production API.
		final double xi = 4.0 * Math.PI * Math.PI * 21.3e-27
				/ (0.2 * Math.log(10.0) / 10.0 / 1000.0);
		final double sourceWidth = pich.getOpticalBandwidthHz(slotBandwidth);
		final double lower = 191.0e12;
		final double victimWidth = 12.5e9;
		final double victimFrequency = pich.getCenterFrequencyHz(lower, slotBandwidth)
				+ 75.0e9;
		final double sourcePsd = powerW / 2.0 / sourceWidth;
		final double expectedXci = 15.0 / 40.0 * sourcePsd * sourcePsd
				* network.GnModelMath.johannissonXciKernel(
						75.0e9, victimWidth, sourceWidth, xi);
		final double actualXci = PichClassicalNonlinearity.crossTermJohannissonExact(
				ab, 1, victimFrequency, victimWidth, 1.0, lower,
				slotBandwidth, 2.0, xi, 0.0, 40.0);
		close(actualXci, expectedXci, 1e-12, "PICh exact Eq.15 XCI on 15 km");

		double bandwidth = 4.0 * slotBandwidth;
		// Use elevated h to distinguish the linear Lobato hL from the old
		// saturating approximation hL/(1+hL), which is not thesis Eq. 3.35.
		double chosenH = 1e-5;
		double expectedPsd = powerW * (2.0/5.0) * chosenH * 15_000.0 / bandwidth;
		double actualPsd = PichClassicalCrosstalk.noisePsd(ab, 0,
				victim.getSpectrumAssignedByLink(ab), bandwidth, chosenH, 2.0, false);
		close(actualPsd, expectedPsd, 1e-12, "PICh 15 km Lobato Eq 3.35");
		// PICh in AB must never be counted when evaluating BC.
		close(PichClassicalCrosstalk.noisePsd(bc, 0,
				victim.getSpectrumAssignedByLink(bc), bandwidth, chosenH, 2.0, false),
				0.0, 0.0, "no PICh spillover across SNetS node");
		close(PichClassicalCrosstalk.noisePsd(ab, 0,
				victim.getSpectrumAssignedByLink(ab), bandwidth, chosenH, 2.0, true),
				expectedPsd/2.0, 1e-12, "per-polarization XT");

		// The classical XT_SEPARATE engine must include the same PICh source,
		// normalized by victim power, without counting it on BC.
		double onlyPichXt = Math.pow(10.0,
				xt.calculateCrosstalk(victim, null, false) / 10.0);
		close(onlyPichXt, h * 15_000.0 * (2.0 / 5.0), 1e-10,
				"PICh in Lobato classical XT_SEPARATE");

		// A second independent PICh belongs only to BC and has DIFFERENT
		// signal slots. Its longitudinal extent is 20..50 of a 60-km link.
		final QKDSpan secondSpan = new QKDSpan("B", "C", 0, 1,
				20.0, 50.0, "TN-BC20", "TN-BC50");
		QKDPichCircuit secondPich = new QKDPichCircuit(bc, secondSpan, 1,
				new int[] {9, 13}, 1, powerW);
		secondPich.assignModulation(modulation);
		bc.getCore(1).addAuxiliaryClassicalCircuit(secondPich);

		double bothPichXt = Math.pow(10.0,
				xt.calculateCrosstalk(victim, null, false) / 10.0);
		double expectedBoth = h * (15_000.0 * (2.0 / 5.0)
				+ 30_000.0 * (3.0 / 5.0));
		close(bothPichXt, expectedBoth, 1e-10,
				"two independent PICh, link spectra and partial spans");

		// A PICh must never identify BC while physically attached to AB.
		boolean rejectedCrossNode = false;

		try {
			new QKDPichCircuit(ab, new QKDSpan("B", "C", 0, 1,
					0.0, 10.0, "B", "C"), 1, new int[] {3, 7}, 1, powerW);
		} catch (IllegalArgumentException expected) {
			rejectedCrossNode = true;
		}

		if (!rejectedCrossNode) {
			throw new AssertionError("PICh crossing an SNetS node must be rejected");
		}

		System.out.println("PASS QKDLobatoPerLinkPichVerification");
		System.out.printf(java.util.Locale.ROOT,
				"ISO AB=%.2f ISO BC=%.2f h=%.8g PIchPsd=%.8g%n",
				isoAB, isoBC, h, actualPsd);
	}

	private static void close(double actual, double expected, double relative, String label) {
		if (Math.abs(actual-expected) > relative*Math.max(Math.abs(expected), 1e-30)) {
			throw new AssertionError(label+": "+actual+" vs "+expected);
		}
	}
}
