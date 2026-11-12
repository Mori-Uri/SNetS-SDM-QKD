package qkd.multiband;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import network.Amplifier;

/**
 * Frequency-resolved, shared-saturation EDFA surrogate for co-propagating
 * classical carriers. Each configured band has a small-signal gain and NF;
 * By default ALL bands compete for a common finite output-power reservoir.
 * An explicit PER_BAND option models separate, independently pumped gain
 * blocks (each with the configured output-saturation scale). This is NOT a
 * rate-equation erbium population inversion model: profiles and saturation
 * must be calibrated, particularly for L-band and hybrid C+L gain blocks.
 *
 * All powers in W; frequencies Hz; gains and noise factors linear.
 * The model neither amplifies quantum photons nor assumes C-band ASE leaks
 * into a distant quantum band. No reverse-fibre propagation is evaluated.
 */
public final class MultiBandEdFA implements Serializable {
	private static final long serialVersionUID = 1L;

	/** SHARED: one gain reservoir; PER_BAND: independently pumped gain blocks.
	 * Neither configuration implements erbium population rate equations. */
	public enum SaturationScope {
		SHARED, PER_BAND;
		public static SaturationScope parse(String raw) {
			if (raw == null || "shared".equalsIgnoreCase(raw.trim())) return SHARED;
			if ("per-band".equalsIgnoreCase(raw.trim()) || "per_band".equalsIgnoreCase(raw.trim())
					|| "perband".equalsIgnoreCase(raw.trim())) return PER_BAND;
			throw new IllegalArgumentException("edfaSaturationScope must be shared or per-band: " + raw);
		}
	}
	public static final class Band implements Serializable {
		private static final long serialVersionUID = 1L;
		public final String name;
		public final double lowerHz, upperHz, smallSignalGain, noiseFigure;
		public Band(String name, double lowerHz, double upperHz,
				double gainDb, double noiseFigureDb) {
			if (name == null || name.trim().isEmpty() || !(lowerHz > 0)
					|| !(upperHz > lowerHz) || !Double.isFinite(upperHz)
					|| !Double.isFinite(gainDb) || gainDb < 0.0
					|| !Double.isFinite(noiseFigureDb) || noiseFigureDb < 0.0)
				throw new IllegalArgumentException("invalid EDFA band");
			this.name = name;
			this.lowerHz = lowerHz; this.upperHz = upperHz;
			this.smallSignalGain = Math.pow(10.0, gainDb / 10.0);
			this.noiseFigure = Math.pow(10.0, noiseFigureDb / 10.0);
		}

		public boolean contains(double hz) {
			return hz >= lowerHz && hz < upperHz;
		}
	}

	/** Power already attenuated to an amplifier input, with physical frequency. */
	public static final class Input implements Serializable {
		private static final long serialVersionUID = 1L;
		public final double frequencyHz, powerW;
		public Input(double frequencyHz, double powerW) {
			if (!(frequencyHz > 0.0) || !Double.isFinite(frequencyHz)
					|| !Double.isFinite(powerW) || powerW < 0.0)
				throw new IllegalArgumentException("invalid input channel");
			this.frequencyHz = frequencyHz; this.powerW = powerW;
		}
	}

	/** One immutable result of an amplifier; usable by both QoT and QKD. */
	/** Immutable, frequency-resolved operating point for one EDFA. */
	public static final class State implements Serializable {
		private static final long serialVersionUID = 1L;
		private final MultiBandEdFA model;
		private final double[] compression, bandInputW;
		private final double inputW, outputSignalW;
		private State(MultiBandEdFA model, double[] compression, double[] bandInputW,
				double inputW, double outputSignalW) {
			this.model = model;
			this.compression = compression.clone();
			this.bandInputW = bandInputW.clone();
			this.inputW = inputW;
			this.outputSignalW = outputSignalW;
		}

		public double getInputW() { return inputW; }
		public double getOutputSignalW() { return outputSignalW; }
		/** Unambiguous only for a physically shared gain reservoir. */
		public double getCompression() {
			if (model.saturationScope != SaturationScope.SHARED)
				throw new IllegalStateException("per-band EDFA has no single compression factor");
			return compression[0];
		}
		public double getCompression(double frequencyHz) {
			return compression[model.bandIndex(frequencyHz)];
		}
		public double getBandInputW(double frequencyHz) {
			return bandInputW[model.bandIndex(frequencyHz)];
		}
		public double gain(double frequencyHz) {
			final int index = model.bandIndex(frequencyHz);
			return Amplifier.gainFromCompression(
					model.bands.get(index).smallSignalGain, compression[index]);
		}
		public double asePsd(double frequencyHz, double planckJs) {
			final int index = model.bandIndex(frequencyHz);
			final double saturationInputW = model.saturationScope == SaturationScope.SHARED
					? inputW : bandInputW[index];
			return Amplifier.asePsd(frequencyHz, model.bands.get(index).noiseFigure,
					gain(frequencyHz), planckJs, saturationInputW,
					model.noiseFactorA1, model.noiseFactorA2, model.saturated);
		}
	}

	private final List<Band> bands;
	private final double saturationOutputW;
	private final boolean saturated;
	private final SaturationScope saturationScope;
	private final double noiseFactorA1;
	private final double noiseFactorA2;

	public MultiBandEdFA withNoiseFactorModel(double a1, double a2) {
		Amplifier.noiseFactor(1.0, 0.0, a1, a2, false);

		return new MultiBandEdFA(bands, saturationOutputW, saturated, a1, a2, saturationScope);
	}

	private MultiBandEdFA(List<Band> profiles, double saturationOutputW,
			boolean saturated, double a1, double a2, SaturationScope scope) {
		this.saturationScope = scope;
		this.bands = Collections.unmodifiableList(new ArrayList<Band>(profiles));
		this.saturationOutputW = saturationOutputW;
		this.saturated = saturated;
		this.noiseFactorA1 = a1;
		this.noiseFactorA2 = a2;
	}

	public MultiBandEdFA(List<Band> profiles, double saturationOutputDbm, boolean saturated) {
		this(profiles, saturationOutputDbm, saturated, SaturationScope.SHARED);
	}

	/** Explicit opt-in for separately pumped EDFA blocks (e.g. C and L). */
	public MultiBandEdFA(List<Band> profiles, double saturationOutputDbm,
			boolean saturated, SaturationScope scope) {
		if (scope == null) throw new IllegalArgumentException("null EDFA saturation scope");
		this.saturationScope = scope;
		if (profiles == null || profiles.isEmpty() || !Double.isFinite(saturationOutputDbm))
			throw new IllegalArgumentException("EDFA requires calibrated band profiles");
		ArrayList<Band> sorted = new ArrayList<Band>(profiles);
		Collections.sort(sorted, new Comparator<Band>() {
			public int compare(Band a, Band b) { return Double.compare(a.lowerHz, b.lowerHz); }
		});

		for (int i = 1; i < sorted.size(); i++) {
			if (sorted.get(i).lowerHz < sorted.get(i-1).upperHz)
				throw new IllegalArgumentException("overlapping EDFA gain bands");
		}

		this.bands = Collections.unmodifiableList(sorted);
		this.saturationOutputW = Math.pow(10.0, (saturationOutputDbm - 30.0) / 10.0);

		if (!(saturationOutputW > 0.0) || !Double.isFinite(saturationOutputW))
			throw new IllegalArgumentException("EDFA saturation power out of range");
		this.saturated = saturated;
		this.noiseFactorA1 = 0.0;
		this.noiseFactorA2 = 0.0;
	}

	public List<Band> getBands() { return bands; }
	public boolean isSaturated() { return saturated; }
	public SaturationScope getSaturationScope() { return saturationScope; }
	public double getSaturationOutputW() { return saturationOutputW; }
	public double noiseFigureAt(double frequencyHz) { return band(frequencyHz).noiseFigure; }
	private int bandIndex(double frequencyHz) {
		for (int index = 0; index < bands.size(); index++)
			if (bands.get(index).contains(frequencyHz)) return index;
		throw new IllegalArgumentException("frequency outside configured EDFA bands: " + frequencyHz);
	}

	public Band band(double frequencyHz) { return bands.get(bandIndex(frequencyHz)); }

	public State evaluate(List<Input> inputs) {
		if (inputs == null) throw new IllegalArgumentException("null EDFA inputs");
		final double[] bandInput = new double[bands.size()];
		final double[] bandUnsaturatedOutput = new double[bands.size()];
		double total = 0.0;
		double unsaturatedOutput = 0.0;

		for (Input source : inputs) {
			if (source == null) throw new IllegalArgumentException("null EDFA input channel");
			final int index = bandIndex(source.frequencyHz);
			bandInput[index] += source.powerW;
			bandUnsaturatedOutput[index] += source.powerW * bands.get(index).smallSignalGain;
			total += source.powerW;
			unsaturatedOutput += source.powerW * bands.get(index).smallSignalGain;
		}

		if (!Double.isFinite(total) || !Double.isFinite(unsaturatedOutput))
			throw new IllegalArgumentException("EDFA input overflow");
		final double[] compression = new double[bands.size()];
		if (saturationScope == SaturationScope.SHARED) {
			final double common = Amplifier.sharedCompression(
					unsaturatedOutput, saturationOutputW, saturated);
			java.util.Arrays.fill(compression, common);
		} else {
			for (int index = 0; index < bands.size(); index++) {
				compression[index] = Amplifier.sharedCompression(
						bandUnsaturatedOutput[index], saturationOutputW, saturated);
			}
		}
		double output = 0.0;

		for (Input source : inputs) {
			final int index = bandIndex(source.frequencyHz);
			output += source.powerW * Amplifier.gainFromCompression(
					bands.get(index).smallSignalGain, compression[index]);
		}
		if (!Double.isFinite(output))
			throw new IllegalArgumentException("EDFA output overflow");
		return new State(this, compression, bandInput, total, output);
	}
}
