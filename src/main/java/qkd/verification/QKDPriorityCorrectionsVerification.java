package qkd.verification;

import java.util.Arrays;
import qkd.network.QKDLinkPlan;
import qkd.network.QKDSpan;

/**
 * Focused regression of the mandatory SNetS-node QKD boundary invariant.
 * No external JSON or network runtime is required.
 */
public final class QKDPriorityCorrectionsVerification {
	private QKDPriorityCorrectionsVerification() { }

	private static QKDSpan span(String src, String dst, int idx, double start, double end) {
		return new QKDSpan(src, dst, idx, 2, start, end,
				idx == 0 ? src : "TN", idx == 1 ? dst : "TN");
	}

	private static void rejects(String name, Runnable test) {
		try {
			test.run();
		} catch (IllegalArgumentException expected) {
			System.out.println("[OK] rejected " + name);

			return;
		}

		throw new AssertionError("Expected rejection: " + name);
	}

	public static void main(String[] args) {
		new QKDLinkPlan("A", "B", 40.0, 25.0, Arrays.asList(
				span("A", "B", 0, 0, 25), span("A", "B", 1, 25, 40)));
		System.out.println("[OK] contiguous QKDSpans contained within A-B");
		rejects("span crossing a SNetS node", new Runnable() {
			public void run() {
				new QKDLinkPlan("A", "B", 40.0, 25.0, Arrays.asList(
						span("A", "B", 0, 0, 25), span("B", "C", 1, 25, 40)));
			}

		});
		rejects("noncontiguous spans", new Runnable() {
			public void run() {
				new QKDLinkPlan("A", "B", 40.0, 25.0, Arrays.asList(
						span("A", "B", 0, 0, 25), span("A", "B", 1, 24, 39)));
			}

		});
		rejects("span beyond link endpoint", new Runnable() {
			public void run() {
				new QKDLinkPlan("A", "B", 40.0, 25.0, Arrays.asList(
						span("A", "B", 0, 0, 25), span("A", "B", 1, 25, 41)));
			}

		});
		System.out.println("PASS QKDPriorityCorrectionsVerification");
	}
}
