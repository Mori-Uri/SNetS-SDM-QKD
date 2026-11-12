package qkd.verification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import network.Amplifier;
import network.LongitudinalClassicalQoT;
import qkd.multiband.AmplifierOperatingPoint;
import qkd.multiband.ClassicalPowerEvolution;
import qkd.multiband.ClassicalPowerEvolution.LoadSource;
import qkd.multiband.MultiBandEdFA;

/** Stage 3 physical limiting cases and cross-band/single-band invariants.
 * Deterministic, no sample calibration or external research claims. Java 8.
 */
public final class QKDStage3AmplifierChainVerification {
    private static final double H = 6.62607015e-34;
    private static final double C = 193.4e12;
    private static final double L = 187.2e12;
    private static final double A = 0.2*Math.log(10.0)/10.0;
    private static final double NF = Math.pow(10.0, 5.0/10.0);
    private QKDStage3AmplifierChainVerification() { }

    private static void ok(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void close(double actual, double expected, double rel, String message) {
        if (!Double.isFinite(actual) || Math.abs(actual-expected) > rel*Math.max(1e-30, Math.abs(expected)))
            throw new AssertionError(message + " got=" + actual + " expected=" + expected);
    }
    private static void rejects(Runnable call, String msg) {
        try { call.run(); } catch (IllegalArgumentException | IllegalStateException ex) { return; }
        throw new AssertionError(msg);
    }

    private static List<MultiBandEdFA.Band> bands() {
        return Arrays.asList(new MultiBandEdFA.Band("C",191e12,196e12,18,5),
                new MultiBandEdFA.Band("L",186e12,191e12,14,6));
    }

    private static void multiband() {
        MultiBandEdFA shared = new MultiBandEdFA(bands(),18,true);
        MultiBandEdFA separate = new MultiBandEdFA(bands(),18,true,
                MultiBandEdFA.SaturationScope.PER_BAND);
        MultiBandEdFA.State sharedC = shared.evaluate(Collections.singletonList(
                new MultiBandEdFA.Input(C,0.002)));
        MultiBandEdFA.State sharedCL = shared.evaluate(Arrays.asList(
                new MultiBandEdFA.Input(C,0.002), new MultiBandEdFA.Input(L,0.004)));
        MultiBandEdFA.State separateC = separate.evaluate(Collections.singletonList(
                new MultiBandEdFA.Input(C,0.002)));
        MultiBandEdFA.State separateCL = separate.evaluate(Arrays.asList(
                new MultiBandEdFA.Input(C,0.002), new MultiBandEdFA.Input(L,0.004)));
        ok(sharedCL.gain(C) < sharedC.gain(C), "shared C gain must compress when L load is added");
        close(separateCL.gain(C),separateC.gain(C),1e-13,
                "independent C gain must ignore L input");
        close(separateCL.getBandInputW(L),0.004,1e-13,"band L load");
        ok(separateCL.getCompression(L)<1.0 && separateCL.getCompression(C)<1.0,
                "both bands individually compressed");
        ok(separateCL.asePsd(C,H)>0 && separateCL.asePsd(L,H)>0,"positive frequency ASE");
        rejects(() -> separateCL.getCompression(),"per-band compression cannot be scalar");
        ok(MultiBandEdFA.SaturationScope.parse("per-band") == MultiBandEdFA.SaturationScope.PER_BAND,
                "parse per band");
        rejects(() -> MultiBandEdFA.SaturationScope.parse("fake"),"invalid scope accepted");
        rejects(() -> shared.evaluate(Arrays.asList((MultiBandEdFA.Input)null)),"null input accepted");
        rejects(() -> shared.evaluate(Arrays.asList(new MultiBandEdFA.Input(C, Double.MAX_VALUE),
                new MultiBandEdFA.Input(C, Double.MAX_VALUE))),"overflow accepted");

        // All optical channels contribute their PRE-amplifier input, with
        // source-specific booster and preceding wavelength-dependent gains.
        final ClassicalPowerEvolution template = new ClassicalPowerEvolution(40,16,
                ClassicalPowerEvolution.GainModel.SATURATED,18,0,5,30)
                .withMultiBandEdFA(shared).withBooster(3.0,0.08,true);
        final List<LoadSource> sources = Arrays.asList(
                new LoadSource(0.002,0,100,C), new LoadSource(0.004,0,100,L),
                new LoadSource(0.001,45,90,C));
        final ClassicalPowerEvolution snapshot = template.withDynamicLoad(sources,100,A);
        final double booster=snapshot.getBoosterGainLinear();
        close(snapshot.inputPowerWAt(1),0.006*booster*Math.exp(-A*40),1e-12,
                "booster must affect both spectral line inputs");
        final double input2 = Math.exp(-A*80)*booster*(0.002*snapshot.gainLinearAt(1,A,C)
                +0.004*snapshot.gainLinearAt(1,A,L)) + 0.001*Math.exp(-A*35);
        close(snapshot.inputPowerWAt(2),input2,1e-12,
                "dynamic load with mid-link PICh must account for each prior carrier gain once");
        close(snapshot.aggregatePowerAt(75,A),
                0.002*snapshot.powerRatio(0,75,A,C)
                +0.004*snapshot.powerRatio(0,75,A,L)
                +0.001*snapshot.powerRatio(45,75,A,C),1e-12,
                "aggregate longitudinal power");
        rejects(() -> snapshot.gainLinearAt(1,A),"scalar multiband gain must be rejected");
        rejects(() -> snapshot.powerRatio(0,75,A),"scalar multiband power ratio must be rejected");
        AmplifierOperatingPoint point=snapshot.operatingPointAt(2,A,C,H);
        close(point.getGainLinear(),snapshot.spectralStateAt(2).gain(C),1e-13,
                "shared C gain in optical QoT and QKD");
        close(point.getAsePsdWHz(),snapshot.spectralStateAt(2).asePsd(C,H),1e-13,
                "shared C ASE in optical QoT and QKD");
        ok(!template.hasDynamicLoad(),"snapshot must not mutate EDFA template");
    }

    private static void boundaryAndAse() {
        final ClassicalPowerEvolution noAmp = new ClassicalPowerEvolution(40,0,
                ClassicalPowerEvolution.GainModel.IDEAL,20,0,5,0)
                .withBooster(3,0.1,false);
        final ClassicalPowerEvolution single = noAmp.withDynamicLoad(
                Collections.singletonList(new LoadSource(0.001,0,30,C)),30,0);
        final double expectedBooster = Amplifier.asePsd(C,NF,3,H);
        close(single.aseAtQuantumReceiverW(0,30,0,0,C,1e9,H),
                expectedBooster*1e9,1e-12,"booster ASE reaches quantum filter only with calibrated leakage");
        close(single.aseAtQuantumReceiverW(10,30,0,0,C,1e9,H),0,1e-12,
                "booster must not be invented at an intermediate QKD trusted node");
        ClassicalPowerEvolution disabled = new ClassicalPowerEvolution(40,0,
                ClassicalPowerEvolution.GainModel.IDEAL,20,0,5,3000)
                .withBooster(3,0.1,false).withDynamicLoad(
                        Collections.singletonList(new LoadSource(0.001,0,30,C)),30,0);
        close(disabled.aseAtQuantumReceiverW(0,30,0,0,C,1e9,H),0,1e-12,
                "zero leakage must stay zero");
        LongitudinalClassicalQoT.Result r=LongitudinalClassicalQoT.evaluate(single,
                C,H,0.001,3,5,0.1,false,NF,30,0);
        close(r.signalTransfer,15,1e-12,"booster * preamp transfer without fibre loss");
        close(r.aseAtReceiverWHz,expectedBooster*5 + Amplifier.asePsd(C,NF,5,H),1e-12,
                "booster and preamp ASE must cascade without double counting");

        // Two line EDFAs, a fractional final span and a receiving preamplifier.
        ClassicalPowerEvolution model=new ClassicalPowerEvolution(40,9,
                ClassicalPowerEvolution.GainModel.SATURATED,20,0,5,3000)
                .withBooster(2,0.1,true);
        ClassicalPowerEvolution loaded=model.withDynamicLoad(
                Collections.singletonList(new LoadSource(.002,0,95,C)),95,A);
        final double pre0=3.0;
        r=LongitudinalClassicalQoT.evaluate(loaded,C,H,.002,2,pre0,.1,true,NF,95,A);
        close(r.signalTransfer, loaded.powerRatio(0,95,A,C)*r.preGain,1e-12,
                "booster, two line EDFAs, partial final span and preamp transfer");
        ok(r.spanLengthKm.length==3,"partial span count");
        close(r.spanLengthKm[2],15,1e-12,"last span must be 15 km");
        final double gain1=loaded.gainLinearAt(1,A,C);
        final double gain2=loaded.gainLinearAt(2,A,C);
        close(r.spanOutputTransfer[0],Math.exp(-A*55)*gain1*gain2*r.preGain,1e-12,
                "forward ASE/NLI emitted before line 1 must receive BOTH line gains");
        close(r.spanOutputTransfer[1],Math.exp(-A*15)*gain2*r.preGain,1e-12,
                "forward ASE/NLI emitted before line 2 must receive line 2 gain");
        close(r.spanOutputTransfer[2],r.preGain,1e-12,
                "last span must receive only preamp gain");
        final double boosterPsd=Amplifier.asePsd(C,NF,loaded.getBoosterGainLinear(),H);
        final double amp1Psd=loaded.operatingPointAt(1,A,C,H).getAsePsdWHz();
        final double amp2Psd=loaded.operatingPointAt(2,A,C,H).getAsePsdWHz();
        final double preInput=loaded.aggregatePowerAt(Math.nextAfter(95.0,Double.NEGATIVE_INFINITY),A);
        final double preAse=Amplifier.asePsd(C,NF,r.preGain,H,preInput,0,0,true);
        final double expectedAse=((boosterPsd*Math.exp(-A*40)*gain1 +amp1Psd)
                *Math.exp(-A*40)*gain2 +amp2Psd)*Math.exp(-A*15)*r.preGain +preAse;
        close(r.aseAtReceiverWHz, expectedAse,1e-12,
                "ASE chain must propagate both preceding ASE stages then preamp");
    }

    private static void manySpans() {
        List<LoadSource> sources=new ArrayList<LoadSource>();
        for(int k=0;k<48;k++)
            sources.add(new LoadSource(1e-5*(1+k%5),k%4==0 ? 0 : 10.0+(k%6)*10.0,
                    1400.0-(k%5)*10.0,C));
        ClassicalPowerEvolution template=new ClassicalPowerEvolution(20,4,
                ClassicalPowerEvolution.GainModel.SATURATED,20,0,5,200);
        ClassicalPowerEvolution snapshot=template.withBooster(2,0.2,true)
                .withDynamicLoad(sources,1400,A);
        ok(snapshot.inputPowerWAt(69) >= 0.0,"finite dynamic load for many spans");
        ok(snapshot.gainLinearAt(69,A)>0.0,"finite gain for many spans");
        LongitudinalClassicalQoT.Result r=LongitudinalClassicalQoT.evaluate(snapshot,
                C,H,0.005,2,1,0.2,true,NF,1400,A);
        ok(r.aseAtReceiverWHz>=0.0 && Double.isFinite(r.aseAtReceiverWHz),"finite ASE for long route");
    }

    public static void main(String[] args) {
        multiband();
        boundaryAndAse();
        manySpans();
        System.out.println("PASS QKDStage3AmplifierChainVerification");
    }
}
