package qkd.verification;

import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;
import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Link;
import network.Mesh;
import qkd.capacity.QKDPichPool;
import qkd.network.QKDLinkPlan;
import qkd.network.QKDRoutePlan;
import qkd.network.QKDSpan;
import qkd.network.QKDNetworkParameters;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.QKDConfig;

/** Regression for real SNetS modulation limits and same-band guard. */
public final class QKDPichIntegratedAdmissionV15Verification {
	private static void check(boolean value, String reason) {
		if (!value) throw new AssertionError(reason);
	}

	public static void main(String[] args) throws Exception {
		Mesh mesh = QKDPichSharedClassicalQoTVerification.mesh(true,false,false);
		Link link = mesh.getLinkList().get(0);
		link.getCore(1).setQkdReserved(true);
		NetworkConfig net = new NetworkConfig();
		net.setCores(Arrays.asList(new CoreConfig(0,Arrays.asList(1)),
				new CoreConfig(1,Arrays.asList(0))));
		QKDConfig qkd = new QKDConfig(true,40.0,1);
		qkd.setPichSlots(1);
		qkd.setPichSnrCheckEnabled(true);
		qkd.setSameBandAlignedGrids(true);
		qkd.setSameBandGuardSlots(1);
		net.setQkd(qkd);
		QKDNetworkParameters params = QKDNetworkParameters.from(net);
		Route route = new Route();
		route.setLinkList(new Vector<Link>(Collections.singletonList(link)));
		QKDRoutePlan plan = new QKDRoutePlan("A", "B", Collections.singletonList(
				new QKDLinkPlan("A", "B", 40.0,40.0,
					Collections.singletonList(new QKDSpan("A","B",0,1,0,40,"A","B")))));
		link.getCore(1).useSpectrumForQKD(new int[]{1,1},0);
		QKDPichPool pool = new QKDPichPool(1,1,params,1e-3);
		pool.setClassicalPhysicalLayer(mesh.getPhysicalLayer(), mesh.getAvaliableModulations().get(0));
		java.util.List<QKDPichPool.Channel> good = pool.acquire(route,plan,0,null);
		check(good != null && good.size()==1 && good.get(0).band[0]>=3,
				"configured PICh QoT and QCh guard must pass");
		check(good.get(0).getOpticalCircuit().getModulation()
				== mesh.getAvaliableModulations().get(0),
				"PICh must reuse configured modulation object");
		pool.release(good);

		// An impossible threshold on a configured modulation must block
		// before the spectrum or auxiliary source is modified.
		Modulation impossible = mesh.getAvaliableModulations().get(1);
		pool = new QKDPichPool(1,1,params,1e-3);
		pool.setClassicalPhysicalLayer(mesh.getPhysicalLayer(),impossible);
		check(pool.acquire(route,plan,0,null)==null,"QoT must reject inadequate OSNR");
		check(link.getCore(0).getUsedSlots()==0
				&& link.getCore(0).getAuxiliaryClassicalCircuitList().isEmpty(),
				"QoT rejection must not leave optical resources");
		link.getCore(1).liberateSpectrumForQKD(new int[]{1,1},0);
		System.out.println("PASS QKDPichIntegratedAdmissionV15Verification");
	}
}
