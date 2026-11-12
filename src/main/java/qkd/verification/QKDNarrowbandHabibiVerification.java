package qkd.verification;

import java.util.Arrays;
import java.util.Collections;
import java.util.TreeSet;
import java.util.Vector;
import network.*;
import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import simulationControl.Util;
import simulationControl.parsers.*;
import simulationControl.parsers.NetworkConfig.*;
import request.RequestForConnection;

/** Runs the production getGnli2() for 12.5-GHz isolated QPSK, 8-QAM, and 32-QAM. */
public final class QKDNarrowbandHabibiVerification {
    private QKDNarrowbandHabibiVerification() {}
    private static double run(boolean hybrid,double format){
        final double carrier=299792458.0/1550e-9;
        NetworkConfig nc=new NetworkConfig();
        nc.setNodes(Arrays.asList(new NodeConfig("A",4,4,0),new NodeConfig("B",4,4,0)));
        nc.setCores(Arrays.asList(new CoreConfig(0,Collections.<Integer>emptyList())));
        nc.setLinks(Arrays.asList(new LinkConfig("A","B",64,12.5e9,100)));
        nc.setModulations(Arrays.asList(new ModulationConfig("QAM"+(int)format,format,10000,-100,-10)));
        PhysicalLayerConfig p=new PhysicalLayerConfig();
        p.setPhysicalLayerModel(PhysicalLayer.MODEL_HABIBI);
        p.setHabibiNliMode(hybrid ? "adaptive" : "approximate");
        p.setNliNarrowbandGHz(25.0);
        p.setHabibiEgnQuadratureOrder(20);
        p.setHabibiEgnReceiverOrder(8);
        p.setActiveASE(false);p.setActiveNLI(true);p.setActiveXT(false);
        p.setActiveQoT(true);p.setPower(0.0);p.setRateOfFEC(0);
        p.setSpanLength(100);p.setFiberLoss(0.2);
        p.setFiberNonlinearity(.0013);p.setFiberDispersion(16.7e-6);
        p.setCenterFrequency(carrier);p.setAmplificationFrequency(carrier);
        p.setConstantOfPlanck(6.62607015e-34);p.setNoiseFigureOfOpticalAmplifier(5);
        p.setSwitchInsertionLoss(0);p.setPolarizationModes(2);
        p.setTypeOfAmplifierGain(PhysicalLayer.AMP_GAIN_FIXED);
        p.setPowerSaturationOfOpticalAmplifier(20);
        Mesh mesh=new Mesh(nc,new TrafficConfig(),p,new OthersConfig(),null,new Util());
        Link link=mesh.getLinkList().get(0);
        Route route=new Route(new Vector<Node>(Arrays.asList(mesh.getNodeList().get(0),mesh.getNodeList().get(1))));
        Modulation mod=mesh.getAvaliableModulations().get(0);
        Circuit cut=new Circuit();cut.setRoute(route);
        cut.setPair(new Pair(route.getSource(),route.getDestination()));
        cut.setModulation(mod);cut.setIndexCore(0);cut.setGuardBand(0);
        cut.setSpectrumAssigned(new int[]{20,20});cut.setLaunchPowerLinear(1e-3);
        RequestForConnection req=new RequestForConnection();req.setPair(cut.getPair());
        req.setRequiredBitRate(mod.potentialBitRate(1));cut.addRequest(req);
        double bw=mod.getBandwidthFromBitRate(cut.getRequiredBitRate());
        if(Math.abs(bw-12.5e9)>1)throw new AssertionError("expected 12.5 GHz, got "+bw);
        double lower=carrier-64*12.5e9/2;
        double center=GnModelMath.centerHz(lower,12.5e9,cut.getSpectrumAssigned());
        TreeSet<Circuit> set=new TreeSet<Circuit>();set.add(cut);
        return mesh.getPhysicalLayer().getGnli2(cut,link,.001,bw,.001/bw,center,set,mod);
    }
    public static void main(String[] args) {
        for(double m : new double[]{4.0,8.0,32.0}) {
            try { run(false,m); throw new AssertionError("negative analytical " + m + "QAM accepted"); }
            catch(ArithmeticException expected) { }
            double corrected=run(true,m);
            if(!Double.isFinite(corrected)||corrected<=0)
                throw new AssertionError("GN fallback SCI must be positive for "+m+"QAM");
            System.out.printf(java.util.Locale.ROOT,
                    "[OK] production %d-QAM: approximate rejected, adaptive=%e W/Hz%n",
                    (int)m,corrected);
        }
        System.out.println("PASS QKDNarrowbandHabibiVerification");
    }
}
