package qkd.multiband;

import java.util.Collections;
import java.util.List;

import qkd.physical.BB84DecoyModel;
import qkd.physical.QKDPhysicalParameters;
import qkd.physical.QKDPhysicalResult;

/**
 * Physical layer of one QKD span in a multi-band (MB) or multi-band multi-core (MB-SDM) fibre: the quantum channel
 * (QCh) sits in any band from O to L, the classical channels are in other bands or other cores, and the key rate is
 * the decoy-state BB84 bound of {@link BB84DecoyModel} with the noise budget computed here.
 *
 * <p><b>Model.</b> For a QCh at lambda_q over a span of L km:</p>
 * <ol>
 *   <li>loss alpha(lambda_q) from {@link FiberAttenuationModel} (the QCh bypasses the amplifiers);</li>
 *   <li>SpRS power inside the QCh filter (width d_lambda = lambda_q^2 B / c, B = QCh bandwidth):
 *       P = sum_m P_m rho(lambda_m, lambda_q) d_lambda G_m, with G_m the forward effective length of
 *       {@link SpontaneousRamanModel} (same core) or the intercore lengths of [CAI20] (neighbour cores);</li>
 *   <li>photon probability per gate: p = P eta_d T_gate / (h nu_q) * f_det, where f_det is the detected fraction
 *       (1.0 is the physical value of [ERAERDS10] eq. 11; 0.5 reproduces eq. 6 of [DIBAJ25]);</li>
 *   <li>p_b = p_dark + p_SpRS(same) + p_SpRS(neighbours); SKR = BB84 decoy bound with alpha and p_b.</li>
 * </ol>
 *
 * <p>Two presets exist: {@link #paperCompatible} (equal-loss forward form, f_det = 0.5: the convention of
 * [MEHDI24]/[DIBAJ25], used to reproduce their figures) and {@link #physical} (exact forward form, f_det = 1.0).
 * The multi-fibre (MF) architecture is the case with no classical channel: p_b = p_dark.</p>
 *
 * <p>The object is immutable and thread-safe. Cost: O(number of classical channels) per evaluation, with no
 * allocation besides the result.</p>
 *
 * <p>Referências: [MEHDI24] P. Mehdizadeh et al., IEEE Commun. Lett. 28(3), 2024 (QKD em O/E com C+L);
 * [DIBAJ25] M. R. Dibaj et al., JLT 43(1), 2025 (eq. 6, mesma configuração); [DIBAJ24] IWCIT 2024 (MF/MB);
 * [ERAERDS10] New J. Phys. 12, 063027 (eqs. 2, 3, 11); [CAI20] New J. Phys. 22, 083020 (eqs. 11 e 18).</p>
 */
public final class MultiBandQkdLinkModel {

	/** Form of the forward (co-propagating) same-core SpRS effective length. */
	public enum ForwardForm {

		/** (e^{-a_q L} - e^{-a_c L}) / (a_c - a_q), exact for different losses. */
		EXACT,

		/** L e^{-a_c L}, the form written in [MEHDI24]/[DIBAJ25]. */
		EQUAL_LOSS
	}

	/** Detected fraction of the SpRS photons in the physical preset. */
	public static final double PHYSICAL_DETECTED_FRACTION = 1.0;

	/** Detected fraction used by [DIBAJ25] eq. 6. */
	public static final double PAPER_DETECTED_FRACTION = 0.5;

	private final QKDPhysicalParameters parameters;
	private final BB84DecoyModel bb84;
	private final FiberAttenuationModel fiber;
	private final SpontaneousRamanModel raman;
	private final ForwardForm forwardForm;
	private final double detectedFraction;

	/** Parte 45: optional NLI model (null = no NLI) and its grid step (Hz). */
	private final NonlinearInterferenceModel nli;
	private final double nliGridHz;

	/**
     * @param parameters BB84 and detector parameters (mu, T_s, f, e_d, eta_d, p_dark, QCh bandwidth, gate)
     * @param fiber fibre loss model
     * @param raman SpRS cross-section model
     * @param forwardForm form of the forward effective length
     * @param detectedFraction fraction of the SpRS photons in the filter that reach the detector (0, 1]
     */
	public MultiBandQkdLinkModel(QKDPhysicalParameters parameters, FiberAttenuationModel fiber,
			SpontaneousRamanModel raman, ForwardForm forwardForm, double detectedFraction) {
		this(parameters, fiber, raman, forwardForm, detectedFraction, null, 0.0);
	}

	private MultiBandQkdLinkModel(QKDPhysicalParameters parameters, FiberAttenuationModel fiber,
			SpontaneousRamanModel raman, ForwardForm forwardForm, double detectedFraction,
			NonlinearInterferenceModel nli, double nliGridHz) {
		if (parameters == null || fiber == null || raman == null || forwardForm == null) {
			throw new IllegalArgumentException("arguments cannot be null");
		}

		if (!(detectedFraction > 0.0 && detectedFraction <= 1.0)) {
			throw new IllegalArgumentException("detectedFraction must be in (0, 1]");
		}

		this.parameters = parameters;
		this.bb84 = new BB84DecoyModel(parameters);
		this.fiber = fiber;
		this.raman = raman;
		this.forwardForm = forwardForm;
		this.detectedFraction = detectedFraction;
		this.nli = nli;
		this.nliGridHz = nliGridHz;
	}

	/**
     * Parte 45: copy of this model that also adds the NLI of the same-core classical channels
     * ({@link NonlinearInterferenceModel}); the channels must have a bandwidth.
     *
     * @param model GN model
     * @param gridStepHz step of the PSD grid of the double integral (e.g. 2.5e9)
     */
	public MultiBandQkdLinkModel withNli(NonlinearInterferenceModel model, double gridStepHz) {
		if (model == null || !(gridStepHz > 0.0)) {
			throw new IllegalArgumentException("NLI model and grid step required");
		}

		return new MultiBandQkdLinkModel(parameters, fiber, raman, forwardForm, detectedFraction, model, gridStepHz);
	}

	/** Convention of [MEHDI24]/[DIBAJ25]: G.652.D loss, fitted SpRS, L e^{-aL}, f_det = 0.5. */
	public static MultiBandQkdLinkModel paperCompatible(QKDPhysicalParameters parameters) {
		return new MultiBandQkdLinkModel(parameters, FiberAttenuationModel.g652d(), SpontaneousRamanModel.standard(),
				ForwardForm.EQUAL_LOSS, PAPER_DETECTED_FRACTION);
	}

	/** Physical preset: G.652.D loss, fitted SpRS, exact forward form, f_det = 1. */
	public static MultiBandQkdLinkModel physical(QKDPhysicalParameters parameters) {
		return new MultiBandQkdLinkModel(parameters, FiberAttenuationModel.g652d(), SpontaneousRamanModel.standard(),
				ForwardForm.EXACT, PHYSICAL_DETECTED_FRACTION);
	}

	public QKDPhysicalParameters getParameters() { return parameters; }

	public FiberAttenuationModel getFiber() { return fiber; }

	public SpontaneousRamanModel getRaman() { return raman; }

	public ForwardForm getForwardForm() { return forwardForm; }

	public double getDetectedFraction() { return detectedFraction; }

	/** Multi-fibre case (dark fibre, no classical channel): p_b = p_dark. */
	public MultiBandLinkResult evaluateDarkFiber(double qchNm, double lengthKm) {
		return evaluate(qchNm, lengthKm, Collections.emptyList(), Collections.emptyList(), 0.0);
	}

	/** Single-core multi-band case: classical channels in the same fibre. */
	public MultiBandLinkResult evaluate(double qchNm, double lengthKm, List<ClassicalChannel> sameCore) {
		return evaluate(qchNm, lengthKm, sameCore, Collections.emptyList(), 0.0);
	}

	/**
     * Full evaluation.
     *
     * @param qchNm QCh wavelength in nm
     * @param lengthKm span length in km (one trusted-node segment)
     * @param sameCore classical channels in the QCh core (MB)
     * @param neighbourCores classical channels in the cores adjacent to the QCh core (MB-SDM); a channel present
     *        in k neighbour cores must appear k times
     * @param couplingPerKm power coupling coefficient h between adjacent cores in 1/km
     */
	public MultiBandLinkResult evaluate(double qchNm, double lengthKm, List<ClassicalChannel> sameCore,
			List<ClassicalChannel> neighbourCores, double couplingPerKm) {
		if (!(qchNm > 0.0) || Double.isInfinite(qchNm)) {
			throw new IllegalArgumentException("qchNm must be finite and > 0");
		}

		if (!(lengthKm >= 0.0) || Double.isInfinite(lengthKm)) {
			throw new IllegalArgumentException("lengthKm must be finite and >= 0");
		}

		if (!(couplingPerKm >= 0.0) || Double.isInfinite(couplingPerKm)) {
			throw new IllegalArgumentException("couplingPerKm must be finite and >= 0");
		}

		final double alphaQdb = fiber.dbPerKm(qchNm);
		final double alphaQ = alphaQdb * FiberAttenuationModel.DB_TO_NEPER;
		final double lambdaQm = qchNm * 1e-9;
		final double filterNm = lambdaQm * lambdaQm * parameters.getQchBandwidthHz() / SpontaneousRamanModel.C * 1e9;

		double sameW = 0.0;

		for (ClassicalChannel ch : sameCore) {
			final double lambdaC = ch.getWavelengthNm();
			final double alphaC = fiber.nepersPerKm(lambdaC);
			final double g;

			if (forwardForm == ForwardForm.EQUAL_LOSS) {
				g = SpontaneousRamanModel.forwardLengthPaperKm(alphaC, lengthKm);
			} else {
				g = SpontaneousRamanModel.forwardLengthKm(alphaC, alphaQ, lengthKm);
			}

			sameW += ch.getPowerW() * raman.crossSectionPerKmNm(lambdaC, qchNm) * g;
		}

		sameW *= filterNm;

		double neighbourW = 0.0;

		if (couplingPerKm > 0.0) {
			for (ClassicalChannel ch : neighbourCores) {
				final double lambdaC = ch.getWavelengthNm();
				final double alphaC = fiber.nepersPerKm(lambdaC);

				final double g = SpontaneousRamanModel.intercoreForwardLengthKm(alphaC, alphaQ,
						couplingPerKm, lengthKm);
				neighbourW += ch.getPowerW() * raman.crossSectionPerKmNm(lambdaC, qchNm) * g;
			}

			neighbourW *= filterNm;
		}

		final double photonsPerJoule = parameters.getDetectorEfficiency() * parameters.getDetectorGateWindowS()
				* detectedFraction * lambdaQm / (parameters.getPlanckConstantJs() * parameters.getSpeedOfLightMPerS());
		final double pSame = sameW * photonsPerJoule;
		final double pNeighbour = neighbourW * photonsPerJoule;
		double nliW = 0.0;

		if (nli != null && !sameCore.isEmpty()) {
			final double[][] g = NonlinearInterferenceModel.grid(sameCore, nliGridHz);

			if (g[1].length > 0) {
				final double centre = NonlinearInterferenceModel.weightedCentreHz(g[1], g[0][0], g[0][1]);
				final double alphaC = fiber.nepersPerKm(SpontaneousRamanModel.C / centre * 1e9);
				nliW = nli.psdWPerHz(g[1], g[0][0], g[0][1], SpontaneousRamanModel.C / lambdaQm, lengthKm, alphaC,
						alphaQ) * parameters.getQchBandwidthHz();
			}
		}

		final double pNli = nliW * photonsPerJoule;
		final double pDark = parameters.getDarkCountProbability();
		final double pb = Math.min(1.0, pDark + pSame + pNeighbour + pNli);

		final QKDPhysicalResult qkd = bb84.evaluateWithAttenuation(alphaQdb, lengthKm, pb);

		return new MultiBandLinkResult(qchNm, lengthKm, alphaQdb, sameW, neighbourW, pDark, pSame, pNeighbour, nliW,
				pNli, qkd);
	}
}
