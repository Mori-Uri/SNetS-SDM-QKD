package qkd.verification;

import java.util.List;
import java.util.Locale;

import qkd.multiband.ClassicalChannel;
import qkd.multiband.MultiBandQkdLinkModel;
import qkd.physical.QKDPhysicalParameters;

/**
 * Parte 45 (VMB-10a/b): the trusted-node method of [DIBAJ24] (IWCIT 2024, multi-band x multi-fibre) with the
 * multi-band physics of the simulator. VMB-10c (Q-TRx in the networks of Fig. 7) is run by
 * {@code qkd.reference.QKDNativeReferenceMain --arquitetura mb}.
 *
 * <ul>
 *   <li>VMB-10a, Fig. 3: 30 km link at 1380 nm, C+L comb: SKR per Q-TRx with hops of 30, 15 and 5 km = 40, 50 and
 *       30.83 Mbit/s (SKR of one hop divided by the number of hops);</li>
 *   <li>VMB-10b, Fig. 5: 40 km link. MB at 1380 nm (SpRS of the comb): best QTND 20 km with whole hops
 *       (N = ceil(40/QTND)); MF at 1550 nm (dark fibre): best QTND 25 km with the fractional count (N = 40/QTND),
 *       gain over no trusted node about 15 %.</li>
 * </ul>
 * The gain of +40 % that the article gives for MB is not reproduced by any hop-count convention (pending; see the
 * notes in docs/multibanda/G1_qkd_multibanda.md, section 3.7).
 */
public final class MultiBandIwcitVerification {

	private MultiBandIwcitVerification() {
	}

	public static void main(String[] args) {
		final QKDPhysicalParameters p = new QKDPhysicalParameters();
		final MultiBandQkdLinkModel mb = MultiBandQkdLinkModel.paperCompatible(p);
		final List<ClassicalChannel> comb = ClassicalChannel.referenceCLComb(0.0);

		// VMB-10a
		final double[][] fig3 = {{30, 1, 40.0}, {15, 2, 50.0}, {5, 6, 30.83}};

		for (double[] t : fig3) {
			final double perQtrx = mb.evaluate(1380, t[0], comb).getSecretKeyRateBps() / 1e6 / t[1];
			relative(perQtrx, t[2], 0.10, String.format(Locale.ROOT,
					"VMB-10a Fig. 3: hops de %.0f km -> SKR/Q-TRx = %.2f Mbit/s", t[0], t[2]));
		}

		// VMB-10b, MB at 1380 nm, whole hops
		int bestMb = 0;
		double bestMbValue = -1;

		for (int d = 5; d <= 40; d += 5) {
			final int hops = (int) Math.ceil(40.0 / d - 1e-9);
			final double v = mb.evaluate(1380, d, comb).getSecretKeyRateBps() / 1e6 / hops; // longest hop = QTND
			if (v > bestMbValue) {
				bestMbValue = v;
				bestMb = d;
			}
		}

		check(bestMb == 20, "VMB-10b MB best QTND = 20 km (got " + bestMb + ")");
		ok("VMB-10b Fig. 5, MB 1380 nm, enlace de 40 km: QTND otimo %d km (artigo: 20 km), SKR/Q-TRx %.1f Mbit/s",
				bestMb, bestMbValue);

		// VMB-10b, MF at 1550 nm, fractional hops
		int bestMf = 0;
		double bestMfValue = -1;
		double noTn = 0;

		for (int d = 5; d <= 40; d += 5) {
			final double v = mb.evaluateDarkFiber(1550, d).getSecretKeyRateBps() / 1e6 * d / 40.0;

			if (d == 40) noTn = v;

			if (v > bestMfValue) {
				bestMfValue = v;
				bestMf = d;
			}
		}

		check(bestMf == 25, "VMB-10b MF best QTND = 25 km (got " + bestMf + ")");
		final double gain = 100.0 * (bestMfValue / noTn - 1.0);
		check(gain > 10.0 && gain < 20.0, "VMB-10b MF gain about 15 % (got " + gain + ")");
		ok("VMB-10b Fig. 5, MF 1550 nm: QTND otimo %d km (artigo: 25 km), ganho sobre sem no confiavel %.1f %% "
				+ "(artigo: 15 %%)", bestMf, gain);
		System.out.println("[PENDENTE] VMB-10b MB: ganho de +40 % do artigo nao reproduzido por nenhuma convencao de "
				+ "contagem de saltos (ver G1_qkd_multibanda.md, 3.7).");
		System.out.println();
		System.out.println("Multi-band IWCIT verification (Parte 45): ALL CHECKS PASSED (1 item pendente, ver acima)");
	}

	private static void relative(double value, double target, double tol, String msg) {
		final double err = Math.abs(value - target) / Math.abs(target);
		check(err <= tol, String.format(Locale.ROOT, "%s: obtido %.4g (erro %.1f%%)", msg, value, 100 * err));
		ok("%s: obtido %.4g (erro %.1f%%)", msg, value, 100 * err);
	}

	private static void ok(String fmt, Object... a) {
		System.out.println("[OK] " + String.format(Locale.ROOT, fmt, a));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
