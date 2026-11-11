package qkd.verification;

import java.util.Arrays;
import gprmcsa.modulation.Modulation;
import network.Circuit;
import network.Link;
import network.Mesh;
import network.PhysicalLayer;
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

/** Deterministic smoke tests for the real classical SNetS PICh QoT bridge. */
public final class QKDPichSharedClassicalQoTVerification {
	private static void check(boolean good, String text) {
		if (!good) throw new AssertionError(text);
	}

	static Mesh mesh(boolean ase, boolean nli, boolean xt) {
		NetworkConfig net = new NetworkConfig();
		net.setNodes(Arrays.asList(new NodeConfig("A", 4,4,0), new NodeConfig("B",4,4,0)));
		net.setCores(Arrays.asList(new CoreConfig(0, Arrays.asList(1)),
				new CoreConfig(1, Arrays.asList(0))));
		net.setLinks(Arrays.asList(new LinkConfig("A","B",16,12.5e9,40.0)));
		net.setModulations(Arrays.asList(
				new ModulationConfig("QPSK",4.0,500.0,-100.0,-10.0),
				new ModulationConfig("QPSK-STRICT",4.0,500.0,300.0,-30.0)));
		PhysicalLayerConfig p = new PhysicalLayerConfig();
		p.setActiveQoT(false);
		p.setActiveASE(ase);
		p.setActiveNLI(nli);
		// The Habibi EGN correction may legitimately clamp short-span NLI
		// to zero; this test requires a nonzero GN example, so select the
		// Johannisson formulation explicitly instead of assuming the default.
		p.setPhysicalLayerModel(PhysicalLayer.MODEL_JOHANNISSON);
		p.setActiveXT(xt);
		p.setCrosstalkModel(PhysicalLayer.XT_TOGETHER);
		p.setPower(0.0);
		p.setSpanLength(20.0);
		p.setFiberLoss(0.2);
		p.setFiberNonlinearity(1.2e-3);
		p.setFiberDispersion(17e-6);
		p.setCenterFrequency(193.4e12);
		p.setConstantOfPlanck(6.62607015e-34);
		p.setNoiseFigureOfOpticalAmplifier(5.0);
		p.setPowerSaturationOfOpticalAmplifier(18.0);
		p.setAmplificationFrequency(193.4e12);
		p.setSwitchInsertionLoss(0.2);
		p.setPolarizationModes(2);
		p.setPropagationConstant(1e7);
		p.setBendingRadius(0.05);
		p.setCouplingCoefficient(0.1);
		p.setCorePitch(4e-5);

		return new Mesh(net, new TrafficConfig(), p, new OthersConfig(), null, new Util());
	}

	private static QKDPichCircuit pich(Mesh mesh, double begin, double end, int core, int slot) {
		Link link = mesh.getLinkList().get(0);
		QKDSpan span = new QKDSpan("A", "B", 0, 1, begin, end, "A", "B");
		QKDPichCircuit c = new QKDPichCircuit(link,span,core,new int[]{slot,slot},0,1e-3);
		Modulation configured = mesh.getAvaliableModulations().get(0);
		c.assignModulation(configured);
		check(c.getModulation() == configured, "modulation identity not reused");

		return c;
	}

	public static void main(String[] args) {
		Mesh ase = mesh(true, false, false);
		PhysicalLayer.PichQoTResult withAse = ase.getPhysicalLayer().evaluatePich(
				pich(ase,0,40,0,1), null);
		check(withAse.asePsdWHz > 0.0, "ASE must reach the PICh receptor");
		check(Double.isFinite(withAse.osnrLinear) && withAse.osnrLinear > 0,
				"SNetS QoT must return finite PICh OSNR");

		Mesh noAse = mesh(false,false,false);
		PhysicalLayer.PichQoTResult withoutAse = noAse.getPhysicalLayer().evaluatePich(
				pich(noAse,0,40,0,1),null);
		check(withoutAse.asePsdWHz == 0.0 && withoutAse.osnrLinear > withAse.osnrLinear,
				"turning on ASE should reduce PICh OSNR");

		Mesh nli = mesh(false,true,false);
		PhysicalLayer.PichQoTResult withNli = nli.getPhysicalLayer().evaluatePich(
				pich(nli,20,40,0,1),null);
		check(withNli.gnPsdWHz > 0, "GN must apply after PICh starts at 20 km");

		Mesh xt = mesh(false,false,true);
		Circuit candidate = new Circuit();
		candidate.setRoute(new gprmcsa.routing.Route(new java.util.Vector<network.Node>(
				Arrays.asList(xt.getNodeList().get(0),xt.getNodeList().get(1)))));
		candidate.setIndexCore(1);
		candidate.setSpectrumAssigned(new int[]{1,1});
		candidate.setLaunchPowerLinear(1e-3);
		candidate.setModulation(xt.getAvaliableModulations().get(0));
		PhysicalLayer.PichQoTResult withXt = xt.getPhysicalLayer().evaluatePich(
				pich(xt,20,40,0,1),candidate);
		check(withXt.xtPsdWHz > 0, "XT must consider the pending adjacent-core DCh");

		// A PICh on a later span cannot be evaluated past a SNetS optical node.
		boolean refused=false;

		try { xt.getPhysicalLayer().evaluatePich(pich(xt,0,50,0,1),null); }
		catch (IllegalArgumentException expected) { refused=true; }
		check(refused,"a QKD span cannot extend beyond a physical SNetS link");
		Modulation selected = qkd.capacity.PichModulationSelection.select(
				ase.getAvaliableModulations(), "qpsk");
		check(selected == ase.getAvaliableModulations().get(0),
				"selected PICh format must be exactly the existing SNetS instance");
		boolean unknown = false;

		try { qkd.capacity.PichModulationSelection.select(
				ase.getAvaliableModulations(), "NONEXISTENT"); }
		catch (IllegalArgumentException expected) { unknown = true; }
		check(unknown, "unknown PICh modulation must be rejected");
		simulationControl.parsers.NetworkConfig legacy = new simulationControl.parsers.NetworkConfig();
		legacy.setCores(Arrays.asList(new simulationControl.parsers.NetworkConfig.CoreConfig(0, Arrays.asList(1)),
				new simulationControl.parsers.NetworkConfig.CoreConfig(1, Arrays.asList(0))));
		simulationControl.parsers.NetworkConfig.QKDConfig q =
				new simulationControl.parsers.NetworkConfig.QKDConfig(true,20,1);
		q.setPichSlots(1);
		q.setPichSnrCheckEnabled(true);
		q.setPichMinimumSnrDb(20.0); // now supplied by modulation, never duplicated
		legacy.setQkd(q);
		boolean duplicate = false;

		try { qkd.network.QKDNetworkParameters.from(legacy); }
		catch (IllegalArgumentException expected) { duplicate = true; }
		check(duplicate, "duplicated PICh OSNR threshold must be rejected");
		System.out.println("PASS QKDPichSharedClassicalQoTVerification");
	}
}
