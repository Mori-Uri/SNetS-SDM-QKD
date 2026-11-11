package qkd.verification;

import java.util.Arrays;
import simulationControl.parsers.SimulationConfig;
import qkd.key.QKDKeyDemand;

/** Pure Java checks of legacy unit conversion and ambiguous configuration rejection. */
public final class QKDLegacyIntervalsABVerification {
    private QKDLegacyIntervalsABVerification() {}

    private static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
    private static void near(double a, double b, String what) {
        check(Math.abs(a - b) <= Math.max(1e-15, Math.abs(b) * 1e-12),
                what + ": observed=" + a + ", expected=" + b);
    }
    public static void main(String[] args) {
        SimulationConfig.QKDKeyDemandConfig seconds = new SimulationConfig.QKDKeyDemandConfig();
        check(Arrays.equals(seconds.getUpdateIntervalSeconds(), new double[]{.064,.128,.256}), "default changed");
        seconds.setUpdateIntervalSeconds(new double[]{.000064,.000128,.000256});
        seconds.validate();
        SimulationConfig.QKDKeyDemandConfig micro = new SimulationConfig.QKDKeyDemandConfig();
        micro.setUpdateIntervalsMicroseconds(new double[]{64,128,256});
        micro.validate();
        SimulationConfig.QKDKeyDemandConfig milli = new SimulationConfig.QKDKeyDemandConfig();
        milli.setUpdateIntervalsMilliseconds(new double[]{64,128,256});
        milli.validate();
        SimulationConfig.QKDKeyDemandConfig typo = new SimulationConfig.QKDKeyDemandConfig();
        typo.setUpdateIntervalsMiliseconds(new double[]{64,128,256});
        typo.validate();
        check(Arrays.equals(seconds.getUpdateIntervalSeconds(), micro.getUpdateIntervalSeconds()),
                "seconds and micro must agree");
        check(Arrays.equals(typo.getUpdateIntervalSeconds(), milli.getUpdateIntervalSeconds()),
                "milli and miliseconds must agree");
        near(micro.getUpdateIntervalSeconds()[0], .000064, "microseconds");
        near(milli.getUpdateIntervalSeconds()[0], .064, "milliseconds");
        near(QKDKeyDemand.calculateRequiredKeyRateBps(256, micro.getUpdateIntervalSeconds()[0]), 4e6, "micro key rate");
        near(QKDKeyDemand.calculateRequiredKeyRateBps(256, milli.getUpdateIntervalSeconds()[0]), 4e3, "milli key rate");
        boolean rejected = false;
        try {
            micro.setUpdateIntervalSeconds(new double[]{.064});
            micro.validate();
        } catch (IllegalArgumentException expected) { rejected = expected.getMessage().contains("Ambiguous"); }
        check(rejected, "conflicting units must fail");
        SimulationConfig.QKDKeyDemandConfig invalid = new SimulationConfig.QKDKeyDemandConfig();
        rejected = false;
        try { invalid.setUpdateIntervalsMiliseconds(new double[]{0}); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "nonpositive legacy intervals must fail");
        System.out.println("PASS QKDLegacyIntervalsABVerification");
    }
}
