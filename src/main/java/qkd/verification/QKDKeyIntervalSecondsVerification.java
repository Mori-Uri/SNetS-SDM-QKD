package qkd.verification;

import java.util.Arrays;
import java.util.TreeSet;

import qkd.key.QKDKeyDemand;
import qkd.key.QKDKeyDemandPolicy;
import simulationControl.parsers.SimulationConfig;

/** Verifies the single seconds-based renewal field for Moghaddam and Dibaj. */
public final class QKDKeyIntervalSecondsVerification {

	private QKDKeyIntervalSecondsVerification() {
	}

	private static void close(double observed, double expected, String context) {
		if (!(Math.abs(observed - expected) <= Math.max(1e-9, Math.abs(expected) * 1e-12))) {
			throw new AssertionError(context + ": observed=" + observed + ", expected=" + expected);
		}
	}

	private static void verify(double[] intervals, double[] expectedBps, String study) {
		QKDKeyDemandPolicy policy = new QKDKeyDemandPolicy(256, intervals, 2024L);
		TreeSet<Double> observed = new TreeSet<Double>();

		for (int i = 0; i < 1000; i++) {
			QKDKeyDemand demand = policy.draw();
			observed.add(demand.getRequiredKeyRateBps());
		}

		if (observed.size() != 3) throw new AssertionError(study + ": expected 3 rates");

		for (int i = 0; i < intervals.length; i++) {
			close(QKDKeyDemand.calculateRequiredKeyRateBps(256, intervals[i]), expectedBps[i], study);
		}

		policy.configureByBitRate(Arrays.asList(100e9, 200e9, 400e9), null);
		close(policy.forBitRate(100e9).getRequiredKeyRateBps(), expectedBps[2], study + " byBitRate smallest");
		close(policy.forBitRate(400e9).getRequiredKeyRateBps(), expectedBps[0], study + " byBitRate largest");
	}

	private static void verifyLegacyJson(String field, double value, double expectedSeconds) {
		String json = "{\"qkdKeyDemand\":{\"keySizeBits\":256,\"profile\":\"custom\",\""
				+ field + "\":[" + value + "]}}";
		SimulationConfig parsed = new com.google.gson.Gson().fromJson(json, SimulationConfig.class);
		double seconds = parsed.getQkdKeyDemand().getUpdateIntervalSeconds()[0];
		close(seconds, expectedSeconds, field);
		close(QKDKeyDemand.calculateRequiredKeyRateBps(256, seconds), 256.0 / expectedSeconds, field + " rate");
	}

	public static void main(String[] args) {
		SimulationConfig.QKDKeyDemandConfig config = new SimulationConfig.QKDKeyDemandConfig();
		if (!Arrays.equals(config.getUpdateIntervalSeconds(), new double[] {0.064, 0.128, 0.256})) {
			throw new AssertionError("Default must follow Moghaddam 2021 (ms converted into seconds)");
		}

		verify(new double[] {0.064, 0.128, 0.256}, new double[] {4000, 2000, 1000}, "Moghaddam 2021");
		verify(new double[] {0.000064, 0.000128, 0.000256},
				new double[] {4000000, 2000000, 1000000}, "Dibaj 2024");

		config.setUpdateIntervalSeconds(new double[] {0.000064, 0.000128, 0.000256});
		close(config.getUpdateIntervalSeconds()[0], 0.000064, "configured seconds");
		double[] defensiveCopy = config.getUpdateIntervalSeconds();
		defensiveCopy[0] = 999.0;
		close(config.getUpdateIntervalSeconds()[0], 0.000064, "defensive copy");
		boolean invalid = false;
		try {
			config.setUpdateIntervalSeconds(new double[] {0.0});
		} catch (IllegalArgumentException expected) {
			invalid = true;
		}
		if (!invalid) throw new AssertionError("zero key-update intervals must be rejected");
		close(config.getUpdateIntervalSeconds()[0], 0.000064, "failed setter must preserve previous settings");

		verifyLegacyJson("updateIntervalsMicroseconds", 64.0, 0.000064);
		verifyLegacyJson("updateIntervalsMiliseconds", 64.0, 0.064);
		boolean rejectedObsolete = false;
		try {
			new com.google.gson.Gson().fromJson("{\"qkdKeyDemand\":{\"updateIntervalsMileseconds\":[64]}}",
					SimulationConfig.class).getQkdKeyDemand();
		} catch (IllegalArgumentException expected) {
			rejectedObsolete = expected.getMessage().contains("updateIntervalsMiliseconds");
		}
		if (!rejectedObsolete) throw new AssertionError("Obsolete spelling must be rejected explicitly");

		verifyLegacyJson("updateIntervalsMilliseconds", 64.0, 0.064);
		verifyLegacyJson("updateIntervalSeconds", 0.064, 0.064);
		boolean refused = false;
		try {
			new com.google.gson.Gson().fromJson("{\"qkdKeyDemand\":{\"updateIntervalSeconds\":[0.064],"
				+ "\"updateIntervalsMicroseconds\":[64]}}", SimulationConfig.class).getQkdKeyDemand();
		} catch (IllegalArgumentException expected) {
			refused = expected.getMessage().contains("Ambiguous");
		}
		if (!refused) throw new AssertionError("Conflicting interval fields must be rejected");
		for (String invalidName : new String[] {"updateIntervalsMicroseconds", "updateIntervalsMiliseconds"}) {
			boolean bad = false;
			try {
				new com.google.gson.Gson().fromJson("{\"qkdKeyDemand\":{\"" + invalidName
						+ "\":[0]}}", SimulationConfig.class).getQkdKeyDemand();
			} catch (IllegalArgumentException expected) { bad = true; }
			if (!bad) throw new AssertionError("Must reject zero in " + invalidName);
		}


		System.out.println("[OK] seconds + legacy micro/milli/mileseconds, profiles, byBitRate, conflicts");
	}
}
