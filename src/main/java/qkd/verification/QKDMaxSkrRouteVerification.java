package qkd.verification;

import java.util.Arrays;
import java.util.Vector;

import gprmcsa.routing.Route;
import network.Link;
import network.Node;
import network.PhysicalLayer;
import qkd.capacity.QKDSharedAllocationResult;
import qkd.capacity.QKDSharedChannel;
import qkd.capacity.QKDSharedResourceManager;
import qkd.network.QKDMesh;
import qkd.network.QKDRoutePlan;
import qkd.network.QKDRoutePlanner;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.TrafficConfig;

/** Verifica a escolha real do slot pela SKR usando o gerenciador de capacidade QKD. */
public final class QKDMaxSkrRouteVerification {
    private QKDMaxSkrRouteVerification() { }

    private static int allocate(String policy) throws Exception {
        NetworkConfig net = new NetworkConfig();
        net.setNodes(Arrays.asList(
                new NetworkConfig.NodeConfig("A", 4, 4, 0),
                new NetworkConfig.NodeConfig("B", 4, 4, 0)));
        net.setCores(Arrays.asList(
                new NetworkConfig.CoreConfig(0, Arrays.asList(1)),
                new NetworkConfig.CoreConfig(1, Arrays.asList(0))));
        net.setLinks(Arrays.asList(new NetworkConfig.LinkConfig("A", "B", 16, 12.5e9, 40.0)));
        net.setModulations(Arrays.asList(new NetworkConfig.ModulationConfig(
                "QPSK", 4.0, 1000.0, -100.0, -10.0)));
        NetworkConfig.QKDConfig q = new NetworkConfig.QKDConfig(true, 25.0, 1);
        q.setQchGranularity("route");
        q.setQchSlotPolicy(policy);
        q.setWavelengthPolicy("grid");
        q.setPichSlots(0);
        q.setPichTransport("ip");
        net.setQkd(q);

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

        QKDMesh mesh = new QKDMesh(net, new TrafficConfig(), phy,
                new OthersConfig(), null, new Util(), 1.0);
        Link link = mesh.getLinkList().get(0);
        link.getCore(1).setQkdReserved(true);
        Vector<Node> nodes = new Vector<Node>(Arrays.asList(
                mesh.getNodeList().get(0), mesh.getNodeList().get(1)));
        Route route = new Route(nodes);
        QKDRoutePlan plan = new QKDRoutePlanner(mesh.getQkdTopologyPlan()).plan(route);
        QKDSharedResourceManager manager = new QKDSharedResourceManager(mesh);
        QKDSharedAllocationResult result = manager.allocate(route, plan, 1e6, 1, 0, 1550.0);
        if (!result.isSuccess()) {
            throw new AssertionError(policy + ": allocation failed: " + result);
        }
        if (manager.snapshotChannels().isEmpty()) throw new AssertionError("no allocated channels");
        QKDSharedChannel channel = manager.snapshotChannels().iterator().next();
        int slot = channel.getAllocation().getSpectrumBand()[0];
        System.out.printf(java.util.Locale.ROOT, "%s: slot=%d lambda=%.8f nm capacity=%.7f Mbit/s%n",
                policy, slot, channel.getWavelengthNm(), channel.getCapacityBps() / 1e6);
        return slot;
    }

    public static void main(String[] args) throws Exception {
        int firstFit = allocate("firstfit");
        int best = allocate("maxskr");
        if (firstFit != 1) throw new AssertionError("firstfit must select slot 1, got " + firstFit);
        if (best < 7 || best > 10) throw new AssertionError("maxskr must favor center slots, got " + best);
        System.out.println("PASS QKDMaxSkrRouteVerification");
    }
}
