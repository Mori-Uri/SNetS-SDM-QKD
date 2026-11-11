package qkd.traffic;

import network.Pair;
import network.RequestGenerator;
import qkd.key.QKDKeyDemand;
import qkd.key.QKDKeyDemandPolicy;
import request.RequestForConnection;
import simulator.Event;
import simulator.EventMachine;
import simulator.eventListeners.ArriveRequestForConexionListener;
import util.RandGenerator;

/**
 * QKD-aware version of the SNetS traffic generator.
 *
 * <p>The original {@link RequestGenerator} remains unchanged. This subclass is
 * instantiated only for QKD-enabled simulations and preserves the original
 * Poisson/exponential timing behavior while tagging each request as either
 * classical or QKD-protected.</p>
 *
 * <p>Refer\u00eancias: [SNETS17] A. Fontinele et al., "An Efficient IA-RMLSA Algorithm for Transparent Elastic Optical Networks", Comput. Netw. 118, 2017 (SNetS) (gera\u00e7\u00e3o Poisson do SNetS), [MORI26].</p>
 */
public class QKDRequestGenerator extends RequestGenerator {

	private static final long serialVersionUID = 1L;

	private final RandGenerator timingRandGenerator;
	private final RandGenerator protectionRandGenerator;
	private final double protectedRequestProbability;
	private double qkdOnlyProbability;
	private final QKDKeyDemandPolicy keyDemandPolicy;
	private double atualTimeHours;
	private QKDArrivalBudget arrivalBudget;

	public QKDRequestGenerator(Pair pair, double bandwidth, double holdRate,
			double arrivedRate, double incLoad, RandGenerator randGenerator,
			double protectedRequestProbability) {
		this(pair, bandwidth, holdRate, arrivedRate, incLoad, randGenerator,
				protectedRequestProbability,
				QKDKeyDemandPolicy.referenceDefault());
	}

	public QKDRequestGenerator(Pair pair, double bandwidth, double holdRate,
			double arrivedRate, double incLoad, RandGenerator randGenerator,
			double protectedRequestProbability,
			QKDKeyDemandPolicy keyDemandPolicy) {
		this(pair, bandwidth, holdRate, arrivedRate, incLoad, randGenerator,
				randGenerator, protectedRequestProbability, keyDemandPolicy);
	}

	/** Production constructor with independent traffic-timing and QKD-class streams. */
	public QKDRequestGenerator(Pair pair, double bandwidth, double holdRate,
			double arrivedRate, double incLoad, RandGenerator timingRandGenerator,
			RandGenerator protectionRandGenerator,
			double protectedRequestProbability,
			QKDKeyDemandPolicy keyDemandPolicy) {
		super(pair, bandwidth, holdRate, arrivedRate, incLoad, timingRandGenerator);

		if (timingRandGenerator == null || protectionRandGenerator == null) {
			throw new IllegalArgumentException("random generators cannot be null");
		}

		if (keyDemandPolicy == null) {
			throw new IllegalArgumentException("keyDemandPolicy cannot be null");
		}

		validateProbability(protectedRequestProbability);
		this.timingRandGenerator = timingRandGenerator;
		this.protectionRandGenerator = protectionRandGenerator;
		this.protectedRequestProbability = protectedRequestProbability;
		this.keyDemandPolicy = keyDemandPolicy;
		this.atualTimeHours = 0.0;
	}

	@Override
	public void scheduleNextRequest(EventMachine eventMachine,
			ArriveRequestForConexionListener listener) {
		if (arrivalBudget != null && !arrivalBudget.tryAcquire()) {
			return;
		}

		RequestForConnection request = new RequestForConnection();

		// Same stochastic timing equations used by the original SNetS generator.
		double arriveTime = timingRandGenerator.negexp(getArrivedRate());
		atualTimeHours += arriveTime;
		request.setTimeOfRequestHours(atualTimeHours);

		double holdTime = timingRandGenerator.negexp(getHoldRate());
		double finalizeTime = request.getTimeOfRequestHours() + holdTime;
		request.setTimeOfFinalizeHours(finalizeTime);

		request.setPair(getPair());
		request.setRequiredBitRate(getBandwidth());
		request.setRequestGenerator(this);

		RequestProtectionType protectionType = drawProtectionType();
		request.setProtectionType(protectionType);

		if (protectionType == RequestProtectionType.QKD_ONLY) request.setRequiredBitRate(0.0);
		if (protectionType == RequestProtectionType.QKD_PROTECTED
				|| protectionType == RequestProtectionType.QKD_ONLY) {
			request.setQkdKeyDemand(drawKeyDemand());
		}

		Event event = new Event(request, listener, atualTimeHours);
		eventMachine.insert(event);
	}

	/** Shares an arrival budget among the generators of one mesh (null = unlimited). */
	public void setArrivalBudget(QKDArrivalBudget arrivalBudget) {
		this.arrivalBudget = arrivalBudget;
	}

	/**
	 * Draws only the request class; independent of the traffic-timing stream.
	 * Kept public for deterministic verification without event scheduling.
	 */
	public RequestProtectionType drawProtectionType() {
		double u = protectionRandGenerator.nextDouble();
		if (u < qkdOnlyProbability) return RequestProtectionType.QKD_ONLY;
		return u < qkdOnlyProbability + protectedRequestProbability
				? RequestProtectionType.QKD_PROTECTED : RequestProtectionType.CLASSICAL;
	}

	/**
     * Draws the key-consumption class from the QKD-only random stream.
     */
	public QKDKeyDemand drawKeyDemand() {
		if (fixedKeyDemand != null) {
			// keep the QKD random stream aligned with the policy-driven case
			keyDemandPolicy.draw();

			return fixedKeyDemand;
		}

		if (keyDemandPolicy.isByBitRate()) {
			// Part 34: the demand follows the bit rate; the draw keeps the QKD random stream aligned
			keyDemandPolicy.draw();

			return keyDemandPolicy.forBitRate(getBandwidth());
		}

		return keyDemandPolicy.draw();
	}

	/** Part 23: fixed key demand of this generator (traffic file keyRateMbps), or null. */
	private QKDKeyDemand fixedKeyDemand;

	public void setFixedKeyRateMbps(Double keyRateMbps) {
		if (keyRateMbps == null) { fixedKeyDemand = null; return; }
		if (!(keyRateMbps > 0.0) || Double.isInfinite(keyRateMbps))
			throw new IllegalArgumentException("traffic keyRateMbps must be > 0");
		int bits = keyDemandPolicy.getKeySizeBits();

		// rate[bit/s] = keySizeBits / interval[s]  =>  interval[s] = bits / (rate[Mbit/s] * 1e6)
		fixedKeyDemand = new QKDKeyDemand(bits, bits / (keyRateMbps.doubleValue() * 1.0e6));
	}

	public QKDKeyDemand getFixedKeyDemand() { return fixedKeyDemand; }

	public QKDKeyDemandPolicy getKeyDemandPolicy() {
		return keyDemandPolicy;
	}

	public void setQkdOnlyProbability(double probability) {
		validateProbability(probability);
		if (probability + protectedRequestProbability > 1.0 + 1e-12)
			throw new IllegalArgumentException("qkdOnlyProbability + qkdProtectedProbability must be <= 1");
		this.qkdOnlyProbability = probability;
	}
	public double getQkdOnlyProbability() { return qkdOnlyProbability; }

	public double getProtectedRequestProbability() {
		return protectedRequestProbability;
	}

	private static void validateProbability(double probability) {
		if (!Double.isFinite(probability)
				|| probability < 0.0
				|| probability > 1.0) {
			throw new IllegalArgumentException(
					"QKD protected-request probability must be in [0,1]");
		}
	}
}
