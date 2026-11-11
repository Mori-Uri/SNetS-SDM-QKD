package qkd.resource;

import network.Circuit;
import network.Core;
import network.Link;

/**
 * Cross-core same-band exclusion rule for explicitly aligned slot grids.
 * This is NOT applicable to multiband architectures with distinct grid origins.
 * The guard forbids a classical channel from using quantum-overlapping slots,
 * but does not reserve extra spectrum cells in unrelated cores.
 */
public final class SameBandSpectrumGuard {
	private SameBandSpectrumGuard() { }
	public static boolean separated(int[] a, int[] b, int guardSlots) {
		if (a == null || b == null || guardSlots < 0)
			throw new IllegalArgumentException("Invalid aligned-grid guard");

		return (long)a[1] + guardSlots < b[0]
				|| (long)b[1] + guardSlots < a[0];
	}

	/** Check an offered classical band against every occupied QCh cell on the reserved core. */
	public static boolean classicalAllowed(Link link, int qkdCoreId, int[] classicalBand, int guardSlots) {
		Core q = link.getCore(qkdCoreId);

		if (q == null || !q.isQkdReserved()) throw new IllegalArgumentException("QKD core missing");
		final int n = q.getNumOfSlots();
		boolean[] free = new boolean[n+1];

		for (int[] b : q.getFreeSpectrumBandsForQKD(0))
			for (int k=Math.max(1,b[0]); k<=Math.min(n,b[1]); k++) free[k]=true;

		for (int k=1;k<=n;k++) if (!free[k] && !separated(new int[] {k,k},classicalBand,guardSlots)) return false;

		return true;
	}

	/** Validate new quantum occupancy against live classical sources and the hypothetical candidate. */
	public static boolean quantumAllowed(Link link, int qkdCoreId, int[] qchBand,
			int guardSlots, Circuit candidate) {
		for (Core core : link.getCores()) {
			if (core.getId() == qkdCoreId) continue;

			for (Circuit c : core.getCircuitList())
				if (c.getSpectrumAssigned()!=null && !separated(qchBand,c.getSpectrumAssigned(),guardSlots)) return false;

			for (Circuit c : core.getAuxiliaryClassicalCircuitList())
				if (c.getSpectrumAssigned()!=null && !separated(qchBand,c.getSpectrumAssigned(),guardSlots)) return false;
		}

		if (candidate!=null && candidate.getSpectrumAssigned()!=null &&
				!separated(qchBand,candidate.getSpectrumAssigned(),guardSlots)) return false;

		return true;
	}
}
