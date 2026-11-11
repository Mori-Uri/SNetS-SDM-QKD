package qkd.verification;

import qkd.capacity.PichSnrAdmission;

/** Deterministic checks of the calibrated SNR gate, not scientific calibration. */
public final class QKDPichSnrAdmissionVerification {
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	public static void main(String[] args) {
		double quiet = PichSnrAdmission.snrDb(1e-3, 20, .2, 1, 1e-9,
				1e-4, 1e2, 0, 1e-3);
		double noisy = PichSnrAdmission.snrDb(1e-3, 20, .2, 1, 1e-9,
				1e-4, 1e2, 1e-3, 3e-3);
		check(quiet > noisy, "Added classical interference must degrade PICh SNR");
		double longer = PichSnrAdmission.snrDb(1e-3, 40, .2, 1, 1e-9,
				1e-4, 1e2, 0, 1e-3);
		check(longer < quiet, "Lossier span must degrade SNR");
		double idealNoise = PichSnrAdmission.snrDb(1e-3, 20, .2, 1, 1e-8,
				0, 0, 0, 0);
		double lessNoise = PichSnrAdmission.snrDb(1e-3, 20, .2, 1, 1e-9,
				0, 0, 0, 0);
		check(lessNoise > idealNoise, "More receiver noise must decrease SNR");
		System.out.println("PASS QKDPichSnrAdmissionVerification");
	}
}
