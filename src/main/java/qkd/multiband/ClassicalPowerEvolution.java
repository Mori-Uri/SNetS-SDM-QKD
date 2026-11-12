package qkd.multiband;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import network.Amplifier;

/**
 * Explicit longitudinal amplifier grid for co-propagating classical pumps.
 * Positions are measured from the source of the directed SNetS link, in km.
 * Quantum photons are NEVER amplified here: they only experience loss from
 * the point where Raman photons are generated to the QKD receiver.
 *
 * Two selectable lumped gain models: ideal (fixed) or compressed by a
 * calibrated aggregate reference launch power. Per-amplifier ASE may leak
 * into the quantum filter only when a leakage factor is explicitly supplied.
 * Dynamic loading and a shared booster stage are available in a snapshot;
 * ISRS and the erbium population-rate equations remain outside the model.
 */
public final class ClassicalPowerEvolution implements Serializable {
	private static final long serialVersionUID = 1L;
	private final double amplifierSpacingKm;
	private final double amplifierGainDb;
	public enum GainModel { IDEAL, SATURATED }
	private GainModel gainModel;
	private double saturationPowerW;
	private double referenceLaunchPowerW;
	private double noiseFigureLinear;
	private double noiseFactorA1;
	private double noiseFactorA2;
	private double aseLeakageLinear;

	/** Optional snapshot of sources on ONE directed fibre/core, including PICh. */
	private List<LoadSource> dynamicSources;
	private double dynamicAlphaNeperKm;
	private double[] dynamicGains;
	private double[] dynamicInputPowersW;
	private MultiBandEdFA spectralModel;

	// A source inserted at the directed-link input traverses the booster.
	// Sources inserted at intermediate trusted nodes do not.
	private double boosterNominalGain = 1.0;
	private double boosterSaturationW = Double.POSITIVE_INFINITY;
	private boolean boosterSaturated = false;
	private double dynamicBoosterGain = 1.0;
	private double dynamicBoosterInputW = 0.0;

	/** Return a new template with an explicit input booster shared by QoT and QKD. */
	public ClassicalPowerEvolution withBooster(double nominalGain, double saturationW,
			boolean saturated) {
		if (!Double.isFinite(nominalGain) || nominalGain < 1.0
				|| !(saturationW > 0.0) || Double.isNaN(saturationW))
			throw new IllegalArgumentException("invalid booster parameters");
		ClassicalPowerEvolution c = copyConfiguration();
		c.boosterNominalGain = nominalGain;
		c.boosterSaturationW = saturationW;
		c.boosterSaturated = saturated;

		return c;
	}

	private ClassicalPowerEvolution copyConfiguration() {
		ClassicalPowerEvolution c = new ClassicalPowerEvolution(amplifierSpacingKm, amplifierGainDb);
		c.gainModel = gainModel;
		c.saturationPowerW = saturationPowerW;
		c.referenceLaunchPowerW = referenceLaunchPowerW;
		c.noiseFigureLinear = noiseFigureLinear;
		c.noiseFactorA1 = noiseFactorA1;
		c.noiseFactorA2 = noiseFactorA2;
		c.aseLeakageLinear = aseLeakageLinear;
		c.spectralModel = spectralModel;
		c.boosterNominalGain = boosterNominalGain;
		c.boosterSaturationW = boosterSaturationW;
		c.boosterSaturated = boosterSaturated;

		return c;
	}

	public boolean hasConfiguredBooster() { return boosterNominalGain != 1.0; }
	public double getBoosterGainLinear() {
		return hasDynamicLoad() ? dynamicBoosterGain : boosterNominalGain;
	}

	public double getBoosterInputPowerW() {
		if (!hasDynamicLoad()) throw new IllegalStateException("no dynamic load snapshot");

		return dynamicBoosterInputW;
	}

	private MultiBandEdFA.State[] spectralStates;

	/** Attach a calibrated multi-band EDFA. All channels in a core share the
     * same gain-compression state; frequency-specific gains/NFs are retained. */
	public ClassicalPowerEvolution withMultiBandEdFA(MultiBandEdFA model) {
		if (model == null) throw new IllegalArgumentException("null EDFA profile");
		ClassicalPowerEvolution c = copyConfiguration();
		c.spectralModel = model.withNoiseFactorModel(
				c.noiseFactorA1, c.noiseFactorA2);

		return c;
	}

	/** Return a template using the same empirical NF law as a classical EDFA. */
	public ClassicalPowerEvolution withNoiseFactorModel(double a1, double a2) {
		// Validate the model before storing it in a persistent configuration.
		Amplifier.noiseFactor(noiseFigureLinear, 0.0, a1, a2, false);
		ClassicalPowerEvolution copy = copyConfiguration();
		copy.noiseFactorA1 = a1;
		copy.noiseFactorA2 = a2;

		if (copy.spectralModel != null) {
			copy.spectralModel = copy.spectralModel.withNoiseFactorModel(a1, a2);
		}

		return copy;
	}

	public double getNoiseFactorA1() { return noiseFactorA1; }
	public double getNoiseFactorA2() { return noiseFactorA2; }

	public boolean hasSpectralModel() { return spectralModel != null; }
	public MultiBandEdFA.State spectralStateAt(int amplifierIndex) {
		if (spectralStates == null || amplifierIndex < 1 || amplifierIndex >= spectralStates.length)
			throw new IllegalArgumentException("no spectral amplifier state at index");

		return spectralStates[amplifierIndex];
	}

	/** Launch/drop coordinates refer to the same directed link as the amplifier grid. */
	public static final class LoadSource implements Serializable {
		private static final long serialVersionUID = 1L;
		public final double launchPowerW, startKm, endKm, frequencyHz, bandwidthHz, modulationPhi;
		public LoadSource(double launchPowerW, double startKm, double endKm) {
			this(launchPowerW, startKm, endKm, Double.NaN, Double.NaN);
		}

		public LoadSource(double launchPowerW, double startKm, double endKm, double frequencyHz) {
			this(launchPowerW, startKm, endKm, frequencyHz, Double.NaN);
		}

		public LoadSource(double launchPowerW, double startKm, double endKm,
				double frequencyHz, double bandwidthHz) {
			this(launchPowerW, startKm, endKm, frequencyHz, bandwidthHz, Double.NaN);
		}

		/** Modulation-dependent EGN Phi, NaN for older physical test sources. */
		public LoadSource(double launchPowerW, double startKm, double endKm,
				double frequencyHz, double bandwidthHz, double modulationPhi) {
			if (!(Double.isNaN(modulationPhi) ||
					(Double.isFinite(modulationPhi) && modulationPhi >= 0.0)))
				throw new IllegalArgumentException("invalid EGN Phi");

			if (!(Double.isNaN(bandwidthHz) ||
					(Double.isFinite(bandwidthHz) && bandwidthHz > 0.0)))
				throw new IllegalArgumentException("invalid classical source bandwidth");

			if (!(Double.isNaN(frequencyHz) || (frequencyHz > 0.0 && Double.isFinite(frequencyHz))))
				throw new IllegalArgumentException("invalid optical carrier frequency");

			if (!Double.isFinite(launchPowerW) || launchPowerW < 0.0
					|| !Double.isFinite(startKm) || startKm < 0.0
					|| !Double.isFinite(endKm) || endKm <= startKm)
				throw new IllegalArgumentException("invalid amplifier load source");
			this.launchPowerW = launchPowerW;
			this.startKm = startKm;
			this.endKm = endKm;
			this.frequencyHz = frequencyHz;
			this.bandwidthHz = bandwidthHz;
			this.modulationPhi = modulationPhi;
		}
	}

	/** An immutable per-evaluation snapshot. No mutation of shared simulation configuration. */
	public ClassicalPowerEvolution withDynamicLoad(List<LoadSource> sources,
			double fibreLengthKm, double alphaNeperKm) {
		if (sources == null || !Double.isFinite(fibreLengthKm) || fibreLengthKm < 0.0
				|| !Double.isFinite(alphaNeperKm) || alphaNeperKm < 0.0)
			throw new IllegalArgumentException("invalid amplifier load snapshot");
		ClassicalPowerEvolution snapshot = copyConfiguration();

		// Only channels already present at z=0 contribute to booster saturation.
		for (LoadSource src : sources)
			if (src.startKm == 0.0) snapshot.dynamicBoosterInputW += src.launchPowerW;
		snapshot.dynamicBoosterGain = Amplifier.gain(
				boosterNominalGain, snapshot.dynamicBoosterInputW,
				boosterSaturationW, boosterSaturated);

		for (LoadSource src : sources) {
			if (src.endKm > fibreLengthKm + 1e-9)
				throw new IllegalArgumentException("load source extends beyond physical directed link");

			if (spectralModel != null && Double.isNaN(src.frequencyHz))
				throw new IllegalArgumentException("spectral EDFA requires a real optical frequency for every load");

			if (spectralModel != null) spectralModel.band(src.frequencyHz);
		}

		snapshot.dynamicSources = Collections.unmodifiableList(new ArrayList<LoadSource>(sources));
		snapshot.dynamicAlphaNeperKm = alphaNeperKm;
		final int count = (int)Math.floor((fibreLengthKm - 1e-9) / amplifierSpacingKm);
		snapshot.dynamicGains = new double[Math.max(0, count) + 1];
		snapshot.dynamicInputPowersW = new double[snapshot.dynamicGains.length];

		if (spectralModel != null)
			snapshot.spectralStates = new MultiBandEdFA.State[snapshot.dynamicGains.length];

		// Track the gain accumulated by every active source only once per EDFA.
		// Previous code recomputed all preceding gains for each source and each
		// amplifier (O(sources * amplifiers^2)); this is O(sources * amplifiers).
		// Insertions and drops are evaluated on the directed fibre, not on QCh.
		final double[] priorGainLog = new double[sources.size()];
		for (int index = 1; index <= count; index++) {
			double z = index * amplifierSpacingKm;
			double input = 0.0;
			final List<MultiBandEdFA.Input> spectralInputs = spectralModel == null ? null
					: new ArrayList<MultiBandEdFA.Input>();

			for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
				final LoadSource src = sources.get(sourceIndex);
				if (src.startKm >= z || src.endKm <= z) continue;
				double logGain = -alphaNeperKm * (z - src.startKm)
						+ priorGainLog[sourceIndex];

				if (src.startKm == 0.0) logGain += Math.log(snapshot.dynamicBoosterGain);

				final double sourceInput = src.launchPowerW * Math.exp(logGain);
				input += sourceInput;

				if (spectralInputs != null)
					spectralInputs.add(new MultiBandEdFA.Input(snapshot.sourceFrequency(src), sourceInput));
			}

			snapshot.dynamicInputPowersW[index] = input;

			if (spectralInputs == null) {
				snapshot.dynamicGains[index] = snapshot.gainFromInput(input);
			} else {
				snapshot.spectralStates[index] = spectralModel.evaluate(spectralInputs);
				// A scalar gain has no physical meaning for a multiband amplifier.
				// Frequency-resolved callers must use gainLinearAt(index,alpha,f).
				snapshot.dynamicGains[index] = Double.NaN;
			}
			for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
				final LoadSource src = sources.get(sourceIndex);
				if (z > src.startKm && z < src.endKm) {
					final double sourceGain = spectralModel == null
							? snapshot.dynamicGains[index]
							: snapshot.spectralStates[index].gain(src.frequencyHz);
					priorGainLog[sourceIndex] += Math.log(sourceGain);
				}
			}
		}

		return snapshot;
	}

	private double sourceFrequency(LoadSource src) {
		return src.frequencyHz;
	}

	private double gainFromInput(double incomingW) {
		final double unsaturated = Math.pow(10.0, amplifierGainDb / 10.0);

		if (gainModel != GainModel.SATURATED) return unsaturated;

		return Amplifier.gain(unsaturated, incomingW, saturationPowerW, true);
	}

	/**
     * Immutable amplifier state shared with the classical QoT layer.
     * Frequency-dependent gain/ASE use the same dynamic-load snapshot as QKD.
     * The returned state contains no mutable circuit or topology reference.
     */
	public qkd.multiband.AmplifierOperatingPoint operatingPointAt(
			int amplifierIndex, double attenuationNeperKm, double frequencyHz,
			double planckJs) {
		if (!hasDynamicLoad())
			throw new IllegalStateException("build a dynamic load snapshot before requesting an operating point");

		if (!(frequencyHz > 0.0) || !Double.isFinite(frequencyHz)
				|| !(planckJs > 0.0) || !Double.isFinite(planckJs))
			throw new IllegalArgumentException("invalid optical frequency or Planck constant");
		double gain = gainLinearAt(amplifierIndex, attenuationNeperKm, frequencyHz);
		double psd = spectralStates == null
				? Amplifier.asePsd(frequencyHz, noiseFigureLinear, gain, planckJs,
						inputPowerWAt(amplifierIndex), noiseFactorA1, noiseFactorA2,
						gainModel == GainModel.SATURATED)
				: spectralStates[amplifierIndex].asePsd(frequencyHz, planckJs);

		return new qkd.multiband.AmplifierOperatingPoint(amplifierIndex,
				amplifierIndex * amplifierSpacingKm, frequencyHz,
				inputPowerWAt(amplifierIndex), gain, psd);
	}

	public boolean hasDynamicLoad() { return dynamicGains != null; }

	/** Read-only list for span-local GN integration. */
	public List<LoadSource> dynamicSources() {
		return dynamicSources == null ? Collections.<LoadSource>emptyList() : dynamicSources;
	}

	/** Aggregate directed-core signal power at a position before insertion/removal
     * of any node-boundary equipment. A signal dropped exactly at z is absent;
     * for receiver-side load use a point infinitesimally before the link end. */
	public double aggregatePowerAt(double zKm, double alphaNeperKm) {
		if (!hasDynamicLoad() || !Double.isFinite(zKm) || zKm < 0.0)
			throw new IllegalArgumentException("invalid aggregate load position");
		double power=0.0;

		for (LoadSource src: dynamicSources) {
			if (src.startKm <= zKm && zKm < src.endKm) {
				power+=src.launchPowerW*powerRatio(src.startKm,zKm,
						alphaNeperKm,src.frequencyHz);
			}
		}

		return power;
	}

	public double inputPowerWAt(int index) {
		if (!hasDynamicLoad() || index < 1 || index >= dynamicInputPowersW.length)
			throw new IllegalArgumentException("no load snapshot for amplifier index");

		return dynamicInputPowersW[index];
	}

	public ClassicalPowerEvolution(double amplifierSpacingKm, double amplifierGainDb) {
		if (!Double.isFinite(amplifierSpacingKm) || amplifierSpacingKm <= 0.0)
			throw new IllegalArgumentException("amplifierSpacingKm must be > 0 and finite");

		if (!Double.isFinite(amplifierGainDb) || amplifierGainDb < 0.0)
			throw new IllegalArgumentException("amplifierGainDb must be finite and >= 0");
		this.amplifierSpacingKm = amplifierSpacingKm;
		this.amplifierGainDb = amplifierGainDb;
		this.gainModel = GainModel.IDEAL;
		this.saturationPowerW = Double.POSITIVE_INFINITY;
		this.referenceLaunchPowerW = 0.0;
		this.noiseFigureLinear = 1.0;
		this.noiseFactorA1 = 0.0;
		this.noiseFactorA2 = 0.0;
		this.aseLeakageLinear = 0.0;
	}

	/**
     * Calibrated lumped-amplifier extension. referenceLaunchPowerW is the aggregate
     * launch power on the considered core (proxy load, not a full multi-channel
     * EDFA dynamic gain simulation). aseLeakageLinear is an experimentally
     * determined leakage factor into the QCh passband; zero disables ASE->QCh.
     */
	public ClassicalPowerEvolution(double spacingKm, double gainDb, GainModel gainModel,
			double saturationPowerDbm, double referenceLaunchPowerDbm,
			double noiseFigureDb, double aseLeakageDb) {
		this(spacingKm, gainDb);

		if (gainModel == null || !Double.isFinite(saturationPowerDbm)
				|| !Double.isFinite(referenceLaunchPowerDbm) || !Double.isFinite(noiseFigureDb)
				|| noiseFigureDb < 0.0 || !Double.isFinite(aseLeakageDb)
				|| aseLeakageDb < 0.0)
			throw new IllegalArgumentException("invalid amplifier parameter");
		this.gainModel = gainModel;
		this.saturationPowerW = Math.pow(10.0, (saturationPowerDbm - 30.0) / 10.0);
		this.referenceLaunchPowerW = Math.pow(10.0, (referenceLaunchPowerDbm - 30.0) / 10.0);

		if (!(saturationPowerW > 0.0) || !Double.isFinite(saturationPowerW)
				|| !(referenceLaunchPowerW > 0.0) || !Double.isFinite(referenceLaunchPowerW))
			throw new IllegalArgumentException("amplifier powers out of floating-point range");
		this.noiseFigureLinear = Math.pow(10.0, noiseFigureDb / 10.0);
		this.aseLeakageLinear = Math.pow(10.0, -aseLeakageDb / 10.0);
	}

	public double getAmplifierSpacingKm() { return amplifierSpacingKm; }
	public double getAmplifierGainDb() { return amplifierGainDb; }
	public GainModel getGainModel() { return gainModel; }
	public boolean usesGainSaturation() {
		return spectralModel == null ? gainModel == GainModel.SATURATED
				: spectralModel.isSaturated();
	}

	public double saturationPowerW() {
		return spectralModel == null ? saturationPowerW
				: spectralModel.getSaturationOutputW();
	}

	public double noiseFigureAt(double frequencyHz) {
		return spectralModel == null ? noiseFigureLinear
				: spectralModel.noiseFigureAt(frequencyHz);
	}

	/** Gain linear for the i-th amplifier, using the actual preceding gains and span losses
     * of the reference aggregate launch power. Index i begins at 1. */
	public double gainLinearAt(int amplifierIndex, double attenuationNeperKm) {
		if (amplifierIndex < 1 || !Double.isFinite(attenuationNeperKm) || attenuationNeperKm < 0)
			throw new IllegalArgumentException("invalid amplifier index or attenuation");

		if (hasDynamicLoad()) {
			if (spectralStates != null)
				throw new IllegalStateException("multiband amplifier gain requires a carrier frequency");
			if (amplifierIndex >= dynamicGains.length)
				throw new IllegalArgumentException("amplifier index outside load snapshot");

			// Gain is determined by the aggregate-band attenuation used to
			// build the snapshot. Individual signal loss still uses its own alpha.
			return dynamicGains[amplifierIndex];
		}

		double incoming = referenceLaunchPowerW;
		double gain = 1.0;

		for (int k = 1; k <= amplifierIndex; k++) {
			incoming *= Math.exp(-attenuationNeperKm * amplifierSpacingKm);
			gain = gainFromInput(incoming);
			incoming *= gain;
		}

		return gain;
	}

	/** The frequency-dependent effective gain of an amplifier snapshot. */
	public double gainLinearAt(int amplifierIndex, double attenuationNeperKm, double frequencyHz) {
		if (spectralStates != null) {
			if (amplifierIndex < 1 || amplifierIndex >= spectralStates.length)
				throw new IllegalArgumentException("spectral gain index out of range");

			return spectralStates[amplifierIndex].gain(frequencyHz);
		}

		return gainLinearAt(amplifierIndex, attenuationNeperKm);
	}

	/** Exact input power for the legacy single-reference-source evolution. */
	private double referenceAmplifierInputW(int amplifierIndex, double attenuationNeperKm) {
		if (hasDynamicLoad()) return inputPowerWAt(amplifierIndex);

		double incoming = referenceLaunchPowerW;

		for (int index = 1; index <= amplifierIndex; index++) {
			incoming *= Math.exp(-attenuationNeperKm * amplifierSpacingKm);

			if (index < amplifierIndex) incoming *= gainFromInput(incoming);
		}

		return incoming;
	}

	/** Sum of per-amplifier ASE reaching QCh after a calibrated filter/transfer factor.
     * Units: W in the quantum filter bandwidth at its receiver. QCh is not amplified.
     * The emission at the QCh frequency is NOT assumed without leakage calibration.
     */
	public double aseAtQuantumReceiverW(double startKm, double endKm, double alphaClassical,
			double alphaQuantum, double classicalFrequencyHz, double quantumBandwidthHz, double planckJs) {
		if (!Double.isFinite(startKm) || !Double.isFinite(endKm) || startKm < 0.0
				|| !Double.isFinite(alphaClassical) || alphaClassical < 0.0
				|| !Double.isFinite(alphaQuantum) || alphaQuantum < 0.0
				|| !(classicalFrequencyHz > 0.0) || !Double.isFinite(classicalFrequencyHz)
				|| !Double.isFinite(quantumBandwidthHz) || quantumBandwidthHz < 0.0
				|| !Double.isFinite(planckJs) || planckJs <= 0.0)
			throw new IllegalArgumentException("invalid classical-ASE/QCh receiver transfer parameters");
		if (endKm <= startKm || aseLeakageLinear == 0.0) return 0.0;
		final int last = (int) Math.floor((endKm - 1e-9) / amplifierSpacingKm);
		double ase = 0.0;

		// Classical booster ASE is generated at the directed-link input. Its
		// transfer to a QCh filter is still controlled by the EXPLICIT spectral
		// leakage parameter; no quantum gain or spontaneous leakage is assumed.
		if (startKm == 0.0 && getBoosterGainLinear() > 1.0) {
			final double boosterGain = getBoosterGainLinear();
			final double boosterPsd = Amplifier.asePsd(classicalFrequencyHz,
					noiseFigureAt(classicalFrequencyHz), boosterGain, planckJs,
					hasDynamicLoad() ? dynamicBoosterInputW : referenceLaunchPowerW,
					noiseFactorA1, noiseFactorA2, boosterSaturated);
			ase += boosterPsd * quantumBandwidthHz * aseLeakageLinear
					* powerRatio(0.0, endKm, alphaClassical, classicalFrequencyHz)
					/ boosterGain;
		}

		for (int index = 1; index <= last; index++) {
			double z = index * amplifierSpacingKm;

			if (z <= startKm) continue;
			final double gain = gainLinearAt(index, alphaClassical, classicalFrequencyHz);
			final double psd = spectralStates == null
					? Amplifier.asePsd(classicalFrequencyHz, noiseFigureLinear, gain,
							planckJs, referenceAmplifierInputW(index, alphaClassical),
							noiseFactorA1, noiseFactorA2, gainModel == GainModel.SATURATED)
					: spectralStates[index].asePsd(classicalFrequencyHz, planckJs);

			// Classical-band ASE propagates to the receiver through later line
			// EDFAs; only the final calibrated coupling enters the QCh filter.
			ase += psd * quantumBandwidthHz * aseLeakageLinear
					* powerRatio(z, endKm, alphaClassical, classicalFrequencyHz);
		}

		return ase;
	}

	/**
     * Power ratio at distance x, relative to launch at sourceKm.
     * Amplifiers at n*spacing act on the classical pump only. A source launched
     * at the location of an amplifier is assumed to be inserted AFTER it.
     */
	public double powerRatio(double sourceKm, double xKm, double alphaClassicalNeperKm) {
		return powerRatio(sourceKm, xKm, alphaClassicalNeperKm, Double.NaN);
	}

	public double powerRatio(double sourceKm, double xKm, double alphaClassicalNeperKm,
			double frequencyHz) {
		check(sourceKm, xKm, alphaClassicalNeperKm);
		if (spectralStates != null && !Double.isFinite(frequencyHz))
			throw new IllegalArgumentException("multiband power evolution requires optical frequency");

		if (xKm < sourceKm) return 0.0;
		final double eps = 1e-10 * amplifierSpacingKm;
		final long first = (long) Math.floor((sourceKm + eps) / amplifierSpacingKm) + 1;
		long last = (long) Math.floor((xKm + eps) / amplifierSpacingKm);

		if (hasDynamicLoad()) last = Math.min(last, dynamicGains.length - 1);
		final long amplifiers = Math.max(0L, last - first + 1L);
		double gainLog = (sourceKm == 0.0 && xKm > sourceKm)
				? Math.log(getBoosterGainLinear()) : 0.0;

		for (long index = first; index <= last; index++) {
			gainLog += Math.log(Double.isNaN(frequencyHz)
					? gainLinearAt((int) index, alphaClassicalNeperKm)
					: gainLinearAt((int) index, alphaClassicalNeperKm, frequencyHz));
		}

		return Math.exp(-alphaClassicalNeperKm * (xKm - sourceKm) + gainLog);
	}

	/**
     * Forward effective Raman length in km, referenced to the pump LAUNCH power.
     * Integrates over [startKm,endKm] with quantum loss from z to endKm.
     * The inter-core weight 1-exp(-2*h*(z-startKm)) follows the forward
     * coupling convention used in SpontaneousRamanModel.
     */
	public double forwardEffectiveLengthKm(double startKm, double endKm,
			double sourceKm, double alphaClassical, double alphaQuantum,
			double couplingPerKm) {
		if (!Double.isFinite(startKm) || !Double.isFinite(endKm)
				|| startKm < 0.0 || endKm <= startKm || sourceKm < 0.0
				|| sourceKm > endKm || !Double.isFinite(sourceKm)
				|| !Double.isFinite(alphaClassical) || alphaClassical < 0.0
				|| !Double.isFinite(alphaQuantum) || alphaQuantum < 0.0
				|| !Double.isFinite(couplingPerKm) || couplingPerKm < 0.0)
			throw new IllegalArgumentException("invalid longitudinal Raman parameters");
		final double length = endKm - startKm;
		final int steps = Math.max(1, (int) Math.ceil(length / 0.25));
		final double dz = length / steps;
		double integral = 0.0;

		for (int i = 0; i < steps; i++) {
			final double z = startKm + (i + 0.5) * dz;

			if (z < sourceKm) continue;
			double coupling = couplingPerKm == 0.0 ? 1.0
					: -Math.expm1(-2.0 * couplingPerKm * (z - startKm));
			integral += powerRatio(sourceKm, z, alphaClassical)
					* Math.exp(-alphaQuantum * (endKm - z)) * coupling * dz;
		}

		return integral;
	}

	/**
     * Forward Raman integration for pumps terminating before the full fibre
     * length (for example, the public channel of a different QKD span).
     */
	public double forwardEffectiveLengthKm(double startKm, double endKm,
			double sourceKm, double pumpEndKm, double alphaClassical,
			double alphaQuantum, double couplingPerKm) {
		return forwardEffectiveLengthKm(startKm,endKm,sourceKm,pumpEndKm,
				alphaClassical,alphaQuantum,couplingPerKm,Double.NaN);
	}

	public double forwardEffectiveLengthKm(double startKm, double endKm,
			double sourceKm, double pumpEndKm, double alphaClassical,
			double alphaQuantum, double couplingPerKm, double frequencyHz) {
		if (!Double.isFinite(pumpEndKm) || pumpEndKm < sourceKm || pumpEndKm < 0.0)
			throw new IllegalArgumentException("invalid forward pump end");

		if (!Double.isFinite(startKm) || !Double.isFinite(endKm)
				|| startKm < 0.0 || endKm <= startKm || !Double.isFinite(sourceKm)
				|| sourceKm < 0.0 || !Double.isFinite(alphaClassical)
				|| alphaClassical < 0.0 || !Double.isFinite(alphaQuantum)
				|| alphaQuantum < 0.0 || !Double.isFinite(couplingPerKm)
				|| couplingPerKm < 0.0)
			throw new IllegalArgumentException("invalid forward Raman geometry");
		final double lo = Math.max(startKm, sourceKm);
		final double hi = Math.min(endKm, pumpEndKm);

		if (hi <= lo) return 0.0;
		final int steps = Math.max(1, (int) Math.ceil((hi - lo) / 0.10));
		final double dz = (hi - lo) / steps;
		double total = 0.0;

		for (int i = 0; i < steps; i++) {
			final double z = lo + (i + 0.5) * dz;
			final double coupling = couplingPerKm == 0.0 ? 1.0
					: -Math.expm1(-2.0 * couplingPerKm * (z - startKm));
			total += powerRatio(sourceKm, z, alphaClassical, frequencyHz)
					* Math.exp(-alphaQuantum * (endKm - z)) * coupling * dz;
		}

		return total;
	}

	private static void check(double sourceKm, double xKm, double attenuation) {
		if (!Double.isFinite(sourceKm) || !Double.isFinite(xKm)
				|| !Double.isFinite(attenuation) || sourceKm < 0.0
				|| xKm < 0.0 || attenuation < 0.0)
			throw new IllegalArgumentException("invalid longitudinal position or attenuation");
	}
}
