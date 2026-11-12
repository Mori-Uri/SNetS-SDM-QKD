package qkd.verification;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import gprmcsa.GPRMCSA;
import gprmcsa.routing.Route;
import network.Circuit;
import network.Mesh;
import network.Pair;
import qkd.control.QKDControlPlane;
import qkd.control.QKDProvisioningResult;
import qkd.control.QKDProvisioningService;
import qkd.key.QKDKeyDemand;
import qkd.key.QKDTemporalKeyInventory;
import qkd.network.QKDMesh;
import qkd.network.QKDMeshFactory;
import qkd.resource.QKDSession;
import qkd.traffic.RequestProtectionType;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.TrafficConfig;

/** Stage 4: non-partial keys, multi-hop atomicity, interleaved generation,
 * microsecond fast path, bounded batching, and configuration backward compatibility. */
public final class QKDStage4InventoryDynamicsVerification {
    private QKDStage4InventoryDynamicsVerification() { }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void eq(double got, double want, String message) {
        if (Math.abs(got-want)>1e-4*Math.max(1.0,Math.abs(want)))
            throw new AssertionError(message + ": " + got + " versus " + want);
    }
    private static SimulationConfig.QKDKeyInventoryConfig config(double initial, double capacity) {
        SimulationConfig.QKDKeyInventoryConfig c = new SimulationConfig.QKDKeyInventoryConfig();
        c.setInitialBits(initial); c.setCapacityBits(capacity); c.setMode("temporal-inventory");
        return c;
    }
    public static void main(String[] args) throws Exception {
        Gson gson = new Gson();
        NetworkConfig network = gson.fromJson(QKDSpanGranularityVerification.networkJson(), NetworkConfig.class);
        TrafficConfig traffic = gson.fromJson(QKDSpanGranularityVerification.trafficJson(), TrafficConfig.class);
        PhysicalLayerConfig physical = gson.fromJson(QKDSpanGranularityVerification.physicalLayerJson(), PhysicalLayerConfig.class);
        SimulationConfig simulation = gson.fromJson(QKDSpanGranularityVerification.simulationJson(), SimulationConfig.class);
        check("rate".equals(simulation.getQkdKeyInventory().getMode()), "legacy default must remain rate-reservation");
        check(!gson.fromJson("{\"qkdKeyInventory\":{\"mode\":\"rate-reservation\"}}",SimulationConfig.class)
                .getQkdKeyInventory().isStock(), "rate alias broken");
        check(gson.fromJson("{\"qkdKeyInventory\":{\"mode\":\"temporal-inventory\"}}", SimulationConfig.class)
                .getQkdKeyInventory().isStock(), "temporal alias broken");
        simulation.setQkdKeyInventory(config(256, 1024));
        Util util = new Util(); util.projectPath = ".";
        Mesh generic = QKDMeshFactory.create(network, traffic, physical, new OthersConfig(),
                new HashMap<String,HashMap<Double,Double>>(), util, simulation);
        QKDMesh mesh = (QKDMesh)generic;
        Pair pair = QKDSpanGranularityVerification.findPair(mesh,"1","2");
        Route route = QKDSpanGranularityVerification.directRoute(pair);
        GPRMCSA algorithm = new GPRMCSA("notrafficgrooming","completesharing","djk","ksp",
                "modulationbydistance","firstfit","aar","qabne","fsalfav1","cpsd");
        QKDControlPlane cp = new QKDControlPlane(mesh, GPRMCSA.RMCSA_INTEGRATED,
                algorithm.instantiateGrooming(),algorithm.instantiateIntegratedRSA(),
                algorithm.instantiateRouting(),algorithm.instantiateKRouting(),
                algorithm.instantiateSpectrumAssignment(),algorithm.instantiateModulationSelection(),
                algorithm.instantiateCoreAndSpectrumAssignment(),algorithm.instantiateReallocation(),
                algorithm.instantiatePowerAssignment());
        RequestForConnection request = new RequestForConnection();
        request.setPair(pair);request.setProtectionType(RequestProtectionType.QKD_PROTECTED);
        request.setRequiredBitRate(1e9);request.setQkdKeyDemand(new QKDKeyDemand(256,1.0));
        Circuit circuit=cp.createNewCircuit(request);circuit.setRoute(route);
        QKDProvisioningService provisioner = new QKDProvisioningService(mesh);
        QKDProvisioningResult result = provisioner.provision(request);
        check(result.isSuccess(),"fixture quantum provisioning failed: "+result.getFailureReason());
        QKDSession session = result.getConnection().getQkdSession();

        QKDTemporalKeyInventory atomic = new QKDTemporalKeyInventory(config(256,512),true);
        check(atomic.hops(session).size()==2,"expected two independent trusted-node hops");
        atomic.open(request,session);
        Map<String,Double> rates = new HashMap<String,Double>();
        String[] hops = atomic.hops(session).toArray(new String[0]);
        rates.put(hops[0],256.0);rates.put(hops[1],128.0);
        atomic.advanceRates(1.0,rates);
        eq(atomic.getBalanceBits(hops[0]),256.0,"no partial debit on available hop");
        eq(atomic.getBalanceBits(hops[1]),128.0,"no partial debit on deficient hop");
        check(atomic.getUnservedRenewals()==1,"missing end-to-end renewal should count ONCE");
        eq(atomic.getConsumedBits(),512.0,"only initial two-hop key should be delivered");
        eq(atomic.getUnservedBits(),512.0,"missing complete 256-bit keys on BOTH hops");
        rates.put(hops[0],0.0);rates.put(hops[1],128.0);
        atomic.advanceRates(2.0,rates);
        check(atomic.getServedRenewals()==1,"replenished two-hop renewal not served");
        eq(atomic.getTotalBalanceBits(),0.0,"replenished inventory not debited atomically");
        atomic.close(request);
        atomic.advanceRates(3.0,Collections.<String,Double>emptyMap());
        eq(atomic.getTotalBalanceBits(),0.0,"released channels must not generate keys");

        QKDTemporalKeyInventory cap = new QKDTemporalKeyInventory(config(256,512),true);
        request.setQkdKeyDemand(new QKDKeyDemand(256,1e-3));
        cap.open(request,session);
        for (String h : hops) rates.put(h,1e6);
        cap.advanceRates(0.002,rates);
        check(cap.getServedRenewals()==2,"generation must interleave two renewals");
        check(cap.getUnservedRenewals()==0,"incorrect overflow before consumption");
        eq(cap.getTotalBalanceBits(),512.0,"capacity-limited balance after two renewals");
        eq(cap.getDiscardedBits(),2464.0,"exact inter-event capacity overflow");
        cap.close(request);

        QKDTemporalKeyInventory sustainable = new QKDTemporalKeyInventory(config(256,1024),true);
        request.setQkdKeyDemand(new QKDKeyDemand(256,1e-6));
        sustainable.open(request,session);
        for (String h : hops) rates.put(h,1e9);
        sustainable.advanceRates(1.0,rates); // one million renewals, without one million iterations
        check(sustainable.getServedRenewals()==1000000L,"microsecond sustainable throughput");
        check(sustainable.getBatchedWindows()==0L,"sustainable fast path must be exact");
        eq(sustainable.getTotalBalanceBits(),1536.0,"capped final inventory (768 per hop)");
        check(sustainable.getUnservedRenewals()==0L,"false exhaustion in sustainable mode");
        sustainable.close(request);

        QKDTemporalKeyInventory depleted = new QKDTemporalKeyInventory(config(256,512),true);
        depleted.open(request,session);
        depleted.advanceRates(1.0,Collections.<String,Double>emptyMap());
        check(depleted.getBatchedWindows()>0,"bounded batch path not used for one million events");
        check(depleted.getApproximatedRenewals()==1000000L,"approximate events not disclosed");
        check(depleted.getUnservedRenewals()==1000000L,"unserved renewals under no production");
        eq(depleted.getConsumedBits(),512.0,"no nonexistent keys may be consumed");
        depleted.close(request);

        QKDTemporalKeyInventory rollback = new QKDTemporalKeyInventory(config(256,512),true);
        rollback.open(request,session);
        rollback.rollbackOpen(request);
        eq(rollback.getTotalBalanceBits(),512.0,"rollback must return key to both hops");
        eq(rollback.getConsumedBits(),0.0,"rollback must restore counters");
        check(rollback.getActiveConsumerCount()==0,"rollback must close consumer");
        rollback.open(request,session);
        rollback.advanceRates(0.1,Collections.<String,Double>emptyMap());
        boolean failed=false;
        try { rollback.rollbackOpen(request); } catch (IllegalStateException expected) { failed=true; }
        check(failed,"late rollback must never mint keys");
        rollback.close(request);
        check(provisioner.release(result.getConnection()),"leaked optical QChs after verification");
        System.out.println("PASS QKDStage4InventoryDynamicsVerification: atomic multi-hop, full keys, interleaving/cap, 1M microsecond renewals, exhausted batching, rollback, aliases");
    }
}
