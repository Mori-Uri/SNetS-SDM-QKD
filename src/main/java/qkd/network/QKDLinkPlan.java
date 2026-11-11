package qkd.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pre-simulation logical QKD segmentation of one physical SNetS link.
 *
 * <p>Refer\u00eancias: [DIBAJ24] (trechos QKD de cada enlace).</p>
 */
public final class QKDLinkPlan implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final double EPSILON_KM = 1e-9;

	private final String source;
	private final String destination;
	private final double physicalLengthKm;
	private final double qtndKm;
	private final List<QKDSpan> spans;

	public QKDLinkPlan(String source, String destination,
			double physicalLengthKm, double qtndKm, List<QKDSpan> spans) {
		this(source, destination, physicalLengthKm, qtndKm, spans, false);
	}

	/**
	 * Constructs an evaluation plan for just one physical subspan, retaining its
	 * absolute offsets inside the original SNetS link. In particular, do not
	 * renumber the span to 0/1 or reset its startKm: Raman, PICh and FWM
	 * calculations need the real position along the fiber.
	 */
	public static QKDLinkPlan forPhysicalSubspan(QKDLinkPlan fullPlan, QKDSpan span) {
		if (fullPlan == null || span == null || !fullPlan.getSpans().contains(span)) {
			throw new IllegalArgumentException("QKD subspan must belong to its complete physical link");
		}

		return new QKDLinkPlan(fullPlan.getSource(), fullPlan.getDestination(),
				fullPlan.getPhysicalLengthKm(), fullPlan.getQtndKm(),
				Collections.singletonList(span), true);
	}

	private QKDLinkPlan(String source, String destination,
			double physicalLengthKm, double qtndKm, List<QKDSpan> spans,
			boolean physicalSubspan) {
		if (source == null || source.trim().isEmpty()
				|| destination == null || destination.trim().isEmpty()) {
			throw new IllegalArgumentException("link endpoints cannot be blank");
		}

		if (!Double.isFinite(physicalLengthKm) || physicalLengthKm <= 0.0) {
			throw new IllegalArgumentException("physicalLengthKm must be finite and > 0");
		}

		if (!Double.isFinite(qtndKm) || qtndKm <= 0.0) {
			throw new IllegalArgumentException("qtndKm must be finite and > 0");
		}

		if (spans == null || spans.isEmpty() || (physicalSubspan && spans.size() != 1)) {
			throw new IllegalArgumentException("spans cannot be null/empty or multiple physical subspans");
		}

		double sum = 0.0;
		double expectedStart = 0.0;

		for (int i = 0; i < spans.size(); i++) {
			final QKDSpan span = spans.get(i);

			if (span == null) {
				throw new IllegalArgumentException("spans cannot contain null");
			}

			// Physical SNetS nodes are mandatory QKD boundaries. A QKDSpan
			// must belong to this link, and spans must cover it contiguously.
			if (!source.equals(span.getPhysicalLinkSource())
					|| !destination.equals(span.getPhysicalLinkDestination())
					|| (!physicalSubspan && (span.getIndex() != i
						|| span.getTotalSpans() != spans.size()
						|| Math.abs(span.getStartKm() - expectedStart) > EPSILON_KM))
					|| span.getEndKm() > physicalLengthKm + EPSILON_KM) {
				throw new IllegalArgumentException(
						"QKDSpan cannot cross a physical SNetS node or leave a gap: " + span);
			}

			expectedStart = span.getEndKm();

			if (span.getLengthKm() - qtndKm > EPSILON_KM) {
				throw new IllegalArgumentException(
						"QKD span exceeds configured QTND: " + span);
			}

			sum += span.getLengthKm();
		}

		if (!physicalSubspan && Math.abs(sum - physicalLengthKm) > EPSILON_KM) {
			throw new IllegalArgumentException(
					"sum of QKD spans (" + sum
					+ " km) differs from physical link length ("
					+ physicalLengthKm + " km)");
		}

		this.source = source;
		this.destination = destination;
		this.physicalLengthKm = physicalLengthKm;
		this.qtndKm = qtndKm;
		this.spans = Collections.unmodifiableList(
				new ArrayList<QKDSpan>(spans));
	}

	public String getSource() {
		return source;
	}

	public String getDestination() {
		return destination;
	}

	public double getPhysicalLengthKm() {
		return physicalLengthKm;
	}

	public double getQtndKm() {
		return qtndKm;
	}

	public List<QKDSpan> getSpans() {
		return spans;
	}

	public int getSpanCount() {
		return spans.size();
	}

	/**
     * Number of logical trusted-node boundaries required inside this link.
     */
	public int getVirtualTrustedNodeCount() {
		return Math.max(0, spans.size() - 1);
	}

	@Override
	public String toString() {
		return "QKDLinkPlan{" + source + "->" + destination
				+ ", physicalLengthKm=" + physicalLengthKm
				+ ", qtndKm=" + qtndKm
				+ ", spans=" + spans.size()
				+ ", virtualTrustedNodes=" + getVirtualTrustedNodeCount()
				+ '}';
	}
}
