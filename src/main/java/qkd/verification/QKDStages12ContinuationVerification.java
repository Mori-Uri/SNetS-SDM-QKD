package qkd.verification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import network.Link;
import network.Oxc;
import qkd.capacity.PichClassicalNonlinearity;
import qkd.capacity.QKDPichCircuit;
import qkd.multiband.ClassicalPowerEvolution;
import qkd.multiband.SpontaneousRamanModel;
import qkd.network.QKDSpan;
import simulationControl.parsers.NetworkConfig.CoreConfig;

/** Standalone contract checks for the continuation of physical stages 1/2. */
public final class QKDStages12ContinuationVerification {
	private QKDStages12ContinuationVerification() { }

	private static void check(boolean ok, String label) {
		if (!ok) throw new AssertionError(label);
	}

	private static void close(double actual, double expected, double tol, String label) {
		if (Math.abs(actual - expected) > tol * Math.max(1e-16, Math.abs(expected)))
			throw new AssertionError(label + ": " + actual + " != " + expected);
	}

	private static void testLongitudinalRaman() {
		final double alphaC = 0.2 * Math.log(10.0) / 10.0;
		final double alphaQ = 0.25 * Math.log(10.0) / 10.0;
		final ClassicalPowerEvolution noGain = new ClassicalPowerEvolution(1000.0, 0.0);
		close(noGain.forwardEffectiveLengthKm(0, 40, 0, alphaC, alphaQ, 0),
				SpontaneousRamanModel.forwardLengthKm(alphaC, alphaQ, 40), 2e-5,
				"zero-gain numerical model must reproduce analytic passive link");
		close(noGain.forwardEffectiveLengthKm(0, 40, 0, alphaC, alphaQ, 0.01),
				SpontaneousRamanModel.intercoreForwardLengthKm(alphaC, alphaQ, 0.01, 40),
				2e-5, "inter-core coupling numerical integral");
		final ClassicalPowerEvolution amplified = new ClassicalPowerEvolution(20, 4);
		final double passive = noGain.forwardEffectiveLengthKm(20, 40, 0, alphaC, alphaQ, 0);
		final double withAmp = amplified.forwardEffectiveLengthKm(20, 40, 0, alphaC, alphaQ, 0);
		check(withAmp > passive, "classical amplifiers must increase the Raman pump");
		close(amplified.powerRatio(0, 20, alphaC),
				Math.exp(-20 * alphaC) * Math.pow(10.0, 4.0 / 10.0),
				1e-12, "lumped amplifier position");
		final double sourceAtTrustedNode = noGain.forwardEffectiveLengthKm(20, 40, 20, alphaC, alphaQ, 0);
		close(sourceAtTrustedNode, SpontaneousRamanModel.forwardLengthKm(alphaC, alphaQ, 20),
				2e-5, "PICh is launched at the QKDSpan start, not physical link source");
		close(amplified.forwardEffectiveLengthKm(0, 20, 0, alphaC, alphaQ, 0),
				noGain.forwardEffectiveLengthKm(0, 20, 0, alphaC, alphaQ, 0),
				1e-12, "amplifier at receiver cannot affect previously generated Raman");
	}

	private static void testPichClassicalNli() {
		final List<CoreConfig> cores = new ArrayList<CoreConfig>();
		cores.add(new CoreConfig(0, Arrays.asList(Integer.valueOf(1))));
		cores.add(new CoreConfig(1, Arrays.asList(Integer.valueOf(0))));
		final Link link = new Link(new Oxc("A"), new Oxc("B"), 80, 12.5e9, 80.0, cores);
		final QKDSpan span = new QKDSpan("A", "B", 0, 2, 0.0, 40.0, "A", "TN1");
		final double lo = 190e12;
		final double slot = 12.5e9;
		final double victim = lo + 10 * slot;
		final QKDPichCircuit src = new QKDPichCircuit(link, span, 0, new int[] {20, 21}, 0, 1e-3);
		link.getCore(0).addAuxiliaryClassicalCircuit(src);
		final double expectedPower = 0.5e-3;
		close(PichClassicalNonlinearity.meanPichPowerW(link, 0), expectedPower, 1e-12,
				"PICh must contribute proportionally to occupied longitudinal section");
		check(PichClassicalNonlinearity.meanPichPowerW(link, 1) == 0,
				"other core cannot saturate this core amplifier approximation");
		final double joh = PichClassicalNonlinearity.crossTermJohannisson(
				link, 0, victim, 1e-14, 1e-5, lo, slot, 2.0);
		check(joh > 0, "PICh must generate same-core GN XCI");
		check(PichClassicalNonlinearity.crossTermJohannisson(
				link, 1, victim, 1e-14, 1e-5, lo, slot, 2.0) == 0,
				"different-core PICh must not generate same-core Kerr XCI");
		final double hab = PichClassicalNonlinearity.crossTermHabibi(link, 0,
				victim, 25e9, 1e-14, 1e-8, 2.3e-5, 2.1e-26, lo, slot);
		check(hab > 0, "Habibi PICh GN XCI must be positive");
		link.getCore(0).removeAuxiliaryClassicalCircuit(src);
		check(PichClassicalNonlinearity.crossTermJohannisson(
				link, 0, victim, 1e-14, 1e-5, lo, slot, 2.0) == 0,
				"rollback/removal must remove source from nonlinear impairment");
	}

	public static void main(String[] args) {
		testLongitudinalRaman();
		testPichClassicalNli();
		System.out.println("PASS: QKDStages12ContinuationVerification");
	}
}
