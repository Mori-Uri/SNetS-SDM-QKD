package qkd.verification;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import gprmcsa.routing.Route;
import network.Link;
import network.Node;
import qkd.capacity.QKDPichPool;
import qkd.capacity.QKDSharedResourceManager;
import qkd.capacity.QKDSharedAllocationResult;
import qkd.network.QKDMesh;
import qkd.network.QKDRoutePlanner;
import qkd.resource.QKDChannelAllocation;
import simulationControl.Util;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.TrafficConfig;
import simulationControl.parsers.OthersConfig;
import network.PhysicalLayer;
import qkd.network.QKDLinkPlan;
import qkd.network.QKDNetworkParameters;
import qkd.network.QKDRoutePlan;
import qkd.network.QKDSpan;
import simulationControl.parsers.NetworkConfig;

/**
 * Checks independent QKD/PICh optical sections at trusted nodes, without
 * relaxing the contiguous-spectrum requirement inside any single section.
 * No random traffic or external libraries are required by these PICh tests.
 */
public final class QKDSlotContinuityVerification {
    private QKDSlotContinuityVerification() { }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static final class Fixture {
        final Route route;
        final QKDRoutePlan plan;
        final Link ab, bc;
        final QKDNetworkParameters parameters;
        Fixture(boolean continuous) {
            Node a = new Node("A"), b = new Node("B"), c = new Node("C");
            List<NetworkConfig.CoreConfig> cores = Arrays.asList(
                    new NetworkConfig.CoreConfig(0, Arrays.asList(1)),
                    new NetworkConfig.CoreConfig(1, Arrays.asList(0)));
            ab = new Link(a.getOxc(), b.getOxc(), 8, 12.5e9, 60.0, cores);
            bc = new Link(b.getOxc(), c.getOxc(), 8, 12.5e9, 60.0, cores);
            ab.getCore(1).setQkdReserved(true);
            bc.getCore(1).setQkdReserved(true);
            route = new Route();
            route.setNodeList(new Vector<Node>(Arrays.asList(a,b,c)));
            route.setLinkList(new Vector<Link>(Arrays.asList(ab,bc)));
            QKDLinkPlan p1 = new QKDLinkPlan("A", "B", 60.0, 30.0, Arrays.asList(
                new QKDSpan("A", "B", 0, 2, 0.0, 30.0, "A", "A-B-T1"),
                new QKDSpan("A", "B", 1, 2, 30.0, 60.0, "A-B-T1", "B")));
            QKDLinkPlan p2 = new QKDLinkPlan("B", "C", 60.0, 30.0, Arrays.asList(
                new QKDSpan("B", "C", 0, 2, 0.0, 30.0, "B", "B-C-T1"),
                new QKDSpan("B", "C", 1, 2, 30.0, 60.0, "B-C-T1", "C")));
            plan = new QKDRoutePlan("A", "C", Arrays.asList(p1,p2));
            NetworkConfig conf = new NetworkConfig();
            conf.setCores(cores);
            NetworkConfig.QKDConfig qkd = new NetworkConfig.QKDConfig(true, 30.0, 1);
            qkd.setPichSlots(1);
            qkd.setPichSnrCheckEnabled(false);
            qkd.setPichSlotContinuityAtNetworkNodes(continuous);
            qkd.setQchSlotContinuityAtNetworkNodes(continuous);
            qkd.setPichTransport("optical");
            conf.setQkd(qkd);
            parameters = QKDNetworkParameters.from(conf);
            check(parameters.isQchSlotContinuityAtNetworkNodes() == continuous, "QCh parser flag");
            check(parameters.isPichSlotContinuityAtNetworkNodes() == continuous, "PICh parser flag");
        }
        QKDPichPool pool() { return new QKDPichPool(1, 0, parameters, 0.001); }
    }

    /** Creates a real QKDMesh to verify that the QCh data-plane allocator
     * can convert a band at B but never at artificial QTND trusted nodes. */
    private static QKDMesh meshForQch(boolean continuity, String granularity) {
        NetworkConfig net = new NetworkConfig();
        net.setNodes(Arrays.asList(new NetworkConfig.NodeConfig("A", 4, 4, 0),
                new NetworkConfig.NodeConfig("B", 4, 4, 0),
                new NetworkConfig.NodeConfig("C", 4, 4, 0)));
        net.setCores(Arrays.asList(new NetworkConfig.CoreConfig(0, Arrays.asList(1)),
                new NetworkConfig.CoreConfig(1, Arrays.asList(0))));
        net.setLinks(Arrays.asList(new NetworkConfig.LinkConfig("A", "B", 4, 12.5e9, 40.0),
                new NetworkConfig.LinkConfig("B", "C", 4, 12.5e9, 40.0)));
        net.setModulations(Collections.singletonList(new NetworkConfig.ModulationConfig(
                "QPSK", 4.0, 1000.0, -100.0, -10.0)));
        NetworkConfig.QKDConfig qkd = new NetworkConfig.QKDConfig(true, 25.0, 1);
        qkd.setQchGranularity(granularity);
        qkd.setQchSlotPolicy("firstfit");
        qkd.setQchSlotContinuityAtNetworkNodes(continuity);
        qkd.setPichSlots(0);
        qkd.setPichTransport("ip");
        net.setQkd(qkd);
        PhysicalLayerConfig phy = new PhysicalLayerConfig();
        phy.setPhysicalLayerModel(PhysicalLayer.MODEL_JOHANNISSON);
        phy.setActiveASE(true);
        phy.setActiveNLI(false);
        phy.setActiveXT(false);
        phy.setPower(0);
        phy.setSpanLength(40.0);
        phy.setFiberLoss(0.2);
        phy.setFiberDispersion(16.7e-6);
        phy.setFiberNonlinearity(0.0012);
        phy.setCenterFrequency(193.4e12);
        phy.setConstantOfPlanck(6.62607015e-34);
        phy.setNoiseFigureOfOpticalAmplifier(5.0);
        phy.setPowerSaturationOfOpticalAmplifier(18.0);
        phy.setAmplificationFrequency(193.4e12);
        phy.setPolarizationModes(2);
        phy.setCouplingCoefficient(1e-7);
        phy.setBendingRadius(0.05);
        phy.setPropagationConstant(5.9e6);
        phy.setCorePitch(41e-6);
        return new QKDMesh(net, new TrafficConfig(), phy,
                new OthersConfig(), null, new Util(), 1.0);
    }

    private static Route qchRoute(QKDMesh mesh) {
        return new Route(new Vector<Node>(Arrays.asList(
                mesh.getNodeList().get(0), mesh.getNodeList().get(1), mesh.getNodeList().get(2))));
    }

    private static void verifyQch(boolean strict, String granularity) throws Exception {
        QKDMesh mesh = meshForQch(strict, granularity);
        Route route = qchRoute(mesh);
        Link ab = route.getLink(0), bc = route.getLink(1);
        ab.getCore(1).setQkdReserved(true);
        bc.getCore(1).setQkdReserved(true);
        // Disjoint sets force route-level failure in strict mode, but permit
        // two independent QKDSpans per link to choose their own bands.
        if (!strict || "route".equals(granularity)) {
            ab.getCore(1).useSpectrumForQKD(new int[]{1,2}, 0);
            bc.getCore(1).useSpectrumForQKD(new int[]{3,4}, 0);
        }
        QKDSharedResourceManager mgr = new QKDSharedResourceManager(mesh);
        QKDSharedAllocationResult result = mgr.allocate(route,
                new QKDRoutePlanner(mesh.getQkdTopologyPlan()).plan(route),
                1e6, 1, 0, 1550.0);
        if (strict && "route".equals(granularity)) {
            check(!result.isSuccess(), "route-continuous QCh must reject lack of a common band");
            check(ab.getCore(1).getUsedSlots()==2 && bc.getCore(1).getUsedSlots()==2,
                    "failed QCh admission must not leak spectrum");
        } else {
            check(result.isSuccess(), "QCh must be allocated: " + result.getFailureReason());
            List<QKDChannelAllocation> allocations = result.getSession().getAllocations();
            check(allocations.size() == ("span".equals(granularity) ? 4 : 2),
                    "one QCh reservation per tested physical section");
            if ("span".equals(granularity)) {
                // Distinct QKDSpans can share one physical lane longitudinally;
                // each still has its own Q-TRx and key-generation capacity.
                int[] expected = strict ? new int[]{1, 1, 1, 1} : new int[]{3, 3, 1, 1};
                for (int k = 0; k < 4; k++)
                    check(allocations.get(k).getSpectrumBand()[0] == expected[k],
                            "independent QKDSpans must be allowed different slot bands");
            } else {
                int first = strict ? 1 : 3;
                check(allocations.get(0).getSpectrumBand()[0] == first
                        && allocations.get(1).getSpectrumBand()[0] == 1,
                        "link-QCh may convert only at physical SNetS node B");
            }
            mgr.release(result.getSession());
            int expectedOtherSlots = (!strict || "route".equals(granularity)) ? 2 : 0;
            check(ab.getCore(1).getUsedSlots()==expectedOtherSlots
                        && bc.getCore(1).getUsedSlots()==expectedOtherSlots,
                        "QCh release must preserve external spectrum occupants and clear all lanes");
        }
    }

    public static void main(String[] args) throws Exception {
        Fixture strict = new Fixture(true);
        QKDPichPool pool = strict.pool();
        List<QKDPichPool.Channel> got = pool.acquire(strict.route, strict.plan, 0, null);
        check(got != null && got.size()==4, "four independent PICh optical sources");
        check(got.get(0).band[0]==1 && got.get(1).band[0]==2
                        && got.get(2).band[0]==1 && got.get(3).band[0]==2,
                "PICh may retune independently between QTND trusted spans");
        check(strict.ab.getCore(0).getUsedSlots()==2 && strict.bc.getCore(0).getUsedSlots()==2,
                "one optical allocation per independent PICh");
        pool.release(got);
        check(strict.ab.getCore(0).getUsedSlots()==0 && strict.bc.getCore(0).getUsedSlots()==0,
                "release must clear shared-spectrum leases");

        Fixture loose = new Fixture(false);
        loose.ab.getCore(0).useSpectrum(new int[]{1,1}, 0);
        loose.bc.getCore(0).useSpectrum(new int[]{2,2}, 0);
        pool = loose.pool();
        got = pool.acquire(loose.route, loose.plan, 0, null);
        check(got != null && got.size()==4, "network-node conversion must allow admission");
        check(got.get(0).band[0]==2 && got.get(1).band[0]==3,
                "A-B trusted node supports independent PICh bands");
        check(got.get(2).band[0]==1 && got.get(3).band[0]==3,
                "B-C trusted node supports independent PICh bands");
        pool.release(got);
        check(loose.ab.getCore(0).getUsedSlots()==1 && loose.bc.getCore(0).getUsedSlots()==1,
                "release cannot remove external spectrum occupants");

        Fixture blocked = new Fixture(true);
        blocked.ab.getCore(0).useSpectrum(new int[]{1,1}, 0);
        blocked.bc.getCore(0).useSpectrum(new int[]{1,8}, 0);
        pool = blocked.pool();
        got = pool.acquire(blocked.route, blocked.plan, 0, null);
        check(got == null, "full B-C spectrum must reject and rollback PIChs");
        check(blocked.ab.getCore(0).getUsedSlots()==1 && blocked.bc.getCore(0).getUsedSlots()==8,
                "strict failed admission must rollback all PICh reservations and auxiliary sources");
        check(blocked.ab.getCore(0).getAuxiliaryClassicalCircuitList().isEmpty()
                        && blocked.bc.getCore(0).getAuxiliaryClassicalCircuitList().isEmpty(),
                "no leaked optical PICh after rollback");
        verifyQch(true, "route");
        verifyQch(false, "route");
        verifyQch(true, "span");
        verifyQch(false, "span");
        verifyQch(true, "link");
        verifyQch(false, "link");
        System.out.println("PASS QKDSlotContinuityVerification: trusted-node retuning, continuous individual spans, rollback");
    }
}
