package qkd.multiband;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import qkd.physical.QKDCrosstalkReceiverTransferMode;
import qkd.physical.QKDPhysicalParameters;

/**
 * Command-line link calculator: physical layer of one QKD span in a multi-fibre, multi-band or multi-band
 * multi-core fibre, outside the network simulator. It is used to check the models against the papers and to choose
 * QCh wavelengths and QTND values before running a network scenario.
 *
 * <pre>
 * java -cp SNetS-SDM-QKD-exp.jar qkd.multiband.LinkCalculator [chave=valor ...]
 *
 *   arq=mb|mf|mbsdm        arquitetura (padrao mb): mf = fibra escura, mb = C+L no mesmo nucleo,
 *                          mbsdm = C+L nos nucleos vizinhos (e opcionalmente no proprio, com mesmo=1)
 *   lambda=1381            comprimento de onda do QCh em nm, ou faixa ini:fim:passo (varredura)
 *   L=70                   comprimento do trecho em km, ou lista 5,15,30, ou faixa ini:fim:passo
 *   preset=artigo|fisico   artigo = L e^{-aL} e fracao 0,5 [MEHDI24]/[DIBAJ25]; fisico = forma exata e fracao 1
 *   pot=0                  potencia por canal classico em dBm
 *   vizinhos=3             numero de nucleos vizinhos com o comb (mbsdm)
 *   h=1e-6                 coeficiente de acoplamento de potencia entre nucleos em 1/km (mbsdm)
 *   mesmo=0|1              mbsdm: comb tambem no nucleo do QCh
 *   mu=0.7 pdark=1e-8 etad=0.3 ed=0.015 f=1.16 ts=250e-12 gate=100e-12 bqch=12.5e9
 *   nli=gn                 soma o NLI (modelo GN) dos canais do mesmo nucleo (Parte 45); passonli=2.5 (GHz)
 *   saida=arquivo.csv      grava CSV (sem saida: imprime na tela)
 * </pre>
 *
 * <p>Referências: ver {@link MultiBandQkdLinkModel}.</p>
 */
public final class LinkCalculator {

	private LinkCalculator() {
	}

	public static void main(String[] args) throws IOException {
		final Map<String, String> opt = parse(args);

		if (opt.containsKey("ajuda") || opt.containsKey("help")) {
			usage(System.out);

			return;
		}

		final QKDPhysicalParameters p = parameters(opt);
		MultiBandQkdLinkModel model = "fisico".equals(opt.getOrDefault("preset", "artigo"))
				? MultiBandQkdLinkModel.physical(p) : MultiBandQkdLinkModel.paperCompatible(p);

		if ("gn".equals(opt.get("nli"))) { // Parte 45
			model = model.withNli(NonlinearInterferenceModel.standard(), num(opt, "passonli", 2.5) * 1e9);
		}

		final String arq = opt.getOrDefault("arq", "mb");

		if (opt.containsKey("sentido")) throw new IllegalArgumentException("sentido is obsolete: each directed link is an independent fibre");
		final List<ClassicalChannel> comb = ClassicalChannel.referenceCLComb(num(opt, "pot", 0.0));
		final List<ClassicalChannel> same;
		final List<ClassicalChannel> neighbours;
		final double h;

		switch (arq) {
			case "mf":
				same = Collections.emptyList();
				neighbours = Collections.emptyList();
				h = 0.0;
				break;
			case "mbsdm":
				same = "1".equals(opt.get("mesmo")) ? comb : Collections.<ClassicalChannel>emptyList();
				neighbours = repeat(comb, (int) num(opt, "vizinhos", 3));
				h = num(opt, "h", 1e-6);
				break;
			case "mb":
				same = comb;
				neighbours = Collections.emptyList();
				h = 0.0;
				break;
			default:
				throw new IllegalArgumentException("arq deve ser mf, mb ou mbsdm");
		}

		final double[] lambdas = values(opt.getOrDefault("lambda", "1381"));
		final double[] lengths = values(opt.getOrDefault("L", "70"));
		final StringBuilder csv = new StringBuilder(MultiBandLinkResult.csvHeader()).append('\n');

		for (double l : lengths) {
			for (double lam : lambdas) {
				csv.append(model.evaluate(lam, l, same, neighbours, h).toCsvRow()).append('\n');
			}
		}

		final String out = opt.get("saida");

		if (out == null) {
			System.out.print(csv);
		} else {
			final Path path = Paths.get(out);
			Files.write(path, csv.toString().getBytes(StandardCharsets.UTF_8));
			System.out.println("gravado: " + path.toAbsolutePath() + " (" + lambdas.length * lengths.length
					+ " pontos)");
		}
	}

	private static QKDPhysicalParameters parameters(Map<String, String> o) {
		final QKDPhysicalParameters d = new QKDPhysicalParameters();

		return new QKDPhysicalParameters(num(o, "mu", d.getMu()), num(o, "ts", d.getPulseIntervalS()),
				num(o, "f", d.getErrorCorrectionInefficiency()), num(o, "ed", d.getPhaseErrorProbability()),
				num(o, "etad", d.getDetectorEfficiency()), num(o, "pdark", d.getDarkCountProbability()),
				num(o, "bqch", d.getQchBandwidthHz()), d.getQchPowerDbm(), d.getAttenuationFloorDbPerKm(),
				d.getAttenuationQuadratic(), d.getAttenuationCenterNm(), d.getPlanckConstantJs(),
				d.getSpeedOfLightMPerS(), num(o, "gate", d.getDetectorGateWindowS()),
				d.getReceiverOpticalEfficiency(), QKDCrosstalkReceiverTransferMode.RECEIVER_REFERENCED, true);
	}

	private static List<ClassicalChannel> repeat(List<ClassicalChannel> comb, int times) {
		final List<ClassicalChannel> list = new ArrayList<>(comb.size() * Math.max(0, times));

		for (int i = 0; i < times; i++) {
			list.addAll(comb);
		}

		return list;
	}

	/** "x", "a,b,c" or "ini:fim:passo". */
	static double[] values(String s) {
		if (s.contains(":")) {
			final String[] r = s.split(":");
			final double a = Double.parseDouble(r[0]);
			final double b = Double.parseDouble(r[1]);
			final double step = r.length > 2 ? Double.parseDouble(r[2]) : 1.0;

			if (!(step > 0.0) || b < a) {
				throw new IllegalArgumentException("faixa invalida: " + s);
			}

			final int n = (int) Math.floor((b - a) / step + 1e-9) + 1;
			final double[] v = new double[n];

			for (int i = 0; i < n; i++) {
				v[i] = a + i * step;
			}

			return v;
		}

		final String[] parts = s.split(",");
		final double[] v = new double[parts.length];

		for (int i = 0; i < parts.length; i++) {
			v[i] = Double.parseDouble(parts[i].trim());
		}

		return v;
	}

	private static double num(Map<String, String> o, String key, double def) {
		final String v = o.get(key);

		return v == null ? def : Double.parseDouble(v);
	}

	private static Map<String, String> parse(String[] args) {
		final Map<String, String> m = new LinkedHashMap<>();

		for (String a : args) {
			final int i = a.indexOf('=');

			if (i < 0) {
				m.put(a.replaceFirst("^-+", "").toLowerCase(Locale.ROOT), "");
			} else {
				m.put(a.substring(0, i).toLowerCase(Locale.ROOT), a.substring(i + 1));
			}
		}

		if (m.containsKey("l") && !m.containsKey("L")) {
			m.put("L", m.remove("l"));
		}

		return m;
	}

	private static void usage(PrintStream out) {
		out.println("Calculadora de enlace QKD multibanda (SNetS-SDM-QKD, Parte 42)");
		out.println("uso: java -cp SNetS-SDM-QKD-exp.jar qkd.multiband.LinkCalculator arq=mb lambda=1260:1460:5 "
				+ "L=5,15,30 preset=artigo saida=varredura.csv");
		out.println("opcoes: arq, lambda, L, preset, pot, vizinhos, h, mesmo, mu, pdark, etad, ed, f, ts, "
				+ "gate, bqch, nli, passonli, saida");
	}
}
