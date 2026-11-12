package qkd.verification;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import network.GnModelMath;
import network.OpticalNoiseCascade;
import network.PhysicalLayer;
import network.Poggiolini2017GnIntegral;
import network.Poggiolini2017GnIntegral.Channel;
import qkd.multiband.NonlinearInterferenceModel;
import network.Poggiolini2017GnIntegral.Fiber;

/**
 * Stage-2 acceptance tests. Invalid analytical NLI must never become zero;
 * Habibi 8-QAM and 32-QAM use the modulation-independent numerical GN
 * fallback when the approximate EGN model is not trustworthy.
 * No claim of complete EGN numerical integration is implied.
 */
public final class QKDStage2NliSafetyVerification {
    private QKDStage2NliSafetyVerification() { }

    private static void check(boolean result,String reason) {
        if(!result) throw new AssertionError(reason);
    }

    private static void close(double actual,double expected,String label) {
        double tolerance=Math.max(1e-28,Math.abs(expected)*1e-9);
        check(Double.isFinite(actual) && Math.abs(actual-expected)<=tolerance,
                label+": " +actual+" vs "+expected);
    }

    private static void rejected(Runnable operation,String label) {
        boolean rejected=false;
        try { operation.run(); }
        catch(ArithmeticException correct) { rejected=true; }
        check(rejected,label+" failed open");
    }

    private static void checkGuards() {
        GnModelMath.resetDiagnostics();
        rejected(() -> GnModelMath.nonnegative(-1e-19,"stage2"),"negative NLI");
        rejected(() -> GnModelMath.nonnegative(Double.NaN,"stage2"),"NaN NLI");
        rejected(() -> GnModelMath.nonnegative(Double.POSITIVE_INFINITY,"stage2"),"infinite NLI");
        close(GnModelMath.nonnegative(0,"stage2"),0,"zero source");
        check(GnModelMath.negativeRejectionCount()==1 && GnModelMath.nonFiniteCount()==2,
                "diagnostic counters lost invalid events");
        NonlinearInterferenceModel qkdNli=NonlinearInterferenceModel.standard();
        rejected(() -> qkdNli.psdWPerHz(new double[]{1e-13,Double.NaN},
                193e12,12.5e9,193.1e12,80,0.046,0.046),
                "QKD GN with NaN optical input");
        rejected(() -> qkdNli.psdWPerHz(new double[]{1e-13,-1e-13},
                193e12,12.5e9,193.1e12,80,0.046,0.046),
                "QKD GN with negative optical input");
        try { qkdNli.psdWPerHz(new double[]{1e-13},
                193e12,12.5e9,193.1e12,-1,0.046,0.046);
            throw new AssertionError("negative QKD section length accepted");
        } catch(IllegalArgumentException expected) { }
        OpticalNoiseCascade cascade=new OpticalNoiseCascade(1e-10);
        rejected(() -> cascade.addNli(-1e-18),"cascade negative NLI");
        check(cascade.getNliPsd()==0,"cascade mutated after invalid NLI");
        rejected(() -> GnModelMath.johannissonSciKernel(.1,12.5e9,
                "approximate",25,false),"Johannisson logarithmic SCI");
        check(GnModelMath.johannissonSciKernel(.1,12.5e9,
                "adaptive",25,false)>0,"adaptive SCI integral missing");
        double xi=4*Math.PI*Math.PI*21.3e-27/(0.2*Math.log(10)/10000.0);
        rejected(() -> GnModelMath.johannissonXciKernel(5e9,12.5e9,
                25e9,xi,"approximate",25,false),"Johannisson overlapping XCI");
        check(GnModelMath.johannissonXciKernel(5e9,12.5e9,
                25e9,xi,"adaptive",25,false)>0,"adaptive XCI integral missing");
    }

    private static void checkHabibiFor(double positivePhi,String name) {
        final double freq=193.1e12, b=12.5e9;
        Fiber fib=new Fiber(.2,1.3,-21.3,80,1);
        Channel cut=PhysicalLayer.supportedChannel(freq,b,.001,positivePhi);
        Channel neighbor=PhysicalLayer.supportedChannel(freq+50e9,b,.001,positivePhi);
        check(cut!=null && neighbor!=null,name+" channel metadata absent");
        close(-cut.phi,positivePhi,name+" positive Phi metadata");
        double rawSci=PhysicalLayer.legacySci(fib,cut,positivePhi);
        check(rawSci<0,name+" negative narrow SCI not reproduced");
        rejected(() -> PhysicalLayer.evaluate(fib,cut,Collections.<Channel>emptyList(),
                rawSci,25,false,20,8),name+" invalid analytical SCI");
        PhysicalLayer.HabibiGnDecision one=PhysicalLayer.evaluate(fib,cut,
                Collections.<Channel>emptyList(),rawSci,25,true,20,8);
        double refSci=Poggiolini2017GnIntegral.evaluateGnIntegral(fib,
                Collections.singletonList(cut),0,20,8).sci;
        close(one.corrected,refSci,name+" isolated GN fallback");
        check(one.corrected>0 && one.applied,name+" fallback not applied");

        List<Channel> pair=Arrays.asList(cut,neighbor);
        double raw=rawSci+PhysicalLayer.legacyXci(fib,cut,neighbor,positivePhi);
        PhysicalLayer.HabibiGnDecision two=PhysicalLayer.evaluate(fib,cut,
                Collections.singletonList(neighbor),raw,25,true,20,8);
        Poggiolini2017GnIntegral.GnResult reference=
                Poggiolini2017GnIntegral.evaluateGnIntegral(fib,pair,0,20,8);
        check(two.supportedPairs==1 && two.legacyPairs==0,
                name+" interferer silently dropped");
        close(two.corrected,refSci+reference.xci,name+" SCI+XCI fallback");
        check(two.corrected>0 && Double.isFinite(two.corrected),name+" invalid GN fallback");
        System.out.printf(java.util.Locale.ROOT,
                "[OK] %s 12.5GHz: raw=%g GN_SCI=%g GN_XCI=%g%n",
                name,rawSci,refSci,reference.xci);
    }

    public static void main(String[] args) {
        checkGuards();
        checkHabibiFor(2.0/3.0,"8-QAM");
        checkHabibiFor(69.0/100.0,"32-QAM");
        check(PhysicalLayer.supportedChannel(193.1e12,12.5e9,.001,.725)==null,
                "unrecognized modulation correction masqueraded as reference");
        System.out.println("PASS QKDStage2NliSafetyVerification");
    }
}
