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
import qkd.control.QKDActiveConnection;
import qkd.control.QKDControlPlane;
import qkd.control.QKDProvisioningResult;
import qkd.control.QKDProvisioningService;
import qkd.key.QKDKeyDemand;
import qkd.key.QKDTemporalKeyInventory;
import qkd.network.QKDMesh;
import qkd.network.QKDMeshFactory;
import qkd.traffic.RequestProtectionType;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.*;

/** Deterministic renewal/replenishment/exhaustion/rollback tests for optional stock mode. */
public final class QKDTemporalKeyInventoryVerification {
    private static void check(boolean b, String msg) { if (!b) throw new AssertionError(msg); }
    private static void close(double a,double b,String msg) {
        if(Math.abs(a-b)>1e-4) throw new AssertionError(msg+": "+a+" vs "+b);
    }
    public static void main(String[] ignored) throws Exception {
        Gson gson=new Gson();
        NetworkConfig net=gson.fromJson(QKDSpanGranularityVerification.networkJson(),NetworkConfig.class);
        TrafficConfig tr=gson.fromJson(QKDSpanGranularityVerification.trafficJson(),TrafficConfig.class);
        PhysicalLayerConfig phys=gson.fromJson(QKDSpanGranularityVerification.physicalLayerJson(),PhysicalLayerConfig.class);
        SimulationConfig sim=gson.fromJson(QKDSpanGranularityVerification.simulationJson(),SimulationConfig.class);
        check("rate".equals(sim.getQkdKeyInventory().getMode()),"legacy mode must be rate");
        SimulationConfig.QKDKeyInventoryConfig cfg=new SimulationConfig.QKDKeyInventoryConfig();
        cfg.setCapacityBits(1024);
        cfg.setInitialBits(512);
        cfg.setMode("stock");
        sim.setQkdKeyInventory(cfg);
        Util util=new Util(); util.projectPath=".";
        Mesh generic=QKDMeshFactory.create(net,tr,phys,new OthersConfig(),new HashMap<String,HashMap<Double,Double>>(),util,sim);
        QKDMesh mesh=(QKDMesh)generic;
        check(mesh.getKeyInventoryConfig().isStock(),"stock mode not propagated through factory");
        Pair pair=QKDSpanGranularityVerification.findPair(mesh,"1","2");
        Route route=QKDSpanGranularityVerification.directRoute(pair);
        GPRMCSA a=new GPRMCSA("notrafficgrooming","completesharing","djk","ksp",
                "modulationbydistance","firstfit","aar","qabne","fsalfav1","cpsd");
        QKDControlPlane cp=new QKDControlPlane(mesh,GPRMCSA.RMCSA_INTEGRATED,
                a.instantiateGrooming(),a.instantiateIntegratedRSA(),a.instantiateRouting(),
                a.instantiateKRouting(),a.instantiateSpectrumAssignment(),a.instantiateModulationSelection(),
                a.instantiateCoreAndSpectrumAssignment(),a.instantiateReallocation(),a.instantiatePowerAssignment());
        QKDProvisioningService provisioner=new QKDProvisioningService(mesh);
        RequestForConnection request=new RequestForConnection();request.setPair(pair);
        request.setProtectionType(RequestProtectionType.QKD_PROTECTED);
        request.setRequiredBitRate(1e9);
        // 256 Gbit/s of average demand is well ABOVE QCh physical ~94 Mbit/s.
        request.setQkdKeyDemand(new QKDKeyDemand(256,1e-9));
        Circuit circuit=cp.createNewCircuit(request);circuit.setRoute(route);
        QKDProvisioningResult result=provisioner.provision(request);
        check(result.isSuccess(),"stock must be usable despite demand > physical SKR: "+result.getFailureReason());
        QKDActiveConnection session=result.getConnection();
        check(session.isStockManaged(),"session must carry stock semantics");
        check(session.getMinimumSecretKeyRateBps()>1e6,"report physical SKR, not 1-bit/s QCh anchor");
        check(session.getQkdSession().getProvisionedKeyRateBps()==1.0,"only minimum QCh TDM anchor in stock mode");
        QKDTemporalKeyInventory stock=new QKDTemporalKeyInventory(cfg,true);
        check(stock.hops(session.getQkdSession()).size()==2,"each trusted span must have own key store");
        check(stock.canOpen(request,session.getQkdSession()),"512 initial bits should suffice per span");
        stock.open(request,session.getQkdSession());
        for(String hop:stock.hops(session.getQkdSession())) close(stock.getBalanceBits(hop),256,"initial key debit");
        close(stock.getConsumedBits(),512,"256-bit admission key consumed per hop");
        stock.advanceRates(1e-9,Collections.<String,Double>emptyMap());
        for(String hop:stock.hops(session.getQkdSession())) close(stock.getBalanceBits(hop),0,"first renewal consumes next 256 bits");
        stock.advanceRates(2e-9,Collections.<String,Double>emptyMap());
        check(stock.getExhaustionEvents()==1,"one atomic end-to-end renewal must be rejected");
        check(stock.isExhausted(request),"active request must flag insufficient key stock");
        close(stock.getConsumedBits(),1024,"no nonexistent bits may be reported as consumed");
        close(stock.getUnservedBits(),512,"missing renewal 256 bits per hop");
        stock.close(request);
        check(stock.getActiveConsumerCount()==0,"closing a request removes future consumption");
        Map<String,Double> rates=new HashMap<String,Double>();
        for(String hop:stock.hops(session.getQkdSession())) rates.put(hop,2.0e9);
        stock.advanceRates(2.001e-6,rates);
        for(String hop:stock.hops(session.getQkdSession())) close(stock.getBalanceBits(hop),1024,"replenishment must saturate at capacity");
        check(stock.getDiscardedBits()>0,"excess generated keys must be counted");
        stock.open(request,session.getQkdSession());
        stock.rollbackOpen(request);
        check(stock.getActiveConsumerCount()==0,"failed downstream commit removes key consumer");
        for(String hop:stock.hops(session.getQkdSession())) close(stock.getBalanceBits(hop),1024,"rollback must restore keys on ALL hops");
        check(provisioner.release(session),"optical resources leaked after inventory check");
        check(provisioner.getResourceManager().getActiveQuantumChannelCount()==0,"QCh must be released");
        System.out.println("PASS QKDTemporalKeyInventoryVerification: mode/rate isolation, stock-backed high demand, two-hop keys, renewal, exhaustion, refill, cap, rollback, release");
    }
}
