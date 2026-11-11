package qkd.verification;

import qkd.physical.BB84DecoyModel;
import qkd.physical.QKDOpticalBudget;
import qkd.physical.QKDPhysicalParameters;

/** Run with java -cp target/classes qkd.verification.QKDStages12Verification. */
public final class QKDStages12Verification {
	private QKDStages12Verification() { }

	private static void check(boolean condition, String msg) {
		if (!condition) throw new AssertionError(msg);
	}

	private static void testPichCrosstalkGeometry() {
		java.util.List<simulationControl.parsers.NetworkConfig.CoreConfig> configs =
				new java.util.ArrayList<simulationControl.parsers.NetworkConfig.CoreConfig>();
		configs.add(new simulationControl.parsers.NetworkConfig.CoreConfig(0,
				java.util.Arrays.asList(Integer.valueOf(1))));
		configs.add(new simulationControl.parsers.NetworkConfig.CoreConfig(1,
				java.util.Arrays.asList(Integer.valueOf(0))));
		configs.add(new simulationControl.parsers.NetworkConfig.CoreConfig(2,
				java.util.Collections.<Integer>emptyList()));
		network.Link link = new network.Link(new network.Oxc("A"), new network.Oxc("B"),
				80, 12.5e9, 60.0, configs);
		qkd.network.QKDSpan first = new qkd.network.QKDSpan("A", "B", 0, 2,
				0, 30.0, "A", "TN");
		qkd.capacity.QKDPichCircuit pich = new qkd.capacity.QKDPichCircuit(link,
				first, 1, new int[] {3, 4}, 0, 0.001);
		link.getCore(1).addAuxiliaryClassicalCircuit(pich);
		double affected = qkd.capacity.PichClassicalCrosstalk.noisePsd(link,
				0, new int[] {3, 4}, 25e9, 1e-7, 2.0, false);
		check(affected > 0.0, "adjacent PICh must interfere");
		check(qkd.capacity.PichClassicalCrosstalk.noisePsd(link,
				0, new int[] {10, 11}, 25e9, 1e-7, 2.0, false) == 0.0,
				"disjoint spectral bands cannot interfere through linear XT");
		check(qkd.capacity.PichClassicalCrosstalk.noisePsd(link,
				2, new int[] {3, 4}, 25e9, 1e-7, 2.0, false) == 0.0,
				"nonadjacent cores cannot interfere through linear XT");
		double half = qkd.capacity.PichClassicalCrosstalk.noisePsd(link,
				0, new int[] {3, 3}, 25e9, 1e-7, 2.0, false);
		check(half > 0.0 && half < affected, "overlap must be accounted for");
		link.getCore(1).removeAuxiliaryClassicalCircuit(pich);
		check(qkd.capacity.PichClassicalCrosstalk.noisePsd(link,
				0, new int[] {3, 4}, 25e9, 1e-7, 2.0, false) == 0.0,
				"PICh release must remove the noise");
	}

	public static void main(String[] args) {
		QKDPhysicalParameters base = new QKDPhysicalParameters();
		BB84DecoyModel bb84 = new BB84DecoyModel(base);
		double a = base.attenuationDbPerKm(1550.0);
		double p = base.getDarkCountProbability();
		double reference = bb84.evaluateWithAttenuation(a, 30.0, p).getSecretKeyRateBps();
		double withZeroLoss = bb84.evaluateWithOpticalLoss(a, 30.0, p, 0.0).getSecretKeyRateBps();
		check(Double.compare(reference, withZeroLoss) == 0, "zero loss changes baseline");
		double lossy = bb84.evaluateWithOpticalLoss(a, 30.0, p, 3.0).getSecretKeyRateBps();
		check(lossy < reference, "3 dB insertion loss must reduce SKR");
		qkd.physical.QKDPhysicalLayer layer = new qkd.physical.QKDPhysicalLayer(base);
		double ase = layer.evaluateFromReceivedPower(1e-13, 1550.0)
				.getCrosstalkBackgroundProbability();
		check(ase > 0.0 && ase < 1.0, "ASE photon count must be a valid probability");
		double noisySkr = bb84.evaluateWithAttenuation(a, 30.0,
				1.0 - (1.0 - p) * (1.0 - ase)).getSecretKeyRateBps();
		check(noisySkr < reference, "receiver-referenced ASE must reduce SKR");
		check(bb84.evaluateWithOpticalLoss(a, 30.0, p, 3.0).getTransmittanceWithDetector()
				< bb84.evaluateWithAttenuation(a, 30.0, p).getTransmittanceWithDetector(),
				"insertion loss must reduce signal transmission");
		QKDOpticalBudget budget = new QKDOpticalBudget(1.0, 2.0, 0.5, 1e-15);
		QKDPhysicalParameters modified = base.withOpticalBudget(budget);
		check(modified.getOpticalBudget().getSignalInsertionLossDb() == 3.5, "loss sum");
		check(base.getOpticalBudget().getSignalInsertionLossDb() == 0.0, "parameter copy isolation");
		check(modified.withPichLaunchPowerDbm(-3.0).getOpticalBudget()
				.getSignalInsertionLossDb() == 3.5, "optical budget lost on copy");
		check(modified.withReceiverAssumptions(base.getDetectorGateWindowS(),
				base.getReceiverOpticalEfficiency(), base.getReceiverTransferMode())
				.getOpticalBudget().getSignalInsertionLossDb() == 3.5, "optical budget lost on receiver copy");
		boolean failed = false;

		try { new QKDOpticalBudget(-1.0, 0, 0, 0); }
		catch (IllegalArgumentException expected) { failed = true; }
		check(failed, "negative loss must be rejected");
		testPichCrosstalkGeometry();
		System.out.println("PASS: QKDStages12Verification");
	}
}
