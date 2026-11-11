package qkd.verification;

import java.util.Arrays;
import java.util.Collections;

import network.Link;
import network.Mesh;
import qkd.capacity.QKDLinkChannelPhysicalResult;
import qkd.network.QKDLinkPlan;
import qkd.network.QKDNetworkParameters;
import qkd.network.QKDSpan;
import qkd.physical.QKDPhysicalLayer;
import qkd.resource.QKDChannelAllocation;
import simulationControl.parsers.NetworkConfig;

/** Verifies optional QBER gating without disabling QBER accounting in BB84 SKR. */
public final class QKDOptionalQberVerification {
    private QKDOptionalQberVerification() { }
    private static void check(boolean ok, String context) {
        if (!ok) throw new AssertionError(context);
    }

    public static void main(String[] args) throws Exception {
        NetworkConfig network = new NetworkConfig();
        network.setCores(Arrays.asList(new NetworkConfig.CoreConfig(0, Arrays.asList(1)),
                new NetworkConfig.CoreConfig(1, Arrays.asList(0))));
        NetworkConfig.QKDConfig settings = new NetworkConfig.QKDConfig(true, 20, 1);
        network.setQkd(settings);

        QKDNetworkParameters defaultSettings = QKDNetworkParameters.from(network);
        check(!defaultSettings.isQberThresholdEnabled(), "independent QBER cap must default OFF");
        check(defaultSettings.getMaximumQber() == 0.11, "configured QBER cap must remain 11%");
        check(defaultSettings.getEffectiveMaximumQber() == 1.0, "disabled gate must be nonrestrictive");
        settings.setMaximumQber(0.01);
        QKDNetworkParameters skrOnly = QKDNetworkParameters.from(network);
        check(!skrOnly.isQberThresholdEnabled() && skrOnly.getMaximumQber() == 0.01
                && skrOnly.getEffectiveMaximumQber() == 1.0,
                "setting maxQber alone must not enable extra gate");
        settings.setQberThresholdEnabled(true);
        QKDNetworkParameters gated = QKDNetworkParameters.from(network);
        check(gated.isQberThresholdEnabled() && gated.getEffectiveMaximumQber() == 0.01,
                "enabled gate must use requested cap");
        settings.setQberThresholdEnabled(false);
        check(QKDNetworkParameters.from(network).getEffectiveMaximumQber() == 1.0,
                "gate must be reversible without erasing configured threshold");

        Mesh mesh = QKDPichSharedClassicalQoTVerification.mesh(false, false, false);
        Link link = mesh.getLinkList().get(0);
        link.getCore(1).setQkdReserved(true);
        QKDLinkPlan plan = new QKDLinkPlan("A", "B", 40.0, 40.0,
                Collections.singletonList(new QKDSpan("A", "B", 0, 1,
                        0.0, 40.0, "A", "B")));
        QKDPhysicalLayer evaluator = new QKDPhysicalLayer();
        QKDChannelAllocation channel = new QKDChannelAllocation(link, 1,
                new int[] {1, 1}, 0, 1);
        evaluator.setMaximumQber(skrOnly.getEffectiveMaximumQber());
        QKDLinkChannelPhysicalResult withoutGate = evaluator.evaluateLinkChannel(
                link, plan, mesh.getPhysicalLayer(), channel, 1550);
        check(withoutGate.getMinimumSecretKeyRateBps() > 0,
                "fixture must have strictly positive SKR");
        check(withoutGate.getMaximumQber() > gated.getMaximumQber(),
                "fixture must exceed separately configured QBER cap");
        check(withoutGate.getSpans().get(0).isViable(),
                "SKR-positive channel must pass when extra cap is OFF");
        evaluator.setMaximumQber(gated.getEffectiveMaximumQber());
        QKDLinkChannelPhysicalResult withGate = evaluator.evaluateLinkChannel(
                link, plan, mesh.getPhysicalLayer(), channel, 1550);
        check(!withGate.getSpans().get(0).isViable(),
                "same physical channel must fail when extra cap is ON");
        check(withGate.getMinimumSecretKeyRateBps() == withoutGate.getMinimumSecretKeyRateBps(),
                "optional QBER gate cannot change SKR physical computation");
        check(withGate.getMaximumQber() == withoutGate.getMaximumQber(),
                "observed QBER must be reported independently of admission policy");
        System.out.println("PASS QKDOptionalQberVerification SKR="
                + withoutGate.getMinimumSecretKeyRateBps() + " QBER="
                + withoutGate.getMaximumQber() + " gateOFF=true gateON=false");
    }
}
