package qkd.verification;

import java.util.Arrays;
import java.util.Collections;
import network.Circuit;
import network.Link;
import network.Oxc;
import qkd.multiband.FiberAttenuationModel;
import qkd.multiband.MultiBandSettings;
import qkd.multiband.SpontaneousRamanModel;
import qkd.network.QKDSpan;
import qkd.physical.QKDPhysicalLayer;
import qkd.physical.QKDPhysicalParameters;
import qkd.physical.QKDSpanPhysicalResult;
import simulationControl.parsers.NetworkConfig.CoreConfig;

/** Regression: opposite directed links are independent physical fibres (never counter-propagating). */
public final class QKDDirectedFibresVerification {
    private QKDDirectedFibresVerification() { }

    public static void main(String[] args) {
        for (MultiBandSettings.Architecture architecture : new MultiBandSettings.Architecture[] {
                MultiBandSettings.Architecture.MB, MultiBandSettings.Architecture.MBSDM }) {
            MultiBandSettings mb = new MultiBandSettings(architecture, MultiBandSettings.RamanModel.FISICO,
                    FiberAttenuationModel.g652d(), SpontaneousRamanModel.standard(), 0,
                    191.3e12, true, 1381.0);
            QKDPhysicalParameters p = new QKDPhysicalParameters().withMultiband(mb);
            QKDPhysicalLayer physical = new QKDPhysicalLayer(p);
            Link ab = link("A", "B");
            Link ba = link("B", "A");
            add(ab, 0, 150, 155, 1e-3);
            QKDSpan span = new QKDSpan("A", "B", 0, 1, 0.0, 25.0, "A", "B");
            QKDSpanPhysicalResult before = physical.evaluateSpanUsingStoredLaunchPowers(ab, span, 1,
                    new int[] {1, 1}, 1e-9, 1381.0);
            double r0 = before.getRamanPowerW();
            // Loading the other fibre (including overlapping slots/cores) must not modify A->B.
            add(ba, 0, 150, 155, 0.1);
            add(ba, 1, 160, 165, 0.1);
            QKDSpanPhysicalResult after = physical.evaluateSpanUsingStoredLaunchPowers(ab, span, 1,
                    new int[] {1, 1}, 1e-9, 1381.0);
            double r1 = after.getRamanPowerW();
            require(Double.doubleToLongBits(r0) == Double.doubleToLongBits(r1),
                    architecture + " reverse traffic changed forward Raman noise");
            require(Double.doubleToLongBits(before.getSecretKeyRateBps()) ==
                    Double.doubleToLongBits(after.getSecretKeyRateBps()),
                    architecture + " reverse traffic changed A->B SKR");
            require(Double.doubleToLongBits(before.getQber()) ==
                    Double.doubleToLongBits(after.getQber()),
                    architecture + " reverse traffic changed A->B QBER");
            require(ab.getCore(0).getCircuitList().size() == 1,
                    "A->B core shares circuit occupancy with B->A");
            require(ba.getCore(0).getCircuitList().size() == 1,
                    "B->A core did not record independent circuit occupancy");
            require(r0 >= 0.0 && Double.isFinite(r0), "Nonfinite forward Raman");
            System.out.println("[OK] " + architecture + ": two independent directed fibres, A->B Raman=" + r0);
        }
        System.out.println("QKDDirectedFibresVerification: PASS");
    }

    private static Link link(String source, String dest) {
        return new Link(new Oxc(source), new Oxc(dest), 320, 12.5e9, 25.0,
                Arrays.asList(new CoreConfig(0, Collections.singletonList(1)),
                        new CoreConfig(1, Collections.singletonList(0))));
    }

    private static void add(Link link, int core, int from, int to, double watts) {
        Circuit c = new Circuit();
        c.setIndexCore(core);
        c.setSpectrumAssigned(new int[] {from, to});
        c.setLaunchPowerLinear(watts);
        link.getCore(core).addCircuit(c);
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
