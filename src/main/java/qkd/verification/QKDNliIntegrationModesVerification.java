package qkd.verification;

import java.util.Arrays;
import java.util.Collections;
import java.util.TreeSet;
import java.util.Vector;
import network.Circuit;
import network.GnModelMath;
import network.Link;
import network.Mesh;
import network.NliIntegrationMode;
import network.Node;
import network.Pair;
import network.PhysicalLayer;
import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.TrafficConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;
import simulationControl.parsers.NetworkConfig.ModulationConfig;
import simulationControl.parsers.NetworkConfig.NodeConfig;

/** Verifies the three independent NLI modes, including the production Habibi path. */
public final class QKDNliIntegrationModesVerification {
    private QKDNliIntegrationModesVerification() { }
    private static void check(boolean ok,String text) { if(!ok) throw new AssertionError(text); }
    private static void mustReject(Runnable work,String description) {
        try { work.run(); throw new AssertionError(description+" unexpectedly accepted"); }
        catch(ArithmeticException expected) { }
    }
    private static void close(double actual,double expected,String message) {
        if(!(Math.abs(actual-expected)<=1e-10*Math.max(1e-30,Math.abs(expected))))
            throw new AssertionError(message+": "+actual+" != "+expected);
    }
    private static double production(int model,String mode,double widthGHz) {
        double carrier=299792458.0/1550e-9;
        NetworkConfig nc=new NetworkConfig();
        nc.setNodes(Arrays.asList(new NodeConfig("A",4,4,0),new NodeConfig("B",4,4,0)));
        nc.setCores(Arrays.asList(new CoreConfig(0,Collections.<Integer>emptyList())));
        nc.setLinks(Arrays.asList(new LinkConfig("A","B",64,12.5e9,100)));
        nc.setModulations(Arrays.asList(new ModulationConfig("QPSK",4.0,10000,-100,-10)));
        PhysicalLayerConfig p=new PhysicalLayerConfig();
        p.setPhysicalLayerModel(model);
        p.setHabibiNliMode(mode);
        p.setJohannissonNliMode(mode);
        p.setNliNarrowbandGHz(25.0);
        p.setHabibiEgnQuadratureOrder(16);
        p.setHabibiEgnReceiverOrder(6);
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
        Route route=new Route(new Vector<Node>(Arrays.asList(
                mesh.getNodeList().get(0),mesh.getNodeList().get(1))));
        Modulation mod=mesh.getAvaliableModulations().get(0);
        Circuit cut=new Circuit();cut.setRoute(route);
        cut.setPair(new Pair(route.getSource(),route.getDestination()));
        cut.setModulation(mod);cut.setIndexCore(0);cut.setGuardBand(0);
        int slots=(int)Math.ceil(widthGHz/12.5);
        cut.setSpectrumAssigned(new int[]{20,20+slots-1});cut.setLaunchPowerLinear(1e-3);
        RequestForConnection req=new RequestForConnection();req.setPair(cut.getPair());
        req.setRequiredBitRate(mod.potentialBitRate(1)*(widthGHz/12.5));cut.addRequest(req);
        double bw=mod.getBandwidthFromBitRate(cut.getRequiredBitRate());
        check(Math.abs(bw/1e9-widthGHz)<1e-6,"actual bandwidth must match modulation bits/symbol");
        double lower=carrier-64*12.5e9/2;
        double center=GnModelMath.centerHz(lower,12.5e9,cut.getSpectrumAssigned());
        TreeSet<Circuit> set=new TreeSet<Circuit>();set.add(cut);
        if (model == PhysicalLayer.MODEL_JOHANNISSON)
            return mesh.getPhysicalLayer().getGnli(cut,link,.001,bw,.001/(2*bw),center,set);
        return mesh.getPhysicalLayer().getGnli2(cut,link,.001,bw,.001/bw,center,set,mod);
    }
    private static double habibi(String mode,double widthGHz) {
        return production(PhysicalLayer.MODEL_HABIBI,mode,widthGHz);
    }
    private static double johannisson(String mode,double widthGHz) {
        return production(PhysicalLayer.MODEL_JOHANNISSON,mode,widthGHz);
    }
    public static void main(String[] args) {
        double alpha=0.2*Math.log(10.0)/10000.0;
        double beta=21.3e-27;
        double narrow=12.5e9;
        double rho=Math.PI*Math.PI*beta*narrow*narrow/alpha;
        mustReject(() -> GnModelMath.johannissonSciKernel(rho,narrow,
                "approximate",25.0,false), "Johannisson invalid log");
        double integral=GnModelMath.johannissonSciKernel(rho,narrow,
                "integral",25.0,false);
        double adaptive=GnModelMath.johannissonSciKernel(rho,narrow,
                "adaptive",25.0,false);
        check(integral>0,"narrow Johannisson adaptive must return positive integral");
        close(adaptive,integral,"Johannisson adaptive for narrow SCI");
        double wide=50e9;
        double rhoWide=Math.PI*Math.PI*beta*wide*wide/alpha;
        close(GnModelMath.johannissonSciKernel(rhoWide,wide,
                "adaptive",25.0,false),
              GnModelMath.johannissonSciKernel(rhoWide,wide,"approximate",25.0,false),
              "Johannisson wide log must remain approximate");
        double xciIntegral=GnModelMath.johannissonXciKernel(75e9,12.5e9,32e9,
                4*Math.PI*Math.PI*beta/alpha,"integral",25.0,false);
        double xciAdaptive=GnModelMath.johannissonXciKernel(75e9,12.5e9,32e9,
                4*Math.PI*Math.PI*beta/alpha,"adaptive",25.0,false);
        close(xciAdaptive,xciIntegral,"Johannisson narrow source/victim XCI");
        mustReject(() -> johannisson("approximate",12.5), "production Johannisson invalid log");
        double johIntegral=johannisson("integral",12.5);
        double johAuto=johannisson("adaptive",12.5);
        check(johIntegral>0.0,"production Johannisson integral must be positive");
        close(johAuto,johIntegral,"production Johannisson adaptive narrow");
        close(johannisson("adaptive",50.0),johannisson("approximate",50.0),
                "production Johannisson wide uses logarithmic approximation");
        double gnNarrow=habibi("integral",12.5);
        double autoNarrow=habibi("adaptive",12.5);
        mustReject(() -> habibi("approximate",12.5), "production Habibi invalid approximation");
        check(gnNarrow>0.0,"Habibi integral must recover physical NLI");
        close(autoNarrow,gnNarrow,"Habibi adaptive narrow");
        double rawWide=habibi("approximate",50.0);
        double autoWide=habibi("adaptive",50.0);
        double integralWide=habibi("integral",50.0);
        close(autoWide,rawWide,"Habibi wide positive must retain analytical approximation");
        check(Double.isFinite(integralWide) && integralWide>0.0,
                "Habibi integral mode must run for wide channels");
        PhysicalLayerConfig c=new PhysicalLayerConfig();
        check("adaptive".equals(c.getJohannissonNliMode()),"default Johannisson mode must be adaptive");
        check("adaptive".equals(c.getHabibiNliMode()),"default Habibi mode must be adaptive");
        for(String mode:new String[]{"approximate","integral","adaptive"}) {
            c.setJohannissonNliMode(mode);c.setHabibiNliMode(mode);
            check(mode.equals(c.getJohannissonNliMode()) && mode.equals(c.getHabibiNliMode()),
                    "parser mode roundtrip");
        }
        try { c.setHabibiNliMode("bad"); throw new AssertionError("invalid mode accepted"); }
        catch(IllegalArgumentException expected) { }
        check(!NliIntegrationMode.useHabibiIntegral("approximate",true,narrow,25,-1),
                "explicit mode must override legacy flag");
        check(NliIntegrationMode.useHabibiIntegral("adaptive",false,50e9,25,-1),
                "Habibi adaptive must react to negative NLI even for a wide circuit");
        check(NliIntegrationMode.useJohannissonIntegral("adaptive",false,50e9,25,true),
                "Johannisson adaptive must recover nonphysical logarithmic SCI");
        check(!NliIntegrationMode.useJohannissonIntegral("approximate",false,12.5e9,25,true),
                "strict logarithmic mode must never run the integral");
        System.out.printf(java.util.Locale.ROOT,
                "PASS NLI modes Johannisson/Habibi: Sci narrow [log=%.6g integral=%.6g], Habibi 12.5GHz [approx=%.6g integral=%.6g] wide [approx=%.6g integral=%.6g]%n",
                Double.NaN,integral,Double.NaN,gnNarrow,rawWide,integralWide);
    }
}
