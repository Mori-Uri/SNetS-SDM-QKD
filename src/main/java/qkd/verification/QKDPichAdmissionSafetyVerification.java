package qkd.verification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import gprmcsa.routing.Route;
import network.Link;
import network.Oxc;
import qkd.capacity.QKDPichCircuit;
import qkd.capacity.QKDPichPool;
import qkd.network.QKDLinkPlan;
import qkd.network.QKDRoutePlan;
import qkd.network.QKDSpan;
import simulationControl.parsers.NetworkConfig.CoreConfig;

/** Standalone regression checks: PICh rollback on partial allocation failure. */
public final class QKDPichAdmissionSafetyVerification {
	private QKDPichAdmissionSafetyVerification() { }

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}

	private static QKDSpan span(int index, double from, double to) {
		return new QKDSpan("A", "B", index, 2, from, to,
				index == 0 ? "A" : "TN1", index == 1 ? "B" : "TN1");
	}

	public static void main(String[] args) {
		List<CoreConfig> configs = new ArrayList<CoreConfig>();
		configs.add(new CoreConfig(0, Arrays.asList(Integer.valueOf(1))));
		configs.add(new CoreConfig(1, Arrays.asList(Integer.valueOf(0))));
		Link link = new Link(new Oxc("A"), new Oxc("B"), 2, 12.5e9, 40.0, configs);
		link.getCore(1).setQkdReserved(true);
		Route route = new Route();
		route.setLinkList(new Vector<Link>(Collections.singletonList(link)));
		QKDRoutePlan plan = new QKDRoutePlan("A", "B", Collections.singletonList(
				new QKDLinkPlan("A", "B", 40.0, 20.0, Arrays.asList(
						span(0, 0.0, 20.0), span(1, 20.0, 40.0)))));
		QKDPichPool pool = new QKDPichPool(1, 1);
		// Independent trusted-node spans: two distinct PICh optical sources
		// can use different slots and must never double-book spectrum.
		List<QKDPichPool.Channel> admitted = pool.acquire(route, plan, 0, null);
		check(admitted != null && admitted.size() == 2,
				"two QKDSpans must each get a physical PICh");
		check(admitted.get(0).band[0] == 1 && admitted.get(1).band[0] == 2,
				"independent trusted-node spans must use separate available slots");
		check(pool.getOpenChannels() == 2 && link.getCore(0).getUsedSlots() == 2,
				"independent optical PIChs require distinct spectrum leases");
		pool.release(admitted);
		check(pool.getOpenChannels() == 0, "PICh release must close both optical carriers");
		check(link.getCore(0).getUsedSlots() == 0,
				"PICh release must restore classical spectrum");
		check(link.getCore(0).getAuxiliaryClassicalCircuitList().isEmpty(),
				"PICh release must remove all optical sources");

		// Failure on the SECOND physical link after a PICh was opened on the first.
		// The route/plan mismatch must release the first reservation too.
		Link second = new Link(new Oxc("B"), new Oxc("C"), 2, 12.5e9, 40.0, configs);
		second.getCore(1).setQkdReserved(true);
		Link first = new Link(new Oxc("A"), new Oxc("B"), 2, 12.5e9, 40.0, configs);
		first.getCore(1).setQkdReserved(true);
		Route twoLinks = new Route();
		twoLinks.setLinkList(new Vector<Link>(Arrays.asList(first, second)));
		QKDLinkPlan firstPlan = new QKDLinkPlan("A", "B", 40.0, 40.0,
				Collections.singletonList(new QKDSpan("A", "B", 0, 1, 0.0, 40.0, "A", "B")));
		QKDLinkPlan wrongSecondPlan = new QKDLinkPlan("X", "Y", 40.0, 40.0,
				Collections.singletonList(new QKDSpan("X", "Y", 0, 1, 0.0, 40.0, "X", "Y")));
		QKDRoutePlan inconsistent = new QKDRoutePlan("A", "C", Arrays.asList(firstPlan, wrongSecondPlan));
		QKDPichPool pool2 = new QKDPichPool(1, 1);
		boolean rejected = false;

		try { pool2.acquire(twoLinks, inconsistent, 0, null); }
		catch (IllegalStateException expected) { rejected = true; }
		check(rejected, "route/plan mismatch must fail closed");
		check(pool2.getOpenChannels() == 0 && pool2.snapshotOpenChannels().isEmpty(),
				"exception on later link must revert prior PICh channels");
		check(first.getCore(0).getUsedSlots() == 0
						&& first.getCore(0).getAuxiliaryClassicalCircuitList().isEmpty(),
				"exception on later link must release first-link spectrum and source");

		// A successful one-span reservation has a well-defined centre frequency.
		QKDSpan whole = new QKDSpan("A", "B", 0, 1, 0.0, 40.0, "A", "B");
		QKDPichCircuit source = new QKDPichCircuit(link, whole, 0, new int[] {1, 1}, 0, 1e-3);
		double slotHz = 12.5e9;
		check(source.getOpticalBandwidthHz(slotHz) == slotHz, "physical PICh bandwidth");
		check(source.getCenterFrequencyHz(190e12, slotHz) == 190e12 + slotHz / 2.0,
				"physical PICh centre frequency");
		boolean invalid = false;

		try { source.getCenterFrequencyHz(-1, slotHz); }
		catch (IllegalArgumentException expected) { invalid = true; }
		check(invalid, "reject invalid optical grid");
		check(QKDPichPool.withinPichSlotWindow(5, 2, 5, 9), "within PICh physical band");
		check(!QKDPichPool.withinPichSlotWindow(4, 2, 5, 9), "reject start before PICh band");
		check(!QKDPichPool.withinPichSlotWindow(9, 2, 5, 9), "reject end beyond PICh band");
		check(QKDPichPool.withinPichSlotWindow(1, 1, -1, -1), "legacy unrestricted band");
		System.out.println("PASS QKDPichAdmissionSafetyVerification");
	}
}
