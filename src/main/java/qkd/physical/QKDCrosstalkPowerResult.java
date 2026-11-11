package qkd.physical;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of the SNetS-state -> absolute inter-core crosstalk-power bridge for
 * one optical link and one candidate QKD spectrum interval.
 *
 * <p>The value exposed by {@link #getTotalPowerW()} is an absolute power in
 * watts. It is deliberately kept separate from the normalized XT quantities
 * traditionally used by the classical SNetS QoT checks, because the QKD
 * receiver model converts optical power into mean photon/detection events.</p>
 */
public final class QKDCrosstalkPowerResult implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String linkName;
	private final int quantumCoreId;
	private final int quantumSpectrumStart;
	private final int quantumSpectrumEnd;
	private final double couplingCoefficientPerM;
	private final double linkDistanceKm;
	private final int adjacentCoresExamined;
	private final int circuitsExamined;
	private final int overlappingCircuits;
	private final List<QKDCrosstalkContribution> contributions;
	private final double totalPowerW;
	/** Optional exact receiver-referenced XT power (W), -1 => legacy transfer convention. */
	private final double receiverPowerOverrideW;

	public QKDCrosstalkPowerResult withReceiverPowerW(double receiverPowerW) {
		if (!Double.isFinite(receiverPowerW) || receiverPowerW < 0.0)
			throw new IllegalArgumentException("receiver XT power must be finite and >= 0");
		return new QKDCrosstalkPowerResult(linkName, quantumCoreId,
				quantumSpectrumStart, quantumSpectrumEnd, couplingCoefficientPerM,
				linkDistanceKm, adjacentCoresExamined, circuitsExamined,
				overlappingCircuits, contributions, totalPowerW, receiverPowerW);
	}
	public boolean hasReceiverPowerOverride() { return receiverPowerOverrideW >= 0.0; }
	public double getReceiverPowerOverrideW() { return receiverPowerOverrideW; }


	public QKDCrosstalkPowerResult(
			String linkName,
			int quantumCoreId,
			int quantumSpectrumStart,
			int quantumSpectrumEnd,
			double couplingCoefficientPerM,
			double linkDistanceKm,
			int adjacentCoresExamined,
			int circuitsExamined,
			int overlappingCircuits,
			List<QKDCrosstalkContribution> contributions,
			double totalPowerW) {
		this(linkName, quantumCoreId, quantumSpectrumStart, quantumSpectrumEnd,
				couplingCoefficientPerM, linkDistanceKm, adjacentCoresExamined,
				circuitsExamined, overlappingCircuits, contributions, totalPowerW, -1.0);
	}

	private QKDCrosstalkPowerResult(
			String linkName, int quantumCoreId, int quantumSpectrumStart,
			int quantumSpectrumEnd, double couplingCoefficientPerM, double linkDistanceKm,
			int adjacentCoresExamined, int circuitsExamined, int overlappingCircuits,
			List<QKDCrosstalkContribution> contributions, double totalPowerW,
			double receiverPowerOverrideW) {
		this.receiverPowerOverrideW = receiverPowerOverrideW;
		this.linkName = linkName;
		this.quantumCoreId = quantumCoreId;
		this.quantumSpectrumStart = quantumSpectrumStart;
		this.quantumSpectrumEnd = quantumSpectrumEnd;
		this.couplingCoefficientPerM = couplingCoefficientPerM;
		this.linkDistanceKm = linkDistanceKm;
		this.adjacentCoresExamined = adjacentCoresExamined;
		this.circuitsExamined = circuitsExamined;
		this.overlappingCircuits = overlappingCircuits;
		this.contributions = Collections.unmodifiableList(
				new ArrayList<QKDCrosstalkContribution>(contributions));
		this.totalPowerW = totalPowerW;
	}

	public String getLinkName() { return linkName; }
	public int getQuantumCoreId() { return quantumCoreId; }
	public int getQuantumSpectrumStart() { return quantumSpectrumStart; }
	public int getQuantumSpectrumEnd() { return quantumSpectrumEnd; }
	public double getCouplingCoefficientPerM() { return couplingCoefficientPerM; }
	public double getLinkDistanceKm() { return linkDistanceKm; }
	public int getAdjacentCoresExamined() { return adjacentCoresExamined; }
	public int getCircuitsExamined() { return circuitsExamined; }
	public int getOverlappingCircuits() { return overlappingCircuits; }
	public List<QKDCrosstalkContribution> getContributions() { return contributions; }
	public double getTotalPowerW() { return totalPowerW; }

	public boolean hasCrosstalk() {
		return totalPowerW > 0.0;
	}

	@Override
	public String toString() {
		return "QKDCrosstalkPowerResult{" +
				"link='" + linkName + '\'' +
				", quantumCore=" + quantumCoreId +
				", qSpectrum=[" + quantumSpectrumStart + ',' + quantumSpectrumEnd + ']' +
				", hPerM=" + couplingCoefficientPerM +
				", linkKm=" + linkDistanceKm +
				", adjacentCores=" + adjacentCoresExamined +
				", circuits=" + circuitsExamined +
				", overlapping=" + overlappingCircuits +
				", totalPxtW=" + totalPowerW +
				'}';
	}
}
