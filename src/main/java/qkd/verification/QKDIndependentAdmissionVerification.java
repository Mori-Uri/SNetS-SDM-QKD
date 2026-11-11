package qkd.verification;

import java.util.*;
import gprmcsa.GPRMCSA;
import network.*;
import qkd.control.*;
import qkd.key.*;
import qkd.network.*;
import qkd.traffic.*;
import request.RequestForConnection;
import simulationControl.*;
import simulationControl.parsers.*;

public final class QKDIndependentAdmissionVerification {
  static void ok(boolean v, String msg) {if(!v)throw new AssertionError(msg);}
  static NetworkConfig network(String allocation, String transport) {
    NetworkConfig n=new NetworkConfig();
    n.setNodes(Arrays.asList(new NetworkConfig.NodeConfig("1",20,20,0),new NetworkConfig.NodeConfig("2",20,20,0)));
    n.setLinks(Arrays.asList(new NetworkConfig.LinkConfig("1","2",40,1.25e10,40),new NetworkConfig.LinkConfig("2","1",40,1.25e10,40)));
    n.setCores(Arrays.asList(new NetworkConfig.CoreConfig(0,Arrays.asList(1)),new NetworkConfig.CoreConfig(1,Arrays.asList(0))));
    n.setModulations(Arrays.asList(new NetworkConfig.ModulationConfig("4QAM",4,5000,8.95,-19.03)));
    n.setGuardBand(0);
    NetworkConfig.QKDConfig q=new NetworkConfig.QKDConfig(true,25,0);
    q.setQchGranularity("span");q.setTdmSlots(256);q.setWavelengthPolicy("grid");
    q.setPichTransport(transport);q.setPichAllocation(allocation);q.setPichSessionsPerChannel(2);
    if ("ip".equals(transport))q.setPichSlots(0);
    n.setQkd(q);return n;
  }
  static PhysicalLayerConfig physical() {
    PhysicalLayerConfig p=new PhysicalLayerConfig();
    p.setPower(0);p.setSpanLength(80);p.setFiberLoss(.2);p.setFiberNonlinearity(.0013);
    p.setFiberDispersion(1.6e-5);p.setCenterFrequency(193.85e12);
    p.setConstantOfPlanck(6.626e-34);p.setNoiseFigureOfOpticalAmplifier(5);
    p.setPowerSaturationOfOpticalAmplifier(16);p.setNoiseFactorModelParameterA1(100);
    p.setNoiseFactorModelParameterA2(4);p.setAmplificationFrequency(193.85e12);
    p.setSwitchInsertionLoss(5);p.setReferenceBandwidthForPowerSpectralDensity(12.5e9);
    p.setPropagationConstant(1e7);p.setBendingRadius(.01);p.setCouplingCoefficient(1e-12);
    p.setCorePitch(4.5e-5);p.setPolarizationModes(2);p.setRateOfFEC(.25);
    return p;
  }
  static RequestForConnection req(Pair pair){
    RequestForConnection r = new RequestForConnection();r.setPair(pair);
    r.setProtectionType(RequestProtectionType.QKD_ONLY);
    r.setQkdKeyDemand(new QKDKeyDemand(256,0.064));return r;
  }
  static void run(String mode,String transport)throws Exception {
    TrafficConfig t = new TrafficConfig();
    t.setRequestGenerators(Arrays.asList(new TrafficConfig.RequestGeneratorConfig("1","2",1e9,1,1,0)));
    SimulationConfig s = new SimulationConfig();s.setQkdProtectedRequestProbability(0);
    Util util = new Util();util.projectPath=".";
    QKDMesh mesh=(QKDMesh)QKDMeshFactory.create(network(mode,transport),t,physical(),new OthersConfig(),new HashMap<String,HashMap<Double,Double>>(),util,s);
    GPRMCSA g=new GPRMCSA("notrafficgrooming","completesharing","djk","ksp","modulationbydistance","firstfit","aar","qabne","fsalfav1","cpsd");
    QKDControlPlane cp=new QKDControlPlane(mesh,GPRMCSA.RMCSA_INTEGRATED,g.instantiateGrooming(),g.instantiateIntegratedRSA(),g.instantiateRouting(),g.instantiateKRouting(),g.instantiateSpectrumAssignment(),g.instantiateModulationSelection(),g.instantiateCoreAndSpectrumAssignment(),g.instantiateReallocation(),g.instantiatePowerAssignment());
    Link link = mesh.getLink("1", "2");
    Pair pair = null;for(Pair p:mesh.getPairList()) if(p.getSource().getName().equals("1")&&p.getDestination().getName().equals("2"))pair=p;
    ok(pair!=null,"pair");
    RequestForConnection a=req(pair);RequestForConnection b=req(pair);
    ok(cp.handleRequisition(a), "first QKD-only request " +mode+"/"+transport);
    ok(cp.handleRequisition(b),"second QKD-only request " +mode+"/"+transport);
    ok(cp.getActiveQkdSessionCount()==2,"two sessions");
    int expectedOptical = "ip".equals(transport) ? 0 : "tdm".equals(mode) ? 2 : 4;
    ok(link.getCore(1).getAuxiliaryClassicalCircuitList().size() == expectedOptical,
       "optical PICh carrier count does not match configuration: "+mode+"/"+transport);
    ok(a.getCircuits()==null || a.getCircuits().isEmpty(), "QKD-only must not allocate DCh");
    RequestForConnection third = null;
    if ("tdm".equals(mode) && "optical".equals(transport)) {
      third=req(pair);
      ok(cp.handleRequisition(third), "third QKD TDM request");
      ok(link.getCore(1).getAuxiliaryClassicalCircuitList().size()==4,
         "TDM channel capacity reached: next session must open a new PICh");
    }
    cp.finalizeConnection(a);
    ok(cp.getActiveQkdSessionCount()==(third==null ? 1:2),"first released");
    int afterOne = "ip".equals(transport) ? 0 : third==null ? 2 : 4;
    ok(link.getCore(1).getAuxiliaryClassicalCircuitList().size() == afterOne,
       "premature PICh release or dedicated carrier leak");
    cp.finalizeConnection(b);
    if (third != null) {
      ok(link.getCore(1).getAuxiliaryClassicalCircuitList().size()==2,
         "TDM second carrier should remain for the third session");
      cp.finalizeConnection(third);
    }
    ok(cp.getActiveQkdSessionCount()==0,"all released");
    ok(link.getCore(1).getAuxiliaryClassicalCircuitList().isEmpty(), "PICh leak after QKD release");
    ok(link.getCore(0).getUsedSlots()==0, "QCh spectral leak after release");
    System.out.println("[OK] QKD-only admission/release " +mode+"/"+transport);
  }
  public static void main(String[]args)throws Exception {run("dedicated","ip");run("dedicated","optical");run("tdm","optical");}
}
