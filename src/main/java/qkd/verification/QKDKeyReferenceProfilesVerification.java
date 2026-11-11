package qkd.verification;

import qkd.key.QKDKeyDemandPolicy;
import simulationControl.parsers.SimulationConfig;

/** Checks the 1,000x literature unit difference and the explicit fixed-rate switch. */
public final class QKDKeyReferenceProfilesVerification {
    private static void check(boolean yes,String msg) { if(!yes) throw new AssertionError(msg); }
    private static void close(double actual,double expected,String msg) {
        if(Math.abs(actual-expected)>Math.abs(expected)*1e-11)
            throw new AssertionError(msg+" expected="+expected+" actual="+actual);
    }
    public static void main(String[] args) {
        SimulationConfig.QKDKeyDemandConfig cfg=new SimulationConfig.QKDKeyDemandConfig();
        check("custom".equals(cfg.getProfile()),"backward-compatible custom default");
        check(cfg.isPreferGeneratorFixedRate(),"old fixed generator override preserved by default");
        cfg.setProfile("moghaddam2021");
        for(double s:cfg.getUpdateIntervalSeconds())
            check(s>=.064 && s<=.256,"Moghaddam periods are MILLISECONDS in seconds");
        close(256.0/cfg.getUpdateIntervalSeconds()[0],4000,"4 kbit/s Moghaddam");
        cfg.setProfile("dibaj2024");
        close(256.0/cfg.getUpdateIntervalSeconds()[0],4.0e6,"4 Mbit/s Dibaj");
        cfg.setPreferGeneratorFixedRate(false);
        check(!cfg.isPreferGeneratorFixedRate(),"profile must be selectable despite traffic keyRateMbps");
        QKDKeyDemandPolicy policy=new QKDKeyDemandPolicy(cfg.getKeySizeBits(),
                cfg.getUpdateIntervalSeconds(),130);
        policy.setPreferGeneratorFixedRate(cfg.isPreferGeneratorFixedRate());
        check(!policy.isPreferGeneratorFixedRate(),"policy option propagated");
        for(double s:policy.getUpdateIntervalSeconds()) check(s<=.000256,"Dibaj periods");
        cfg.setProfile("unknown");
        boolean rejected=false;
        try { cfg.validate(); } catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"unsupported literature profile rejected");
        System.out.println("PASS QKDKeyReferenceProfilesVerification");
    }
}
