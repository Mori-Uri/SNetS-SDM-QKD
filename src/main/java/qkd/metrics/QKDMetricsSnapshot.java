package qkd.metrics;

import java.io.Serializable;

/**
 * End-of-replication QKD statistics used by the general experiment CSV.
 *
 * <p>Refer\u00eancias: [JAIN91] R. Jain, "The Art of Computer Systems Performance Analysis", Wiley, 1991 (lei de Little, m\u00e9dias por chegada / PASTA, intervalos de confian\u00e7a); [DIBAJ24] (Q-TRx, n\u00f3s confi\u00e1veis).</p>
 */
public final class QKDMetricsSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;
    public int loadPoint;
    public int replication;
    public long totalRequestAttempts, protectedAttempts, accepted, rejected, released;
    public long existingSessionImpactChecks, classicalRejectionsByQkdImpact, activeRefreshes;
    public long precommitAdmissionChecks, precommitAdmissionRejections;
    public long activatedQchs, deactivatedQchs, tdmReuses, capacityFailures;
    public long peakActiveQchs, peakQTrxPairs;
    public double totalDemandMbps;
    public double meanAdmissionRawSkrMbps;
    public double minimumAdmissionRawSkrMbps;
    public double meanAdmissionQber;
    public double maximumAdmissionQber;
    public double maximumAdmissionPxtW;
    /** Parte 44: multi-band layer on (qkdPhysicalLayer.multiband); the Raman fields below are 0 otherwise. */
    public boolean multiband;
    /** Parte 44: SpRS background probability per gate on the spans of the admitted sessions (mean and max). */
    public double meanAdmissionRamanProbability, maximumAdmissionRamanProbability;
    /** Parte 44: largest SpRS power at a QCh receiver among the admitted sessions (W). */
    public double maximumAdmissionRamanPowerW;
    /** Parte 45: NLI on the QCh (only with multiband.nliModel = gn). */
    public boolean nli;
    public double meanAdmissionNliProbability, maximumAdmissionNliProbability;
    public double acceptanceRatio = Double.NaN;
    public double blockingProbability = Double.NaN;
    /** Arrival-averaged (PASTA) occupancy after warm-up. */
    public double meanActiveQchs, meanActiveQTrxPairs, meanActiveSessions;
    public double qtndKm;
    public double warmupArrivals;
    /** Part 23: QKD blocking by cause (counts), alternative routes, admitted-session shape. */
    public long blockedClassical, blockedSpectrum, blockedSkr, blockedPich, blockedOther;
    public long alternativeRouteAccepted;
    public double meanQTrxPerSession = Double.NaN, meanHopsPerSession = Double.NaN, meanRouteKmPerSession = Double.NaN;
    public long routeCandidateBandsEvaluated;
    public int plannedTrustedNodes, plannedQkdSpans;
    /** "src|dst" -> {QKD-protected attempts, QKD-protected blocked}. */
    /** Part 26: QTx/QRx and shared PICh statistics. */
    public long blockedQtrx, qEquipmentFailures;
    public double meanOpenPichs = Double.NaN;
    public long peakOpenPichs = -1, pichOpened;
    /** Parte 65: physical PICh (classical channel) and its failures by reason. */
    public boolean pichPhysical;
    public long pichFailSpectrum, pichFailQot, pichFailClassical, pichFailQkd;
    public double pichMeanSlots = Double.NaN;
    public double pichMeanOpenSlots = Double.NaN;
    public final java.util.Map<String, Double> pichOpenByModulation = new java.util.TreeMap<String, Double>();
    /** node -> {mean QTx, mean QRx, peak QTx, peak QRx, QTx limit, QRx limit, network node 1/0}. */
    public final java.util.Map<String, double[]> qEquipment = new java.util.TreeMap<String, double[]>();
    public final java.util.Map<String, long[]> perPair = new java.util.TreeMap<String, long[]>();
}
