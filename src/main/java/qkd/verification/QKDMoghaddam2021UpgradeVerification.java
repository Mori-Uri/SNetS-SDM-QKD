package qkd.verification;

import java.util.Arrays;
import java.util.Collections;

import network.AbsoluteFrequencyGrid;
import network.Circuit;
import network.Link;
import network.Oxc;
import qkd.key.QKDKeyDemand;
import qkd.key.QKDKeyDemandPolicy;
import qkd.multiband.MultiBandSettings;
import qkd.multiband.SpontaneousRamanModel;
import qkd.network.QKDNetworkParameters;
import qkd.network.QKDSpan;
import qkd.physical.QKDPhysicalLayer;
import qkd.physical.QKDPhysicalParameters;
import qkd.physical.QKDSpanPhysicalResult;
import simulationControl.parsers.NetworkConfig;

/** Pure-Java regression scenarios without Gson or simulation launcher dependencies. */
public final class QKDMoghaddam2021UpgradeVerification {

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	public static void main(String[] args) {
		keyDemand();
		grid();
		pichConfiguration();
		fwmToQuantumDetector();
		System.out.println("[OK] QKD 2021 upgrade: key, grid, PICh and QCh/FWM checks passed");
	}

	private static void keyDemand() {
		for (int intervalMs : new int[] {64, 128, 256}) {
			QKDKeyDemand demand = new QKDKeyDemand(256, intervalMs / 1000.0);
			check(Math.abs(demand.getRequiredKeyRateBps() - 256000.0 / intervalMs) < 1e-9,
					"Moghaddam 2021: expected AES-256 renewal interval in milliseconds");
		}
		check(Arrays.equals(QKDKeyDemandPolicy.referenceDefault().getUpdateIntervalSeconds(),
				new double[] {0.064, 0.128, 0.256}), "reference demand intervals");
	}

	private static void grid() {
		double center = 193.4e12, bw = 12.5e9;
		double lower = AbsoluteFrequencyGrid.lowerEdgeHz(center, 320, bw);
		check(Math.abs(lower - 191.4e12) < 1.0, "absolute lower band edge");
		check(Math.abs(AbsoluteFrequencyGrid.centerHz(lower, 1, 320, bw) - center) < 1.0,
				"center of 320 slots should agree with the configured center");
	}

	private static void pichConfiguration() {
		NetworkConfig network = new NetworkConfig();
		network.setCores(Arrays.asList(
				new NetworkConfig.CoreConfig(0, Collections.singletonList(1)),
				new NetworkConfig.CoreConfig(1, Collections.singletonList(0))));
		NetworkConfig.QKDConfig qkd = new NetworkConfig.QKDConfig(true, 25.0, 0);
		network.setQkd(qkd);
		QKDNetworkParameters dedicated = QKDNetworkParameters.from(network);
		check(dedicated.getPichSlots() > 0 && !dedicated.isPichOverIp()
				&& !dedicated.isPichTdmSharing(), "dedicated optical PICh by default");
		qkd.setPichAllocation("tdm");
		qkd.setPichSessionsPerChannel(3);
		QKDNetworkParameters shared = QKDNetworkParameters.from(network);
		check(shared.isPichTdmSharing() && shared.getPichSessionsPerChannel() == 3,
				"TDM mode must have finite per-carrier multiplexing capacity");
		qkd.setPichTransport("ip");
		qkd.setPichSlots(0);
		QKDNetworkParameters ip = QKDNetworkParameters.from(network);
		check(ip.isPichOverIp() && ip.getPichSlots() == 0, "IP PICh without optical allocation");
		qkd.setPichTransport("optical");
		try {
			QKDNetworkParameters.from(network);
			throw new AssertionError("Optical PICh with zero slots must fail");
		} catch (IllegalArgumentException expected) {
			check(expected.getMessage().contains("PICh"), "clear configuration error");
		}
	}

	private static void fwmToQuantumDetector() {
		final int n = 320;
		final double width = 12.5e9;
		final double lower = 191.2e12;
		final int targetSlot = 166;
		final double qHz = lower + (targetSlot - 0.5) * width;
		final double qNm = 299792458.0 / qHz * 1e9;
		final Link link = new Link(new Oxc("A"), new Oxc("B"), n, width, 25.0,
				Arrays.asList(new NetworkConfig.CoreConfig(0, Collections.<Integer>emptyList()),
					new NetworkConfig.CoreConfig(1, Collections.<Integer>emptyList())));
		for (int slot : new int[] {170, 174, 178}) {
			Circuit pump = new Circuit();
			pump.setIndexCore(0);
			pump.setSpectrumAssigned(new int[] {slot, slot});
			pump.setLaunchPowerLinear(0.02); // 13 dBm per pump for the isolated stress test
			link.getCore(0).addCircuit(pump);
		}
		final QKDSpan span = new QKDSpan("A", "B", 0, 1, 0.0, 25.0, "A", "B");
		final MultiBandSettings mb = new MultiBandSettings(
				MultiBandSettings.Architecture.MB, MultiBandSettings.RamanModel.NENHUM,
				null, SpontaneousRamanModel.standard(), 0, lower, false, qNm);
		final QKDPhysicalParameters without = new QKDPhysicalParameters().withMultiband(
				mb.withFwm(false, 1.3, 16.0, 0.06, true));
		final QKDPhysicalParameters with = new QKDPhysicalParameters().withMultiband(
				mb.withFwm(true, 1.3, 16.0, 0.06, true));
		QKDSpanPhysicalResult a = new QKDPhysicalLayer(without).evaluateSpanUsingStoredLaunchPowers(
				link, span, 1, new int[]{targetSlot, targetSlot}, 0.0, qNm);
		QKDSpanPhysicalResult b = new QKDPhysicalLayer(with).evaluateSpanUsingStoredLaunchPowers(
				link, span, 1, new int[]{targetSlot, targetSlot}, 0.0, qNm);
		check(a.getFwmPowerW() == 0.0 && a.getFwmBackgroundProbability() == 0.0,
				"FWM disabled: no new background");
		check(b.getFwmPowerW() > 0.0 && b.getFwmBackgroundProbability() > 0.0,
				"FWM enabled: a product inside QCh filter causes detector background");
		check(b.getQber() >= a.getQber(), "FWM should not decrease QBER");
		check(b.getSecretKeyRateBps() <= a.getSecretKeyRateBps(), "FWM should not improve SKR");
		System.out.println("[OK] FWM off/on: pBg=" + a.getTotalBackgroundProbability() + " -> "
				+ b.getTotalBackgroundProbability() + "; QBER=" + a.getQber() + " -> " + b.getQber()
				+ "; SKR=" + a.getSecretKeyRateBps() + " -> " + b.getSecretKeyRateBps());
	}
}
