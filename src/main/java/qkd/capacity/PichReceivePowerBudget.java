package qkd.capacity;

/**
 * Optional receiver POWER budget for a physical PICh QKDSpan.
 * This is deliberately not called SNR/BER/QoT: it cannot model ASE,
 * nonlinear noise, modulation penalties, dispersion or receiver OSNR.
 * Use only as a conservative pre-admission screen when calibrated
 * receiver sensitivity and optical losses are explicitly supplied.
 */
public final class PichReceivePowerBudget {
	private PichReceivePowerBudget() { }

	public static double receivePowerDbm(double launchPowerW, double spanKm,
			double fiberLossDbPerKm, double insertionLossDb) {
		if (!Double.isFinite(launchPowerW) || launchPowerW <= 0.0
				|| !Double.isFinite(spanKm) || spanKm <= 0.0
				|| !Double.isFinite(fiberLossDbPerKm) || fiberLossDbPerKm < 0.0
				|| !Double.isFinite(insertionLossDb) || insertionLossDb < 0.0)
			throw new IllegalArgumentException("Invalid PICh optical budget");

		return 10.0 * Math.log10(launchPowerW / 1.0e-3)
				- spanKm * fiberLossDbPerKm - insertionLossDb;
	}

	/** Apply receiver sensitivity to actual received power computed by SNetS QoT.
     * Fibre loss, insertion loss and amplifiers are NOT applied twice.
     */
	public static boolean isReceivedPowerFeasible(double receiverSignalW,
			double receiverSensitivityDbm) {
		if (!(receiverSignalW > 0.0) || !Double.isFinite(receiverSignalW)
				|| !Double.isFinite(receiverSensitivityDbm))
			throw new IllegalArgumentException("Invalid PICh received optical power");

		return 10.0 * Math.log10(receiverSignalW / 1.0e-3) >= receiverSensitivityDbm;
	}

	public static boolean isFeasible(double launchPowerW, double spanKm,
			double fiberLossDbPerKm, double insertionLossDb,
			double receiverSensitivityDbm) {
		if (!Double.isFinite(receiverSensitivityDbm))
			throw new IllegalArgumentException("Invalid PICh receiver sensitivity");

		return receivePowerDbm(launchPowerW, spanKm,
				fiberLossDbPerKm, insertionLossDb) >= receiverSensitivityDbm;
	}
}
