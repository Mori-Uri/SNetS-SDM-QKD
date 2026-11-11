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
 * ALL bands compete for the same finite output power. This is NOT a rate-
 * equation erbium population inversion model: spectral profiles must be
 * calibrated, particularly for L-band and hybrid C+L gain blocks.
 *
 * All powers in W; frequencies Hz; gains and noise factors linear.
 * The model neither amplifies quantum photons nor assumes C-band ASE leaks
 * into a distant quantum band. No reverse-fibre propagation is evaluated.
 */
public final class MultiBandEdFA implements Serializable {
	private static final long serialVersionUID = 1L;
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
	public static final class State implements Serializable {
		private static final long serialVersionUID = 1L;
		private final MultiBandEdFA model;
		private final double compression, inputW, outputSignalW;
		private State(MultiBandEdFA model, double compression, double inputW, double outputSignalW) {
			this.model = model; this.compression = compression;
			this.inputW = inputW; this.outputSignalW = outputSignalW;
		}

		public double getInputW() { return inputW; }
		public double getOutputSignalW() { return outputSignalW; }
		public double getCompression() { return compression; }
		public double gain(double frequencyHz) {
			return Amplifier.gainFromCompression(
					model.band(frequencyHz).smallSignalGain, compression);
		}

		public double asePsd(double frequencyHz, double planckJs) {
			return Amplifier.asePsd(frequencyHz,
					model.band(frequencyHz).noiseFigure, gain(frequencyHz), planckJs,
					inputW, model.noiseFactorA1, model.noiseFactorA2, model.saturated);
		}
	}

	private final List<Band> bands;
	private final double saturationOutputW;
	private final boolean saturated;
	private final double noiseFactorA1;
	private final double noiseFactorA2;

	public MultiBandEdFA withNoiseFactorModel(double a1, double a2) {
		Amplifier.noiseFactor(1.0, 0.0, a1, a2, false);

		return new MultiBandEdFA(bands, saturationOutputW, saturated, a1, a2);
	}

	private MultiBandEdFA(List<Band> profiles, double saturationOutputW,
			boolean saturated, double a1, double a2) {
		this.bands = Collections.unmodifiableList(new ArrayList<Band>(profiles));
		this.saturationOutputW = saturationOutputW;
		this.saturated = saturated;
		this.noiseFactorA1 = a1;
		this.noiseFactorA2 = a2;
	}

	public MultiBandEdFA(List<Band> profiles, double saturationOutputDbm, boolean saturated) {
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
	public double getSaturationOutputW() { return saturationOutputW; }
	public double noiseFigureAt(double frequencyHz) { return band(frequencyHz).noiseFigure; }
	public Band band(double frequencyHz) {
		for (Band b : bands) if (b.contains(frequencyHz)) return b;

		throw new IllegalArgumentException("frequency outside configured EDFA bands: " + frequencyHz);
	}

	public State evaluate(List<Input> inputs) {
		if (inputs == null) throw new IllegalArgumentException("null EDFA inputs");
		double total = 0.0;
		double unsaturatedOutput = 0.0;

		for (Input i : inputs) {
			total += i.powerW;
			unsaturatedOutput += i.powerW * band(i.frequencyHz).smallSignalGain;
		}

		if (!Double.isFinite(total) || !Double.isFinite(unsaturatedOutput))
			throw new IllegalArgumentException("EDFA input overflow");

		// One common inversion reservoir: interband gain competition.
		// Use aggregate *unsaturated output* to produce gain compression.
		final double compression = Amplifier.sharedCompression(
				unsaturatedOutput, saturationOutputW, saturated);
		double output = 0.0;

		for (Input i : inputs) {
			output += i.powerW * Amplifier.gainFromCompression(
					band(i.frequencyHz).smallSignalGain, compression);
		}

		return new State(this, compression, total, output);
	}
}
