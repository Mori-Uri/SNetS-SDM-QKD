package qkd.control;

import java.util.ArrayList;
import java.util.List;

import gprmcsa.modulation.Modulation;
import network.Circuit;
import network.Core;
import network.Crosstalk;
import network.Link;
import network.PhysicalLayer;
import qkd.capacity.QKDPichCircuit;
import qkd.capacity.QKDPichPool;
import qkd.network.QKDMesh;

/**
 * Parte 65 (pedido do coorientador): admission of a new PICh as a classical channel. The PICh is already in the
 * circuit list of its core when check() runs, and the pending classical circuit of the QKD request (not yet
 * allocated) is added as a hypothetical interferer, as in the QoTO/XTO checks of SNetS. The same switches of the
 * physical layer are followed (activeQoT, activeQoTForOther, activeXT, activeXTForOther, XT model).
 *
 * <ol>
 *   <li>QoT of the PICh: SNR of every section (from one trusted node to the next) above the threshold of its
 *       modulation; XT of every section below the XT threshold (XT separate).</li>
 *   <li>Interference on the classical channels: the circuits of the core of the PICh on its link keep their QoT,
 *       the circuits of the adjacent cores that overlap the PICh keep their XT (other PIChs included), and so does
 *       the pending classical circuit.</li>
 *   <li>Interference on the QChs (when classical noise reaches the QKD core): every QCh of the link that the PICh
 *       reaches keeps the key rate reserved on it, the reservations of the pending session included.</li>
 * </ol>
 */
public final class QKDPichPhysicalCheck implements QKDPichPool.Physics {

    private static final long serialVersionUID = 1L;

    private final QKDMesh mesh;
    private final QKDProvisioningService provisioning;
    private final boolean qkdCoupled;

    public QKDPichPhysicalCheck(QKDMesh mesh, QKDProvisioningService provisioning, boolean qkdCoupled) {
        this.mesh = mesh;
        this.provisioning = provisioning;
        this.qkdCoupled = qkdCoupled;
    }

    private transient List<Modulation> modulations;

    /**
     * Modulations tried for a new PICh: all of the network (most efficient first, as ModulationSelectionByQoTv2), or
     * only network.qkd.pichModulation when it is given.
     */
    @Override
    public List<Modulation> modulations() {
        if (modulations == null) {
            final String fixed = mesh.getQkdNetworkParameters().getPichModulation();
            if (fixed == null || fixed.isEmpty()) {
                modulations = mesh.getAvaliableModulations();
            } else {
                for (Modulation m : mesh.getAvaliableModulations()) {
                    if (m.getName().equalsIgnoreCase(fixed)) {
                        modulations = java.util.Collections.singletonList(m);
                        break;
                    }
                }
                if (modulations == null) {
                    final StringBuilder names = new StringBuilder();
                    for (Modulation m : mesh.getAvaliableModulations()) names.append(names.length() == 0 ? "" : ", ").append(m.getName());
                    throw new IllegalArgumentException("network.qkd.pichModulation \"" + fixed
                            + "\" is not a modulation of the network (" + names + ")");
                }
            }
        }
        return modulations;
    }

    @Override
    public int check(QKDPichCircuit pich, Circuit candidate) {
        final PhysicalLayer pl = mesh.getPhysicalLayer();
        final Crosstalk xtModel = pl.getCrosstalk();
        final boolean xtSeparate = pl.getXTModel() == PhysicalLayer.XT_SEPARATE;
        final Link link = pich.getLink();
        final int core = pich.getIndexCore();
        final Modulation mod = pich.getModulation();
        final Circuit test = candidate != null && candidate.getRoute() != null && candidate.getSpectrumAssigned() != null
                ? candidate : null;
        final boolean candidateHere = test != null && test.getRoute().containThisLink(link);

        // 1. QoT of the PICh
        if (pl.isActiveQoT()) {
            final double snr = pl.computeSNRSegment(pich, pich.getRoute(), 0, 1, mod, core, pich.getSpectrumAssigned(), test, true);
            pich.setSNRlinear(snr);
            if (!pl.isAdmissible(mod, snr)) return QKDPichPool.QOT;
        }
        if (pl.isActiveXT() && xtSeparate) {
            final double xt = xtModel.calculateCrosstalk(pich, test, true);
            pich.setXt(xt);
            if (!xtModel.isAdmissible(pich, xt)) return QKDPichPool.QOT;
        }

        // 2. the classical channels keep their QoT and XT
        if (pl.isActiveQoTForOther()) {
            for (Circuit c : new ArrayList<Circuit>(link.getCore(core).getSortedCircuitList())) {
                if (c == pich) continue;
                if (!qotOk(pl, c, test)) return QKDPichPool.CLASSICAL;
            }
            if (candidateHere && !qotOk(pl, test, null)) return QKDPichPool.CLASSICAL;
        }
        if (pl.isActiveXTForOther() && xtSeparate) {
            for (Core adj : link.getAdjacentCores(core)) {
                for (Circuit c : new ArrayList<Circuit>(adj.getSortedCircuitList())) {
                    if (!xtModel.isIntersection(pich.getSpectrumAssigned(), c.getSpectrumAssigned())) continue;
                    if (xtModel.calculateCrosstalk(c, test, true) > c.getModulation().getXTthreshold()) {
                        return QKDPichPool.CLASSICAL;
                    }
                }
            }
            if (candidateHere && test.getSpectrumAssigned() != null
                    && xtModel.isIntersection(pich.getSpectrumAssigned(), test.getSpectrumAssigned())
                    && isAdjacent(link, core, qkd.metrics.BlockingDetailRecorder.rawCore(test))
                    && xtModel.calculateCrosstalk(test, null, false) > test.getModulation().getXTthreshold()) {
                return QKDPichPool.CLASSICAL;
            }
        }

        // 3. the QChs keep their key rate
        if (qkdCoupled && !provisioning.getResourceManager().keepsChannelsWithPich(pich, test,
                provisioning.getStageWavelengthNm())) {
            return QKDPichPool.QKD;
        }
        return QKDPichPool.OK;
    }

    /** QoT of a circuit with the network as it is (PICh included) plus the test circuit; nothing is changed. */
    private static boolean qotOk(PhysicalLayer pl, Circuit c, Circuit test) {
        final double snr = pl.computeSNRSegment(c, c.getRoute(), 0, c.getRoute().getNodeList().size() - 1,
                c.getModulation(), c.getIndexCore(), c.getSpectrumAssigned(), test, test != null);
        return pl.isAdmissible(c.getModulation(), snr);
    }

    private static boolean isAdjacent(Link link, int core, int other) {
        for (Core c : link.getAdjacentCores(core)) if (c != null && c.getId() == other) return true;
        return false;
    }
}
