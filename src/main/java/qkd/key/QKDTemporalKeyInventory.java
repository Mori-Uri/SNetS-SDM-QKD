package qkd.key;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import qkd.capacity.QKDChannelShare;
import qkd.capacity.QKDLinkCapacityReservation;
import qkd.capacity.QKDSharedChannel;
import qkd.network.QKDSpan;
import qkd.resource.QKDSession;
import request.RequestForConnection;
import simulationControl.parsers.SimulationConfig.QKDKeyInventoryConfig;

/**
 * Optional QKD secret-key ledger, independent for every trusted-node hop.
 * All quantities are in bits, bits/s and seconds. Every key renewal is an
 * indivisible end-to-end operation: either EVERY hop provides a complete key,
 * or no hop is debited. Keys never pass unchanged through trusted nodes.
 *
 * <p>For up to {@value #EXACT_RENEWAL_LIMIT} renewals per interval, renewals
 * are processed chronologically, with physical generation and storage caps
 * applied between consecutive events. For larger intervals, events are counted
 * arithmetically and grouped into bounded time slices; this avoids billions of
 * microsecond-sized queue events, but the precise outage time and arbitration
 * within each slice are approximate. Counters expose when this occurs.</p>
 *
 * <p>The SKR of each active QCh is treated as a piecewise constant input
 * between consecutive network events, NOT a proof of steady QKD production.</p>
 */
public final class QKDTemporalKeyInventory implements Serializable {
    private static final long serialVersionUID = 2L;
    private static final double EPS = 1e-7;
    private static final long EXACT_RENEWAL_LIMIT = 100000L;
    private static final int MAX_BATCH_SLICES = 256;

    private final double capacityBits, initialBits;
    private final boolean undirected;
    private double lastSeconds;
    private final Map<String, Bucket> buckets = new LinkedHashMap<String, Bucket>();
    private final Map<RequestForConnection, Consumer> consumers =
            new IdentityHashMap<RequestForConnection, Consumer>();
    private final List<Consumer> orderedConsumers = new ArrayList<Consumer>();
    private long nextConsumerId;

    private double totalGeneratedBits, totalConsumedBits, totalDiscardedBits, totalUnservedBits;
    private long exhaustionEvents, rejectedAdmissions, servedRenewals, unservedRenewals;
    private long batchedWindows, approximatedRenewals;

    private static final class Bucket implements Serializable {
        private static final long serialVersionUID = 1L;
        double bits, updatedAt;
        Bucket(double initial, double time) { bits = initial; updatedAt = time; }
    }
    private static final class Consumer implements Serializable {
        private static final long serialVersionUID = 1L;
        final long id;
        final List<String> hops;
        final double sizeBits, intervalSeconds, openedAt;
        double nextEventSeconds;
        boolean exhausted, recordedAdmission;
        long renewalsProcessed;
        Consumer(long id, List<String> hops, double bits, double interval, double now) {
            this.id = id;
            this.hops = hops;
            this.sizeBits = bits;
            this.intervalSeconds = interval;
            this.openedAt = now;
            this.nextEventSeconds = now + interval;
            this.recordedAdmission = true;
        }
    }

    public QKDTemporalKeyInventory(QKDKeyInventoryConfig config, boolean undirected) {
        if (config == null || !config.isStock())
            throw new IllegalArgumentException("QKD inventory requires temporal stock mode");
        config.validate();
        capacityBits = config.getCapacityBits();
        initialBits = config.getInitialBits();
        this.undirected = undirected;
    }
    public String key(String source, String destination, int index, double lengthKm) {
        if (source == null || destination == null || index < 0 || !Double.isFinite(lengthKm))
            throw new IllegalArgumentException("invalid QKDSpan identity");
        String a = source, b = destination;
        if (undirected && a.compareTo(b) > 0) { a = destination; b = source; }
        return (undirected ? "U|" : "D|") + a + "|" + b + "|" + index + "|"
                + String.format(java.util.Locale.ROOT, "%.9f", lengthKm);
    }
    public String key(QKDSpan span) {
        return key(span.getPhysicalLinkSource(), span.getPhysicalLinkDestination(),
                span.getIndex(), span.getLengthKm());
    }
    private Bucket bucket(String key) {
        Bucket b = buckets.get(key);
        if (b == null) { b = new Bucket(initialBits, lastSeconds); buckets.put(key, b); }
        return b;
    }
    public synchronized Set<String> hops(QKDSession session) {
        if (session == null || !session.isSharedCapacityManaged())
            throw new IllegalArgumentException("stock mode requires managed per-hop QCh allocations");
        Set<String> keys = new LinkedHashSet<String>();
        for (QKDLinkCapacityReservation reservation : session.getCapacityReservations()) {
            for (QKDChannelShare share : reservation.getShares()) {
                for (QKDSpan span : share.getChannel().getEvaluationPlan().getSpans())
                    keys.add(key(span));
            }
        }
        if (keys.isEmpty()) throw new IllegalArgumentException("empty QKD trusted-node path");
        return keys;
    }
    private boolean hasBalance(List<String> hops, double bits) {
        for (String hop : hops) if (bucket(hop).bits + EPS < bits) return false;
        return true;
    }
    public synchronized boolean canOpen(RequestForConnection request, QKDSession session) {
        if (request == null || request.getQkdKeyDemand() == null || consumers.containsKey(request))
            return false;
        boolean available = hasBalance(new ArrayList<String>(hops(session)),
                request.getQkdKeyDemand().getKeySizeBits());
        if (!available) rejectedAdmissions++;
        return available;
    }
    /** Admission commits the first complete key at every trusted-node hop. */
    public synchronized void open(RequestForConnection request, QKDSession session) {
        if (request == null || request.getQkdKeyDemand() == null || consumers.containsKey(request))
            throw new IllegalArgumentException("invalid or duplicate QKD key consumer");
        List<String> path = new ArrayList<String>(hops(session));
        QKDKeyDemand demand = request.getQkdKeyDemand();
        if (!hasBalance(path, demand.getKeySizeBits())) {
            rejectedAdmissions++;
            throw new IllegalStateException("insufficient full QKD key on every hop");
        }
        double bits = demand.getKeySizeBits();
        for (String hop : path) bucket(hop).bits -= bits;
        totalConsumedBits += bits * path.size();
        Consumer c = new Consumer(++nextConsumerId, path, bits,
                demand.getUpdateIntervalSeconds(), lastSeconds);
        consumers.put(request, c);
        orderedConsumers.add(c);
    }
    /** Only valid before the clock advances past the admission transaction. */
    public synchronized void rollbackOpen(RequestForConnection request) {
        Consumer c = consumers.get(request);
        if (c == null) return;
        if (c.renewalsProcessed != 0L || lastSeconds > c.openedAt)
            throw new IllegalStateException("cannot roll back an admission after key renewals/time advanced");
        consumers.remove(request);
        orderedConsumers.remove(c);
        for (String hop : c.hops) bucket(hop).bits += c.sizeBits;
        if (c.recordedAdmission) totalConsumedBits -= c.sizeBits * c.hops.size();
    }
    /** Call after advancing the ledger to the departure instant. */
    public synchronized void close(RequestForConnection request) {
        Consumer c = consumers.remove(request);
        if (c != null) orderedConsumers.remove(c);
    }

    public synchronized void advance(double nowSeconds, List<QKDSharedChannel> activeChannels) {
        if (activeChannels == null) throw new IllegalArgumentException("null QCh list");
        Map<String, Double> generation = new HashMap<String, Double>();
        for (QKDSharedChannel channel : activeChannels) {
            double rate = channel.getCapacityBps();
            if (!Double.isFinite(rate) || rate < 0.0)
                throw new IllegalStateException("nonphysical QCh SKR in key inventory");
            for (QKDSpan span : channel.getEvaluationPlan().getSpans()) {
                String hop = key(span);
                Double old = generation.get(hop);
                generation.put(hop, (old == null ? 0.0 : old.doubleValue()) + rate);
            }
        }
        advanceRates(nowSeconds, generation);
    }
    /** Independent fixed-SKR input for deterministic physical and ledger tests. */
    public synchronized void advanceRates(double nowSeconds, Map<String, Double> generationRates) {
        if (!Double.isFinite(nowSeconds) || nowSeconds < lastSeconds)
            throw new IllegalArgumentException("QKD event clock moved backwards");
        if (generationRates == null) throw new IllegalArgumentException("null generation rate map");
        for (Map.Entry<String, Double> e : generationRates.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || !Double.isFinite(e.getValue()) || e.getValue() < 0.0)
                throw new IllegalArgumentException("invalid per-QKDSpan secret-key rate");
            bucket(e.getKey());
        }
        long due = 0L;
        for (Consumer c : orderedConsumers) {
            long n = due(c, nowSeconds);
            if (n > Long.MAX_VALUE - due) throw new IllegalStateException("key renewal counter overflow");
            due += n;
        }
        if (due <= EXACT_RENEWAL_LIMIT) advanceExact(nowSeconds, generationRates);
        else if (!advanceSingleConsumerSustainable(nowSeconds, generationRates, due))
            advanceBatched(nowSeconds, generationRates, due);
        lastSeconds = nowSeconds;
    }
    private static long due(Consumer c, double end) {
        // Relative tolerance avoids treating micro/nanosecond renewals as due
        // merely because the global simulation clock uses seconds.
        double tolerance = Math.max(8.0 * Math.ulp(end), 1e-12 * c.intervalSeconds);
        if (end + tolerance < c.nextEventSeconds) return 0L;
        double cycles = (end - c.nextEventSeconds) / c.intervalSeconds;
        if (!Double.isFinite(cycles) || cycles > Long.MAX_VALUE - 2.0)
            throw new IllegalStateException("unrepresentable key renewal count");
        return Math.max(1L, (long)Math.floor(cycles + 1e-10) + 1L);
    }
    private void accrue(String hop, double time, Map<String, Double> rates) {
        Bucket b = bucket(hop);
        double delta = time - b.updatedAt;
        if (delta < 0.0) throw new IllegalStateException("nonmonotonic stock accrual");
        if (delta == 0.0) return;
        Double rv = rates.get(hop);
        double rate = rv == null ? 0.0 : rv.doubleValue();
        double generated = rate * delta;
        if (!Double.isFinite(generated) || !Double.isFinite(totalGeneratedBits + generated))
            throw new IllegalStateException("secret-key generation overflow");
        totalGeneratedBits += generated;
        double available = b.bits + generated;
        if (available > capacityBits) {
            totalDiscardedBits += available - capacityBits;
            available = capacityBits;
        }
        b.bits = available;
        b.updatedAt = time;
    }
    private void accrueAll(double end, Map<String, Double> rates) {
        for (String hop : buckets.keySet()) accrue(hop, end, rates);
    }
    private void delivered(Consumer c, long n) {
        double bits = n * c.sizeBits;
        for (String hop : c.hops) {
            Bucket b = bucket(hop);
            b.bits -= bits;
            if (b.bits < 0.0 && b.bits >= -EPS) b.bits = 0.0;
            if (b.bits < -EPS) throw new IllegalStateException("negative stock after atomic renewal");
        }
        totalConsumedBits += bits * c.hops.size();
        servedRenewals += n;
    }
    private void failed(Consumer c, long n) {
        c.exhausted = true;
        exhaustionEvents += n;
        unservedRenewals += n;
        totalUnservedBits += n * c.sizeBits * c.hops.size();
    }
    private void advanceExact(double now, Map<String, Double> rates) {
        PriorityQueue<Consumer> queue = new PriorityQueue<Consumer>(11, new Comparator<Consumer>() {
            public int compare(Consumer a, Consumer b) {
                int order = Double.compare(a.nextEventSeconds, b.nextEventSeconds);
                return order != 0 ? order : Long.compare(a.id, b.id);
            }
        });
        for (Consumer c : orderedConsumers) if (due(c, now) > 0L) queue.add(c);
        while (!queue.isEmpty()) {
            Consumer c = queue.poll();
            double event = Math.min(now, c.nextEventSeconds);
            for (String hop : c.hops) accrue(hop, event, rates);
            if (hasBalance(c.hops, c.sizeBits)) delivered(c, 1L);
            else failed(c, 1L);
            c.renewalsProcessed++;
            c.nextEventSeconds = c.openedAt + (c.renewalsProcessed + 1.0) * c.intervalSeconds;
            if (due(c, now) > 0L) queue.add(c);
        }
        accrueAll(now, rates);
    }
    /**
     * Exact O(hops) shortcut for one consumer whose physical generation in
     * EVERY renewal interval is enough to refill one entire key. This avoids
     * millions of microsecond events and preserves storage-capacity losses.
     * For shared/undersupplied routes use chronological or documented batch
     * processing; never silently interpret an incomplete key as delivered.
     */
    private boolean advanceSingleConsumerSustainable(double now,
            Map<String, Double> rates, long events) {
        if (orderedConsumers.size() != 1 || events == 0L) return false;
        Consumer c = orderedConsumers.get(0);
        if (capacityBits + EPS < c.sizeBits) return false;
        double first = c.nextEventSeconds;
        for (String hop : c.hops) {
            Double value = rates.get(hop);
            double rate = value == null ? 0.0 : value.doubleValue();
            Bucket b = bucket(hop);
            if (rate * c.intervalSeconds + EPS < c.sizeBits ||
                    Math.min(capacityBits, b.bits + rate * (first - b.updatedAt)) + EPS < c.sizeBits)
                return false;
        }
        // All hops have proven sufficient at the first and every later event.
        for (String hop : c.hops) accrue(hop, first, rates);
        delivered(c, 1L);
        if (events > 1L) {
            double last = c.openedAt + (c.renewalsProcessed + (double)events) * c.intervalSeconds;
            for (String hop : c.hops) {
                Bucket b = bucket(hop);
                Double v = rates.get(hop);
                double produced = v == null ? 0.0 : v.doubleValue() * (last - first);
                double consumed = (events - 1L) * c.sizeBits;
                double ending = Math.min(capacityBits - c.sizeBits,
                        b.bits + produced - consumed);
                if (!Double.isFinite(produced) || ending < -EPS)
                    throw new IllegalStateException("invalid sustainable renewal shortcut");
                totalGeneratedBits += produced;
                totalDiscardedBits += Math.max(0.0, b.bits + produced - consumed - ending);
                b.bits = Math.max(0.0, ending);
                b.updatedAt = last;
            }
            // The individual balances and discarded bits were already updated.
            totalConsumedBits += (events - 1L) * c.sizeBits * c.hops.size();
            servedRenewals += events - 1L;
        }
        c.renewalsProcessed += events;
        c.nextEventSeconds = c.openedAt + (c.renewalsProcessed + 1.0) * c.intervalSeconds;
        accrueAll(now, rates);
        return true;
    }

    /** Bounded-cost approximation, counted separately in metrics and logs. */
    private void advanceBatched(double now, Map<String, Double> rates, long dueEvents) {
        batchedWindows++;
        approximatedRenewals += dueEvents;
        double start = lastSeconds;
        int slices = MAX_BATCH_SLICES;
        for (int step = 1; step <= slices; step++) {
            double end = start + (now - start) * step / slices;
            accrueAll(end, rates);
            List<Consumer> sorted = new ArrayList<Consumer>(orderedConsumers);
            Collections.sort(sorted, new Comparator<Consumer>() {
                public int compare(Consumer a, Consumer b) {
                    int order = Double.compare(a.nextEventSeconds, b.nextEventSeconds);
                    return order != 0 ? order : Long.compare(a.id, b.id);
                }
            });
            for (Consumer c : sorted) {
                long count = due(c, end);
                if (count == 0L) continue;
                long possible = count;
                for (String hop : c.hops)
                    possible = Math.min(possible, (long)Math.floor((bucket(hop).bits + EPS) / c.sizeBits));
                if (possible > 0L) delivered(c, possible);
                if (possible < count) failed(c, count - possible);
                c.renewalsProcessed += count;
                c.nextEventSeconds = c.openedAt + (c.renewalsProcessed + 1.0) * c.intervalSeconds;
            }
        }
    }
    public synchronized double getBalanceBits(String key) { return bucket(key).bits; }
    public synchronized boolean isExhausted(RequestForConnection request) {
        Consumer c = consumers.get(request);
        return c != null && c.exhausted;
    }
    public synchronized long getExhaustionEvents() { return exhaustionEvents; }
    public synchronized long getRejectedAdmissions() { return rejectedAdmissions; }
    public synchronized long getServedRenewals() { return servedRenewals; }
    public synchronized long getUnservedRenewals() { return unservedRenewals; }
    public synchronized long getBatchedWindows() { return batchedWindows; }
    public synchronized long getApproximatedRenewals() { return approximatedRenewals; }
    public synchronized double getGeneratedBits() { return totalGeneratedBits; }
    public synchronized double getConsumedBits() { return totalConsumedBits; }
    public synchronized double getDiscardedBits() { return totalDiscardedBits; }
    public synchronized double getUnservedBits() { return totalUnservedBits; }
    public synchronized double getTotalBalanceBits() {
        double total = 0.0;
        for (Bucket b : buckets.values()) total += b.bits;
        return total;
    }
    public synchronized Map<String, Double> balances() {
        Map<String, Double> out = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Bucket> e : buckets.entrySet()) out.put(e.getKey(), e.getValue().bits);
        return Collections.unmodifiableMap(out);
    }
    public synchronized int getActiveConsumerCount() { return consumers.size(); }
    public synchronized double getCurrentSeconds() { return lastSeconds; }
    /** Keep actual balances and active consumers after the statistics warm-up. */
    public synchronized void resetStatistics() {
        totalGeneratedBits = totalConsumedBits = totalDiscardedBits = totalUnservedBits = 0.0;
        exhaustionEvents = rejectedAdmissions = servedRenewals = unservedRenewals = 0L;
        batchedWindows = approximatedRenewals = 0L;
        for (Consumer c : orderedConsumers) c.recordedAdmission = false;
    }
}
