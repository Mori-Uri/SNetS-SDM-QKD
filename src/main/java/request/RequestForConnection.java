package request;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import network.Circuit;
import network.Pair;
import network.RequestGenerator;
import qkd.key.QKDKeyDemand;
import qkd.traffic.RequestProtectionType;

/**
 * Connection request handled by the SNetS event engine.
 *
 * <p>Part 5 added the protection classification and Part 11 adds an optional
 * QKD key-demand object. Existing SNetS code remains compatible because
 * requests default to {@link RequestProtectionType#CLASSICAL} and therefore
 * carry no QKD key demand.</p>
 */
public class RequestForConnection implements Serializable {

    private static final long serialVersionUID = 1L;

    protected Pair pair;
    protected double timeOfRequestHours;
    protected double timeOfFinalizeHours;
    protected double requiredBitRate;
    protected RequestGenerator rg;
    protected List<Circuit> circuit;

    /** CLASSICAL for legacy requests, QKD_PROTECTED for the selected fraction. */
    protected RequestProtectionType protectionType;

    /**
     * Key-consumption requirement of a QKD-protected request.
     * Classical requests keep this field null.
     */
    protected QKDKeyDemand qkdKeyDemand;

    /** Parte 54: stamp of the last GroomingStatistics observation that counted this request (distinct count). */
    private transient int countStamp;

    /** Parte 54: true the first time this request is seen with {@code stamp}. */
    public boolean markCounted(int stamp) {
        if (countStamp == stamp) {
            return false;
        }
        countStamp = stamp;
        return true;
    }

    public RequestForConnection() {
        circuit = new ArrayList<Circuit>();
        protectionType = RequestProtectionType.CLASSICAL;
        qkdKeyDemand = null;
    }

    public Pair getPair() {
        return pair;
    }

    public void setPair(Pair pair) {
        this.pair = pair;
    }

    public double getTimeOfRequestHours() {
        return timeOfRequestHours;
    }

    public void setTimeOfRequestHours(double timeOfRequestHours) {
        this.timeOfRequestHours = timeOfRequestHours;
    }

    public double getTimeOfFinalizeHours() {
        return timeOfFinalizeHours;
    }

    public void setTimeOfFinalizeHours(double timeOfFinalizeHours) {
        this.timeOfFinalizeHours = timeOfFinalizeHours;
    }

    public double getRequiredBitRate() {
        return requiredBitRate;
    }

    /**
     * Parte 57 (desempenho): number of calls of {@link #setRequiredBitRate} in this JVM. A circuit keeps the sum of
     * the bit rates of its requests only while this counter and its list of requests do not change (see
     * {@code Circuit.getRequiredBitRate}). The bit rate is set when a request is created, before it joins a circuit,
     * so the sum is kept for the whole life of the circuit in practice.
     */
    private static final java.util.concurrent.atomic.AtomicLong BIT_RATE_CHANGES = new java.util.concurrent.atomic.AtomicLong();

    /**
     * Parte 59: true once this request joined the list of requests of a circuit (TrackedRequestList). Only then a new
     * bit rate can change the sum kept by a circuit. The generators set the bit rate of a new request before it
     * joins a circuit, so those calls no longer invalidate the sums of all circuits.
     */
    private boolean inTrackedList;

    /** Parte 59: called by TrackedRequestList when this request joins a circuit. */
    public void markInTrackedList() {
        inTrackedList = true;
    }

    /** Parte 57: see {@link #BIT_RATE_CHANGES}. */
    public static long bitRateChanges() {
        return BIT_RATE_CHANGES.get();
    }

    public void setRequiredBitRate(double requiredBitRate) {
        if (inTrackedList) BIT_RATE_CHANGES.incrementAndGet(); // Parte 57/59: invalidates the bit rate kept by the circuits
        this.requiredBitRate = requiredBitRate;
    }

    public List<Circuit> getCircuits() {
        return circuit;
    }

    public void setCircuit(List<Circuit> circuit) {
        this.circuit = circuit;
    }

    public RequestGenerator getRequestGenerator() {
        return rg;
    }

    public void setRequestGenerator(RequestGenerator rg) {
        this.rg = rg;
    }

    public RequestProtectionType getProtectionType() {
        return protectionType;
    }

    public void setProtectionType(RequestProtectionType protectionType) {
        this.protectionType = protectionType == null
                ? RequestProtectionType.CLASSICAL
                : protectionType;
        if (this.protectionType == RequestProtectionType.CLASSICAL) {
            this.qkdKeyDemand = null;
        }
    }

    /** Convenience method for later ControlPlane integration. */
    public boolean isQkdProtected() {
        return protectionType == RequestProtectionType.QKD_PROTECTED;
    }

    public QKDKeyDemand getQkdKeyDemand() {
        return qkdKeyDemand;
    }

    public void setQkdKeyDemand(QKDKeyDemand qkdKeyDemand) {
        if (qkdKeyDemand == null) {
            this.qkdKeyDemand = null;
            return;
        }
        if (!isQkdProtected()) {
            throw new IllegalStateException(
                    "QKD key demand can only be assigned to QKD-protected requests");
        }
        this.qkdKeyDemand = qkdKeyDemand;
    }

    public double getRequiredQkdKeyRateBps() {
        return qkdKeyDemand == null ? 0.0
                : qkdKeyDemand.getRequiredKeyRateBps();
    }

    public double getRequiredQkdKeyRateMbps() {
        return qkdKeyDemand == null ? 0.0
                : qkdKeyDemand.getRequiredKeyRateMbps();
    }
}
