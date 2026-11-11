package qkd.verification;

import java.util.Arrays;
import java.util.ArrayList;
import gprmcsa.modulation.Modulation;
import network.Mesh;
import network.PhysicalLayer;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.TrafficConfig;

/** Regression tests for the optional pre-FEC BER classical QoT mode. */
public final class ClassicalBerQotVerification {
    private ClassicalBerQotVerification() { }
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    private static PhysicalLayer layer(int qotMode, Double berLimit) {
        return layer(qotMode, berLimit, "jurandir");
    }
    private static PhysicalLayer layer(int qotMode, Double berLimit, String berModel) {
        return layer(qotMode, berLimit, berModel, 2.0);
    }
    private static PhysicalLayer layer(int qotMode, Double berLimit, String berModel, double pol) {
        NetworkConfig n = new NetworkConfig();
        n.setNodes(Arrays.asList(new NetworkConfig.NodeConfig("A", 2, 2, 0),
                new NetworkConfig.NodeConfig("B", 2, 2, 0)));
        n.setCores(Arrays.asList(new NetworkConfig.CoreConfig(0, new ArrayList<Integer>())));
        n.setLinks(Arrays.asList(new NetworkConfig.LinkConfig("A", "B", 128, 12.5e9, 80.0)));
        n.setModulations(Arrays.asList(new NetworkConfig.ModulationConfig("QPSK", 4.0, 200.0, 10.0, -20.0)));
        PhysicalLayerConfig p = new PhysicalLayerConfig();
        p.setPhysicalLayerModel(PhysicalLayer.MODEL_HABIBI);
        p.setTypeOfTestQoT(qotMode);
        p.setBerThreshold(berLimit);
        p.setBerModel(berModel);
        p.setPower(0.0);
        p.setRateOfFEC(0.25);
        p.setSpanLength(80.0);
        p.setFiberLoss(0.2);
        p.setFiberNonlinearity(0.0013);
        p.setFiberDispersion(16.7e-6);
        p.setCenterFrequency(193.4e12);
        p.setConstantOfPlanck(6.62607015e-34);
        p.setNoiseFigureOfOpticalAmplifier(5.0);
        p.setPowerSaturationOfOpticalAmplifier(18.0);
        p.setTypeOfAmplifierGain(PhysicalLayer.AMP_GAIN_FIXED);
        p.setAmplificationFrequency(193.4e12);
        p.setSwitchInsertionLoss(0.0);
        p.setActiveQoT(false);
        p.setActiveNLI(false);
        p.setActiveXT(false);
        p.setActiveASE(false);
        p.setPolarizationModes(pol);
        return new Mesh(n, new TrafficConfig(), p, new OthersConfig(), null, new Util()).getPhysicalLayer();
    }
    public static void main(String[] args) {
        // QPSK in Jurandir: optical OSNR=10, SNR_bit=OSNR/log2(4)=5.
        double qpsk = PhysicalLayer.getBER(10.0, 4.0, 2.0);
        check(Math.abs(qpsk - 7.827011294e-4) < 2e-9, "QPSK thesis BER from optical OSNR");
        check(PhysicalLayer.getBER(0.0, 4.0, 2.0) == 0.5, "QPSK zero OSNR");
        check(PhysicalLayer.getBER(1000.0, 4.0, 2.0) > 0.0, "high optical OSNR must not cancel 1-erf");
        check(Double.isNaN(PhysicalLayer.getBER(-1.0, 4.0, 2.0)), "negative optical OSNR rejected");
        check(Double.isNaN(PhysicalLayer.getBER(Double.NaN, 4.0, 2.0)), "NaN rejected");
        check(Double.isNaN(PhysicalLayer.getBER(1.0, 3.0, 2.0)), "unsupported order rejected");
        for (double m : new double[] {4.0, 8.0, 16.0, 32.0, 64.0}) {
            double low = PhysicalLayer.getBER(10.0, m, 2.0), high = PhysicalLayer.getBER(100.0, m, 2.0);
            check(low > high && high >= 0.0, "BER monotonic for M=" + m);
        }
        check(PhysicalLayer.getBER(10.0, 8.0, 2.0) != PhysicalLayer.getBERLegacyRectangularAwgn(10.0, 8.0),
                "8-QAM uses thesis Eq. 3.6");
        check(PhysicalLayer.getBER(10.0, 32.0, 2.0) != PhysicalLayer.getBERLegacyRectangularAwgn(10.0, 32.0),
                "32-QAM uses thesis Eq. 3.7");
        check(Math.abs(PhysicalLayer.osnrToSnrPerBit(12.0, 16.0, 2.0) - 3.0) < 1e-12,
                "Eq. 3.10 polarization-multiplexed");
        check(Math.abs(PhysicalLayer.osnrToSnrPerBit(12.0, 16.0, 1.0) - 6.0) < 1e-12,
                "Eq. 3.8 single polarization");
        check(PhysicalLayer.getBER(12.0, 16.0, 1.0)
                < PhysicalLayer.getBER(12.0, 16.0, 2.0),
                "same optical OSNR gives lower BER for p=1 under Eqs. 3.8-3.9");
        PhysicalLayer singlePol = layer(1, null, "jurandir", 1.0);
        PhysicalLayer doublePol = layer(1, null, "jurandir", 2.0);
        check(Math.abs(singlePol.getBER(12.0,16.0) -
                PhysicalLayer.getBER(12.0,16.0,1.0)) < 1e-15,
                "instance getBER must respect configured p=1");
        check(Math.abs(doublePol.getBER(12.0,16.0) -
                PhysicalLayer.getBER(12.0,16.0,2.0)) < 1e-15,
                "instance getBER must respect configured p=2");
        check(singlePol.getBER(12.0,16.0) < doublePol.getBER(12.0,16.0),
                "single polarization yields a different BER at equal defined OSNR");
        PhysicalLayer singleBer = layer(1, 0.02, "jurandir", 1.0);
        PhysicalLayer dualBer = layer(1, 0.02, "jurandir", 2.0);
        Modulation reference16 = new Modulation("16QAM", 1000.0, 16.0, 10.0,
                0.25, 12.5e9, 2.0, -20.0);
        check(singleBer.isAdmissible(reference16, 12.0),
                "configured single-polarization BER admission");
        check(!dualBer.isAdmissible(reference16, 12.0),
                "configured dual-polarization BER admission");
        PhysicalLayer osnrLayer = layer(0, null);
        PhysicalLayer berLayer = layer(1, null);
        PhysicalLayer explicitLayer = layer(1, 1e-3);
        Modulation mod = new Modulation("QPSK", 1000.0, 4.0, 10.0, 0.25, 12.5e9, 2.0, -20.0);
        double threshold = mod.getOSNRthresholdLinear();
        check(osnrLayer.isAdmissible(mod, threshold), "OSNR at threshold");
        check(berLayer.isAdmissible(mod, threshold), "derived BER at threshold");
        check(!berLayer.isAdmissible(mod, threshold * 0.5), "derived BER below threshold");
        check(explicitLayer.isAdmissible(mod, threshold), "explicit BER admissible");
        check(!explicitLayer.isAdmissible(mod, threshold * 0.5), "explicit BER rejected");
        PhysicalLayer legacy = layer(1, 1.0e-3, "legacyRectangularAwgn");
        Modulation mod8 = new Modulation("8QAM", 1000.0, 8.0, 8.0, 0.25, 12.5e9, 2.0, -20.0);
        Modulation mod32 = new Modulation("32QAM", 1000.0, 32.0, 8.0, 0.25, 12.5e9, 2.0, -20.0);
        check(explicitLayer.isAdmissible(mod8, Math.pow(10.0, 14.1/10.0)),
                "Jurandir 8-QAM admitted at 14.1 dB optical OSNR");
        check(!legacy.isAdmissible(mod8, Math.pow(10.0, 14.1/10.0)),
                "legacy 8-QAM rejected at 14.1 dB OSNR");
        check(explicitLayer.isAdmissible(mod32, Math.pow(10.0, 20.0/10.0)),
                "Jurandir 32-QAM admitted at 20.0 dB OSNR");
        check(!legacy.isAdmissible(mod32, Math.pow(10.0, 20.0/10.0)),
                "legacy 32-QAM rejected at 20.0 dB OSNR");
        check(legacy.isAdmissible(mod8, Math.pow(10.0, 15.0/10.0)),
                "legacy mode remains usable");
        check(!berLayer.isAdmissible(mod, Double.NaN), "nonphysical NaN rejected");
        boolean invalid = false;
        try { new PhysicalLayerConfig().setBerThreshold(0.6); }
        catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "invalid BER target rejected");
        invalid = false;
        try { new PhysicalLayerConfig().setBerModel("unknown"); }
        catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "unknown BER model rejected");
        PhysicalLayerConfig defaults = new PhysicalLayerConfig();
        check("jurandir".equals(defaults.getBerModel()), "thesis model is the default");
        System.out.println("PASS ClassicalBerQotVerification: thesis BER, OSNR threshold equivalence, explicit target and invalid inputs");
    }
}
