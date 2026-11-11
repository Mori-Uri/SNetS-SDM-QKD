package qkd.verification;

import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;
import network.Link;
import network.Mesh;
import gprmcsa.routing.Route;
import qkd.capacity.PichReceivePowerBudget;
import qkd.capacity.QKDPichPool;
import qkd.network.QKDLinkPlan;
import qkd.network.QKDRoutePlan;
import qkd.network.QKDSpan;
import qkd.network.QKDNetworkParameters;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.QKDConfig;

/** Receiver budget now uses the physical SNetS amplifier/fibre signal path. */
public final class QKDPichReceiverPowerVerification {
	private static void check(boolean good,String text) {
		if(!good) throw new AssertionError(text);
	}

	public static void main(String[] args) {
		check(Math.abs(PichReceivePowerBudget.receivePowerDbm(1e-3,20,0.2,2)+6)<1e-9,
				"legacy pure function still calculates received power");
		Mesh mesh = QKDPichSharedClassicalQoTVerification.mesh(true,false,false);
		Link link = mesh.getLinkList().get(0);
		NetworkConfig net = new NetworkConfig();
		net.setCores(Arrays.asList(new CoreConfig(0,Arrays.asList(1)),
				new CoreConfig(1,Arrays.asList(0))));
		QKDConfig qkd = new QKDConfig(true,40.0,1);
		qkd.setPichSlots(1);
		qkd.setPichRxPowerCheckEnabled(true);
		qkd.setPichReceiverSensitivityDbm(30.0);
		net.setQkd(qkd);
		Route route = new Route();
		route.setLinkList(new Vector<Link>(Collections.singletonList(link)));
		QKDRoutePlan plan = new QKDRoutePlan("A","B",Collections.singletonList(
				new QKDLinkPlan("A","B",40,40,Collections.singletonList(
						new QKDSpan("A","B",0,1,0,40,"A","B")))));
		QKDPichPool pool = new QKDPichPool(1,1,QKDNetworkParameters.from(net),1e-3);
		pool.setClassicalPhysicalLayer(mesh.getPhysicalLayer(),mesh.getAvaliableModulations().get(0));
		check(pool.acquire(route,plan,0,null)==null,"infeasible PICh receiver budget must reject");
		check(link.getCore(0).getUsedSlots()==0,"no leaked spectrum after sensitivity rejection");
		qkd.setPichReceiverSensitivityDbm(-100.0);
		pool = new QKDPichPool(1,1,QKDNetworkParameters.from(net),1e-3);
		pool.setClassicalPhysicalLayer(mesh.getPhysicalLayer(),mesh.getAvaliableModulations().get(0));
		java.util.List<QKDPichPool.Channel> admitted = pool.acquire(route,plan,0,null);
		check(admitted != null && admitted.size()==1,"feasible PICh budget must pass");
		pool.release(admitted);
		check(link.getCore(0).getUsedSlots()==0,"release must free spectrum");
		System.out.println("PASS QKDPichReceiverPowerVerification");
	}
}
