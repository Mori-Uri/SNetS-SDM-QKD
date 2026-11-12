package qkd.verification;

import java.util.HashMap;
import com.google.gson.Gson;
import gprmcsa.modulation.Modulation;
import network.Mesh;
import simulationControl.Util;
import simulationControl.parsers.*;

/** Regression: active QoT must not invoke thousands of artificial reach scans at startup. */
public final class ModulationReachStartupVerification {
  private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  public static void main(String[] args){
    Gson gson=new Gson();
    NetworkConfig net=gson.fromJson(QKDSpanGranularityVerification.networkJson(),NetworkConfig.class);
    TrafficConfig tr=gson.fromJson(QKDSpanGranularityVerification.trafficJson(),TrafficConfig.class);
    PhysicalLayerConfig phy=gson.fromJson(QKDSpanGranularityVerification.physicalLayerJson(),PhysicalLayerConfig.class);
    phy.setActiveQoT(true);
    long start=System.nanoTime();
    Mesh mesh=new Mesh(net,tr,phy,new OthersConfig(),null,new Util());
    long elapsed=(System.nanoTime()-start)/1000000L;
    Modulation mod=mesh.getAvaliableModulations().get(0);
    check(mesh.getModTrDistance().get(mod.getName()).isEmpty(),"no eager 4000-point reach scans");
    check(elapsed<15000L,"simulation initialization unexpectedly expensive: "+elapsed+" ms");
    double result=mesh.getModulationDistanceByBitRate(mod,1e9);
    check(result==mod.getMaxRange(),"fallback must use configured maximum reach as prefilter");
    HashMap<String,HashMap<Double,Double>> reference=new HashMap<String,HashMap<Double,Double>>();
    HashMap<Double,Double> rates=new HashMap<Double,Double>();rates.put(1e9,50.0);
    reference.put(mod.getName(),rates);
    Mesh withReference=new Mesh(net,tr,phy,new OthersConfig(),reference,new Util());
    check(withReference.getModulationDistanceByBitRate(withReference.getAvaliableModulations().get(0),1e9)==50.0,
            "explicit precomputed reach must remain backward-compatible");
    System.out.println("PASS ModulationReachStartupVerification: no eager 4,000-scan reach; startup="+elapsed+" ms; configured/explicit prefilter intact");
  }
}
