package qkd.physical;

import java.io.Serializable;

/** Optional, independently calibrated receiver optical budget for one QKDSpan.
 * Values are disabled (zero) by default for backwards-compatible experiments.
 * Losses are in dB. ASE is power at the receiver optical input, after
 * any wavelength-selective attenuation not already represented by the
 * receiverOpticalEfficiency parameter.
 * Do not include receiverOpticalEfficiency again in the insertion-loss fields.
 */
public final class QKDOpticalBudget implements Serializable {
	private static final long serialVersionUID = 1L;
	private final double txMuxLossDb, rxDemuxLossDb, bypassLossDb;
	private final double residualAseReceiverW;

	public QKDOpticalBudget(double txMuxLossDb, double rxDemuxLossDb,
			double bypassLossDb, double residualAseReceiverW) {
		finiteNonnegative(txMuxLossDb, "txMuxLossDb");
		finiteNonnegative(rxDemuxLossDb, "rxDemuxLossDb");
		finiteNonnegative(bypassLossDb, "bypassLossDb");
		finiteNonnegative(residualAseReceiverW, "residualAseReceiverW");
		this.txMuxLossDb = txMuxLossDb;
		this.rxDemuxLossDb = rxDemuxLossDb;
		this.bypassLossDb = bypassLossDb;
		this.residualAseReceiverW = residualAseReceiverW;
	}

	public static QKDOpticalBudget disabled() {
		return new QKDOpticalBudget(0, 0, 0, 0);
	}

	public double getSignalInsertionLossDb() {
		return txMuxLossDb + rxDemuxLossDb + bypassLossDb;
	}

	public double getResidualAseReceiverW() { return residualAseReceiverW; }

	private static void finiteNonnegative(double x, String field) {
		if (!Double.isFinite(x) || x < 0)
			throw new IllegalArgumentException(field + " must be finite and >= 0");
	}
}
