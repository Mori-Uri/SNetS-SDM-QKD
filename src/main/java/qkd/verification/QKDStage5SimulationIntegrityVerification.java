package qkd.verification;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.Vector;

import com.google.gson.Gson;

import gprmcsa.routing.Route;
import network.Circuit;
import network.Link;
import network.Mesh;
import network.Node;
import network.PhysicalLayer;
import network.Poggiolini2017GnIntegral;
import simulationControl.SimulationFileManager;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.NodeConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;
import simulationControl.parsers.NetworkConfig.ModulationConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.SimulationRequest;
import simulationControl.parsers.TrafficConfig;

/** Regression tests found during scientific Stage 5's first real dynamic campaign.
 * No reference-data scientific validation is claimed by this functional check. */
public final class QKDStage5SimulationIntegrityVerification {
    private QKDStage5SimulationIntegrityVerification() { }
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    private static void verifyExport() throws Exception {
        Path temp = Files.createTempDirectory("stage5-config-");
        try {
            NetworkConfig net = new NetworkConfig();
            NetworkConfig.QKDConfig qkd = new NetworkConfig.QKDConfig(true,25.0,0);
            // Finite optional parameter MUST survive JSON export; unset NaN sentinels MUST be omitted.
            qkd.setPichReceiverNoiseW(1e-7);
            net.setQkd(qkd);
            SimulationRequest request = new SimulationRequest();
            request.setName("case");
            request.setNetworkConfig(net);
            request.setSimulationConfig(new SimulationConfig());
            request.setPhysicalLayerConfig(new PhysicalLayerConfig());
            request.setTrafficConfig(new TrafficConfig());
            request.setOthersConfig(new OthersConfig());
            new SimulationFileManager().writeSimulation(temp.toString(),request);
            String contents = new String(Files.readAllBytes(temp.resolve("case/network.json")),StandardCharsets.UTF_8);
            check(!contents.contains(":NaN") && !contents.contains(":Infinity"),
                    "Network export contains invalid JSON nonfinite numbers");
            NetworkConfig fromJson = new Gson().fromJson(contents, NetworkConfig.class);
            check(fromJson.getQkd() != null, "QKD configuration disappeared on export");
            check(Math.abs(fromJson.getQkd().getPichReceiverNoiseW() - 1e-7) < 1e-14,
                    "Finite PICh receiver noise changed on export");
            check(!Double.isFinite(fromJson.getQkd().getPichXtCouplingPerKm()),
                    "Absent PICh XT sentinel should remain unset on reload");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temp)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyCrossCoreNoNli() throws Exception {
        NetworkConfig config = new NetworkConfig();
        config.setNodes(Arrays.asList(new NodeConfig("A",10,10,0), new NodeConfig("B",10,10,0)));
        config.setCores(Arrays.asList(
                new CoreConfig(0,Arrays.asList(1)),
                new CoreConfig(1,Arrays.asList(0))));
        config.setLinks(Arrays.asList(new LinkConfig("A","B",128,12.5e9,80.0)));
        config.setGuardBand(1);
        config.setModulations(Arrays.asList(new ModulationConfig("QPSK",4,10000,-100,-10)));
        PhysicalLayerConfig phys = new PhysicalLayerConfig();
        phys.setCenterFrequency(193.1e12);
        phys.setAmplificationFrequency(193.1e12);
        phys.setFiberLoss(.2);
        phys.setSpanLength(80.0);
        phys.setFiberNonlinearity(.0013);
        phys.setFiberDispersion(16.7e-6);
        phys.setPolarizationModes(2.0);
        phys.setConstantOfPlanck(6.626e-34);
        phys.setNoiseFigureOfOpticalAmplifier(5.0);
        phys.setPowerSaturationOfOpticalAmplifier(20.0);
        phys.setActiveQoT(false);
        phys.setActiveASE(false);
        phys.setActiveNLI(false);
        phys.setActiveXT(false);
        Mesh mesh = new Mesh(config,new TrafficConfig(),phys,new OthersConfig(),null,new Util());
        Link link = mesh.getLinkList().get(0);
        Route route = new Route(new Vector<Node>(Arrays.asList(mesh.getNodeList().get(0),
                mesh.getNodeList().get(1))));
        Circuit victim = new Circuit();victim.setRoute(route);victim.setIndexCore(0);
        Circuit otherCore = new Circuit();otherCore.setRoute(route);otherCore.setIndexCore(1);
        Circuit sameCore = new Circuit();sameCore.setRoute(route);sameCore.setIndexCore(0);
        Method get = PhysicalLayer.class.getDeclaredMethod("getCircuitList", Link.class,int.class,
                Circuit.class,Circuit.class,boolean.class);
        get.setAccessible(true);
        TreeSet<Circuit> without = (TreeSet<Circuit>)get.invoke(mesh.getPhysicalLayer(),
                link,0,victim,otherCore,true);
        check(without.size()==1 && without.contains(victim) && !without.contains(otherCore),
                "Candidate on neighboring core incorrectly creates same-core GN NLI");
        TreeSet<Circuit> with = (TreeSet<Circuit>)get.invoke(mesh.getPhysicalLayer(),
                link,0,victim,sameCore,true);
        check(with.size()==2 && with.contains(sameCore),
                "Same-core candidate was ignored by GN");
    }

    private static void verifyDisjointUnequalWidths() {
        double f=193.1e12;
        Poggiolini2017GnIntegral.Fiber fiber = new Poggiolini2017GnIntegral.Fiber(.2,1.3,-21.3,80.0,1);
        Poggiolini2017GnIntegral.Channel cut = Poggiolini2017GnIntegral.Channel.qpsk(f,50e9,.001);
        // Bcut/2+Bsrc/2=31.25 GHz, so 32 GHz separation is strictly NON-overlapping.
        Poggiolini2017GnIntegral.Channel neighbor = Poggiolini2017GnIntegral.Channel.qpsk(f+32e9,12.5e9,.001);
        double legacy = PhysicalLayer.legacySci(fiber,cut,1.0) + PhysicalLayer.legacyXci(fiber,cut,neighbor,1.0);
        PhysicalLayer.HabibiGnDecision value = PhysicalLayer.evaluate(fiber,cut,
                Arrays.asList(neighbor),legacy,100.0,true,20,8);
        check(value.applied && value.supportedPairs==1 && Double.isFinite(value.corrected)
                && value.corrected>0,"Disjoint 50/12.5-GHz channels rejected by Habibi fallback");
        // Genuine spectral overlap, rather than unequal-width adjacency, stays fail-closed.
        Poggiolini2017GnIntegral.Channel overlapping = Poggiolini2017GnIntegral.Channel.qpsk(f+30e9,12.5e9,.001);
        boolean rejected=false;
        try {
            PhysicalLayer.evaluate(fiber,cut,Arrays.asList(overlapping),legacy,100.0,true,20,8);
        } catch (ArithmeticException expected) { rejected=true; }
        check(rejected,"Overlapping same-core channels must be rejected explicitly");
    }

    public static void main(String[] args) throws Exception {
        verifyExport();
        verifyCrossCoreNoNli();
        verifyDisjointUnequalWidths();
        System.out.println("PASS QKDStage5SimulationIntegrityVerification: network JSON, cross-core filtering, disjoint-width GN");
    }
}
