package network;

import java.util.Arrays;
import java.util.Vector;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;
import simulationControl.parsers.NetworkConfig.ModulationConfig;
import simulationControl.parsers.NetworkConfig.NodeConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.TrafficConfig;

/**
 * Vale & Almeida (2019) cubic-SCI reference-invariance regression.
 *
 * Tests the REAL PhysicalLayer propagation, not just the analytic identity.
 * The 1 mW production reference is a numerical normalization, not a measured
 * fibre/amplifier property. These tests apply only to fixed-gain (cubic SCI)
 * propagation; saturated longitudinal amplifier settings have their own
 * numerical optimizer and must not be justified by cubic scaling.
 */
public final class CascadedMaximumPowerReferenceVerification {
    private CascadedMaximumPowerReferenceVerification() { }

    private static final double[] REFERENCES_W = {
            0.1e-3, 0.25e-3, 0.5e-3, 1.0e-3, 2.0e-3, 5.0e-3, 10.0e-3
    };

    private static void check(boolean value, String context) {
        if (!value) throw new AssertionError(context);
    }

    private static void testCase(int model, boolean integral, double secondSpanKm) {
        NetworkConfig nc = new NetworkConfig();
        nc.setNodes(Arrays.asList(new NodeConfig("A", 4, 4, 0),
                new NodeConfig("B", 4, 4, 0), new NodeConfig("C", 4, 4, 0)));
        nc.setCores(Arrays.asList(new CoreConfig(0, Arrays.<Integer>asList(1)),
                new CoreConfig(1, Arrays.<Integer>asList(0))));
        nc.setLinks(Arrays.asList(new LinkConfig("A", "B", 64, 12.5e9, 80.0),
                new LinkConfig("B", "C", 64, 12.5e9, secondSpanKm)));
        nc.setModulations(Arrays.asList(new ModulationConfig("QPSK", 4.0,
                10000.0, -100.0, -10.0)));
        PhysicalLayerConfig pc = new PhysicalLayerConfig();
        pc.setPhysicalLayerModel(model);
        pc.setJohannissonNliMode(integral ? "integral" : "approximate");
        pc.setActiveASE(true);
        pc.setActiveNLI(true);
        pc.setActiveXT(false);
        pc.setPower(0.0);
        pc.setSpanLength(80.0);
        pc.setFiberLoss(0.2);
        pc.setFiberNonlinearity(1.3e-3);
        pc.setFiberDispersion(16.7e-6);
        pc.setCenterFrequency(193.4e12);
        pc.setConstantOfPlanck(6.62607015e-34);
        pc.setNoiseFigureOfOpticalAmplifier(5.0);
        pc.setPowerSaturationOfOpticalAmplifier(18.0);
        pc.setAmplificationFrequency(193.4e12);
        pc.setSwitchInsertionLoss(1.0);
        pc.setPolarizationModes(2.0);
        pc.setTypeOfAmplifierGain(PhysicalLayer.AMP_GAIN_FIXED);

        Mesh mesh = new Mesh(nc, new TrafficConfig(), pc, new OthersConfig(), null, new Util());
        PhysicalLayer layer = mesh.getPhysicalLayer();
        Modulation modulation = mesh.getAvaliableModulations().get(0);
        Route route = new Route(new Vector<Node>(mesh.getNodeList()));
        Circuit victim = new Circuit();
        victim.setRoute(route);
        victim.setPair(new Pair(route.getSource(), route.getDestination()));
        victim.setModulation(modulation);
        victim.setIndexCore(0);
        victim.setSpectrumAssigned(new int[] {12, 15});
        victim.setLaunchPowerLinear(1.0e-3);
        RequestForConnection req = new RequestForConnection();
        req.setPair(victim.getPair());
        req.setRequiredBitRate(modulation.potentialBitRate(4));
        victim.addRequest(req);

        final double rate = victim.getRequiredBitRate();
        final double base = layer.computePowerThatMaximizesOSNR(victim, rate,
                route, 0, 2, modulation, 0, victim.getSpectrumAssigned());
        check(base > 1e-9 && base < 0.1, "fixture must have a non-clamped optimum: " + base);
        final double initial = victim.getLaunchPowerLinear();
        double worstRelative = 0.0;
        for (double ref : REFERENCES_W) {
            double candidate = layer.computeCascadedMaximumPowerAtReference(victim,
                    rate, route, 0, 2, modulation, 0,
                    victim.getSpectrumAssigned(), ref);
            double delta = Math.abs(candidate - base) / base;
            worstRelative = Math.max(worstRelative, delta);
            check(Double.isFinite(candidate) && delta < 1.0e-7,
                    "reference dependence: model=" + model + " integral=" + integral
                    + " span=" + secondSpanKm + " ref=" + ref
                    + " predicted=" + candidate + " 1mW=" + base + " err=" + delta);
            check(victim.getLaunchPowerLinear() == initial,
                    "reference calculation mutated launch power");
        }
        for (double invalid : new double[] {0.0, -1e-3, Double.NaN,
                Double.POSITIVE_INFINITY}) {
            boolean rejected = false;
            try {
                layer.computeCascadedMaximumPowerAtReference(victim, rate,
                        route, 0, 2, modulation, 0, victim.getSpectrumAssigned(), invalid);
            } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid reference must be rejected: " + invalid);
        }
        System.out.printf(java.util.Locale.ROOT,
                "PASS model=%s integral=%s lastSpanKm=%.1f production=%.12g W "
                + "maxRelativeError=%.3g refs=%d%n",
                model == PhysicalLayer.MODEL_JOHANNISSON ? "Johannisson" : "Habibi",
                integral, secondSpanKm, base, worstRelative, REFERENCES_W.length);
    }

    public static void main(String[] args) {
        for (int model : new int[] {PhysicalLayer.MODEL_JOHANNISSON,
                PhysicalLayer.MODEL_HABIBI}) {
            for (boolean integral : new boolean[] {false, true}) {
                for (double lastSpan : new double[] {80.0, 37.0}) {
                    testCase(model, integral, lastSpan);
                }
            }
        }
    }
}
