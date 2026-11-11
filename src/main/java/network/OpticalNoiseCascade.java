package network;

/**
 * Receiver-referenced PSD state for cascaded all-optical impairments.
 * Each gain or attenuation multiplies both signal and already generated
 * uncorrelated noise; each optical source injects its own PSD afterwards.
 * All densities are W/Hz, using the same polarization convention.
 */
public final class OpticalNoiseCascade {

	private double signalPsd;
	private double asePsd;
	private double nliPsd;
	private double xtPsd;

	public OpticalNoiseCascade(double launchSignalPsd) {
		signalPsd = Math.max(0.0, launchSignalPsd);
	}

	public void scale(double ratio) {
		if (!Double.isFinite(ratio) || ratio < 0.0) {
			throw new IllegalArgumentException("invalid stage power transfer");
		}

		signalPsd *= ratio;
		asePsd *= ratio;
		nliPsd *= ratio;
		xtPsd *= ratio;
	}

	public void addAse(double psd) {
		asePsd += nonnegative(psd);
	}

	public void addNli(double psd) {
		nliPsd += nonnegative(psd);
	}

	public void addXt(double psd) {
		xtPsd += nonnegative(psd);
	}

	private static double nonnegative(double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("non-finite noise density");
		}

		return Math.max(0.0, value);
	}

	/** Snapshot of all PSDs, in fixed order: signal, ASE, NLI and XT. */
	double[] snapshot() {
		return new double[] {signalPsd, asePsd, nliPsd, xtPsd};
	}

	void restore(double[] values) {
		if (values == null || values.length != 4) throw new IllegalArgumentException("invalid QoT cache");
		signalPsd = values[0];
		asePsd = values[1];
		nliPsd = values[2];
		xtPsd = values[3];
	}

	public double getSignalPsd() {
		return signalPsd;
	}

	public double getAsePsd() {
		return asePsd;
	}

	public double getNliPsd() {
		return nliPsd;
	}

	public double getXtPsd() {
		return xtPsd;
	}

	public double osnrLinear() {
		double noise = asePsd + nliPsd + xtPsd;

		return signalPsd / Math.max(1.0e-30, noise);
	}
	/** @deprecated The optical PSD ratio was historically named SNR. */
	@Deprecated public double snrLinear() { return osnrLinear(); }
}
