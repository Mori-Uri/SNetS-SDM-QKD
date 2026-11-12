package qkd.verification;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import com.google.gson.Gson;

import measurement.Measurements;
import simulationControl.SimulationManagement;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.SimulationRequest;
import simulationControl.parsers.TrafficConfig;
import simulator.Simulation;
import simulator.Simulator;

/** Smoke test: with active QoT, execution must reach an actual request event,
 * not stop at the former artificial modulation-reach sweep. */
public final class ModulationReachRequestLoopVerification {
    public static void main(String[] args) throws Exception {
        File tmp = Files.createTempDirectory("snets-reach-event-").toFile();
        File run = new File(tmp, "smoke");
        if (!run.mkdir()) throw new IllegalStateException("cannot create case directory");
        Gson gson = new Gson();
        SimulationRequest request = new SimulationRequest();
        request.setPath(tmp.getAbsolutePath());
        request.setName("smoke");
        request.setNetworkConfig(gson.fromJson(QKDSpanGranularityVerification.networkJson(), NetworkConfig.class));
        request.setTrafficConfig(gson.fromJson(QKDSpanGranularityVerification.trafficJson(), TrafficConfig.class));
        PhysicalLayerConfig physical = gson.fromJson(QKDSpanGranularityVerification.physicalLayerJson(), PhysicalLayerConfig.class);
        physical.setActiveQoT(true);
        request.setPhysicalLayerConfig(physical);
        SimulationConfig sc = gson.fromJson(QKDSpanGranularityVerification.simulationJson(), SimulationConfig.class);
        request.setSimulationConfig(sc);
        request.setOthersConfig(new OthersConfig());
        long started = System.nanoTime();
        List<List<Simulation>> grouped = SimulationManagement.createAllSimulations(request);
        long initMs = (System.nanoTime() - started) / 1000000L;
        if (grouped.size() != 1 || grouped.get(0).size() != 1)
            throw new AssertionError("expected exactly one replication");
        Simulation sim = grouped.get(0).get(0);
        Measurements measurements = new Simulator(sim).start();
        if (!measurements.finished()) throw new AssertionError("the event loop did not finish its request");
        if (initMs >= 15000L) throw new AssertionError("slow startup: " + initMs + " ms");
        if (!new File(run, "kRoutesByPar.txt").isFile())
            throw new AssertionError("request routing was not reached");
        System.out.println("PASS ModulationReachRequestLoopVerification: reached first request and finished event loop, startup=" + initMs + " ms");
    }
}
