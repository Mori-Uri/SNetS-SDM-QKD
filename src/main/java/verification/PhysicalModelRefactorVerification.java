package verification;

import java.util.Arrays;

import network.Amplifier;
import network.Crosstalk;
import network.Poggiolini2017GnIntegral;
import network.PhysicalLayer;

/**
 * Structural and numerical smoke test for consolidating the physical models.
 * No original numeric law should change during this refactoring.
 */
public final class PhysicalModelRefactorVerification {
    private PhysicalModelRefactorVerification() { }

    private static void near(double actual, double expected, String label) {
        final double tolerance = Math.max(1e-29, Math.abs(expected) * 1e-12);
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance)
            throw new AssertionError(label + ": " + actual + " != " + expected);
    }

    public static void main(String[] args) {
        near(Amplifier.gain(100, 0.001, 0.01, true), 100.0 / 11.0,
                "saturated amplifier law");
        near(Amplifier.gain(100, 0.001, 0.01, false), 100,
                "unsaturated amplifier law");
        near(Amplifier.sharedCompression(1, 2, true), 2.0 / 3,
                "shared band compression");
        near(Amplifier.gainFromCompression(100, 0.5), 50,
                "compressed gain");
        final double nf = 2 * (1 + 4 - 4 / 1.2);
        near(Amplifier.noiseFactor(2, 0.001, 4, 0.005, true), nf,
                "nonlinear noise factor");
        near(Amplifier.asePsd(193.1e12, 2, 10, 6.62607015e-34),
                6.62607015e-34 * 193.1e12 * 2 * 9, "ASE PSD");

        int[] victim = {2, 5};
        int[] interferer = {4, 8};
        if (Crosstalk.overlappingSlots(victim, interferer) != 2)
            throw new AssertionError("inclusive signal-slot overlap");
        near(Crosstalk.interfererFraction(victim, interferer), 2.0 / 5,
                "Lobato denominator must use interferer signal slots");
        near(Crosstalk.interfererFraction(victim, new int[]{0, 1}), 0,
                "invalid zero-indexed signal band must not contribute");

        Poggiolini2017GnIntegral.Fiber fiber = new Poggiolini2017GnIntegral.Fiber(.2, 1.3, -21.3, 100, 1);
        Poggiolini2017GnIntegral.Channel cut = Poggiolini2017GnIntegral.Channel.qpsk(
                193.1e12, 12.5e9, .001);
        if (PhysicalLayer.supportedChannel(cut.centerHz, cut.bandwidthHz,
                cut.powerW, 1.0) == null)
            throw new AssertionError("QPSK metadata for numerical GN fallback");
        if (PhysicalLayer.supportedChannel(cut.centerHz, cut.bandwidthHz,
                cut.powerW, .6667) != null)
            throw new AssertionError("unsupported metadata must not create a synthetic reference");

        double gn = Poggiolini2017GnIntegral.evaluateGnIntegral(
                fiber, Arrays.asList(cut), 0, 16, 6).total();
        if (!(gn > 0)) throw new AssertionError("Poggiolini GN integral invalid");

        System.out.println("PASS PhysicalModelRefactorVerification: Amplifier, Crosstalk, PhysicalLayer Habibi, Poggiolini GN (Carena EGN removed)");
    }
}
