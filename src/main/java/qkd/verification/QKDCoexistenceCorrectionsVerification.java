package qkd.verification;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import gprmcsa.routing.Route;
import network.Link;
import network.Mesh;
import qkd.capacity.QKDPichCircuit;
import qkd.capacity.QKDPichPool;
import qkd.network.QKDLinkPlan;
import qkd.network.QKDNetworkParameters;
import qkd.network.QKDRoutePlan;
import qkd.network.QKDSpan;
import qkd.physical.QKDCrosstalkPowerResult;
import qkd.physical.QKDPhysicalLayer;
import qkd.resource.QKDChannelAllocation;
import simulationControl.parsers.NetworkConfig;

/** Deterministic regression of independent QBER and PICh longitudinal XT/spectrum reuse. */
public final class QKDCoexistenceCorrectionsVerification {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void near(double actual, double expected, String message) {
        if (Math.abs(actual-expected) > Math.max(1e-23,Math.abs(expected)*1e-10))
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
    }

    public static void main(String[] args) throws Exception {
        Mesh mesh = QKDPichSharedClassicalQoTVerification.mesh(false,false,false);
        Link link = mesh.getLinkList().get(0);
        link.getCore(1).setQkdReserved(true);
        final QKDSpan s0 = new QKDSpan("A","B",0,2,0.0,20.0,"A","TN");
        final QKDSpan s1 = new QKDSpan("A","B",1,2,20.0,40.0,"TN","B");
        int[] band = {1,1};
        QKDPichCircuit pich = new QKDPichCircuit(link,s0,0,band,0,1e-3);
        pich.assignModulation(mesh.getAvaliableModulations().get(0));
        link.getCore(0).addAuxiliaryClassicalCircuit(pich);
        QKDPhysicalLayer layer = new QKDPhysicalLayer();
        final double h = 1e-15;
        QKDCrosstalkPowerResult beginning = layer.evaluatePhysicalSpanUsingStoredLaunchPowers(
                link,1,band,h,s0,null);
        QKDCrosstalkPowerResult end = layer.evaluatePhysicalSpanUsingStoredLaunchPowers(
                link,1,band,h,s1,null);
        near(beginning.getTotalPowerW(),1e-3*20000*h,"PICh first span only");
        near(end.getTotalPowerW(),0,"PICh must not interfere outside its span");
        link.getCore(0).removeAuxiliaryClassicalCircuit(pich);

        // The physical result can have positive SKR but be rejected by an
        // independent maximum-QBER criterion.
        QKDLinkPlan lp = new QKDLinkPlan("A","B",40.0,40.0,
                Collections.singletonList(new QKDSpan("A","B",0,1,0,40,"A","B")));
        layer.setMaximumQber(0.01);
        QKDChannelAllocation a = new QKDChannelAllocation(link,1,band,0,1);
        qkd.capacity.QKDLinkChannelPhysicalResult tooManyErrors =
                layer.evaluateLinkChannel(link, lp, mesh.getPhysicalLayer(), a, 1550.0);
        check(tooManyErrors.getMinimumSecretKeyRateBps() > 0, "positive SKR expected");
        check(tooManyErrors.getMaximumQber() > 0.01, "QBER must exceed configured limit");
        check(!tooManyErrors.getSpans().get(0).isViable(), "independent QBER admission");
        layer.setMaximumQber(0.11);
        check(layer.evaluateLinkChannel(link,lp,mesh.getPhysicalLayer(),a,1550.0)
                .getSpans().get(0).isViable(), "11% reference bound");

        NetworkConfig network = new NetworkConfig();
        network.setCores(Arrays.asList(
                new NetworkConfig.CoreConfig(0,Arrays.asList(1)),
                new NetworkConfig.CoreConfig(1,Arrays.asList(0))));
        NetworkConfig.QKDConfig config = new NetworkConfig.QKDConfig(true,20,1);
        config.setPichSlots(1);
        config.setPichSnrCheckEnabled(false); // test spectrum lease only
        config.setPichLongitudinalReuse(true);
        network.setQkd(config);
        QKDNetworkParameters np = QKDNetworkParameters.from(network);
        check(np.isPichLongitudinalReuse(),"reuse setting must be parsed");
        check(np.getMaximumQber()==0.11,"stored maximum QBER");
        check(!np.isQberThresholdEnabled() && np.getEffectiveMaximumQber()==1.0,
                "independent gate disabled by default");
        config.setMaximumQber(0.06);
        check(QKDNetworkParameters.from(network).getMaximumQber()==0.06,"overridden QBER");
        check(QKDNetworkParameters.from(network).getEffectiveMaximumQber()==1.0,
                "QBER threshold alone must not enable gate");
        config.setQberThresholdEnabled(true);
        check(QKDNetworkParameters.from(network).getEffectiveMaximumQber()==0.06,
                "explicit gate uses configured cap");

        Route route = new Route();
        route.setLinkList(new Vector<Link>(Collections.singletonList(link)));
        QKDRoutePlan rp = new QKDRoutePlan("A","B",Collections.singletonList(
                new QKDLinkPlan("A","B",40,20,Arrays.asList(s0,s1))));
        QKDPichPool pool = new QKDPichPool(1,1,np,1e-3);
        List<QKDPichPool.Channel> channels = pool.acquire(route,rp,0,null);
        check(channels != null && channels.size()==2,"two dedicated PIChs opened");
        check(channels.get(0).band[0] == channels.get(1).band[0],
                "disjoint PICh spans should share one spectral band");
        check(link.getCore(0).getUsedSlots()==1,"one physical spectrum lease");
        List<QKDPichPool.Channel> first = new java.util.ArrayList<QKDPichPool.Channel>();
        first.add(channels.remove(0));
        pool.release(first);
        check(link.getCore(0).getUsedSlots()==1,"shared lease retained after first release");
        pool.release(channels);
        check(link.getCore(0).getUsedSlots()==0,"lease released after last PICh");
        check(link.getCore(0).getAuxiliaryClassicalCircuitList().isEmpty(),
                "no orphaned PICh sources");
        System.out.println("PASS QKDCoexistenceCorrectionsVerification");
    }
}
