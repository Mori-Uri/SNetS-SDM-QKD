package qkd.verification;

import qkd.resource.SameBandSpectrumGuard;

public final class QKDSameBandGuardVerification {
	static void check(boolean b) { if (!b) throw new AssertionError("Same-band guard failed"); }
	public static void main(String[] args) {
		check(!SameBandSpectrumGuard.separated(new int[]{10,12}, new int[]{13,14}, 1));
		check(SameBandSpectrumGuard.separated(new int[]{10,12}, new int[]{14,15}, 1));
		check(!SameBandSpectrumGuard.separated(new int[]{10,12}, new int[]{12,15}, 0));
		System.out.println("PASS QKDSameBandGuardVerification");
	}
}
