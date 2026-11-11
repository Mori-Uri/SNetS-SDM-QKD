package qkd.multiband;

import java.io.Serializable;
import java.util.Locale;

/**
 * Multi-band options of a simulation (group {@code multiband} of the case file {@code qkdPhysicalLayer}), Parte 43.
 * When the group is absent the simulator behaves exactly as before (C-band quadratic loss, intercore crosstalk only).
 *
 * <p><b>How the architectures map onto the simulator.</b> The QKD core of the network file keeps holding the QChs
 * (slots, TDM, PICh, Q-TRx as today). In multi-band mode it is a <i>virtual</i> core that stands for:</p>
 * <ul>
 *   <li>{@link Architecture#MF}: a dedicated dark fibre. No classical noise reaches the QCh (p_b = p_dark); linear
 *       crosstalk is switched off.</li>
 *   <li>{@link Architecture#MB}: the O/E (or other) band of the same single-core fibre used by the classical traffic.
 *       The classical core is {@code hostCoreId}; its circuits generate same-core SpRS on the QCh.</li>
 *   <li>{@link Architecture#MBSDM}: the QCh band of core {@code hostCoreId} of a multi-core fibre. Circuits of the
 *       host core generate same-core SpRS and, when {@code intercoreRaman} is true, circuits of the cores adjacent
 *       to the host generate intercore SpRS ([CAI20]).</li>
 * </ul>
 * <p>The virtual QKD core is never used by classical algorithms (qabne already excludes it) and should be declared
 * with no adjacent cores in the network file. Linear intercore crosstalk on the QCh is counted only when the QCh
 * wavelength falls inside the classical slot grid (in-band); out of band it is removed by the QCh filter.</p>
 *
 * <p><b>Raman models</b> ({@link RamanModel}), selectable like the crosstalk and modulation variants:</p>
 * <ul>
 *   <li>{@code nenhum}: SpRS neglected, as assumed by [DIBAJ25] for the O band;</li>
 *   <li>{@code artigo}: convention of [MEHDI24]/[DIBAJ25] eq. 6 (forward effective length L e^{-a_c L}, half of the
 *       photons detected);</li>
 *   <li>{@code fisico}: exact forward effective length and all photons in the filter detected ([ERAERDS10]).</li>
 * </ul>
 *
 * <p>Classical channels are placed on the frequency axis by their slots: slot k (1-based) of the host core covers
 * [f0 + (k-1) w, f0 + k w], where f0 derives from physicalLayer.centerFrequency,
 * number of classical slots and slot width. QKDMesh overrides the legacy classicalBandStartThz.</p>
 *
 * <p>Immutable and thread-safe.</p>
 *
 * <p>Referências: [MEHDI24], [DIBAJ25], [ERAERDS10], [CAI20]; ver {@link MultiBandQkdLinkModel}.</p>
 */
public final class MultiBandSettings implements Serializable {

	private static final long serialVersionUID = 1L;

	/** Fibre architecture of the quantum channels. */
	public enum Architecture {

		/** Multi-fibre: QCh in a dedicated dark fibre. */
		MF,

		/** Multi-band: QCh in another band of the classical single-core fibre. */
		MB,

		/** Multi-band SDM: QCh in another band of one core of a multi-core fibre. */
		MBSDM;

		/** Parses "mf", "mb", "mbsdm" / "mb-sdm" (case-insensitive). */
		public static Architecture parse(String s) {
			final String v = s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");

			switch (v) {
				case "mf": return MF;
				case "mb": return MB;
				case "mbsdm": return MBSDM;
				default: throw new IllegalArgumentException("multiband.architecture must be MF, MB or MBSDM: " + s);
			}
		}
	}

	/** Spontaneous Raman scattering model. */
	public enum RamanModel {

		/** SpRS neglected ([DIBAJ25] assumption for the O band). */
		NENHUM,

		/** [MEHDI24]/[DIBAJ25] convention: L e^{-a_c L}, detected fraction 0.5. */
		ARTIGO,

		/** Exact forward effective length, detected fraction 1. */
		FISICO;

		/** Parses "nenhum"/"none", "artigo"/"paper", "fisico"/"physical" (case-insensitive). */
		public static RamanModel parse(String s) {
			final String v = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);

			switch (v) {
				case "nenhum": case "none": return NENHUM;
				case "artigo": case "paper": return ARTIGO;
				case "fisico": case "físico": case "physical": return FISICO;
				default: throw new IllegalArgumentException("multiband.ramanModel must be nenhum, artigo or fisico: " + s);
			}
		}
	}

	private final Architecture architecture;
	private final RamanModel ramanModel;
	private final FiberAttenuationModel attenuation;
	private final SpontaneousRamanModel raman;
	private final int hostCoreId;
	private final double classicalBandStartHz;
	private final boolean intercoreRaman;
	private final double qchCenterNm;

	/** Parte 45: NLI on the QCh (null = not modelled). */
	private NonlinearInterferenceModel nli;

	/** Optional longitudinal classical pump power evolution (null retains analytic legacy model). */
	private ClassicalPowerEvolution powerEvolution;
	private boolean longitudinalNli;
	private boolean longitudinalRaman;

	/** Discrete co-propagating CW FWM spectral products in a QCh filter. */
	private boolean fwmEnabled;
	private double fwmGammaPerWKm;
	private double fwmDispersionPsNmKm;
	private double fwmSlopePsNm2Km;
	private boolean fwmPeakEnvelope;

	/** Stage 1: explicit same-band coexistence rule. */
	private boolean intrabandSeparationEnabled;
	private double minimumQchClassicalSeparationHz;

	/**
     * @param architecture MF, MB or MBSDM
     * @param ramanModel SpRS model
     * @param attenuation loss model of the fibre, or {@code null} to keep the quadratic C-band fit of
     *        {@code qkdPhysicalLayer.fiber}
     * @param raman SpRS cross-section model
     * @param hostCoreId classical core that shares the fibre/core with the QCh (MB, MBSDM); ignored for MF
     * @param classicalBandStartHz lower edge of classical slot 1 in Hz
     * @param intercoreRaman MBSDM: include SpRS from the cores adjacent to the host core
     * @param qchCenterNm centre of the QCh grid in nm
     */
	public MultiBandSettings(Architecture architecture, RamanModel ramanModel, FiberAttenuationModel attenuation,
			SpontaneousRamanModel raman, int hostCoreId, double classicalBandStartHz, boolean intercoreRaman,
			double qchCenterNm) {
		if (architecture == null || ramanModel == null || raman == null) {
			throw new IllegalArgumentException("architecture, ramanModel and raman cannot be null");
		}

		if (!(classicalBandStartHz > 0.0) || Double.isInfinite(classicalBandStartHz)) {
			throw new IllegalArgumentException("classicalBandStartHz must be finite and > 0");
		}

		if (!(qchCenterNm > 0.0) || Double.isInfinite(qchCenterNm)) {
			throw new IllegalArgumentException("qchCenterNm must be finite and > 0");
		}

		if (architecture != Architecture.MF && hostCoreId < 0) {
			throw new IllegalArgumentException("multiband.hostCoreId must be >= 0 for MB and MBSDM");
		}

		this.architecture = architecture;
		this.ramanModel = ramanModel;
		this.attenuation = attenuation;
		this.raman = raman;
		this.hostCoreId = hostCoreId;
		this.classicalBandStartHz = classicalBandStartHz;
		this.intercoreRaman = intercoreRaman;
		this.qchCenterNm = qchCenterNm;

	}

	public Architecture getArchitecture() { return architecture; }

	public RamanModel getRamanModel() { return ramanModel; }

	/** Loss model, or {@code null} when the quadratic fit of {@code qkdPhysicalLayer.fiber} is kept. */
	public FiberAttenuationModel getAttenuation() { return attenuation; }

	public SpontaneousRamanModel getRaman() { return raman; }

	public int getHostCoreId() { return hostCoreId; }

	public double getClassicalBandStartHz() { return classicalBandStartHz; }

	/** Align the classical DCh/PICh frequency grid with SNetS PhysicalLayer. */
	public MultiBandSettings withClassicalBandStartHz(double lowerHz) {
		MultiBandSettings s = new MultiBandSettings(architecture, ramanModel, attenuation, raman,
				hostCoreId, lowerHz, intercoreRaman, qchCenterNm);
		s.nli = nli;
		s.powerEvolution = powerEvolution;
		s.longitudinalNli = longitudinalNli;
		s.longitudinalRaman = longitudinalRaman;
		s.intrabandSeparationEnabled = intrabandSeparationEnabled;
		s.minimumQchClassicalSeparationHz = minimumQchClassicalSeparationHz;
		s.fwmEnabled = fwmEnabled;
		s.fwmGammaPerWKm = fwmGammaPerWKm;
		s.fwmDispersionPsNmKm = fwmDispersionPsNmKm;
		s.fwmSlopePsNm2Km = fwmSlopePsNm2Km;
		s.fwmPeakEnvelope = fwmPeakEnvelope;
		return s;
	}


	public boolean isIntercoreRaman() { return intercoreRaman; }

	public double getQchCenterNm() { return qchCenterNm; }


	/**
     * Parte 45: copy with the GN-model NLI of the host-core classical circuits added to the QCh noise
     * ({@link NonlinearInterferenceModel}); {@code null} removes it.
     */
	public MultiBandSettings withNli(NonlinearInterferenceModel model) {
		final MultiBandSettings s = new MultiBandSettings(architecture, ramanModel, attenuation, raman, hostCoreId,
				classicalBandStartHz, intercoreRaman, qchCenterNm);
		s.nli = model;
		s.powerEvolution = powerEvolution;
		s.longitudinalNli = longitudinalNli;
		s.longitudinalRaman = longitudinalRaman;
		s.intrabandSeparationEnabled = intrabandSeparationEnabled;
		s.minimumQchClassicalSeparationHz = minimumQchClassicalSeparationHz;
		s.fwmEnabled = fwmEnabled;
		s.fwmGammaPerWKm = fwmGammaPerWKm;
		s.fwmDispersionPsNmKm = fwmDispersionPsNmKm;
		s.fwmSlopePsNm2Km = fwmSlopePsNm2Km;
		s.fwmPeakEnvelope = fwmPeakEnvelope;

		return s;
	}

	/** Enable an explicit gain grid for forward Raman, without changing other models. */
	public MultiBandSettings withPowerEvolution(ClassicalPowerEvolution evolution) {
		MultiBandSettings s = withNli(nli);
		s.powerEvolution = evolution;

		return s;
	}

	public ClassicalPowerEvolution getPowerEvolution() { return powerEvolution; }
	public boolean isLongitudinalRaman() { return longitudinalRaman; }
	public MultiBandSettings withLongitudinalRaman(boolean enabled) {
		MultiBandSettings s = withNli(nli);
		s.longitudinalRaman = enabled;

		return s;
	}

	public boolean isLongitudinalNli() { return longitudinalNli; }
	public MultiBandSettings withLongitudinalNli(boolean enabled) {
		MultiBandSettings s = withNli(nli);
		s.longitudinalNli = enabled;

		return s;
	}

	/** Parte 45: NLI model, or {@code null}. */
	public NonlinearInterferenceModel getNli() { return nli; }

	/**
     * Copy with explicit intraband coexistence separation. The 200-GHz value
     * commonly used in the supplied Moghaddam-2021 scenario is not hard-coded:
     * it is a configurable experimental parameter.
     */
	public MultiBandSettings withIntraband(boolean enabled, double minimumSeparationHz) {
		if (!Double.isFinite(minimumSeparationHz) || minimumSeparationHz < 0.0)
			throw new IllegalArgumentException("minimum intraband separation must be finite and >= 0");
		MultiBandSettings s = new MultiBandSettings(architecture, ramanModel, attenuation, raman, hostCoreId,
				classicalBandStartHz, intercoreRaman, qchCenterNm);
		s.nli = nli;
		s.powerEvolution = powerEvolution;
		s.longitudinalNli = longitudinalNli;
		s.longitudinalRaman = longitudinalRaman;
		s.intrabandSeparationEnabled = enabled;
		s.minimumQchClassicalSeparationHz = minimumSeparationHz;

		return s;
	}

	public MultiBandSettings withFwm(boolean enabled, double gammaPerWKm, double dispersionPsNmKm,
			double slopePsNm2Km, boolean peak) {
		if (!Double.isFinite(gammaPerWKm) || gammaPerWKm < 0.0
				|| !Double.isFinite(dispersionPsNmKm) || !Double.isFinite(slopePsNm2Km))
			throw new IllegalArgumentException("invalid discrete FWM parameters");
		MultiBandSettings s = withNli(nli);
		s.fwmEnabled = enabled;
		s.fwmGammaPerWKm = gammaPerWKm;
		s.fwmDispersionPsNmKm = dispersionPsNmKm;
		s.fwmSlopePsNm2Km = slopePsNm2Km;
		s.fwmPeakEnvelope = peak;
		return s;
	}
	public boolean isFwmEnabled() { return fwmEnabled; }
	public double getFwmGammaPerWKm() { return fwmGammaPerWKm; }
	public double getFwmDispersionPsNmKm() { return fwmDispersionPsNmKm; }
	public double getFwmSlopePsNm2Km() { return fwmSlopePsNm2Km; }
	public boolean isFwmPeakEnvelope() { return fwmPeakEnvelope; }

	public boolean isIntrabandSeparationEnabled() { return intrabandSeparationEnabled; }
	public double getMinimumQchClassicalSeparationHz() { return minimumQchClassicalSeparationHz; }

	/** Parte 45: true when the NLI of the classical channels reaches the QCh (MB/MBSDM with an NLI model). */
	public boolean hasNli() {
		return nli != null && architecture != Architecture.MF;
	}

	/** True when classical channels generate SpRS on the QCh (MB/MBSDM with a Raman model other than nenhum). */
	public boolean hasRaman() {
		return architecture != Architecture.MF && ramanModel != RamanModel.NENHUM;
	}

	/** Forward effective-length form of the selected Raman model. */
	public MultiBandQkdLinkModel.ForwardForm getForwardForm() {
		return ramanModel == RamanModel.ARTIGO ? MultiBandQkdLinkModel.ForwardForm.EQUAL_LOSS
				: MultiBandQkdLinkModel.ForwardForm.EXACT;
	}

	/** Fraction of the SpRS photons in the QCh filter that are detected. */
	public double getDetectedFraction() {
		return ramanModel == RamanModel.ARTIGO ? MultiBandQkdLinkModel.PAPER_DETECTED_FRACTION
				: MultiBandQkdLinkModel.PHYSICAL_DETECTED_FRACTION;
	}

	/** Centre frequency in Hz of the slots [first, last] (1-based) of a classical core with the given slot width. */
	public double slotCentreHz(int firstSlot, int lastSlot, double slotWidthHz) {
		return classicalBandStartHz + ((firstSlot + lastSlot) / 2.0 - 0.5) * slotWidthHz;
	}

	/**
     * True when the QCh wavelength is inside the classical slot grid of a core with {@code slots} slots of width
     * {@code slotWidthHz} (linear crosstalk is then in-band and counted).
     */
	public boolean isInClassicalBand(double qchNm, int slots, double slotWidthHz) {
		final double f = SpontaneousRamanModel.C / (qchNm * 1e-9);

		return f >= classicalBandStartHz && f <= classicalBandStartHz + slots * slotWidthHz;
	}

	@Override
	public String toString() {
		return String.format(Locale.ROOT,
				"MultiBandSettings[%s, raman=%s, host=%d, intercore=%s, qch=%.2f nm, classical from %.3f THz, loss=%s, intrabandGuard=%.1f GHz]",
				architecture, ramanModel, hostCoreId, intercoreRaman, qchCenterNm,
				classicalBandStartHz * 1e-12, attenuation == null ? "quadratica" : "g652d",
				intrabandSeparationEnabled ? minimumQchClassicalSeparationHz * 1e-9 : 0.0);
	}
}
