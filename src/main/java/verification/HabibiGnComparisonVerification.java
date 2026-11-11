package verification;

import network.Poggiolini2017GnIntegral;
import network.PhysicalLayer;
import java.util.*;
import java.io.*;

/** A/B of Habibi original vs GN quadrature narrow/negative fallback.
 * This is NOT a Carena EGN comparison or a full-EGN calculation. */
public final class HabibiGnComparisonVerification {
    private HabibiGnComparisonVerification() { }
    private static void check(boolean p,String message){if(!p)throw new AssertionError(message);}
    private static double relative(double a,double b){return Math.abs(a-b)/Math.max(1e-29,Math.max(Math.abs(a),Math.abs(b)));}
    public static void main(String[] args) throws Exception {
        File target=new File(args.length>0?args[0]:"habibi_gn_ab.csv");
        File parent=target.getAbsoluteFile().getParentFile();if(parent!=null)parent.mkdirs();
        Poggiolini2017GnIntegral.Fiber fiber=new Poggiolini2017GnIntegral.Fiber(.2,1.3,-21.3,100,1);
        int rows=0;
        try(PrintWriter out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(target),"UTF-8"))){
            out.println("bandwidth_GHz,channels,habibi_raw,habibi_clamped,habibi_gn_fallback,gn_full_comb,gn_sci,gn_xci,gn_mci,relative_to_gn_full");
            for(double bw:new double[]{12.5,25,32,37.5,50}) for(int n=1;n<=3;n++) {
                double b=bw*1e9;
                Poggiolini2017GnIntegral.Channel cut=Poggiolini2017GnIntegral.Channel.qpsk(193.1e12,b,.001);
                List<Poggiolini2017GnIntegral.Channel> other=new ArrayList<Poggiolini2017GnIntegral.Channel>();
                if(n>1)other.add(Poggiolini2017GnIntegral.Channel.qpsk(cut.centerHz+1.05*b,b,.001));
                if(n>2)other.add(Poggiolini2017GnIntegral.Channel.qpsk(cut.centerHz-1.05*b,b,.001));
                double raw=PhysicalLayer.legacySci(fiber,cut,1.0);
                for(Poggiolini2017GnIntegral.Channel x:other)raw+=PhysicalLayer.legacyXci(fiber,cut,x,1.0);
                PhysicalLayer.HabibiGnDecision enabled=PhysicalLayer.evaluate(fiber,cut,other,raw,25,true,20,8);
                PhysicalLayer.HabibiGnDecision disabled=PhysicalLayer.evaluate(fiber,cut,other,raw,25,false,20,8);
                check(relative(disabled.corrected,Math.max(0,raw))<1e-12,"legacy changed");
                List<Poggiolini2017GnIntegral.Channel> all=new ArrayList<Poggiolini2017GnIntegral.Channel>();all.add(cut);all.addAll(other);
                Poggiolini2017GnIntegral.GnResult gn=Poggiolini2017GnIntegral.evaluateGnIntegral(fiber,all,0,20,8);
                check(Double.isFinite(gn.total())&&gn.total()>0,"GN result invalid");
                check(Double.isFinite(enabled.corrected)&&enabled.corrected>=0,"hybrid invalid");
                if(n==1 && bw<=25)check(relative(enabled.corrected,gn.sci)<1e-12,"GN SCI fallback mismatch");
                out.printf(Locale.ROOT,"%.3f,%d,%.12e,%.12e,%.12e,%.12e,%.12e,%.12e,%.12e,%.8g%n",
                        bw,n,raw,disabled.corrected,enabled.corrected,gn.total(),gn.sci,gn.xci,gn.mci,relative(enabled.corrected,gn.total()));
                rows++;
            }
        }
        System.out.println("PASS Habibi vs GN integral A/B: "+rows+" rows; "+target);
    }
}
