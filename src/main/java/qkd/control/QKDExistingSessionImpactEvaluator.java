package qkd.control;

import java.util.ArrayList;
import java.util.List;

import network.Circuit;
import network.Core;
import network.Link;
import qkd.network.QKDMesh;
import qkd.physical.QKDPhysicalParameters;
import qkd.physical.QKDPhysicalLayer;
import qkd.physical.QKDRoutePhysicalResult;
import qkd.physical.QKDSpanPhysicalResult;
import qkd.resource.QKDChannelAllocation;
import qkd.capacity.QKDChannelShare;
import qkd.capacity.QKDLinkCapacityReservation;
import qkd.capacity.QKDLinkChannelPhysicalResult;
import qkd.capacity.QKDSharedChannel;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Evaluates the impact of a not-yet-allocated classical circuit on QKD
 * sessions that are already active.
 *
 * <p>The evaluation is deliberately non-destructive. The candidate circuit is
 * passed to the physical model as an additional hypothetical interferer; no
 * SNetS spectrum, circuit list or QKD session is modified during the check.</p>
 *
 * <p>This implements the QKD counterpart of the existing SNetS QoTO/XTO idea:
 * a new classical candidate is admissible only if the already-established QKD
 * sessions remain physically viable after the candidate is considered.</p>
 *
 * <p>Refer\u00eancias: [MORI26]; an\u00e1logo qu\u00e2ntico do teste de XT nos outros circuitos (XTO) de [LACERDA24] J. C. Lacerda Jr., A. V. T. Cartaxo, A. C. B. Soares, "Novel dynamic impairment-aware algorithm for modulation, core, and spectrum assignment in SDM-EONs", Opt. Switch. Netw. 51, 2024.</p>
 */
public final class QKDExistingSessionImpactEvaluator implements java.io.Serializable {

    private final QKDMesh mesh;
    private final QKDPhysicalLayer physicalEvaluator;
    private final double wavelengthNm;
    private final QKDPhysicalLayer linkChannelEvaluator;

    public QKDExistingSessionImpactEvaluator(QKDMesh mesh) {
        if (mesh == null) {
            throw new IllegalArgumentException("mesh cannot be null");
        }
        this.mesh = mesh;
        QKDPhysicalParameters parameters = mesh.getQkdPhysicalParameters();
        this.physicalEvaluator = new QKDPhysicalLayer(parameters);
        this.wavelengthNm = parameters.getAttenuationCenterNm();
        this.linkChannelEvaluator = this.physicalEvaluator;
    }

    /**
     * Performs a non-destructive what-if check against every active QKD
     * session. Unaffected sessions are retained in the result with their
     * current physical state so the result always represents a complete
     * registry snapshot.
     */
    public QKDExistingSessionImpactResult evaluateCandidate(
            Circuit candidate,
            QKDSessionRegistry registry) {

        if (candidate == null) {
            throw new IllegalArgumentException("candidate cannot be null");
        }
        if (registry == null) {
            throw new IllegalArgumentException("registry cannot be null");
        }

        final List<QKDSessionImpact> impacts =
                new ArrayList<QKDSessionImpact>();

        for (QKDActiveConnection connection : registry.snapshot()) {
            if (connection == null || !connection.isActive()) {
                continue;
            }

            final boolean affected = candidateCanAffect(candidate, connection);
            final QKDRoutePhysicalResult projected;

            if (!affected) {
                projected = connection.getPhysicalResult();
                impacts.add(new QKDSessionImpact(connection, false, projected));
            } else if (connection.getQkdSession().isSharedCapacityManaged()) {
                SharedProjection sp = projectSharedSession(connection, candidate);
                impacts.add(new QKDSessionImpact(connection, true,
                        sp.routeSnapshot, sp.viable, sp.projectedMinimumCapacityBps));
            } else {
                QKDSpanPhysicalResult criteriaSource =
                        connection.getPhysicalResult().getSpanResults().get(0);

                projected = physicalEvaluator
                        .evaluateAllocatedSessionWithAdditionalClassicalCircuit(
                                connection.getClassicalRoute(),
                                connection.getQkdSession().getRoutePlan(),
                                mesh.getPhysicalLayer(),
                                connection.getQkdSession(),
                                candidate,
                                wavelengthNm,
                                connection.getRequiredKeyRateBps(),
                                criteriaSource.getMaximumQber());
                impacts.add(new QKDSessionImpact(connection, true, projected));
            }
        }

        return new QKDExistingSessionImpactResult(impacts);
    }

    /**
     * Recomputes the stored physical state of all active QKD sessions from the
     * current committed SNetS network state. This is called after a classical
     * circuit is really allocated or released.
     *
     * @return number of active QKD sessions refreshed
     */
    public int refreshAllActiveSessions(QKDSessionRegistry registry) {
        return refreshActiveSessions(registry, null);
    }

    /**
     * Same as {@link #refreshAllActiveSessions(QKDSessionRegistry)}, but only
     * re-evaluates the sessions with a QKD channel on a physical link of the
     * classical circuit that was just allocated or released: the crosstalk on
     * a link depends only on the circuits of that link, so the stored state of
     * every other session is already up to date. Channels shared by several
     * sessions are evaluated once per call. The stored results are identical
     * to a full refresh; the return value still counts every active session
     * (logical refreshes), as before.
     *
     * @param changed the circuit that changed the network state, or null to
     *        refresh every session
     */
    public int refreshActiveSessions(QKDSessionRegistry registry, Circuit changed) {
        if (registry == null) {
            throw new IllegalArgumentException("registry cannot be null");
        }

        java.util.Set<Link> changedLinks = null;
        if (changed != null && changed.getRoute() != null && changed.getRoute().getLinkList() != null) {
            changedLinks = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Link, Boolean>());
            changedLinks.addAll(changed.getRoute().getLinkList());
        }
        java.util.Map<Long, QKDLinkChannelPhysicalResult> evaluated = new java.util.HashMap<Long, QKDLinkChannelPhysicalResult>();
        // Parte 54 (desempenho): see changeCanAffect. Only when the changed circuit is the only change since the last
        // refresh: no circuit in the network was changed (reallocation, spectrum expansion, power) and the core lists
        // changed exactly once per link of the changed circuit (its own allocation or release).
        final boolean filter = changedLinks != null && simulator.PerfChecks.ENABLED && refreshSynced
                && mesh.getQkdPhysicalParameters().getMultiband() == null
                && !network.PhysicalStateChanges.isUntracked()
                && lastRefreshMutations == network.PhysicalStateChanges.mutations()
                && network.PhysicalStateChanges.memberships() - lastRefreshMemberships
                        == changed.getRoute().getLinkList().size()
                && changed.getSpectrumAssigned() != null;
        int refreshed = 0;
        final Link[] changedArray = changedLinks == null ? null
                : changed.getRoute().getLinkList().toArray(new Link[0]);
        for (QKDActiveConnection connection : registry.snapshot()) {
            if (connection == null || !connection.isActive()) {
                continue;
            }
            if (changedLinks != null && !touchesChanged(connection, changedLinks, changedArray)) {
                refreshed++;
                continue;
            }
            final boolean skip = filter && !changeCanAffect(connection, changed);
            if (simulator.PerfChecks.STATS) {
                simulator.PerfChecks.count(skip ? simulator.PerfChecks.REFRESH_SKIP : simulator.PerfChecks.REFRESH_DONE);
            }
            if (skip) {
                if (!simulator.PerfChecks.VERIFY) {
                    refreshed++;
                    continue;
                }
                if (verifySkipped == null) {
                    verifySkipped = new ArrayList<QKDActiveConnection>();
                    verifyBefore = new java.util.IdentityHashMap<QKDActiveConnection, QKDRoutePhysicalResult>();
                }
                verifySkipped.add(connection);
                verifyBefore.put(connection, connection.getPhysicalResult());
            }

            if (connection.getQkdSession().isSharedCapacityManaged()) {
                refreshSharedSession(connection, evaluated);
            } else {
                QKDSpanPhysicalResult criteriaSource =
                        connection.getPhysicalResult().getSpanResults().get(0);
                QKDRoutePhysicalResult updated = physicalEvaluator.evaluateAllocatedSession(
                        connection.getClassicalRoute(),
                        connection.getQkdSession().getRoutePlan(),
                        mesh.getPhysicalLayer(),
                        connection.getQkdSession(),
                        wavelengthNm,
                        connection.getRequiredKeyRateBps(),
                        criteriaSource.getMaximumQber());
                connection.updatePhysicalResult(updated);
            }
            refreshed++;
        }
        if (verifySkipped != null && !verifySkipped.isEmpty()) {
            verifySkippedSessions();
        }
        lastRefreshMutations = network.PhysicalStateChanges.mutations();
        lastRefreshMemberships = network.PhysicalStateChanges.memberships();
        refreshSynced = true;
        return refreshed;
    }

    /*
     * Parte 54 (desempenho): without multiband, the classical noise in a QKD channel is only the intercore XT, and a
     * classical circuit adds XT to a channel only on a link where it uses a core adjacent to the QKD core and slots
     * that overlap the channel (QKDPhysicalLayer.evaluateInternal). When the circuit that was allocated or released
     * does not meet these conditions for any channel of a session, a new evaluation of that session would give the
     * same numbers, so the stored result is kept. This is used only when the circuit is the only change since the last
     * refresh (see lastRefreshMutations); with -Dsnets.verifyPerf=true the skipped sessions are evaluated anyway and
     * compared bit for bit.
     */
    private transient long lastRefreshMutations;
    private transient long lastRefreshMemberships;
    private transient boolean refreshSynced;
    private transient List<QKDActiveConnection> verifySkipped;

    private boolean changeCanAffect(QKDActiveConnection connection, Circuit changed) {
        final int core = qkd.metrics.BlockingDetailRecorder.rawCore(changed);
        final int[] band = changed.getSpectrumAssigned();
        for (QKDChannelAllocation allocation : connection.getQkdSession().getAllocations()) {
            if (channelCanBeAffected(allocation, changed, core, band)) return true;
        }
        if (connection.getQkdSession().isSharedCapacityManaged()) {
            for (QKDLinkCapacityReservation lr : connection.getQkdSession().getCapacityReservations()) {
                for (QKDChannelShare share : lr.getShares()) {
                    if (channelCanBeAffected(share.getChannel().getAllocation(), changed, core, band)) return true;
                }
            }
        }
        return false;
    }

    private static boolean channelCanBeAffected(QKDChannelAllocation allocation, Circuit changed, int core, int[] band) {
        if (allocation == null) return true;
        if (QKDPhysicalLayer.numberOfOverlappingSlots(allocation.getSpectrumBand(), band) <= 0) return false;
        final Link link = allocation.getLink();
        if (link == null || !routeContainsLinkByIdentity(changed, link)) return false;
        return isCoreAdjacentToQkdCore(link, allocation.getQuantumCoreId(), core);
    }

    /** Parte 54 (-Dsnets.verifyPerf=true): the skipped sessions were refreshed anyway; their results must not change. */
    private void verifySkippedSessions() {
        try {
            for (QKDActiveConnection connection : verifySkipped) {
                final QKDRoutePhysicalResult now = connection.getPhysicalResult();
                final QKDRoutePhysicalResult before = verifyBefore.get(connection);
                if (before == null) continue;
                if (before.getSpanResults().size() != now.getSpanResults().size()) {
                    throw new IllegalStateException("snets.verifyPerf: sessao QKD pulada mudou (numero de spans)");
                }
                for (int k = 0; k < now.getSpanResults().size(); k++) {
                    final QKDSpanPhysicalResult a = before.getSpanResults().get(k);
                    final QKDSpanPhysicalResult b = now.getSpanResults().get(k);
                    simulator.PerfChecks.same(a.getCrosstalkPowerW(), b.getCrosstalkPowerW(), "XT de sessao QKD pulada");
                    simulator.PerfChecks.same(a.getTotalBackgroundProbability(), b.getTotalBackgroundProbability(),
                            "p_b de sessao QKD pulada");
                    simulator.PerfChecks.same(a.getSecretKeyRateBps(), b.getSecretKeyRateBps(), "SKR de sessao QKD pulada");
                    simulator.PerfChecks.same(a.getQber(), b.getQber(), "QBER de sessao QKD pulada");
                }
            }
        } finally {
            verifySkipped.clear();
            verifyBefore.clear();
        }
    }

    private transient java.util.IdentityHashMap<QKDActiveConnection, QKDRoutePhysicalResult> verifyBefore;

    private SharedProjection projectSharedSession(QKDActiveConnection connection, Circuit candidate) {
        List<qkd.physical.QKDSpanPhysicalResult> representativeSpans =
                new ArrayList<qkd.physical.QKDSpanPhysicalResult>();
        boolean viable = true;
        double minProjectedCapacity = Double.POSITIVE_INFINITY;
        double maxAllowedQber = connection.getPhysicalResult().getSpanResults().get(0).getMaximumQber();
        for (QKDLinkCapacityReservation lr : connection.getQkdSession().getCapacityReservations()) {
            QKDLinkChannelPhysicalResult representative = null;
            Set<Long> seen = new LinkedHashSet<Long>();
            for (QKDChannelShare share : lr.getShares()) {
                QKDSharedChannel c = share.getChannel();
                if (!seen.add(c.getId())) continue;
                QKDLinkChannelPhysicalResult projected =
                        linkChannelEvaluator.evaluateLinkChannelWithAdditionalClassicalCircuit(
                                c.getLink(), c.getEvaluationPlan(), mesh.getPhysicalLayer(),
                                c.getAllocation(), c.wavelengthOr(wavelengthNm), candidate);
                minProjectedCapacity = Math.min(minProjectedCapacity, projected.getMinimumSecretKeyRateBps());
                if (projected.getMinimumSecretKeyRateBps() + 1e-6 < c.getUsedKeyRateBps()
                        || projected.getMaximumQber() > maxAllowedQber) viable = false;
                if (representative == null
                        || projected.getMinimumSecretKeyRateBps() < representative.getMinimumSecretKeyRateBps())
                    representative = projected;
            }
            if (representative != null) representativeSpans.addAll(representative.getSpans());
        }
        QKDRoutePhysicalResult snapshot = new QKDRoutePhysicalResult(
                connection.getQkdSession().getRoutePlan(), representativeSpans);
        return new SharedProjection(snapshot, viable,
                minProjectedCapacity == Double.POSITIVE_INFINITY ? 0.0 : minProjectedCapacity);
    }

    private static boolean touchesChanged(QKDActiveConnection connection, java.util.Set<Link> changedLinks,
            Link[] changedArray) {
        if (!simulator.PerfChecks.ENABLED) {
            return touches(connection, changedLinks);
        }
        final boolean fast = touchesFast(connection, changedArray);
        if (simulator.PerfChecks.VERIFY && fast != touches(connection, changedLinks)) {
            throw new IllegalStateException("snets.verifyPerf: enlaces da sessao QKD diferem");
        }
        return fast;
    }

    /** Parte 54: same answer as touches, from the links of the session kept in the connection (no hashing). */
    private static boolean touchesFast(QKDActiveConnection connection, Link[] links) {
        final Link[] mine = connection.getChannelLinks();
        for (int i = 0; i < mine.length; i++) {
            for (int j = 0; j < links.length; j++) {
                if (mine[i] == links[j]) return true;
            }
        }
        return false;
    }

    /** True when the session has a QKD channel on one of the given links. */
    private static boolean touches(QKDActiveConnection connection, java.util.Set<Link> links) {
        for (QKDChannelAllocation allocation : connection.getQkdSession().getAllocations()) {
            if (links.contains(allocation.getLink())) return true;
        }
        if (connection.getQkdSession().isSharedCapacityManaged()) {
            for (QKDLinkCapacityReservation lr : connection.getQkdSession().getCapacityReservations()) {
                for (QKDChannelShare share : lr.getShares()) {
                    if (links.contains(share.getChannel().getLink())) return true;
                }
            }
        }
        return false;
    }

    private void refreshSharedSession(QKDActiveConnection connection,
            java.util.Map<Long, QKDLinkChannelPhysicalResult> evaluated) {
        List<qkd.physical.QKDSpanPhysicalResult> representativeSpans =
                new ArrayList<qkd.physical.QKDSpanPhysicalResult>();
        for (QKDLinkCapacityReservation lr : connection.getQkdSession().getCapacityReservations()) {
            QKDLinkChannelPhysicalResult representative = null;
            Set<Long> seen = new LinkedHashSet<Long>();
            for (QKDChannelShare share : lr.getShares()) {
                QKDSharedChannel c = share.getChannel();
                if (!seen.add(c.getId())) continue;
                QKDLinkChannelPhysicalResult updated = evaluated.get(c.getId());
                if (updated == null) {
                    updated = linkChannelEvaluator.evaluateLinkChannel(
                            c.getLink(), c.getEvaluationPlan(), mesh.getPhysicalLayer(),
                            c.getAllocation(), c.wavelengthOr(wavelengthNm));
                    c.setPhysicalResult(updated);
                    evaluated.put(c.getId(), updated);
                }
                if (representative == null
                        || updated.getMinimumSecretKeyRateBps() < representative.getMinimumSecretKeyRateBps())
                    representative = updated;
            }
            if (representative != null) representativeSpans.addAll(representative.getSpans());
        }
        connection.updatePhysicalResult(new QKDRoutePhysicalResult(
                connection.getQkdSession().getRoutePlan(), representativeSpans));
    }

    private static final class SharedProjection {
        final QKDRoutePhysicalResult routeSnapshot; final boolean viable; final double projectedMinimumCapacityBps;
        SharedProjection(QKDRoutePhysicalResult r, boolean v, double p) { routeSnapshot=r; viable=v; projectedMinimumCapacityBps=p; }
    }

    /**
     * Fast structural filter used before the more expensive physical
     * re-evaluation. A candidate can affect a QKD session only when it shares a
     * physical link, uses a core adjacent to the reserved QKD core on that
     * link, and overlaps the QKD spectrum allocated on the same link.
     */
    public boolean candidateCanAffect(
            Circuit candidate,
            QKDActiveConnection connection) {

        if (candidate == null || connection == null
                || candidate.getRoute() == null
                || candidate.getRoute().getLinkList() == null
                || candidate.getSpectrumAssigned() == null) {
            return false;
        }

        for (QKDChannelAllocation allocation
                : connection.getQkdSession().getAllocations()) {

            // Parte 54 (desempenho): the cheapest test first (spectral overlap); the three tests are the same as
            // before and all must hold, so the answer does not change.
            if (QKDPhysicalLayer.numberOfOverlappingSlots(
                    allocation.getSpectrumBand(),
                    candidate.getSpectrumAssigned()) <= 0) {
                continue;
            }

            Link qkdLink = allocation.getLink();
            if (!routeContainsLinkByIdentity(candidate, qkdLink)) {
                continue;
            }

            if (!isCoreAdjacentToQkdCore(
                    qkdLink,
                    allocation.getQuantumCoreId(),
                    qkd.metrics.BlockingDetailRecorder.rawCore(candidate))) {
                continue;
            }

            if (QKDPhysicalLayer.numberOfOverlappingSlots(
                    allocation.getSpectrumBand(),
                    candidate.getSpectrumAssigned()) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean routeContainsLinkByIdentity(
            Circuit candidate,
            Link link) {
        for (Link routeLink : candidate.getRoute().getLinkList()) {
            if (routeLink == link) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCoreAdjacentToQkdCore(
            Link link,
            int qkdCoreId,
            int candidateCoreId) {
        List<Core> adjacent = link.getAdjacentCores(qkdCoreId);
        if (adjacent == null) {
            return false;
        }
        for (Core core : adjacent) {
            if (core != null && core.getId() == candidateCoreId) {
                return true;
            }
        }
        return false;
    }
}
