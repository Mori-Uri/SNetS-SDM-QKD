package qkd.verification;

import java.lang.reflect.Method;
import java.util.HashMap;
import com.google.gson.Gson;
import measurement.Measurements;
import network.Mesh;
import qkd.control.QKDControlPlane;
import qkd.metrics.QKDMetricsSnapshot;
import qkd.network.QKDMeshFactory;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.TrafficConfig;
import simulator.Simulation;
import simulator.Simulator;

/** Verifies both stock and legacy modes through the actual SNetS event queue. */
public final class QKDStage4EventLoopVerification {
    private QKDStage4EventLoopVerification() { }
    private static void check(boolean b, String m) { if (!b) throw new AssertionError(m); }
    private static String fixture(String method) throws Exception {
        Method m = QKDMetricsVerification.class.getDeclaredMethod(method);
        m.setAccessible(true);
        return (String)m.invoke(null);
    }
    public static void main(String[] args) throws Exception {
        Gson gson = new Gson();
        for (boolean useStock : new boolean[] {false, true}) {
            NetworkConfig network = gson.fromJson(fixture("networkJson"), NetworkConfig.class);
            TrafficConfig traffic = gson.fromJson(fixture("trafficJson"), TrafficConfig.class);
            PhysicalLayerConfig physical = gson.fromJson(fixture("physicalLayerJson"), PhysicalLayerConfig.class);
            SimulationConfig config = gson.fromJson(fixture("simulationJson"), SimulationConfig.class);
            config.setTrafficRandomSeed(Long.valueOf(9444L));
            SimulationConfig.QKDKeyInventoryConfig keys = new SimulationConfig.QKDKeyInventoryConfig();
            keys.setMode(useStock ? "temporal-inventory" : "rate-reservation");
            keys.setInitialBits(256); keys.setCapacityBits(1048576);
            config.setQkdKeyInventory(keys);
            Util util = new Util(); util.projectPath = ".";
            Mesh mesh = QKDMeshFactory.create(network,traffic,physical,new OthersConfig(),
                    new HashMap<String,HashMap<Double,Double>>(),util,config);
            Simulation simulation = new Simulation(config,mesh,0,0,util);
            Measurements measured = new Simulator(simulation).start();
            check(measured != null, "no measurements");
            QKDControlPlane cp = (QKDControlPlane)simulation.getControlPlane();
            check(cp.getQkdAccepted() > 0,"no QKD session admitted");
            check(cp.getActiveQkdSessionCount()==0,"leaked sessions after departure");
            QKDMetricsSnapshot result = cp.snapshotQkdMetrics(0,0);
            check(result.released==result.accepted, "unmatched key session release");
            if (useStock) {
                check("stock".equals(result.keyInventoryMode),"stock mode not reported");
                check(result.keyGeneratedBits > 0.0,"active QCh did not produce keys");
                check(result.keyConsumedBits > 0.0,"no key consumption in dynamic run");
                check(result.keyRemainingBits >= 0.0,"negative final stock");
                check(result.keyServedRenewals+result.keyUnservedRenewals>0L,
                        "no renewable keys during dynamic execution");
            } else {
                check("rate".equals(result.keyInventoryMode),"legacy mode changed");
                check(result.keyGeneratedBits==0.0 && result.keyConsumedBits==0.0,
                        "legacy must not use the stock ledger");
            }
            System.out.println("[OK] mode=" + result.keyInventoryMode
                    + " admitted=" + result.accepted + " released=" + result.released
                    + " generated=" + result.keyGeneratedBits
                    + " delivered=" + result.keyConsumedBits
                    + " renewals=" + result.keyServedRenewals);
        }
        System.out.println("PASS QKDStage4EventLoopVerification: stock/rate end-to-end events, departure, QCh production and metrics");
    }
}
