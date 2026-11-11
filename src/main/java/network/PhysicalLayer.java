package network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;

/**
 * This class represents the physical layer of the optical network.
 *
 * @author Alexandre
 */
public class PhysicalLayer implements Serializable {

	public static final double maxOSNR = 999999.9; //Maximum OSNR value

	public static final int MODEL_HABIBI = 0; //Model Habibi
	public static final int MODEL_JOHANNISSON = 1; //Model Johannisson

	public static final int XT_SEPARATE = 0; //Crosstalk separate from other physical layer effects
	public static final int XT_TOGETHER = 1; //Crosstalk together with other physical layer effects

	public static final int AMP_GAIN_FIXED = 0; //Fixed amplifier gain
	public static final int AMP_GAIN_SATURATED = 1; //Saturated amplifier gain

	private int physicalLayerModel = MODEL_JOHANNISSON; //To select between Model Johannisson or Model Habibi
    /** Three explicit NLI policies: approximate, integral and adaptive. */
    private String johannissonNliMode = NliIntegrationMode.ADAPTIVE;
    private String habibiNliMode = NliIntegrationMode.ADAPTIVE;
    private double nliNarrowbandGHz = 25.0;

    private int habibiEgnQuadratureOrder = 16;
    private int habibiEgnReceiverOrder = 6;

	private int XTModel = XT_SEPARATE; //To select how to compute Crosstalk

	// Allows you to enable or disable the calculations of physical layer
	private boolean activeQoT; // QoTN
	private boolean activeQoTForOther; // QoTO

	private boolean activeASE; // Active the ASE noise of the amplifier
	private boolean activeNLI; // Active nonlinear noise in the fibers

	private boolean activeXT; // Active the Crosstalk
	private boolean activeXTForOther; // Active the Crosstalk on others circuits

	private double rateOfFEC; // FEC (Forward Error Correction), The most used rate is 7% which corresponds to the BER of 3.8E-3
	private int typeOfTestQoT; // 0: configured OSNR threshold, 1: pre-FEC BER estimated from measured OSNR
	private Double berThreshold; // optional pre-FEC BER target; null derives BER at the modulation OSNR threshold
	/** Formula selection for classical BER; default Jurandir, legacy opt-in for reproducibility. */
	private String berModel = BER_MODEL_JURANDIR;
	public static final String BER_MODEL_JURANDIR = "jurandir";
	public static final String BER_MODEL_LEGACY = "legacyRectangularAwgn";

	private double power;           // Power per channel, dBm
	private double L;               // Size of a span, km
	private double alpha;           // Fiber loss, dB/km

	/** Raw physical fibre Kerr coefficient (W^-1 m^-1), NOT pre-corrected
     * by Manakov 8/9. Selected GN formulas apply polarization corrections. */
	private double gamma;
	private double D;               // Dispersion parameter, s/m^2 or s/m/m
	private double centerFrequency; //Frequency of light

	private double h;                      // Constant of Planck
	private double NF;                     // Amplifier noise figure, dB
	private double pSat;                   // Saturation power of the amplifier, dBm
	private double A1;                     // Amplifier noise factor parameter, A1
	private double A2;                     // Amplifier noise factor parameter, A2
	private double amplificationFrequency; // Frequency used for amplification
	private int typeOfAmplifierGain = AMP_GAIN_FIXED; // Type of amplifier gain
	private int configuredAmplifierGainType = AMP_GAIN_FIXED;

	// Explicit passive optical boundaries. No OXC/switch loss in v23.
	private double muxLossDb;
	private double demuxLossDb;
	private double muxLossLinear;
	private double demuxLossLinear;

	/** Optional v2 band parameters; never mutates the shared GN fields. */
	private final ClassicalMultiBand classicalMultiBand;

	/** Safe per-physical-link QoT cache, never serialized in save states. */
	private transient QotLinkCache linkQotCache;

	private QotLinkCache qotCache() {
		if (linkQotCache == null) linkQotCache = new QotLinkCache();

		return linkQotCache;
	}

	public long getQotLinkCacheHits() {
		return linkQotCache == null ? 0L : linkQotCache.hits();
	}

	public long getQotLinkCacheMisses() {
		return linkQotCache == null ? 0L : linkQotCache.misses();
	}

	/**
     * ASE carrier convention: preserve the configured reference frequency in
     * single-band scenarios (as in the original SNetS experiments), but use
     * the real channel centre in MB/MB-SDM or calibrated C/L-band scenarios. The GN/XT
     * spectral calculations always keep the real channel centre.
     */
	private double aseFrequencyHz(double channelFrequencyHz) {
		if (classicalMultiBand != null || multibandAseCarrierMode
				|| longitudinalMultiband
				|| (longitudinalAmplifierTemplate != null
				&& longitudinalAmplifierTemplate.hasSpectralModel())) {
			return channelFrequencyHz;
		}

		return amplificationFrequency;
	}

	private boolean fixedPowerSpectralDensity; // To enable or disable fixed power spectral density
	private double referenceBandwidth; // Reference bandwidth for power spectral density

	private Amplifier boosterAmp; // Booster amplifier
	private Amplifier lineAmp;    // Line amplifier
	private Amplifier preAmp;     // Pre amplifier

	/** Optional physically shared longitudinal EDFA law. Disabled for legacy runs. */
	private qkd.multiband.ClassicalPowerEvolution longitudinalAmplifierTemplate;
	private double longitudinalClassicalBandStartHz;
	private double longitudinalClassicalAlphaNeperKm;

	/** Explicit MB/MB-SDM architecture, even without a calibrated spectral EDFA. */
	private boolean longitudinalMultiband;

	/** MB/MB-SDM architecture even when longitudinal EDFAs are disabled. */
	private boolean multibandAseCarrierMode;

	public void setMultibandAseCarrierMode(boolean enabled) {
		this.multibandAseCarrierMode = enabled;
		this.linkQotCache = null;
	}

	/**
     * Opt in to directed-fibre/core longitudinal EDFA ASE in classical QoT.
     * QKD uses the same snapshot factory and amplifier law. Existing
     * SNetS results are unchanged until this method is called.
     */
	public void enableLongitudinalAmplifierQoT(
			qkd.multiband.ClassicalPowerEvolution template,
			double bandStartHz, double alphaNeperKm) {
		enableLongitudinalAmplifierQoT(template, bandStartHz, alphaNeperKm, false);
	}

	/** Enable an MB/MB-SDM longitudinal context explicitly, including when the
     * amplifier profile is flat rather than frequency-calibrated. */
	public void enableLongitudinalAmplifierQoT(
			qkd.multiband.ClassicalPowerEvolution template,
			double bandStartHz, double alphaNeperKm, boolean multiband) {
		if (template == null || !Double.isFinite(bandStartHz) || bandStartHz <= 0.0
				|| !Double.isFinite(alphaNeperKm) || alphaNeperKm < 0.0)
			throw new IllegalArgumentException("invalid EDFA QoT configuration");

		if (classicalMultiBand != null) {
			throw new IllegalStateException("classic v2 multiBand cannot be combined with "
					+ "the shared longitudinal model without a unified ISRS-GN model");
		}

		this.linkQotCache = null;
		this.longitudinalAmplifierTemplate = template.withNoiseFactorModel(A1, A2);
		this.longitudinalClassicalBandStartHz = bandStartHz;
		this.longitudinalClassicalAlphaNeperKm = alphaNeperKm;
		this.longitudinalMultiband = multiband || template.hasSpectralModel();
		// The line-EDFA model and classical boundary EDFAs use the same
		// ideal/saturated selection while the opt-in shared path is enabled.
		this.typeOfAmplifierGain = template.usesGainSaturation()
				? AMP_GAIN_SATURATED : AMP_GAIN_FIXED;
	}

	/** Physically identical input-booster configuration for QKD snapshots. */
	public qkd.multiband.ClassicalPowerEvolution sharedBoosterTemplate(
			qkd.multiband.ClassicalPowerEvolution template) {
		if (template == null) throw new IllegalArgumentException("null template");

		return template.withNoiseFactorModel(A1, A2)
				.withBooster(boosterAmp.getGainLinear(), template.saturationPowerW(),
						typeOfAmplifierGain == AMP_GAIN_SATURATED);
	}

	public void disableLongitudinalAmplifierQoT() {
		this.linkQotCache = null;
		this.longitudinalAmplifierTemplate = null;
		this.longitudinalMultiband = false;
		this.typeOfAmplifierGain = configuredAmplifierGainType;
	}

	/**
     * Line-EDFA ASE PSD (W/Hz) from each physical operating point. Booster
     * and preamplifier remain under the legacy SNetS model in this increment.
     * The routine is evaluation-local and does not mutate link occupancy.
     */
	private double longitudinalLineAse(Link link, int core,
			TreeSet<Circuit> circuitList, final double frequencyHz) {
		final qkd.multiband.ClassicalPowerEvolution evolution =
				qkd.multiband.DirectedAmplifierSnapshotFactory.build(
						longitudinalAmplifierTemplate, link, core, circuitList,
						longitudinalClassicalBandStartHz, longitudinalClassicalAlphaNeperKm,
						new qkd.multiband.DirectedAmplifierSnapshotFactory.PowerResolver() {
							@Override public double powerW(Circuit source) {
								if (source instanceof qkd.capacity.QKDPichCircuit)
									return source.getLaunchPowerLinear();

								return getCircuitLaunchPower(source, source.getModulation())
										/ muxLossLinear;
							}

						});
		double ase = 0.0;
		final int count = (int) Math.floor((link.getDistance() - 1e-9)
				/ evolution.getAmplifierSpacingKm());

		for (int j = 1; j <= count; j++) {
			double term = evolution.operatingPointAt(j, longitudinalClassicalAlphaNeperKm,
					frequencyHz, h).getAsePsdWHz();
			final double z = j * evolution.getAmplifierSpacingKm();
			term *= Math.exp(-longitudinalClassicalAlphaNeperKm * (link.getDistance() - z));

			for (int downstream = j + 1; downstream <= count; downstream++)
				term *= evolution.operatingPointAt(downstream,
						longitudinalClassicalAlphaNeperKm, frequencyHz, h).getGainLinear();
			ase += term;
		}

		return ase;
	}

	/**
     * PICh physical feasibility using SNetS fibre, modulation and impairment
     * parameters (ASE/EDFA, longitudinal GN and IC-XT). Unlike a transparent
     * DCh the PICh is inserted and dropped within one directed physical link.
     * No fictitious extra SNetS links or QKD bypass are introduced.
     */

	/**
	 * Receiver-integrated forward IC-XT from co-propagating DCh and physical
	 * PICh optical sources. A DCh occupies [0,L] and a PICh occupies only its
	 * own QKDSpan. The EDFA snapshot shares the amplifier law, spectral
	 * dependence, loading and longitudinal coordinates of evaluatePich().
	 * Quantum photons experience passive attenuation after their generation;
	 * the QCh is never amplified by classical EDFAs. This is an explicitly
	 * opt-in alternative to the legacy lumped XT/receiver-transfer model.
	 *
	 * Integral: sum of h*1000*ISO * P_interferer(z) *
	 * exp(-alpha_Q*(QCh_end-z)) dz, where z is in km.
	 */
	public double qkdLongitudinalXtReceiverPower(Link link, int quantumCoreId,
			int[] quantumBand, qkd.network.QKDSpan qSpan,
			double quantumAttenuationDbPerKm, Circuit pendingDch) {
		if (link == null || qSpan == null || quantumBand == null || quantumBand.length != 2
				|| !(quantumAttenuationDbPerKm >= 0.0) || !Double.isFinite(quantumAttenuationDbPerKm))
			throw new IllegalArgumentException("invalid QKD longitudinal XT arguments");
		qkd.capacity.QKDPichPool.validateSpanInsideLink(link, qSpan);
		if (crosstalk == null || link.getCore(quantumCoreId) == null)
			throw new IllegalArgumentException("missing quantum core/coupling model");
		if (crosstalk.getH() <= 0.0) return 0.0;
		final double classicalAlpha = longitudinalAmplifierTemplate == null
				? alpha * Math.log(10.0) / 10.0 : longitudinalClassicalAlphaNeperKm;
		final double quantumAlpha = quantumAttenuationDbPerKm * Math.log(10.0) / 10.0;
		final double f0 = longitudinalAmplifierTemplate == null
				? lowerFrequency : longitudinalClassicalBandStartHz;
		final boolean saturated = typeOfAmplifierGain == AMP_GAIN_SATURATED;
		qkd.multiband.ClassicalPowerEvolution template = longitudinalAmplifierTemplate;
		if (template == null)
			template = new qkd.multiband.ClassicalPowerEvolution(L,
					ratioForDB(lineAmp.getGainLinear()),
					saturated ? qkd.multiband.ClassicalPowerEvolution.GainModel.SATURATED
						: qkd.multiband.ClassicalPowerEvolution.GainModel.IDEAL,
					pSat, 0.0, NF, 100.0);
		template = template.withNoiseFactorModel(A1, A2)
				.withBooster(boosterAmp.getGainLinear(), template.saturationPowerW(), saturated);
		double receiverWatts = 0.0;
		for (Core adjacent : link.getAdjacentCores(quantumCoreId)) {
			if (adjacent == null) continue;
			final java.util.Set<Circuit> sources = java.util.Collections.newSetFromMap(
					new java.util.IdentityHashMap<Circuit, Boolean>());
			sources.addAll(adjacent.getCircuitList());
			sources.addAll(adjacent.getAuxiliaryClassicalCircuitList());
			if (pendingDch != null && pendingDch.getRoute() != null
					&& pendingDch.getRoute().getLinkList().contains(link)
					&& pendingDch.getIndexCore() == adjacent.getId()) sources.add(pendingDch);
			final java.util.TreeSet<Circuit> ordered = new java.util.TreeSet<Circuit>(sources);
			final qkd.multiband.DirectedAmplifierSnapshotFactory.PowerResolver resolver =
					new qkd.multiband.DirectedAmplifierSnapshotFactory.PowerResolver() {
						@Override public double powerW(Circuit c) {
							if (c instanceof qkd.capacity.QKDPichCircuit) return c.getLaunchPowerLinear();
							return getCircuitLaunchPower(c,c.getModulation()) / muxLossLinear;
						}
					};
			final qkd.multiband.ClassicalPowerEvolution snapshot =
					qkd.multiband.DirectedAmplifierSnapshotFactory.build(template, link,
						adjacent.getId(), ordered, f0, classicalAlpha, resolver);
			for (Circuit interferer : sources) {
				if (interferer == null) continue;
				if (interferer instanceof qkd.capacity.QKDPichCircuit &&
						((qkd.capacity.QKDPichCircuit) interferer).getPhysicalLink() != link) continue;
				final int[] interfererBand = interferer.getSpectrumAssignedByLink(link);
				final double iso = Crosstalk.interfererFraction(quantumBand, interfererBand);
				if (iso <= 0.0) continue;
				final double srcStart, srcEnd;
				if (interferer instanceof qkd.capacity.QKDPichCircuit) {
					final qkd.network.QKDSpan sp = ((qkd.capacity.QKDPichCircuit) interferer).getSpan();
					srcStart=sp.getStartKm(); srcEnd=sp.getEndKm();
				} else { srcStart=0.0; srcEnd=link.getDistance(); }
				final double from = Math.max(qSpan.getStartKm(), srcStart);
				final double to = Math.min(qSpan.getEndKm(), srcEnd);
				if (to - from <= 1e-12) continue;
				final double powerW = resolver.powerW(interferer);
				if (!(powerW > 0.0)) continue;
				final double f = f0 + 0.5 * (interfererBand[0] + interfererBand[1]-1.0)
						* adjacent.getSlotSpectrumBand();
				final int n = Math.max(1,(int)Math.ceil((to-from)/0.25));
				final double dz=(to-from)/n;
				for (int k=0;k<n;k++) {
					final double z=from+(k+0.5)*dz;
					final double classicalPowerAtZ = powerW*snapshot.powerRatio(srcStart,z,classicalAlpha,f);
					final double survivingQuantum = Math.exp(-quantumAlpha*(qSpan.getEndKm()-z));
					receiverWatts += crosstalk.getH()*1000.0*iso*dz
							* classicalPowerAtZ*survivingQuantum;
				}
			}
		}
		if (!Double.isFinite(receiverWatts) || receiverWatts < 0.0)
			throw new IllegalStateException("nonphysical receiver-integrated QKD XT");
		return receiverWatts;
	}

	public PichQoTResult evaluatePich(qkd.capacity.QKDPichCircuit pich, Circuit pendingDch) {
		if (pich == null || pich.getModulation() == null)
			throw new IllegalArgumentException("PICh needs a configured modulation");
		Link link = pich.getPhysicalLink();
		qkd.network.QKDSpan span = pich.getSpan();
		qkd.capacity.QKDPichPool.validateSpanInsideLink(link, span);
		final int coreId = pich.getIndexCore();
		final Core core = link.getCore(coreId);

		if (core == null || core.isQkdReserved() || core.isDisabled())
			throw new IllegalArgumentException("PICh needs an enabled classical core");
		final int[] band = pich.getSpectrumAssigned();
		final double b = pich.getOpticalBandwidthHz(core.getSlotSpectrumBand());
		final double f0 = longitudinalAmplifierTemplate == null
				? lowerFrequency : longitudinalClassicalBandStartHz;
		final double f = pich.getCenterFrequencyHz(f0, core.getSlotSpectrumBand());
		final double z0 = span.getStartKm(), z1 = span.getEndKm();
		final double alphaKm = longitudinalAmplifierTemplate == null
				? alpha * Math.log(10.0) / 10.0 : longitudinalClassicalAlphaNeperKm;
		final double satW = Math.pow(10.0, (pSat - 30.0) / 10.0);
		final boolean saturated = typeOfAmplifierGain == AMP_GAIN_SATURATED;
		qkd.multiband.ClassicalPowerEvolution template = longitudinalAmplifierTemplate;

		if (template == null) {
			template = new qkd.multiband.ClassicalPowerEvolution(L,
					ratioForDB(lineAmp.getGainLinear()),
					saturated ? qkd.multiband.ClassicalPowerEvolution.GainModel.SATURATED
							  : qkd.multiband.ClassicalPowerEvolution.GainModel.IDEAL,
					pSat, 0.0, NF, 100.0);
		}

		template = template.withNoiseFactorModel(A1, A2)
				.withBooster(boosterAmp.getGainLinear(),
						template.saturationPowerW(), saturated);
		java.util.TreeSet<Circuit> sources = new java.util.TreeSet<Circuit>();
		sources.addAll(core.getCircuitList());
		sources.add(pich); // Include the PICh before committing its spectrum.
		if (pendingDch != null && pendingDch.getRoute() != null
				&& pendingDch.getRoute().getLinkList().contains(link)
				&& pendingDch.getIndexCore() == coreId) sources.add(pendingDch);
		final qkd.multiband.ClassicalPowerEvolution snapshot =
				qkd.multiband.DirectedAmplifierSnapshotFactory.build(template,
						link, coreId, sources, f0, alphaKm,
						new qkd.multiband.DirectedAmplifierSnapshotFactory.PowerResolver() {
							@Override public double powerW(Circuit source) {
								if (source instanceof qkd.capacity.QKDPichCircuit)
									return source.getLaunchPowerLinear();

								return getCircuitLaunchPower(source, source.getModulation())
										/ muxLossLinear;
							}

						});
		final double pol = physicalLayerModel == MODEL_JOHANNISSON ? polarizationModes : 1.0;
		final double psd0 = pich.getLaunchPowerLinear() / (b * pol);
		double signalRatio = snapshot.powerRatio(z0, z1, alphaKm, f);
		double ase = 0.0;
		double nli = 0.0;
		final double spacing = snapshot.getAmplifierSpacingKm();
		final double nf = snapshot.noiseFigureAt(f);
		final double aseCarrierHz = aseFrequencyHz(f);
		// ASE at every amplifier traversed by this PICh, including booster
		// only when the PICh starts at the physical-link input.
		if (activeASE && z0 == 0.0 && snapshot.getBoosterGainLinear() > 1.0) {
			ase += Amplifier.asePsd(aseCarrierHz, nf,
					snapshot.getBoosterGainLinear(), h,
					snapshot.getBoosterInputPowerW(), A1, A2, saturated)
					* snapshot.powerRatio(0.0, z1, alphaKm, f)
					/ snapshot.getBoosterGainLinear();
		}

		final int first = (int)Math.floor(z0 / spacing) + 1;
		final int last = (int)Math.floor((z1 - 1e-9) / spacing);

		for (int index = first; index <= last; index++) {
			double z = index * spacing;

			if (z <= z0 || z >= z1) continue;

			if (activeASE) {
				qkd.multiband.AmplifierOperatingPoint op =
						snapshot.operatingPointAt(index, alphaKm, aseCarrierHz, h);
				ase += op.getAsePsdWHz() * snapshot.powerRatio(z, z1, alphaKm, f);
			}
		}

		if (activeNLI) {
			// Habibi: EGN over actual subspans between EDFA or source borders;
			// Johannisson: preserve local-power numerical sections.
			if (physicalLayerModel == MODEL_HABIBI) {
				double[] cuts=gnSectionBoundaries(snapshot,z0,z1);

				for(int i=0;i<cuts.length-1;i++) {
					double from=cuts[i], to=cuts[i+1], mid=(from+to)*0.5;
					double generated=LongitudinalClassicalGN.sectionPsdHabibi(snapshot,
							mid,to-from,f,b,pich.getLaunchPowerLinear(),z0,
							GnModelMath.phi(pich.getModulation()),gamma,beta2,
                            alphaLinear,alphaKm,false,
                            nliNarrowbandGHz,habibiEgnQuadratureOrder,
                            habibiEgnReceiverOrder,habibiNliMode);
					nli+=generated*snapshot.powerRatio(to,z1,alphaKm,f);
				}

			} else {
				final int steps=Math.max(1,(int)Math.ceil((z1-z0)/2.0));
				final double dz=(z1-z0)/steps;

				for(int i=0;i<steps;i++) {
					double z=z0+(i+0.5)*dz;
					double generated=LongitudinalClassicalGN.sectionPsd(snapshot,
							z,dz,f,b,pich.getLaunchPowerLinear(),z0,
							gamma,beta2,alphaLinear,alphaKm,pol,
							johannissonNliMode, nliNarrowbandGHz,
                                    false, false);
					nli+=generated*snapshot.powerRatio(z,z1,alphaKm,f);
				}
			}
		}

		// A preamplifier is a physical SNetS receive-node device, not an
		// imaginary amplifier at each virtual trusted node.
		if (Math.abs(z1 - link.getDistance()) < 1e-8) {
			final double beforeEnd = Math.max(0, Math.nextAfter(z1, Double.NEGATIVE_INFINITY));
			final double inW = snapshot.aggregatePowerAt(beforeEnd, alphaKm);
			final double preG = Amplifier.gain(
					Math.exp(alphaKm * Math.max(0.0, link.getDistance()
							- Math.floor((link.getDistance()-1e-9)/spacing)*spacing))
							* demuxLossLinear,
					inW, template.saturationPowerW(), saturated);
			signalRatio *= preG;
			ase *= preG;
			nli *= preG;

			if (activeASE) {
				ase += Amplifier.asePsd(aseCarrierHz, nf, preG, h,
						inW, A1, A2, saturated);
			}

			// PICh receiver DEMUX: the preceding preamp compensates this
			// loss together with the final fibre segment. Internal trusted
			// nodes do not inherit a physical SNetS receive DEMUX.
			signalRatio /= demuxLossLinear;
			ase /= demuxLossLinear;
			nli /= demuxLossLinear;
		}

		// Apply the same physical IC-XT coefficient and actual SDM adjacency
		// as network.Crosstalk, but only over the occupied longitudinal span.
		double xtW = 0.0;

		if (activeXT) {
			for (Core adjacent : core.getAdjacentCores()) {
				java.util.Set<Circuit> near = java.util.Collections.newSetFromMap(
						new java.util.IdentityHashMap<Circuit, Boolean>());
				near.addAll(adjacent.getCircuitList());
				near.addAll(adjacent.getAuxiliaryClassicalCircuitList());

				if (pendingDch != null && pendingDch.getRoute() != null
						&& pendingDch.getRoute().getLinkList().contains(link)
						&& pendingDch.getIndexCore() == adjacent.getId()) near.add(pendingDch);

				for (Circuit interferer : near) {
					if (interferer == pich) continue;
					final int[] ib = interferer.getSpectrumAssignedByLink(link);
					final double iso = Crosstalk.interfererFraction(band, ib);

					if (iso == 0.0) continue;
					double length = z1-z0;

					if (interferer instanceof qkd.capacity.QKDPichCircuit) {
						qkd.network.QKDSpan other =
								((qkd.capacity.QKDPichCircuit)interferer).getSpan();
						length = Math.max(0, Math.min(z1, other.getEndKm())
								- Math.max(z0, other.getStartKm()));
					}

					if (length <= 0) continue;
					xtW += getCircuitLaunchPower(interferer, interferer.getModulation())
							* iso * crosstalk.getH() * length * 1000.0;
				}
			}
		}

		final double signalPsd = psd0 * signalRatio;
		final double xtPsd = xtW / (b * pol);
		final double denominator = Math.max(1.e-30,
				(activeASE ? ase/pol : 0.0) + (activeNLI ? nli : 0.0)
				+ (activeXT && XTModel == XT_TOGETHER ? xtPsd : 0.0));
		final double osnr = signalPsd / denominator;
		// Preserve the SNetS XT threshold convention (coupled power relative
		// to launch power), rather than mixing it with received-signal OSNR.
		final double xtRatio = xtW / Math.max(1.e-30,
				pich.getLaunchPowerLinear());
		final boolean qot = Double.isFinite(osnr) && isAdmissible(pich.getModulation(), osnr);
		final boolean xtOk = !activeXT ||
				ratioForDB(Math.max(1.e-30, xtRatio)) <= pich.getModulation().getXTthreshold();

		return new PichQoTResult(osnr, xtRatio, ase/pol, nli, xtPsd,
				pich.getLaunchPowerLinear() * signalRatio, qot && xtOk);
	}

	public static final class PichQoTResult {
		public final double osnrLinear, xtLinear, asePsdWHz, gnPsdWHz, xtPsdWHz;
		public final double receivedSignalW;
		public final boolean admissible;
		private PichQoTResult(double osnr, double xt, double ase, double gn,
				double xtPsd, double receivedSignalW, boolean admissible) {
			this.receivedSignalW = receivedSignalW;
			this.osnrLinear=osnr; this.xtLinear=xt; this.asePsdWHz=ase;
			this.gnPsdWHz=gn; this.xtPsdWHz=xtPsd;
			this.admissible=admissible;
		}
	}

	public String getJohannissonNliMode() { return johannissonNliMode; }
    public String getHabibiNliMode() { return habibiNliMode; }
    public double getNliNarrowbandGHz() { return nliNarrowbandGHz; }

	public double getConfiguredFiberLossDbPerKm() { return alpha; }

	/** @deprecated Switch insertion loss is deliberately zero in v23. */
	@Deprecated
	public double getConfiguredSwitchInsertionLossDb() { return 0.0; }
	public double getConfiguredMuxInsertionLossDb() { return muxLossDb; }
	public double getConfiguredDemuxInsertionLossDb() { return demuxLossDb; }
	public ClassicalMultiBand getClassicalMultiBand() { return classicalMultiBand; }

	private double PowerLinear; // Transmitter power, Watt
	private double alphaLinear; // 1/m
	private double beta2;       // Group-velocity dispersion
	private double attenuationBySpanLinear;

	private double slotBandwidth; // Hz
	private double lowerFrequency; // Hz

	private double polarizationModes; // Number of polarization modes

	private Util util;
	private Crosstalk crosstalk;

	/**
	 * Creates a new instance of PhysicalLayerConfig
	 *
	 * @param plc PhysicalLayerConfig
	 */
	public PhysicalLayer(PhysicalLayerConfig plc, Mesh mesh, Util util){
		this.util = util;

		this.physicalLayerModel = plc.getPhysicalLayerModel();
        this.johannissonNliMode = plc.getJohannissonNliMode();
        this.habibiNliMode = plc.getHabibiNliMode();
        this.nliNarrowbandGHz = plc.getNliNarrowbandGHz();
        this.habibiEgnQuadratureOrder = plc.getHabibiEgnQuadratureOrder();
        this.habibiEgnReceiverOrder = plc.getHabibiEgnReceiverOrder();
		this.XTModel = plc.getCrosstalkModel();

		this.activeQoT = plc.isActiveQoT();
		this.activeQoTForOther = plc.isActiveQoTForOther();

		this.activeASE = plc.isActiveASE();
		this.activeNLI = plc.isActiveNLI();

		this.activeXT = plc.isActiveXT();
		this.activeXTForOther = plc.isActiveXTForOther();

		this.typeOfTestQoT = plc.getTypeOfTestQoT();
		this.berThreshold = plc.getBerThreshold();
		this.berModel = plc.getBerModel();
		if (this.berThreshold != null && (!Double.isFinite(this.berThreshold)
				|| this.berThreshold <= 0.0 || this.berThreshold >= 0.5)) {
			throw new IllegalArgumentException("Invalid pre-FEC berThreshold: " + this.berThreshold);
		}
		this.rateOfFEC = plc.getRateOfFEC();

		this.power = plc.getPower();
		this.L = plc.getSpanLength();
		this.alpha = plc.getFiberLoss();
		this.gamma = plc.getFiberNonlinearity();
		this.D = plc.getFiberDispersion();
		this.centerFrequency = plc.getCenterFrequency();
		this.classicalMultiBand = ClassicalMultiBand.of(plc.getMultiBand(),
				this.alpha, this.gamma, this.D,
				plc.getNoiseFigureOfOpticalAmplifier(), this.centerFrequency);

		this.h = plc.getConstantOfPlanck();
		this.NF = plc.getNoiseFigureOfOpticalAmplifier();
		this.pSat = plc.getPowerSaturationOfOpticalAmplifier();
		this.A1 = plc.getNoiseFactorModelParameterA1();
		this.A2 = plc.getNoiseFactorModelParameterA2();
		this.typeOfAmplifierGain = plc.getTypeOfAmplifierGain();
		this.configuredAmplifierGainType = this.typeOfAmplifierGain;
		this.amplificationFrequency = plc.getAmplificationFrequency();

		this.muxLossDb = plc.getMuxInsertionLoss();
		this.demuxLossDb = plc.getDemuxInsertionLoss();
		this.muxLossLinear = ratioOfDB(muxLossDb);
		this.demuxLossLinear = ratioOfDB(demuxLossDb);

		this.fixedPowerSpectralDensity = plc.isFixedPowerSpectralDensity();
		this.referenceBandwidth = plc.getReferenceBandwidthForPowerSpectralDensity();

		this.polarizationModes = plc.getPolarizationModes();

		if(this.polarizationModes == 0.0) {
			this.polarizationModes = 2.0;
		}

		this.PowerLinear = dBm_to_W(power); // converting to Watts
		this.alphaLinear = computeAlphaLinear(alpha);
		this.beta2 = computeBeta2(D, centerFrequency);

		double spanMeter = L * 1000.0; // span in meter
		this.attenuationBySpanLinear = Math.pow(Math.E, alphaLinear * spanMeter);
		double boosterAmpGainLinear = muxLossLinear; // Booster compensates MUX only
		double lineAmpGainLinear = attenuationBySpanLinear;
		double preAmpGainLinear = attenuationBySpanLinear * demuxLossLinear; // Last fibre + DEMUX

		this.boosterAmp = new Amplifier(ratioForDB(boosterAmpGainLinear), pSat, NF, h, amplificationFrequency, 0.0, A1, A2);
		this.lineAmp = new Amplifier(ratioForDB(lineAmpGainLinear), pSat, NF, h, amplificationFrequency, 0.0, A1, A2);
		this.preAmp = new Amplifier(ratioForDB(preAmpGainLinear), pSat, NF, h, amplificationFrequency, 0.0, A1, A2);

		this.slotBandwidth = mesh.getLinkList().firstElement().getCore(0).getSlotSpectrumBand(); //Hz
		double totalSlots = mesh.getLinkList().firstElement().getCore(0).getNumOfSlots();
		this.lowerFrequency = AbsoluteFrequencyGrid.lowerEdgeHz(centerFrequency, (int)totalSlots, slotBandwidth); // Hz, Half slots are removed because center Frequency = 193.55E+12 is the central frequency of the optical spectrum

		this.crosstalk = new Crosstalk(this, plc);
	}

	/**
	 * Returns if QoTN check is active or not
	 *
	 * @return the activeQoT
	 */
	public boolean isActiveQoT() {
		return activeQoT;
	}

	/**
	 * Returns if QoTO check is active or not
	 *
	 * @return the activeQoTForOther
	 */
	public boolean isActiveQoTForOther() {
		return activeQoTForOther;
	}

	/**
	 * Returns if XT check is active or not
	 *
	 * @return the activeQoT
	 */
	public boolean isActiveXT() {
		return activeXT;
	}

	/**
	 * Returns if XT on other circuits check is active or not
	 *
	 * @return the activeQoT
	 */
	public boolean isActiveXTForOther() {
		return activeXTForOther;
	}

	/**
	 * Returns the Size of a span (Km)
	 *
	 * @return the L
	 */
	public double getSpanLength() {
		return L;
	}

	/**
	 * Returns the rate of FEC
	 *
	 * @return double
	 */
	public double getRateOfFEC(){
		return rateOfFEC;
	}

	/**
	 * Return the number of polarization modes
	 *
	 * @return double
	 */
	public double getPolarizationModes() {
		return this.polarizationModes;
	}

	/**
	 * Return the fixed power spectral density
	 *
	 * @return boolean
	 */
	public boolean getFixedPowerSpectralDensity() {
		return fixedPowerSpectralDensity;
	}

	/**
	 * Sets the fixedPowerSpectralDensity
	 *
	 * @param value boolean
	 */
	public void setFixedPowerSpectralDensity(boolean value) {
		this.linkQotCache = null;
		this.fixedPowerSpectralDensity = value;
	}

	/**
	 * Return the crosstalk
	 *
	 * @return crosstalk
	 */
	public Crosstalk getCrosstalk() {
		return crosstalk;
	}

	/**
	 * Return the XTModel
	 *
	 * @return XTModel
	 */
	public int getXTModel() {
		return XTModel;
	}

	/**
	 * Returns the power
	 *
	 * @return double
	 */
	public double getPower() {
		return power;
	}

	/**
	 * Returns the power linear
	 *
	 * @return double
	 */
	public double getPowerLinear() {
		return PowerLinear;
	}

	/**
	 * This method returns the number of amplifiers on a link including the booster and pre
	 *
	 * @param distance double
	 * @return double double
	 */
	public double getNumberOfAmplifiers(double distance){
		return 2.0 + roundUp((distance / L) - 1.0);
	}

	/**
	 * This method returns the number of line amplifiers on a link
	 *
	 * @param distance double
	 * @return double
	 */
	public double getNumberOfLineAmplifiers(double distance){
		return roundUp((distance / L) - 1.0);
	}

	/**
	 * BER at the CONFIGURED OSNR threshold (linear) of the modulation. The
	 * historical getter name getOSNRthresholdLinear() denotes OSNR, not Eb/N0.
	 * If berThreshold is explicit, it overrides this derived limit.
	 */
	public double getBERthreshold(Modulation modulation) {
		if (modulation == null) return Double.NaN;
		return berThreshold == null
				? estimateBerFromOsnr(modulation.getOSNRthresholdLinear(), modulation)
				: berThreshold.doubleValue();
	}

	private double estimateBerFromOsnr(double osnrLinear, Modulation modulation) {
		if (BER_MODEL_LEGACY.equals(berModel))
			return getBERLegacyRectangularAwgn(osnrLinear, modulation.getM());
		return getBER(osnrLinear, modulation.getM()); // uses configured PhysicalLayer.polarizationModes
	}

	/**
	 * Classical QoT: input and configured thresholds are both linear OSNR,
	 * NOT SNR per bit/symbol. The input is an optical PSD ratio.
	 */
	public boolean isAdmissible(Modulation modulation, double osnrLinear) {
		if (modulation == null || !Double.isFinite(osnrLinear) || osnrLinear < 0.0)
			return false;
		if (typeOfTestQoT == 0)
			return osnrLinear >= modulation.getOSNRthresholdLinear();
		if (typeOfTestQoT == 1) {
			final double ber = estimateBerFromOsnr(osnrLinear, modulation);
			final double limit = getBERthreshold(modulation);
			return Double.isFinite(ber) && Double.isFinite(limit) && ber <= limit;
		}
		return false;
	}


	/**
	 * Verifies that the QoT of the circuit is acceptable with the modulation format
	 * The circuit in question must not have allocated the network resources
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param modulation Modulation
	 * @param spectrumAssigned int[]
	 * @param testCircuit Circuit
	 * @param addTestCircuit boolean
	 * @return boolean
	 */
	public boolean isAdmissibleModultion(Circuit circuit, Route route, Modulation modulation, int core, int spectrumAssigned[], Circuit testCircuit, boolean addTestCircuit){
		double osnrLinear = computeOSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, modulation, core, spectrumAssigned, testCircuit, addTestCircuit);

		circuit.setOSNRlinear(osnrLinear);

		boolean QoT = isAdmissible(modulation, osnrLinear);
		// The QoT check receives optical OSNR directly, without a second rescaling.

		return QoT;
	}

	/**
	 * Verifies that the QoT of the circuit is acceptable with the modulation format for segment
	 * The circuit in question must not have allocated the network resources
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param sourceNodeIndex int
	 * @param destinationNodeIndex int
	 * @param modulation Modulation
	 * @param spectrumAssigned int[]
	 * @param testCircuit Circuit
	 * @param addTestCircuit boolean
	 * @return boolean
	 */
	public boolean isAdmissibleModultionBySegment(Circuit circuit, Route route, int sourceNodeIndex, int destinationNodeIndex, Modulation modulation, int core, int spectrumAssigned[], Circuit testCircuit, boolean addTestCircuit){
		double osnrLinear = computeOSNRSegment(circuit, route, sourceNodeIndex, destinationNodeIndex, modulation, core, spectrumAssigned, testCircuit, addTestCircuit);

		circuit.setOSNRlinear(osnrLinear);

		boolean QoT = isAdmissible(modulation, osnrLinear);
		// The QoT check receives optical OSNR directly, without a second rescaling.

		return QoT;
	}

	/**
	 * Based on articles:
	 *  - Nonlinear Impairment Aware Resource Allocation in Elastic Optical Networks (2015)
	 *  - Modeling of Nonlinear Signal Distortion in Fiber-Optic Networks (2014)
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param sourceNodeIndex int - Segment start node index
	 * @param destinationNodeIndex int - Segment end node index
	 * @param modulation Modulation
	 * @param spectrumAssigned int[]
	 * @param testCircuit Circuit - Circuit used to verify the impact on the other circuit informed
	 * @param addTestCircuit boolean - To add the test circuit to the circuit list
	 * @return double - optical OSNR (linear)
	 */
	public double computeOSNRSegment(Circuit circuit, Route route, int sourceNodeIndex, int destinationNodeIndex,
			Modulation modulation, int core, int[] spectrumAssigned, Circuit testCircuit, boolean addTestCircuit) {

		if (longitudinalAmplifierTemplate != null) {
			return computeOSNRSegmentLongitudinal(circuit, route, sourceNodeIndex,
					destinationNodeIndex, modulation, core, spectrumAssigned,
					testCircuit, addTestCircuit);
		}

		final double launchedW = getCircuitLaunchPower(circuit, modulation);
		OpticalNoiseCascade propagation = propagateLegacyCascade(circuit, route,
				sourceNodeIndex, destinationNodeIndex, modulation, core,
				spectrumAssigned, testCircuit, addTestCircuit,
				circuit.getRequiredBitRate(), launchedW, false);

		return propagation.osnrLinear();
	}

	/** @deprecated Compatibility alias; the return value is optical OSNR (linear). */
	@Deprecated
	public double computeSNRSegment(Circuit circuit, Route route, int sourceNodeIndex, int destinationNodeIndex,
			Modulation modulation, int core, int[] spectrumAssigned, Circuit testCircuit, boolean addTestCircuit) {
		return computeOSNRSegment(circuit, route, sourceNodeIndex, destinationNodeIndex,
				modulation, core, spectrumAssigned, testCircuit, addTestCircuit);
	}

	/**
	 * Based on articles:
	 *  - Nonlinear Impairment Aware Resource Allocation in Elastic Optical Networks (2015)
	 *  - Modeling of Nonlinear Signal Distortion in Fiber-Optic Networks (2014)
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param sourceNodeIndex int - Segment start node index
	 * @param destinationNodeIndex int - Segment end node index
	 * @param modulation Modulation
	 * @param spectrumAssigned int[]
	 * @param testCircuit Circuit - Circuit used to verify the impact on the other circuit informed
	 * @param addTestCircuit boolean - To add the test circuit to the circuit list
	 * @return double - optical OSNR (linear)
	 */
	public double computeOSNRSegment2(Circuit circuit, Route route, int sourceNodeIndex, int destinationNodeIndex, Modulation modulation, int core, int spectrumAssigned[], Circuit testCircuit, boolean addTestCircuit){
		if (longitudinalAmplifierTemplate != null)
			return computeOSNRSegmentLongitudinal(circuit, route, sourceNodeIndex,
					destinationNodeIndex, modulation, core, spectrumAssigned,
					testCircuit, addTestCircuit);

		//double numSlotsRequired = spectrumAssigned[1] - spectrumAssigned[0] + 1; // Number of slots required
		double numSlotsRequired = modulation.requiredSlots(circuit.getRequiredBitRate()); // Number of slots required
		//double Bsi = (numSlotsRequired - circuit.getGuardBand()) * slotBandwidth; // Circuit bandwidth, less the guard band
		double Bsi = numSlotsRequired * slotBandwidth; // Circuit bandwidth
		double fi = GnModelMath.centerHz(lowerFrequency, slotBandwidth, spectrumAssigned); // Central frequency of circuit

		Bsi = modulation.getBandwidthFromBitRate(circuit.getRequiredBitRate()); //Effective circuit bandwidth

		double circuitPowerLinear = this.PowerLinear;

		if(circuit.getLaunchPowerLinear() != Double.POSITIVE_INFINITY) {
			circuitPowerLinear = circuit.getLaunchPowerLinear();
		}

		if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
			circuitPowerLinear = circuitPowerLinear / polarizationModes; // Determining the power for each polarization mode
		}

		double I = circuitPowerLinear / referenceBandwidth; // Signal power density for the reference bandwidth
		if(!fixedPowerSpectralDensity){
			I = circuitPowerLinear / Bsi; // Signal power spectral density calculated according to the requested bandwidth
		}

		double Iase = 0.0;
		double Inli = 0.0;
		double Ixt = 0.0;

		Node sourceNode = null;
		Node destinationNode = null;
		Link link = null;
		TreeSet<Circuit> circuitList = null;

		double Nl = 0.0; // Number of line amplifiers
		double noiseNli = 0.0;
		double totalPower = 0.0;
		double boosterAmpNoiseAse = 0.0;
		double preAmpNoiseAse = 0.0;
		double lineAmpNoiseAse = 0.0;
		double lastFiberSegment = 0.0;
		double Pxt = 0.0;

		for(int i = sourceNodeIndex; i < destinationNodeIndex; i++){
			sourceNode = route.getNode(i);
			destinationNode = route.getNode(i + 1);
			link = sourceNode.getOxc().linkTo(destinationNode.getOxc());
			Nl = getNumberOfLineAmplifiers(link.getDistance());

			circuitList = getCircuitList(link, core, circuit, testCircuit, addTestCircuit);

			if(activeNLI){
				// Account for REAL section lengths. A residual 20 km after an
				// 80 km amplifier span is not another 80 km GN contribution.
				double startKm=0.0;

				for (double sectionKm : GnModelMath.spanLengthsKm(link.getDistance(), L)) {
					if (physicalLayerModel == MODEL_JOHANNISSON)
						noiseNli = getGnli(circuit, link, circuitPowerLinear,
								Bsi, I, fi, circuitList, core, startKm, sectionKm);
					else
						noiseNli = getGnli2(circuit, link, circuitPowerLinear,
								Bsi, I, fi, circuitList, modulation, core, startKm, sectionKm);
					Inli += GnModelMath.nonnegative(noiseNli, "legacy span");
					startKm += sectionKm;
				}
			}

			if(activeASE){
				if(typeOfAmplifierGain == AMP_GAIN_SATURATED){
					totalPower = getTotalPowerInTheLink(circuitList)
							+ qkd.capacity.PichClassicalNonlinearity.meanPichPowerW(link, core)
									/ (physicalLayerModel == MODEL_JOHANNISSON ? polarizationModes : 1.0);
				}

				// Computing the last span amplifier gain
				lastFiberSegment = link.getDistance() - (Nl * L);
				preAmp.setGain(alpha * lastFiberSegment + demuxLossDb);

				// Computing the ASE for each amplifier type
				boosterAmpNoiseAse = boosterAmp.getAseByGain(totalPower, boosterAmp.getGainByType(totalPower, typeOfAmplifierGain));
				lineAmpNoiseAse = lineAmp.getAseByGain(totalPower, lineAmp.getGainByType(totalPower, typeOfAmplifierGain));
				preAmpNoiseAse = preAmp.getAseByGain(totalPower, preAmp.getGainByType(totalPower, typeOfAmplifierGain));

				if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
					// Determining the ASE for each polarization mode
					boosterAmpNoiseAse = boosterAmpNoiseAse / polarizationModes;
					lineAmpNoiseAse = lineAmpNoiseAse / polarizationModes;
					preAmpNoiseAse = preAmpNoiseAse / polarizationModes;
				}

				if (longitudinalAmplifierTemplate != null) {
					lineAmpNoiseAse = longitudinalLineAse(link, core, circuitList, fi);

					if (physicalLayerModel == MODEL_JOHANNISSON)
						lineAmpNoiseAse /= polarizationModes;
				} else {
					lineAmpNoiseAse = Nl * lineAmpNoiseAse;
				} // Physical per-amplifier ASE when opt-in, legacy otherwise

				Iase = Iase + (boosterAmpNoiseAse + lineAmpNoiseAse + preAmpNoiseAse);
			}

			if (activeXT && XTModel == XT_TOGETHER) {
				Pxt = crosstalk.calculateCrosstalkPowerInLink(circuit, link, core, spectrumAssigned, testCircuit, addTestCircuit);

				if (physicalLayerModel == MODEL_JOHANNISSON) {
					Pxt = Pxt / polarizationModes;
				}

				Ixt = Ixt + (Pxt / Bsi); // Convert to power spectral density
				// Physical PIChs are auxiliary sources, not regular DCh circuits.
				Ixt += qkd.capacity.PichClassicalCrosstalk.noisePsd(link, core, spectrumAssigned,
						Bsi, crosstalk.getH(), polarizationModes,
						physicalLayerModel == MODEL_JOHANNISSON);
			}
		}

		double osnr = I / (Iase + Inli + Ixt);

		return osnr;
	}

	/**
	 * This method returns the circuit launch power linear
	 *
	 * @param circuit Circuit
	 * @return double - circuitPower (Linear)
	 */
	public double getCircuitLaunchPower(Circuit circuit, Modulation modulation) {
		double circuitPower = circuit.getLaunchPowerLinear();

		if (circuitPower == Double.POSITIVE_INFINITY) {
			circuitPower = this.PowerLinear; // If PSD is not fixed

			if (fixedPowerSpectralDensity) { // PSD is fixed
				double Bsi = modulation.getBandwidthFromBitRate(circuit.getRequiredBitRate()); //Circuit bandwidth
				double I = this.PowerLinear / this.referenceBandwidth; // PSD for the reference bandwidth

				circuitPower = I * Bsi; // Power calculated according to the requested bandwidth
			}

			circuit.setLaunchPowerLinear(circuitPower);
		}

		return circuitPower;
	}

	/**
	 * This method return the signal power spectral density
	 *
	 * @param circuit Circuit
	 * @param circuitPowerLinear double
	 * @param Bsi double
	 * @return double - PSD (Linear)
	 */
	public double getSignalPowerSpectralDensity(double circuitPowerLinear, double Bsi) {
		double PSD = 0.0; // Signal power spectral density

		if (fixedPowerSpectralDensity) { // PSD is fixed
			double referencePower = this.PowerLinear;

			if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
				referencePower = referencePower / polarizationModes; // Determining the power for each polarization mode
			}

			PSD = referencePower / this.referenceBandwidth; // PSD for the reference bandwidth

		} else {  // PSD is not fixed
			PSD = circuitPowerLinear / Bsi; // PSD calculated according to the requested bandwidth
		}

		return PSD;
	}

	/**
	 * Create a list of the circuits that use the link
	 *
	 * @param link Link
	 * @param circuit Circuit
	 * @param testCircuit Circuit
	 * @param addTestCircuit
	 * @return TreeSet<Circuit>
	 */
	private TreeSet<Circuit> getCircuitList(Link link, int core, Circuit circuit, Circuit testCircuit, boolean addTestCircuit){
		TreeSet<Circuit> circuitList = new TreeSet<Circuit>();

		for (Circuit circtuiTemp : link.getCore(core).getCircuitList()) {
			circuitList.add(circtuiTemp);
		}

		if(!circuitList.contains(circuit)){
			circuitList.add(circuit);
		}

		if(testCircuit != null && testCircuit.getRoute().containThisLink(link)) {

			if(!circuitList.contains(testCircuit) && addTestCircuit) {
				circuitList.add(testCircuit);
			}

			if(circuitList.contains(testCircuit) && !addTestCircuit) {
				circuitList.remove(testCircuit);
			}
		}

		return circuitList;
	}

	/**
	 * Total input power on the link
	 *
	 * @param circuitList TreeSet<Circuit>
	 * @return double
	 */
	public double getTotalPowerInTheLink(TreeSet<Circuit> circuitList){
		double totalPower = 0.0;
		double circuitPower = 0.0;

		for(Circuit circuitJ : circuitList){
			circuitPower = getCircuitLaunchPower(circuitJ, circuitJ.getModulation());

			if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
				circuitPower = circuitPower / polarizationModes; // Determining the power for each polarization mode
			}

			totalPower += circuitPower;
		}

		return totalPower;
	}

	/**
     * Longitudinal QoT in the same directed/core snapshot used by QKD.
     * Propagates signal and previously generated impairments through each link.
     * GN is an incoherent, span-local perturbative estimate (not full EGN);
     * it samples the longitudinal channel power at every span midpoint.
     * Amplifiers never act on the QCh itself.
     */
	/** Physical GN integration boundaries. A PICh begins/ends inside one SNetS
     * link; no fictitious bypass across any SNetS node is introduced. */
	private static double[] gnSectionBoundaries(
			qkd.multiband.ClassicalPowerEvolution snapshot, double from, double to) {
		java.util.TreeSet<Double> cuts=new java.util.TreeSet<Double>();
		cuts.add(from); cuts.add(to);
		double spacing=snapshot.getAmplifierSpacingKm();

		if(spacing>0.0) for(int i=1;i*spacing<to && i<100000;i++) {
			double z=i*spacing;

			if(z>from && z<to) cuts.add(z);
		}

		for(qkd.multiband.ClassicalPowerEvolution.LoadSource source:snapshot.dynamicSources()) {
			if(source.startKm>from && source.startKm<to) cuts.add(source.startKm);

			if(source.endKm>from && source.endKm<to) cuts.add(source.endKm);
		}

		double[] out=new double[cuts.size()];int i=0;

		for(Double z:cuts) out[i++]=z;

		return out;
	}

	/**
     * The analytical per-span GN kernel must not change just because the
     * amplifier operating points are computed by the longitudinal engine.
     * Reuse it when both evaluations describe the SAME uniform, full-link
     * DCh system. A spectral EDFA, a PICh (including a full-link PICh), a
     * different line-EDFA grid or a different fibre loss makes this shortcut
     * invalid; those cases retain the position-resolved longitudinal GN.
     */
	private boolean comparableLongitudinalGn(qkd.multiband.ClassicalPowerEvolution snapshot,
			TreeSet<Circuit> circuits, Link link, int core, double alphaKm) {
		if (snapshot.hasSpectralModel()
				|| Math.abs(snapshot.getAmplifierGainDb() - alpha * L) > 1e-9
				|| Math.abs(snapshot.saturationPowerW() - dBm_to_W(pSat))
						> 1e-9 * Math.max(1e-15, dBm_to_W(pSat))
				|| Math.abs(snapshot.noiseFigureAt(amplificationFrequency) - ratioOfDB(NF))
						> 1e-9 * Math.max(1.0, ratioOfDB(NF))
				|| Math.abs(snapshot.getAmplifierSpacingKm() - L) > 1e-9
				|| Math.abs(alphaKm - alpha * Math.log(10.0) / 10.0) > 1e-12
				|| !link.getCore(core).getAuxiliaryClassicalCircuitList().isEmpty()) {
			return false;
		}

		for (Circuit source : circuits) {
			if (source instanceof qkd.capacity.QKDPichCircuit) return false;
		}

		for (qkd.multiband.ClassicalPowerEvolution.LoadSource source : snapshot.dynamicSources()) {
			if (Math.abs(source.startKm) > 1e-9
					|| Math.abs(source.endKm - link.getDistance()) > 1e-9) return false;
		}

		return true;
	}

	private double computeOSNRSegmentLongitudinal(Circuit circuit, Route route,
			int sourceNodeIndex, int destinationNodeIndex, Modulation modulation,
			int core, int[] spectrumAssigned, Circuit testCircuit, boolean addTestCircuit) {
		final double bandwidth=modulation.getBandwidthFromBitRate(circuit.getRequiredBitRate());

		if (!(bandwidth>0.0)) return 0.0;
		final double fi=GnModelMath.centerHz(lowerFrequency, slotBandwidth, spectrumAssigned);
		final double launched=getCircuitLaunchPower(circuit,modulation);
		final double pol=physicalLayerModel==MODEL_JOHANNISSON?polarizationModes:1.0;
		double signal=getSignalPowerSpectralDensity(launched/pol,bandwidth);
		double noise=0.0;
		final double alphaKm=longitudinalClassicalAlphaNeperKm;

		for (int i=sourceNodeIndex;i<destinationNodeIndex;i++) {
			// A new physical link starts with a MUX, but NO switch insertion loss.
			// Existing signal and upstream noise traverse the same passive loss.
			signal /= muxLossLinear;
			noise /= muxLossLinear;

			Link link=route.getNode(i).getOxc().linkTo(route.getNode(i+1).getOxc());
			String cacheKey = null;

			if (QotLinkCache.enabled() && testCircuit == null && !addTestCircuit) {
				cacheKey = QotLinkCache.key(this, link, circuit, modulation, core,
						spectrumAssigned, launched, bandwidth, fi,
						new double[] {signal, noise}, false, true, typeOfAmplifierGain);
				double[] cached = qotCache().get(cacheKey);

				if (cached != null) {
					signal = cached[0];
					noise = cached[1];
					continue;
				}
			}

			TreeSet<Circuit> circuits=getCircuitList(link,core,circuit,testCircuit,addTestCircuit);
			qkd.multiband.ClassicalPowerEvolution snapshot=
					qkd.multiband.DirectedAmplifierSnapshotFactory.build(
							longitudinalAmplifierTemplate.withBooster(boosterAmp.getGainLinear(),
								longitudinalAmplifierTemplate.saturationPowerW(),
								typeOfAmplifierGain == AMP_GAIN_SATURATED),link,core,circuits,
							longitudinalClassicalBandStartHz,alphaKm,
							new qkd.multiband.DirectedAmplifierSnapshotFactory.PowerResolver() {
								@Override public double powerW(Circuit source) {
									if(source instanceof qkd.capacity.QKDPichCircuit)
										return source.getLaunchPowerLinear();
									// DCh is fed to the fibre after the optical MUX.
									return getCircuitLaunchPower(source,source.getModulation())
											/ muxLossLinear;
								}

							});
			double aggregate=0.0;

			for (Circuit source:circuits) {
				if (source instanceof qkd.capacity.QKDPichCircuit) continue;
				aggregate+=getCircuitLaunchPower(source,source.getModulation()) / muxLossLinear;
			}

			// Internal PIChs are inserted at trusted nodes, not at the DCh MUX.
			aggregate+=qkd.capacity.PichClassicalNonlinearity.meanPichPowerW(link,core);
			// Booster and preamp share the gain-compression law with the line EDFAs.
			// Their reference gains retain the legacy SNetS equipment configuration.
			final double b0=boosterAmp.getGainLinear();
			final double last=Math.max(0.0,link.getDistance()-
					getNumberOfLineAmplifiers(link.getDistance())*L);
			final double p0=Math.exp(alphaKm*last)*demuxLossLinear;
			final double sat=longitudinalAmplifierTemplate.saturationPowerW();
			// EDFA noise uses the legacy reference frequency in single band;
			// the actual carrier frequency is required in MB/MB-SDM and C/L EDFAs.
			// GN/XCI still use fi independently of this ASE convention.
			final double aseCarrierHz = aseFrequencyHz(fi);
			LongitudinalClassicalQoT.Result state=LongitudinalClassicalQoT.evaluate(
					snapshot,aseCarrierHz,h,aggregate,b0,p0,sat,
					typeOfAmplifierGain==AMP_GAIN_SATURATED,
					longitudinalAmplifierTemplate.noiseFigureAt(fi),
					link.getDistance(),alphaKm, A1, A2);
			// Amplification/loss of the existing signal and accumulated noise.
			final double signalAtLinkFibreInput = signal;
			signal*=state.signalTransfer;
			noise*=state.signalTransfer;

			if (activeASE) noise+=state.aseAtReceiverWHz/pol;

			if (activeNLI) {
				if (comparableLongitudinalGn(snapshot, circuits, link, core, alphaKm)) {
					// Shared SCI/XCI/EGN equations and per-span optical powers.
					// state.spanStartRatio is measured from the fibre entrance;
					// the MUX loss has already been applied to the signal.
					// GN is referred to each span entrance and the whole
					// downstream transfer carries its generated PSD to Rx.
					for (int k=0; k<state.spanLengthKm.length; k++) {
						final double sectionKm=state.spanLengthKm[k];

						if (!(sectionKm > 0.0)) continue;
						final double startKm=state.spanEndKm[k]-sectionKm;
						final double ratio=state.spanStartRatio[k];

						if (!(ratio > 0.0)) throw new IllegalStateException(
								"nonphysical longitudinal signal transfer");
						final double generated=calculateSectionNli(circuit, link,
								circuits, modulation, core, bandwidth, fi,
								signalAtLinkFibreInput*ratio, pol,
								startKm, sectionKm, ratio/muxLossLinear,
								false, null);
						noise+=GnModelMath.nonnegative(generated,
								"shared longitudinal per-span GN")
								* state.signalTransfer / ratio;
					}

				} else if (physicalLayerModel == MODEL_HABIBI) {
					// EGN coefficients must be evaluated on physical sections,
					// not fed into a 2-km Johannisson logarithmic kernel.
					double[] cuts=gnSectionBoundaries(snapshot,0.0,link.getDistance());

					for(int k=0;k<cuts.length-1;k++) {
						final double start=cuts[k], end=cuts[k+1];
						final double mid=0.5*(start+end);
						final double generated=LongitudinalClassicalGN.sectionPsdHabibi(
								snapshot,mid,end-start,fi,bandwidth,launched/muxLossLinear,0.0,
								GnModelMath.phi(modulation),gamma,beta2,alphaLinear,alphaKm,
                                false,nliNarrowbandGHz,
                                habibiEgnQuadratureOrder,habibiEgnReceiverOrder,habibiNliMode);
						noise+=generated*snapshot.powerRatio(end,link.getDistance(),
								alphaKm,fi)*state.preGain;
					}

				} else {
					// Johannisson: the existing local-power numerical sections.
					for (int k=0;k<state.spanLengthKm.length;k++) {
						if (state.spanLengthKm[k]<=0.0) continue;
						final double end=state.spanEndKm[k];
						final double start=end-state.spanLengthKm[k];
						final int steps=Math.max(1,(int)Math.ceil((end-start)/2.0));
						final double dz=(end-start)/steps;

						for(int step=0;step<steps;step++) {
							final double z=start+(step+0.5)*dz;
							final double generated=LongitudinalClassicalGN.sectionPsd(
									snapshot,z,dz,fi,bandwidth,launched/muxLossLinear,0.0,
									gamma,beta2,alphaLinear,alphaKm,pol,
									johannissonNliMode, nliNarrowbandGHz,
                                    false, false);
							final double after=snapshot.powerRatio(z,link.getDistance(),
									alphaKm,fi)*state.preGain;
							noise+=generated*after;
						}
					}
				}
			}

			if(activeXT && XTModel==XT_TOGETHER) {
				double xt=crosstalk.calculateCrosstalkPowerInLink(circuit,link,core,
						spectrumAssigned,testCircuit,addTestCircuit)/pol/bandwidth;
				xt+=qkd.capacity.PichClassicalCrosstalk.noisePsd(link,core,
						spectrumAssigned,bandwidth,crosstalk.getH(),polarizationModes,
						physicalLayerModel==MODEL_JOHANNISSON);
				noise+=xt;
			}

			// DEMUX attenuation at this link's destination. The following
			// link has its OWN MUX, allowing two-hop routes to conserve energy.
			signal /= demuxLossLinear;
			noise /= demuxLossLinear;

			if (!Double.isFinite(signal) || !Double.isFinite(noise)) return 0.0;

			if (cacheKey != null) {
				qotCache().put(cacheKey, new double[] {signal, noise});
			}
		}

		return signal/Math.max(1.e-30,noise);
	}

	/**
	 * Based on article:
	 *  - Nonlinear Impairment Aware Resource Allocation in Elastic Optical Networks (2015)
	 *
	 * @param circuitI Circuit
	 * @param link Link
	 * @param powerI double
	 * @param BsI double
	 * @param Gi double
	 * @param fI double
	 * @param circuitList TreeSet<Circuit>
	 * @return double
	 */
	public double getGnli(Circuit circuitI, Link link, double powerI, double BsI, double Gi, double fI,
			TreeSet<Circuit> circuitList) {
		return getGnli(circuitI, link, powerI, BsI, Gi, fI, circuitList, circuitI.getIndexCore());
	}

	private double getGnli(Circuit circuitI, Link link, double powerI, double BsI, double Gi, double fI,
			TreeSet<Circuit> circuitList, int victimCore) {
		return getGnli(circuitI,link,powerI,BsI,Gi,fI,circuitList,victimCore,0.0,L);
	}

	private double getGnli(Circuit circuitI, Link link, double powerI, double BsI, double Gi, double fI,
			TreeSet<Circuit> circuitList, int victimCore, double startKm, double sectionKm) {
		return getGnli(circuitI, link, powerI, BsI, Gi, fI, circuitList,
				victimCore, startKm, sectionKm, 1.0);
	}

	private double getGnli(Circuit circuitI, Link link, double powerI, double BsI, double Gi, double fI,
			TreeSet<Circuit> circuitList, int victimCore, double startKm, double sectionKm,
			double aggressorPowerRatio) {
		return getGnli(circuitI, link, powerI, BsI, Gi, fI, circuitList,
				victimCore, startKm, sectionKm, aggressorPowerRatio, null);
	}

	private double getGnli(Circuit circuitI, Link link, double powerI, double BsI, double Gi, double fI,
			TreeSet<Circuit> circuitList, int victimCore, double startKm, double sectionKm,
			double aggressorPowerRatio, ClassicalMultiBand.Params band) {
		final double localAlpha = band == null ? alphaLinear : band.alphaLinear;
		final double localGamma = band == null ? gamma : band.gamma;
		final double localBeta2 = band == null ? beta2 : band.beta2;
		double beta21 = Math.abs(localBeta2);

		//double mi = Gi * (3.0 * localGamma * localGamma) / (2.0 * Math.PI * localAlpha * beta21);
		double mi = Gi * (3.0 * 64.0 * localGamma * localGamma) / (2.0 * 81.0 * Math.PI * localAlpha * beta21);
		//double ro =  BsI * BsI * (Math.PI * Math.PI * beta21) / (2.0 * localAlpha);
		double ro =  BsI * BsI * (Math.PI * Math.PI * beta21) / localAlpha;
		// Eq. (16) SCI log is asymptotic; if it is nonpositive/undefined,
		// GnModelMath falls back to the positive integral (Eqs. 11/15).
		// Positive logarithms are permitted by the explicit simulation flag.
		double term_self = Gi * Gi * GnModelMath.johannissonSciKernel(ro, BsI, johannissonNliMode, nliNarrowbandGHz, false);

		double term_cross = 0.0;
		int saJ[] = null;
		double numOfSlots = 0.0;
		double Bsj = 0.0;
		double fJ = 0.0;
		double deltaFij = 0.0;
		double d1 = 0.0;
		double d2 = 0.0;
		double ratio = 0.0;
		double ln = 0.0;
		double powerJ = powerI; // Power of the circuit j
		double Gj = Gi; // Power spectral density of the circuit j

		for(Circuit circuitJ : circuitList){

			if(!circuitI.equals(circuitJ)){
				saJ = circuitJ.getSpectrumAssignedByLink(link);
				//numOfSlots = saJ[1] - saJ[0] + 1.0;
				numOfSlots = circuitJ.getModulation().requiredSlots(circuitJ.getRequiredBitRate()); // Number of slots required

				//Bsj = (numOfSlots - circuitJ.getGuardBand()) * slotBandwidth; // Circuit bandwidth, less the guard band
				Bsj = numOfSlots * slotBandwidth; // Circuit bandwidth, less the guard band
				fJ = GnModelMath.centerHz(lowerFrequency, slotBandwidth, saJ); // Central frequency of circuit

				Bsj = circuitJ.getModulation().getBandwidthFromBitRate(circuitJ.getRequiredBitRate());

				powerJ = getCircuitLaunchPower(circuitJ, circuitJ.getModulation());
				powerJ = powerJ * aggressorPowerRatio / polarizationModes;
				// Apply the same booster/span gain-loss evolution to XCI aggressors.

				//if(circuitJ.getLaunchPowerLinear() != Double.POSITIVE_INFINITY) {
				//	powerJ = circuitJ.getLaunchPowerLinear();
				//	powerJ = powerJ / polarizationModes; // Determining the power for each polarization mode
				//}

				Gj = powerJ / Bsj; // Local interferer PSD at this span entrance.


				deltaFij = Math.abs(fI - fJ);

				d1 = deltaFij + (Bsj / 2.0);
				d2 = deltaFij - (Bsj / 2.0);

				if(d2 <= 0.0) { //To avoid division by zero
					d2 = 1.0E-30;
				}

				ratio = d1 / d2;

				if (ratio < 1.000001) { //Avoid negative or zero logs
					ratio = 1.000001;
				}

				// Select Eq. (15) integral XCI or Eq. (16) logarithmic XCI
				// using the selected NLI mode.
				final double xi = 4.0 * Math.PI * Math.PI * beta21 / localAlpha;
				final double xciKernel = GnModelMath.johannissonXciKernel(
						deltaFij, BsI, Bsj, xi, johannissonNliMode, nliNarrowbandGHz, false);
				term_cross += Gj * Gj * xciKernel;
			}
		}

		// Auxiliary PICh sources are not included in the ordinary circuitList.
		// Add same-core XCI once here (do not place PICh in DCh circuitList).
		double pichXci = qkd.capacity.PichClassicalNonlinearity.crossTermJohannissonKernel(
				link, victimCore, fI, BsI, mi, lowerFrequency,
				slotBandwidth, polarizationModes,
				4.0 * Math.PI * Math.PI * beta21 / localAlpha,
				startKm, startKm + sectionKm, johannissonNliMode, nliNarrowbandGHz, false);
		double gnli = mi * (term_self + term_cross) + pichXci;
		gnli *= GnModelMath.partialSpanFactor(sectionKm, L, localAlpha);

		return GnModelMath.nonnegative(gnli, "Johannisson legacy");
	}

	/**
	 * Based on article:
	 *  - 2019 - Habibi - Impairment-Aware Manycast Routing, Modulation Level, and Spectrum Assignment in Elastic Optical Networks
	 *
	 * @param circuitI Circuit
	 * @param link Link
	 * @param powerI double
	 * @param BsI double
	 * @param Gi double
	 * @param fI double
	 * @param circuitList TreeSet<Circuit>
	 * @param modulation Modulation
	 * @return double
	 */
	public double getGnli2(Circuit circuitI, Link link, double powerI, double BsI, double Gi, double fI,
			TreeSet<Circuit> circuitList, Modulation modulation) {
		return getGnli2(circuitI, link, powerI, BsI, Gi, fI, circuitList,
				modulation, circuitI.getIndexCore());
	}

	private double getGnli2(Circuit circuitI, Link link, double powerI, double BsI, double Gi, double fI,
			TreeSet<Circuit> circuitList, Modulation modulation, int victimCore) {
		return getGnli2(circuitI,link,powerI,BsI,Gi,fI,circuitList,modulation,victimCore,0.0,L);
	}

	private double getGnli2(Circuit circuitI, Link link, double powerI, double BsI, double Gi, double fI,
			TreeSet<Circuit> circuitList, Modulation modulation, int victimCore,
			double startKm, double sectionKm) {
		return getGnli2(circuitI, link, powerI, BsI, Gi, fI, circuitList,
				modulation, victimCore, startKm, sectionKm, 1.0);
	}

	private double getGnli2(Circuit circuitI, Link link, double powerI, double BsI, double Gi, double fI,
			TreeSet<Circuit> circuitList, Modulation modulation, int victimCore,
			double startKm, double sectionKm, double aggressorPowerRatio) {
		return getGnli2(circuitI, link, powerI, BsI, Gi, fI, circuitList,
				modulation, victimCore, startKm, sectionKm, aggressorPowerRatio, null);
	}

	private double getGnli2(Circuit circuitI, Link link, double powerI, double BsI, double Gi, double fI,
			TreeSet<Circuit> circuitList, Modulation modulation, int victimCore,
			double startKm, double sectionKm, double aggressorPowerRatio,
			ClassicalMultiBand.Params band) {
		final double localAlpha = band == null ? alphaLinear : band.alphaLinear;
		final double localGamma = band == null ? gamma : band.gamma;
		final double localBeta2 = band == null ? beta2 : band.beta2;
		double beta21 = Math.abs(localBeta2);

		double alfaCampo = localAlpha / 2.0;
		double Ls = sectionKm * 1000.0; // ACTUAL span length in metres
		double Leff = (1.0 - Math.pow(Math.E, -2.0 * alfaCampo * Ls)) / (2.0 * alfaCampo);
		double E = (8.0 * localGamma * localGamma * Leff * Leff * 2.0 * alfaCampo) / (27.0 * Math.PI * beta21);

        // Imported selective experimental optimization. The context includes all fibre and CUT
        // inputs, and each entry includes the interferer's frequency, bandwidth and modulation.
        // It does NOT cache powers, PICh, gain saturation or the longitudinal state.
        final boolean pairCacheOn = Boolean.getBoolean("snets.gnPairCache")
                && !simulator.PerfChecks.DISABLED && circuitI != null;
        final GnPairCache pairCache = pairCacheOn
                ? GnPairCache.of(circuitI, fI, BsI, beta21, alfaCampo, E, Ls) : null;

		//Paper: Impairment-Aware Manycast Routing, Modulation Level, and Spectrum Assignment in Elastic Optical Networks (2019)
		double ro = (((Math.PI * Math.PI) / 2.0) * beta21 * BsI * BsI) / (2.0 * alfaCampo);
		double ro2 = E * arcsinh(ro);
		double gsci = Gi * Gi * Gi * ro2;
        double gxci = 0.0;
        final List<Poggiolini2017GnIntegral.Channel> egnInterferers =
                new ArrayList<Poggiolini2017GnIntegral.Channel>();

        // Correction term related to signal modulation
		double Wnn = 0;
		double Wnm = 0;

		//Paper: Impairment-Aware Manycast Routing, Modulation Level, and Spectrum Assignment in Elastic Optical Networks (2019)
		double Phii = getValueOfModulationConstant(modulation);
		Wnn = Gi * Gi * Gi * (E * (5.0 * Phii) / (3.0 * alfaCampo * Ls));
		Wnm = 0.0;

		int saJ[] = null;
		double numOfSlots = 0.0;
		double Bsj = 0.0;
		double fJ = 0.0;
		double deltaFij = 0.0;
		double d1 = 0.0;
		double d2 = 0.0;
		double powerJ = powerI; // Power of the circuit j
		double Gj = Gi; // Power spectral density of the circuit j

		for(Circuit circuitJ : circuitList){

			if(!circuitI.equals(circuitJ)){
				saJ = circuitJ.getSpectrumAssignedByLink(link);
				//numOfSlots = saJ[1] - saJ[0] + 1.0;
				numOfSlots = circuitJ.getModulation().requiredSlots(circuitJ.getRequiredBitRate()); // Number of slots required

				//Bsj = (numOfSlots - circuitJ.getGuardBand()) * slotBandwidth; // Circuit bandwidth, less the guard band
				Bsj = numOfSlots * slotBandwidth; // Circuit bandwidth
				fJ = GnModelMath.centerHz(lowerFrequency, slotBandwidth, saJ); // Central frequency of circuit

				Bsj = circuitJ.getModulation().getBandwidthFromBitRate(circuitJ.getRequiredBitRate());

				powerJ = getCircuitLaunchPower(circuitJ, circuitJ.getModulation())
						* aggressorPowerRatio;

				Gj = powerJ / Bsj; // Local interferer PSD at this span entrance.

                egnInterferers.add(PhysicalLayer.integralChannel(fJ, Bsj,
                        powerJ, GnModelMath.phi(circuitJ.getModulation()),habibiNliMode));


				deltaFij = Math.abs(fI - fJ);

				d1 = deltaFij + (Bsj / 2.0);
				d2 = deltaFij - (Bsj / 2.0);

                // Cache the geometry-only GN/EGN pair coefficients; amplitude remains live.
                final double Phij = getValueOfModulationConstant(circuitJ.getModulation());
                final int entry = pairCache == null ? -1
                        : pairCache.find(circuitJ.getId(), fJ, Bsj, circuitJ.getModulation());
                double pnm, wnmCoefficient;
                if (entry >= 0) {
                    pnm = pairCache.pnm(entry);
                    wnmCoefficient = pairCache.w(entry);
                    simulator.PerfChecks.count(simulator.PerfChecks.GN_PAIR_HIT);
                    if (simulator.PerfChecks.VERIFY) {
                        double nm1 = (Math.PI * Math.PI * beta21 * BsI * d1) / (2.0 * alfaCampo);
                        double nm2 = (Math.PI * Math.PI * beta21 * BsI * d2) / (2.0 * alfaCampo);
                        simulator.PerfChecks.same(pnm, E * (arcsinh(nm1) - arcsinh(nm2)), "GN pair SCI/XCI");
                        simulator.PerfChecks.same(wnmCoefficient,
                                E * (5.0 * Phij * Bsj) / (6.0 * alfaCampo * Ls * Math.max(1e-30, deltaFij)),
                                "EGN pair correction");
                    }
                } else {
                    final double nm1 = (Math.PI * Math.PI * beta21 * BsI * d1) / (2.0 * alfaCampo);
                    final double nm2 = (Math.PI * Math.PI * beta21 * BsI * d2) / (2.0 * alfaCampo);
                    pnm = E * (arcsinh(nm1) - arcsinh(nm2));
                    wnmCoefficient = E * (5.0 * Phij * Bsj) / (6.0 * alfaCampo * Ls * Math.max(1e-30, deltaFij));
                    if (pairCache != null) {
                        pairCache.store(circuitJ.getId(), fJ, Bsj, circuitJ.getModulation(), pnm, wnmCoefficient);
                        simulator.PerfChecks.count(simulator.PerfChecks.GN_PAIR_MISS);
                    }
                }
                gxci += Gi * Gj * Gj * pnm;
                Wnm += Gi * Gj * Gj * wnmCoefficient;
			}
		}

		// PICh has a configured SNetS modulation. Apply BOTH the GN XCI and
		// the EGN modulation correction on the physical subspan traversed.
		double pichXci = qkd.capacity.PichClassicalNonlinearity.crossTermHabibi(
				link, victimCore, fI, BsI, Gi, E,
				alfaCampo, beta21, lowerFrequency, slotBandwidth,
				startKm, startKm+sectionKm);
		double pichCorr = qkd.capacity.PichClassicalNonlinearity.habibiCorrection(
				link, victimCore, fI, Gi, E, alfaCampo, Ls,
				lowerFrequency, slotBandwidth, startKm, startKm+sectionKm);
		//Paper: Impairment-Aware Manycast Routing, Modulation Level, and Spectrum Assignment in Elastic Optical Networks (2019)
		double gnli = gsci + gxci + pichXci;
		double gcorr = Wnn + Wnm + pichCorr;

		//Applying the correction term for the GN model
		double gnli_res = gnli - gcorr;

        // Narrow/negative Habibi: use ONLY numerical Poggiolini GN integrals
        // for SCI and each eligible XCI. The removed Carena/EGN correction
        // integrals are never evaluated. PICh terms retain their existing
        // analytical model. This is not an all-channel EGN/WDM solution.
        if(NliIntegrationMode.useHabibiIntegral(habibiNliMode, false,
                BsI, nliNarrowbandGHz, gnli_res)) {
            Poggiolini2017GnIntegral.Channel cut = PhysicalLayer.integralChannel(
                    fI,BsI,Gi*BsI,Phii,habibiNliMode);
            if(cut == null && NliIntegrationMode.INTEGRAL.equals(habibiNliMode))
                throw new IllegalArgumentException("GN integral unavailable: invalid channel metadata");
            if(cut != null) {
                Poggiolini2017GnIntegral.Fiber fiber = new Poggiolini2017GnIntegral.Fiber(
                        localAlpha*1000.0*(10.0/Math.log(10.0)),
                        localGamma*1000.0,localBeta2*1e27,sectionKm,1);
                // Supply legacy EXCLUDING PICh, then add PICh GN terms after.
                PhysicalLayer.HabibiGnDecision result=PhysicalLayer.evaluate(
                        fiber,cut,egnInterferers,gnli_res-(pichXci-pichCorr),
                        NliIntegrationMode.INTEGRAL.equals(habibiNliMode)
                                ? Double.POSITIVE_INFINITY : nliNarrowbandGHz,
                        true,habibiEgnQuadratureOrder,habibiEgnReceiverOrder);
                gnli_res=result.corrected+pichXci-pichCorr;
            } else {
                PhysicalLayer.warnUnsupportedCut(Phii,BsI);
            }
        }
        return GnModelMath.nonnegative(gnli_res, "Habibi + optional numerical GN fallback");
	}

	/**
	 * Returns the value of modulation constant
	 *
	 * @param modulation Modulation
	 * @return double
	 */
	public double getValueOfModulationConstant(Modulation modulation) {
		return GnModelMath.phi(modulation);
	}

	/**
	 * Function that returns the inverse hyperbolic sine of the argument
	 * asinh == arcsinh
	 *
	 * @param x double
	 * @return double
	 */
	public static double arcsinh(double x){
		// Casos especiais
		if (Double.isNaN(x)) return Double.NaN;

		if (x == Double.POSITIVE_INFINITY) return Double.POSITIVE_INFINITY;

		if (x == Double.NEGATIVE_INFINITY) return Double.NEGATIVE_INFINITY;

		if (x == 0.0) return 0.0; // Preserva sinal de zero

		final double absX = Math.abs(x);
		final double sign = Math.copySign(1.0, x);

		// 1. Taylor expansion for very small x
		if (absX < 1e-4) {
			double x2 = x * x;

			return x * (1.0 - x2 * (1.0 / 6.0 - x2 * (3.0 / 40.0)));
		}

		// 2. Asymptotic approximation for very large x
		if (absX > 1e6) {
			double inv2 = 1.0 / (absX * absX);
			double correction = (inv2 / 4.0) - (5.0 * inv2 * inv2 / 32.0);

			return sign * (Math.log(2.0 * absX) - correction);
		}

		// 3. Intermediate region - standard calculation with numerical stability
		// Uses log1p for better precision at medium/small values
		double sqrtTerm = Math.sqrt(absX * absX + 1.0);
		double inner = absX + (absX * absX) / (1.0 + sqrtTerm);
		double result = Math.log1p(inner);

		return sign * result;
	}

	/**
	 * Jurandir Lacerda Jr., Thesis, Chapter 3: optical OSNR to BER.
	 * The argument osnrLinear is the same PSD ratio and OSNR metric used by
	 * computeOSNRSegment() and the modulation's configured OSNR threshold.
	 * Equation 3.8: OSNR = Rb/(2*Rs) * SNR_bit;
	 * Equation 3.9: Rb = p*log2(M)*Rs. Therefore, for dual polarization
	 * p=2 (Eq. 3.10), SNR_bit = OSNR/log2(M).
	 * This is NOT a raw symbol SNR (Es/N0) argument.
	 * For a configured PhysicalLayer instance, getBER(osnr,M) uses its
	 * polarizationModes (p=1 or p=2). Independent reference calculations
	 * can call the static overload with p explicitly.
	 */
	/** Uses this PhysicalLayer's polarizationModes instead of a fixed p=2. */
	public double getBER(double osnrLinear, double M) {
		return getBER(osnrLinear, M, polarizationModes);
	}

	/** OSNR-to-BER with the modulation's actual number of polarization modes. */
	public static double getBER(double osnrLinear, double M, double polarizations) {
		final double snrBit = osnrToSnrPerBit(osnrLinear, M, polarizations);
		return getBERFromSnrPerBit(snrBit, M);
	}

	/** Eqs. 3.8-3.10. Both OSNR and SNR_bit are linear (not dB). */
	public static double osnrToSnrPerBit(double osnrLinear, double M, double polarizations) {
		if (!Double.isFinite(osnrLinear) || osnrLinear < 0.0 ||
				!Double.isFinite(M) || M < 2.0 ||
				!Double.isFinite(polarizations) || polarizations <= 0.0)
			return Double.NaN;
		final double bits = log2(M);
		final int bitCount = (int) Math.rint(bits);
		if (bitCount < 1 || bitCount > 24 || Math.abs(bits - bitCount) > 1e-10)
			return Double.NaN;
		return osnrLinear * 2.0 / (polarizations * bits);
	}

	/** Eqs. 3.4 (square QAM), 3.6 (8-QAM), 3.7 (32-QAM). */
	public static double getBERFromSnrPerBit(double snrBit, double M) {
		if (!Double.isFinite(snrBit) || snrBit < 0.0 ||
				!Double.isFinite(M) || M < 2.0) return Double.NaN;
		final double bits = log2(M);
		final int bitCount = (int) Math.rint(bits);
		if (bitCount < 1 || bitCount > 24 || Math.abs(bits - bitCount) > 1e-10)
			return Double.NaN;
		final double ber;
		if (bitCount == 1) {
			// BPSK extension: not one of Jurandir's QAM Eqs. 3.4/3.6/3.7.
			ber = 0.5 * erfc(Math.sqrt(snrBit));
		} else if (bitCount == 3) {
			ber = 11.0 / 16.0 * erfc(Math.sqrt(3.0 * snrBit / (3.0 + Math.sqrt(3.0))));
		} else if (bitCount == 5) {
			ber = 91.0 / 240.0 * erfc(Math.sqrt(snrBit / 4.0));
		} else if (bitCount % 2 == 0) {
			final double root = Math.sqrt(M);
			ber = 2.0 / bits * (root - 1.0) / root
					* erfc(Math.sqrt(3.0 * bits * snrBit / (2.0 * (M - 1.0))));
		} else {
			// No constellation geometry for other odd-log2(M) cases in the thesis.
			return Double.NaN;
		}
		// AWGN approximations may exceed 0.5 at vanishing SNR, where they
		// are not physically meaningful; clamp to a valid probability.
		return Math.max(0.0, Math.min(0.5, ber));
	}

	/**
	 * Historical rectangular Gray-AWGN approximation (OSNR treated as
	 * Es/N0). Opt-in solely to reproduce older experiments; it is not the
	 * thesis' specific 8-QAM/32-QAM model.
	 */
	public static double getBERLegacyRectangularAwgn(double osnrLinear, double M) {
		if (!Double.isFinite(osnrLinear) || osnrLinear < 0.0 ||
				!Double.isFinite(M) || M < 2.0) return Double.NaN;
		final double bits = log2(M);
		final int roundedBits = (int) Math.rint(bits);
		if (roundedBits < 1 || roundedBits > 24 || Math.abs(bits - roundedBits) > 1e-10)
			return Double.NaN;
		if (roundedBits == 1) return 0.5 * erfc(Math.sqrt(osnrLinear));
		final double columns = Math.pow(2.0, roundedBits / 2);
		final double rows = Math.pow(2.0, roundedBits - roundedBits / 2);
		final double ratio = (1.0 - 1.0 / columns) + (1.0 - 1.0 / rows);
		final double argument = Math.sqrt(3.0 * osnrLinear / (columns * columns + rows * rows - 2.0));
		return Math.max(0.0, Math.min(0.5, ratio / bits * erfc(argument)));
	}

	/**
	 * Stable erfc approximation (Numerical Recipes). Avoids the cancellation
	 * of 1-erf(x) at high SNR, where BER can be very small.
	 */
	public static double erfc(double x){
		if (Double.isNaN(x)) return Double.NaN;
		if (x == 0.0) return 1.0;
		if (x == Double.POSITIVE_INFINITY) return 0.0;
		if (x == Double.NEGATIVE_INFINITY) return 2.0;
		final double z = Math.abs(x);
		final double t = 1.0 / (1.0 + 0.5 * z);
		final double tail = t * Math.exp(-z*z - 1.26551223 + t*(1.00002368
				+ t*(0.37409196 + t*(0.09678418 + t*(-0.18628806
				+ t*(0.27886807 + t*(-1.13520398 + t*(1.48851587
				+ t*(-0.82215223 + t*0.17087277)))))))));
		return x < 0 ? 2.0 - tail : tail;
	}

	/**
	 * Error function - approximation
	 * http://www.galileu.esalq.usp.br/mostra_topico.php?cod=240
	 *
	 * @param x double
	 * @return double
	 */
	public static double erf(double x){
		double a = 0.140012;
		double v = sgn(x) * Math.sqrt(1.0 - Math.exp(-1.0 * (x * x) * (((4.0 / Math.PI) + (a * x * x)) / (1.0 + (a * x * x)))));

		return v;
	}

	/**
	 * Signal function
	 *
	 * @param x double
	 * @return double
	 */
	public static double sgn(double x){
		double s = 1.0;

		if(x < 0.0){
			s = s * -1.0;
		}else if(x == 0.0){
			s = 0.0;
		}

		return s;
	}

	/**
	 * Converts a ratio (linear) to decibel (dB)
	 *
	 * @param ratio double
	 * @return double dB
	 */
	public static double ratioForDB(double ratio) {
		double dB = 10.0 * Math.log10(ratio);

		return dB;
	}

	/**
	 * Converts a value in decibel (dB) to a linear value (ratio)
	 *
	 * @param dB double
	 * @return double ratio
	 */
	public static double ratioOfDB(double dB) {
		double ratio = Math.pow(10.0, (dB / 10.0));

		return ratio;
	}

	/**
	 * Converts a Watts (linear) to dBm
	 *
	 * @param w double
	 * @return double dBm
	 */
	public static double W_to_dBm(double w) {
		double dBm = 10.0 * Math.log10(w * 1000.0);

		return dBm;
	}

	/**
	 * Converts a value in dBm to a linear value (Watts)
	 *
	 * @param dBm double
	 * @return double Watts
	 */
	public static double dBm_to_W(double dBm) {
		double w = Math.pow(10.0, (dBm / 10.0)) * 1.0E-3;

		return w;
	}

	/**
	 * Logarithm in base 2
	 *
	 * @param x double
	 * @return double
	 */
	public static double log2(double x){
		return (Math.log10(x) / Math.log10(2.0));
	}

	/**
	 * Rounds up a double value for int
	 *
	 * @param res double
	 * @return int
	 */
	public static int roundUp(double res){
		if(res < 0.0) {
			return 0;
		}

		int res2 = (int) res;

		if(res - res2 != 0.0){
			res2++;
		}

		return res2;
	}

	/**
	 * Returns the beta2 parameter
	 *
	 * @param D double
	 * @param frequency double
	 * @return double
	 */
	public static double computeBeta2(double D, double frequency){
		double c = 299792458.0; // speed of light, m/s
		double lambda = c / frequency;
		double beta2 = -1.0 * D * (lambda * lambda) / (2.0 * Math.PI * c);

		return beta2;
	}

	/**
	 * Returns the alpha linear value (1/m) of a value in dB/km
	 *
	 * Example:
	 * 10 * Log10(e^(alpha * km)) = 0.2 dB/km
	 * (alpha * km) * 10 * Log10(e) = 0.2 dB/km
	 * alpha = (0.2 dB/km) / (km * 10 * Log10(e))
	 * alpha = (0.2 dB/km) / (1000 * m * 10 * Log10(e))
	 * alpha = (0.2 dB/km) / (10000 * Log10(e) * m)
	 * alpha (dB/km) = (0.2 dB/km) / (10000 * Log10(e) * m)
	 * alpha (linear) = (0.2 dB/km) / (10000 * Log10(e) * m * dB/km)
	 * alpha (linear) = 4.60517E-5 / m
	 *
	 * @param alpha double
	 * @return double
	 */
	public static double computeAlphaLinear(double alpha){
		double alphaLinear = alpha / (1.0E+4 * Math.log10(Math.E));

		return alphaLinear;
	}

	/**
	 * Calculates the transmission distances of the modulation formats
	 *
	 * @param mesh Mesh
	 * @param avaliableModulations List<Modulation>
	 * @return HashMap<Modulation, HashMap<Double, Double>>
	 */
	public HashMap<String, HashMap<Double, Double>> computesModulationsDistances(Mesh mesh, List<Modulation> avaliableModulations) {
		//System.out.println("Computing of the distances of the modulation formats");

		Set<Double> bitRateList = util.bitRateList;
		HashMap<String, HashMap<Double, Double>> modsTrsDistances = new HashMap<>();

		for(int m = 0; m < avaliableModulations.size(); m++) {
			Modulation mod = avaliableModulations.get(m);

			for(double bitRate : bitRateList) {

				HashMap<Double, Double> slotsDist = modsTrsDistances.get(mod.getName());

				if(slotsDist == null) {
					slotsDist = new HashMap<>();
					modsTrsDistances.put(mod.getName(), slotsDist);
				}

				Double dist = slotsDist.get(bitRate);

				if(dist == null) {
					dist = 0.0;
				}

				slotsDist.put(bitRate, dist);
			}
		}

		for(int m = 0; m < avaliableModulations.size(); m++) {
			Modulation mod = avaliableModulations.get(m);

			for(double bitRate : bitRateList) {

				double distance = computeModulationDistanceByBitRate(mod, bitRate, mesh);
				modsTrsDistances.get(mod.getName()).put(bitRate, distance);
			}
		}

//		for(double transmissionRate : transmissionRateList) {
//			System.out.println("TR(Gbps) = " + (transmissionRate / 1.0E+9));
//
//			for(int m = 0; m < avaliableModulations.size(); m++) {
//				Modulation mod = avaliableModulations.get(m);
//				int slotNumber = mod.requiredSlots(transmissionRate) - mod.getGuardBand();
//
//				System.out.println("Mod = " + mod.getName() + ", slot num = " + slotNumber);
//			}
//		}

//		for(double transmissionRate : transmissionRateList) {
//			System.out.println("TR(Gbps) = " + (transmissionRate / 1.0E+9));
//
//			for(int m = 0; m < avaliableModulations.size(); m++) {
//				Modulation mod = avaliableModulations.get(m);
//
//				double modTrDist = modsTrsDistances.get(mod.getName()).get(transmissionRate);
//				System.out.println("Mod = " + mod.getName() + ", distance(km) = " + modTrDist);
//			}
//		}

		return modsTrsDistances;
	}

	// New method to compute the modulation distance

	/**
	 * Computes the modulation distance by bit rate.
	 * @param mod the mod.
	 * @param bitRate the bitRate.
	 * @param mesh the mesh.
	 * @return the result of the operation.
	 */
	public double computeModulationDistanceByBitRate(Modulation mod, double bitRate, Mesh mesh) {

		int totalSlots = mesh.getLinkList().firstElement().getCore(0).getNumOfSlots();
		int guardBand = mesh.getGuardBand();
		//int numberOfCores = mesh.getLinkList().firstElement().getNumberOfCores();

		double minDistance = 12.5; //km
		double totalDistance = 50000.0; //km
		int quantSpansPorEnlace = (int)(totalDistance / minDistance); // number of spans per link

		int slotNumber = mod.requiredSlots(bitRate);
		int sa[] = new int[2];
		sa[0] = 1;
		sa[1] = sa[0] + slotNumber - 1;
		int indexCore = 0;

		List<CoreConfig> coreList = new ArrayList<>();
		coreList.add(new CoreConfig(0, new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6))));
		coreList.add(new CoreConfig(1, new ArrayList<>(Arrays.asList(0, 2, 6))));
		coreList.add(new CoreConfig(2, new ArrayList<>(Arrays.asList(0, 1, 3))));
		coreList.add(new CoreConfig(3, new ArrayList<>(Arrays.asList(0, 2, 4))));
		coreList.add(new CoreConfig(4, new ArrayList<>(Arrays.asList(0, 3, 5))));
		coreList.add(new CoreConfig(5, new ArrayList<>(Arrays.asList(0, 4, 6))));
		coreList.add(new CoreConfig(6, new ArrayList<>(Arrays.asList(0, 1, 5))));

		double modTrDistance = 0.0;

		for(int ns = 1; ns <= quantSpansPorEnlace; ns++){
			double distance = ns * minDistance;

			Node n1 = new Node("1", 1000, 1000, 0, 1000);
			Node n2 = new Node("2", 1000, 1000, 0, 1000);
			n1.getOxc().addLink(new Link(n1.getOxc(), n2.getOxc(), totalSlots, slotBandwidth, distance, coreList));

			Vector<Node> listNodes = new Vector<Node>();
			listNodes.add(n1);
			listNodes.add(n2);

			Route route = new Route(listNodes);
			Pair pair = new Pair(n1, n2);

			RequestForConnection requestTemp = new RequestForConnection();
			requestTemp.setPair(pair);
			requestTemp.setRequiredBitRate(bitRate);

			Circuit circuitTemp = new Circuit();
			circuitTemp.setPair(pair);
			circuitTemp.setRoute(route);
			circuitTemp.setModulation(mod);
			circuitTemp.setGuardBand(guardBand);
			circuitTemp.setIndexCore(indexCore);
			circuitTemp.setSpectrumAssigned(sa);
			circuitTemp.addRequest(requestTemp);

			double launchPower = Double.POSITIVE_INFINITY;

			if(!fixedPowerSpectralDensity){
				launchPower = computeMaximumPower(circuitTemp, bitRate, route, 0, route.getNodeList().size() - 1, mod, indexCore, sa);
			}

			circuitTemp.setLaunchPowerLinear(launchPower);

			route.getLink(0).getCore(0).addCircuit(circuitTemp);

			double OSNR = computeOSNRSegment(circuitTemp, circuitTemp.getRoute(), 0, circuitTemp.getRoute().getNodeList().size() - 1, circuitTemp.getModulation(), circuitTemp.getIndexCore(), circuitTemp.getSpectrumAssigned(), null, false);
			double OSNRdB = PhysicalLayer.ratioForDB(OSNR);

			if((OSNRdB >= mod.getOSNRthreshold()) && (distance > modTrDistance)){
				modTrDistance = distance;
			}
		}

		return modTrDistance;
	}

	// New method to compute distances

	/**
	 * Computes the modulation distance by bandwidth3.
	 * @param mod the mod.
	 * @param bitRate the bitRate.
	 * @param mesh the mesh.
	 * @return the result of the operation.
	 */
	public double computeModulationDistanceByBandwidth3(Modulation mod, double bitRate, Mesh mesh) {

		int totalSlots = mesh.getLinkList().firstElement().getCore(0).getNumOfSlots();
		int guardBand = mesh.getGuardBand();
		//int numberOfCores  = mesh.getLinkList().firstElement().getNumberOfCores();
		int indexCore = 0;

		double minDistance = 12.5; // km, minimum distance
		double totalDistance = 50000.0; // km, maximum distance
		int quantSpansPorEnlace = (int)(totalDistance / minDistance); // number of spans per link

		int slotNumber = mod.requiredSlots(bitRate);

		List<CoreConfig> coreList = new ArrayList<>();
		coreList.add(new CoreConfig(0, new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6))));
		coreList.add(new CoreConfig(1, new ArrayList<>(Arrays.asList(0, 2, 6))));
		coreList.add(new CoreConfig(2, new ArrayList<>(Arrays.asList(0, 1, 3))));
		coreList.add(new CoreConfig(3, new ArrayList<>(Arrays.asList(0, 2, 4))));
		coreList.add(new CoreConfig(4, new ArrayList<>(Arrays.asList(0, 3, 5))));
		coreList.add(new CoreConfig(5, new ArrayList<>(Arrays.asList(0, 4, 6))));
		coreList.add(new CoreConfig(6, new ArrayList<>(Arrays.asList(0, 1, 5))));

		double modTrDistance = 0.0;

		for(int ns = 1; ns <= quantSpansPorEnlace; ns++){
			double distance = ns * minDistance;

			Node n1 = new Node("1", 1000, 1000, 0, 1000);
			Node n2 = new Node("2", 1000, 1000, 0, 1000);
			n1.getOxc().addLink(new Link(n1.getOxc(), n2.getOxc(), totalSlots, slotBandwidth, distance, coreList));

			Vector<Node> listNodes = new Vector<Node>();
			listNodes.add(n1);
			listNodes.add(n2);

			Route route = new Route(listNodes);
			Pair pair = new Pair(n1, n2);

			int Nch = 15; //number of circuits
			Circuit circuitCentral = null;

			int saTemp = 0;

			for (int c = 1; c <= Nch; c++) {
				RequestForConnection requestTemp = new RequestForConnection();
				requestTemp.setPair(pair);
				requestTemp.setRequiredBitRate(bitRate);

				int sa[] = new int[2];
				sa[0] = saTemp + 1;
				sa[1] = sa[0] + slotNumber - 1;

				saTemp = sa[1]; //stores the final slot of the circuit

				Circuit circuitTemp = new Circuit();
				circuitTemp.setPair(pair);
				circuitTemp.setRoute(route);
				circuitTemp.setModulation(mod);
				circuitTemp.setSpectrumAssigned(sa);
				circuitTemp.setGuardBand(guardBand);
				circuitTemp.setIndexCore(indexCore);
				circuitTemp.addRequest(requestTemp);

				double launchPower = Double.POSITIVE_INFINITY;

				if(!fixedPowerSpectralDensity){
					launchPower = computeMaximumPower(circuitTemp, bitRate, route, 0, route.getNodeList().size() - 1, mod, indexCore, sa);
				}

				circuitTemp.setLaunchPowerLinear(launchPower);

				route.getLink(0).getCore(1).addCircuit(circuitTemp);

				if (c == 8) { //central circuit
					circuitCentral = circuitTemp;
				}
			}

			double OSNR = computeOSNRSegment(circuitCentral, circuitCentral.getRoute(), 0, circuitCentral.getRoute().getNodeList().size() - 1, circuitCentral.getModulation(), circuitCentral.getIndexCore(), circuitCentral.getSpectrumAssigned(), null, false);
			double OSNRdB = PhysicalLayer.ratioForDB(OSNR);

			if((OSNRdB >= mod.getOSNRthreshold()) && (distance > modTrDistance)){
				modTrDistance = distance;
			}
		}

		return modTrDistance;
	}

	// Jurandir test. NLI vs. span

	/**
	 * Computes the modulation distance by bandwidth4.
	 * @param mod the mod.
	 * @param bitRate the bitRate.
	 * @param mesh the mesh.
	 * @return the result of the operation.
	 */
	public double computeModulationDistanceByBandwidth4(Modulation mod, double bitRate, Mesh mesh) {

		int totalSlots = mesh.getLinkList().firstElement().getCore(0).getNumOfSlots();
		int guardBand = mesh.getGuardBand();
		//int numberOfCores = mesh.getLinkList().firstElement().getNumberOfCores();
		int indexCore = 0;

		double minDistance = 100.0; // km, minimum distance
		double totalDistance = 50000.0; // km, maximum distance
		int quantSpansPorEnlace = (int)(totalDistance / minDistance); // number of spans per link

		int slotNumber = mod.requiredSlots(bitRate);

		List<CoreConfig> coreList = new ArrayList<>();
		coreList.add(new CoreConfig(0, new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6))));
		coreList.add(new CoreConfig(1, new ArrayList<>(Arrays.asList(0, 2, 6))));
		coreList.add(new CoreConfig(2, new ArrayList<>(Arrays.asList(0, 1, 3))));
		coreList.add(new CoreConfig(3, new ArrayList<>(Arrays.asList(0, 2, 4))));
		coreList.add(new CoreConfig(4, new ArrayList<>(Arrays.asList(0, 3, 5))));
		coreList.add(new CoreConfig(5, new ArrayList<>(Arrays.asList(0, 4, 6))));
		coreList.add(new CoreConfig(6, new ArrayList<>(Arrays.asList(0, 1, 5))));

		double modTrDistance = 0.0;

		int numSpan = 50;

		for(int ns = 1; ns <= numSpan; ns++){
			double distance = ns * minDistance;

			Node n1 = new Node("1", 1000, 1000, 0, 1000);
			Node n2 = new Node("2", 1000, 1000, 0, 1000);
			n1.getOxc().addLink(new Link(n1.getOxc(), n2.getOxc(), totalSlots, slotBandwidth, distance, coreList));

			Vector<Node> listNodes = new Vector<Node>();
			listNodes.add(n1);
			listNodes.add(n2);

			Route route = new Route(listNodes);
			Pair pair = new Pair(n1, n2);

			int Nch = 15; //number of circuits
			Circuit circuitCentral = null;

			int saTemp = 0;

			for (int c = 1; c <= Nch; c++) {
				RequestForConnection requestTemp = new RequestForConnection();
				requestTemp.setPair(pair);
				requestTemp.setRequiredBitRate(bitRate);

				int sa[] = new int[2];
				sa[0] = saTemp + 1;
				sa[1] = sa[0] + slotNumber - 1;

				saTemp = sa[1]; //stores the final slot of the circuit

				Circuit circuitTemp = new Circuit();
				circuitTemp.setPair(pair);
				circuitTemp.setRoute(route);
				circuitTemp.setModulation(mod);
				circuitTemp.setSpectrumAssigned(sa);
				circuitTemp.setGuardBand(guardBand);
				circuitTemp.setIndexCore(indexCore);
				circuitTemp.addRequest(requestTemp);

				double launchPower = Double.POSITIVE_INFINITY;

				if(!fixedPowerSpectralDensity){
					launchPower = computeMaximumPower(circuitTemp, bitRate, route, 0, route.getNodeList().size() - 1, mod, indexCore, sa);
				}

				circuitTemp.setLaunchPowerLinear(launchPower);

				route.getLink(0).getCore(1).addCircuit(circuitTemp);

				if (c == 8) { //central circuit
					circuitCentral = circuitTemp;
				}
			}

			System.out.println("\n\n\n"+ns);

			double OSNR = computeOSNRSegment(circuitCentral, circuitCentral.getRoute(), 0, circuitCentral.getRoute().getNodeList().size() - 1, circuitCentral.getModulation(), circuitCentral.getIndexCore(), circuitCentral.getSpectrumAssigned(), null, false);
			double OSNRdB = PhysicalLayer.ratioForDB(OSNR);

			System.out.println("\n\n\n");

			if((OSNRdB >= mod.getOSNRthreshold()) && (distance > modTrDistance)){
				modTrDistance = distance;
			}
		}

		return modTrDistance;
	}

	/**
	 * Calculates the distance to a modulation format considering the bandwidth
	 *
	 * @param mod
	 * @param bitRate
	 * @param mesh
	 * @return double
	 */
/*	public double computeModulationDistanceByBandwidth(Modulation mod, double bitRate, Mesh mesh) {

		int totalSlots = mesh.getLinkList().firstElement().getCore(0).getNumOfSlots();
		int numberOfCores = mesh.getLinkList().firstElement().getNumberOfCores();

		Vector<Link> linkList = mesh.getLinkList();
		double sumLastFiberSegment = 0.0;
		for(int l = 0; l < linkList.size(); l++) {
			double Ns = getNumberOfLineAmplifiers(linkList.get(l).getDistance());
			double lastFiberSegment = linkList.get(l).getDistance() - (Ns * L);
			sumLastFiberSegment += lastFiberSegment;
		}
		double averageLastFiberSegment = sumLastFiberSegment / linkList.size();

		double totalDistance = 50000.0; //km
		int quantSpansPorEnlace = (int)(totalDistance / L); // number of spans per link

		int slotNumber = mod.requiredSlots(bitRate);
		int sa[] = new int[2];
		sa[0] = 1;
		sa[1] = sa[0] + slotNumber - 1;

		double modTrDistance = 0.0;

		for(int ns = 0; ns <= quantSpansPorEnlace; ns++){
			double distance = (ns * L) + averageLastFiberSegment;

			Node n1 = new Node("1", 1000, 1000, 0, 1000);
			Node n2 = new Node("2", 1000, 1000, 0, 1000);
			n1.getOxc().addLink(new Link(n1.getOxc(), n2.getOxc(), totalSlots, slotBandwidth, distance));

			Vector<Node> listNodes = new Vector<Node>();
			listNodes.add(n1);
			listNodes.add(n2);

			Route route = new Route(listNodes);
			Pair pair = new Pair(n1, n2);

			RequestForConnection requestTemp = new RequestForConnection();
			requestTemp.setPair(pair);
			requestTemp.setRequiredBandwidth(bitRate);

			Circuit circuitTemp = new Circuit();
			circuitTemp.setPair(pair);
			circuitTemp.setRoute(route);
			circuitTemp.setModulation(mod);
			circuitTemp.setSpectrumAssigned(sa);
			circuitTemp.addRequest(requestTemp);

			double launchPower = Double.POSITIVE_INFINITY;
			if(!fixedPowerSpectralDensity){
				launchPower = computeMaximumPower2(bitRate, route, 0, route.getNodeList().size() - 1, mod, sa);
			}
			circuitTemp.setLaunchPowerLinear(launchPower);

			route.getLink(0).getCore(0).addCircuit(circuitTemp); // Check!!!

			double OSNR = computeOSNRSegment(circuitTemp, circuitTemp.getRoute(), 0, circuitTemp.getRoute().getNodeList().size() - 1, circuitTemp.getModulation(), circuitTemp.getSpectrumAssigned(), null, false);
			double OSNRdB = PhysicalLayer.ratioForDB(OSNR);

			if((OSNRdB >= mod.getOSNRthreshold()) && (distance > modTrDistance)){
				modTrDistance = distance;
			}
		}

		return modTrDistance;
	}
*/

	/**
	 * This method searches for optimal power, considering constraints, that produces the highest possible ONSR.
	 * The constraints consider both ONSR and crosstalk.
	 *
	 * @param bitRate double
	 * @param route Route
	 * @param sourceNodeIndex int
	 * @param destinationNodeIndex int
	 * @param modulation Modulation
	 * @param spectrumAssigned int[]
	 * @return double - Linear value
	 */
	public double computeMaximumPower(Circuit circuit, double bitRate, Route route, int sourceNodeIndex, int destinationNodeIndex, Modulation modulation, int core, int spectrumAssigned[]){

		//Power that maximizes OSNR
		double PmaxOSNR = computePowerThatMaximizesOSNR(circuit, bitRate, route, sourceNodeIndex, destinationNodeIndex, modulation, core, spectrumAssigned);

		// Check if it really is the optical power
		// The shared longitudinal EDFA mode is nonlinear in launched power.
		// Its estimator already searches the ACTUAL active QoT curve.
		if (longitudinalAmplifierTemplate == null) {
			PmaxOSNR = checkMaximumPower(PmaxOSNR, circuit, route,
					modulation, core, spectrumAssigned);
		}

		if (activeXT && XTModel == XT_SEPARATE) {

			//The maximum power that does not violate the XT threshold for a modulation M
			double PmaxXT = crosstalk.computeMaximumPowerAllowedByXT(circuit, route, modulation, core, spectrumAssigned);

			//Optimal Power is the point within this window that produces the highest possible OSNR
			double Popt = Math.min(PmaxOSNR, PmaxXT);

			PmaxOSNR = Popt;
		}

		return PmaxOSNR;
	}

	/**
	 * This method computes the launch power that reaches the maximum OSNR.
	 *
	 * @param circuit Circuit
	 * @param bitRate double
	 * @param route Route
	 * @param sourceNodeIndex int
	 * @param destinationNodeIndex int
	 * @param modulation Modulation
	 * @param core int
	 * @param spectrumAssigned int[]
	 * @return double (Linear power)
	 */
	public double computePowerThatMaximizesOSNR(Circuit circuit, double bitRate, Route route,
			int sourceNodeIndex, int destinationNodeIndex, Modulation modulation, int core,
			int[] spectrumAssigned) {

		return computeCascadedMaximumPower(circuit, bitRate, route, sourceNodeIndex,
				destinationNodeIndex, modulation, core, spectrumAssigned);
	}

	/**
     * Vale & Almeida (2019), Eqs. (18)-(24): OSNR = d*P/(a*P^3+b*P+c),
     * so Pmax = cbrt(c/(2*a)). The coefficients a and c are referred
     * to the SAME receiver after cascading through all spans, EDFAs and nodes.
     *
     * For the reference a, only the victim SCI is included. The XCI term
     * (proportional to P for a fixed set of interferers) does not change
     * the location of the maximum under constant amplifier gains.
     * Saturated EDFAs violate this last assumption: checkMaximumPower()
     * subsequently searches the actual QoT curve on bounded powers.
     */
	private double computeCascadedMaximumPower(Circuit circuit, double bitRate, Route route,
			int sourceNodeIndex, int destinationNodeIndex, Modulation modulation, int core,
			int[] spectrumAssigned) {

		if (longitudinalAmplifierTemplate != null) {
			return maximizePowerOnLongitudinalQoT(circuit, route,
					sourceNodeIndex, destinationNodeIndex, modulation,
					core, spectrumAssigned);
		}

		// 1 mW = 0 dBm: NUMERICAL NORMALIZATION CHOICE, not an experimentally
		// calibrated parameter of the fibre/EDFA and not specified by Vale (2019).
		// With fixed gain and pure cubic SCI, P_opt = P_ref*(ASE/(2*SCI_ref))^(1/3)
		// must be invariant to the selected positive P_ref (verified separately).
		final double referenceW = 1.0e-3;
		return computeCascadedMaximumPowerAtReference(circuit, bitRate, route,
				sourceNodeIndex, destinationNodeIndex, modulation, core,
				spectrumAssigned, referenceW);
	}

	/**
	 * Package-private scientific verification seam: use an alternate reference
	 * without changing the production reference or modifying circuit state.
	 * Valid only for the fixed-gain cubic-SCI approximation; longitudinal
	 * saturating amplifiers instead use a bounded numerical power search.
	 */
	double computeCascadedMaximumPowerAtReference(Circuit circuit, double bitRate,
			Route route, int sourceNodeIndex, int destinationNodeIndex,
			Modulation modulation, int core, int[] spectrumAssigned,
			double referenceW) {
		if (!Double.isFinite(referenceW) || referenceW <= 0.0)
			throw new IllegalArgumentException("referenceW must be finite and > 0");
		if (longitudinalAmplifierTemplate != null) {
			return maximizePowerOnLongitudinalQoT(circuit, route,
					sourceNodeIndex, destinationNodeIndex, modulation, core, spectrumAssigned);
		}
		final double minPowerW = 1.0e-9;
		final double maxPowerW = 0.1;

		if (modulation.getBandwidthFromBitRate(bitRate) <= 0.0) {
			return Math.max(minPowerW, Math.min(maxPowerW, PowerLinear));
		}

		// This reference run uses the same physical stage propagation as QoT,
		// but excludes other-channel XCI, XT and PICh terms from the cubic SCI.
		OpticalNoiseCascade reference = propagateLegacyCascade(circuit, route,
				sourceNodeIndex, destinationNodeIndex, modulation, core,
				spectrumAssigned, null, false, bitRate, referenceW, true);

		final double ase = reference.getAsePsd();
		final double selfNli = reference.getNliPsd();

		// A zero cubic coefficient has no finite interior optimum. Do not
		// invent a tiny NLI coefficient; use bounded physical-search seeds.
		if (!(ase > 0.0) && !(selfNli > 0.0)) {
			return Math.max(minPowerW, Math.min(maxPowerW, PowerLinear));
		}

		if (!(ase > 0.0)) {
			return minPowerW;
		}

		if (!(selfNli > 0.0)) {
			return maxPowerW;
		}

		double optimumW = referenceW * Math.cbrt(ase / (2.0 * selfNli));

		if (!Double.isFinite(optimumW)) {
			return Math.max(minPowerW, Math.min(maxPowerW, PowerLinear));
		}

		return Math.max(minPowerW, Math.min(maxPowerW, optimumW));
	}

	/**
     * Bounded, deterministic search over the actual longitudinal optical OSNR.
     * The Vale/Almeida cubic optimum assumes constant gain and cannot
     * be applied without refinement to saturating EDFA snapshots.
     * Candidate power is restored after evaluation even on exceptions.
     */
	private double maximizePowerOnLongitudinalQoT(Circuit circuit, Route route,
			int sourceNodeIndex, int destinationNodeIndex,
			Modulation modulation, int core, int[] spectrumAssigned) {

		final double minLog = Math.log(1e-9);
		final double maxLog = Math.log(0.1);
		final int grid = 32;
		final double original = circuit.getLaunchPowerLinear();
		double bestOSNR = Double.NEGATIVE_INFINITY;
		int bestIndex = 0;

		try {
			for (int k = 0; k <= grid; k++) {
				double power = Math.exp(minLog + (maxLog-minLog)*k/grid);
				circuit.setLaunchPowerLinear(power);
				double osnr = computeOSNRSegment(circuit, route,
						sourceNodeIndex, destinationNodeIndex,
						modulation, core, spectrumAssigned, null, false);

				if (Double.isFinite(osnr) && osnr > bestOSNR) {
					bestOSNR = osnr;
					bestIndex = k;
				}
			}

			double left = minLog + (maxLog-minLog)*Math.max(0,bestIndex-1)/grid;
			double right = minLog + (maxLog-minLog)*Math.min(grid,bestIndex+1)/grid;

			for (int k = 0; k < 28; k++) {
				double p1 = left + (right-left)/3.0;
				double p2 = right - (right-left)/3.0;
				circuit.setLaunchPowerLinear(Math.exp(p1));
				double a = computeOSNRSegment(circuit,route,sourceNodeIndex,
						destinationNodeIndex,modulation,core,spectrumAssigned,null,false);
				circuit.setLaunchPowerLinear(Math.exp(p2));
				double b = computeOSNRSegment(circuit,route,sourceNodeIndex,
						destinationNodeIndex,modulation,core,spectrumAssigned,null,false);

				if (a < b) left = p1;
				else right = p2;
			}

			return Math.max(1e-9,Math.min(0.1,Math.exp((left+right)/2.0)));
		} finally {
			circuit.setLaunchPowerLinear(original);
		}
	}

	/**
     * Propagate signal PSD and accumulated ASE/NLI/XT PSD, in W/Hz, over the
     * physical directed route, accounting for actual span lengths and loss/gain.
     *
     * The source and interferer powers are in total Watts. The choice of
     * per-polarization PSD is made once at the receiver input. The model
     * uses the same SCI/XCI flags and EGN correction as the DCh QoT engine.
     *
     * The legacy amplifier model is retained here. When the shared
     * longitudinal EDFA mode is enabled, computeOSNRSegmentLongitudinal()
     * remains authoritative for saturated and multi-band operating points.
     */
	/**
     * Optional v2 triangular ISRS redistribution. Only classical channels in
     * the SAME directed link and core are considered; guard slots have no power.
     * The approximation changes longitudinal power and ASE, not the GN kernel.
     */
	private double isrsRatio(ClassicalMultiBand.Params band, Link link,
			TreeSet<Circuit> circuits, Circuit victim, int[] victimSlots,
			double frequencyHz, double sectionKm, double victimLaunchW) {

		if (classicalMultiBand == null || !classicalMultiBand.isIsrs()
				|| sectionKm <= 0.0) return 1.0;

		double minimum = Double.POSITIVE_INFINITY;
		double maximum = Double.NEGATIVE_INFINITY;
		double totalW = 0.0;

		for (Circuit source : circuits) {
			int[] slots = source == victim ? victimSlots
					: source.getSpectrumAssignedByLink(link);

			if (slots == null || slots.length < 2) continue;

			minimum = Math.min(minimum, lowerFrequency
					+ (slots[0] - 1.0) * slotBandwidth);
			maximum = Math.max(maximum, lowerFrequency
					+ slots[1] * slotBandwidth);
			totalW += source == victim ? victimLaunchW
					: getCircuitLaunchPower(source, source.getModulation());
		}

		if (!(maximum > minimum)) return 1.0;

		double ratio = classicalMultiBand.isrsRatio(frequencyHz, minimum,
				maximum, totalW, band.alphaLinear, sectionKm);

		if (!(ratio > 0.0) || !Double.isFinite(ratio)) {
			throw new IllegalStateException("nonphysical multiband ISRS ratio");
		}

		return ratio;
	}

	private OpticalNoiseCascade propagateLegacyCascade(Circuit circuit, Route route,
			int sourceNodeIndex, int destinationNodeIndex, Modulation modulation,
			int core, int[] spectrumAssigned, Circuit testCircuit,
			boolean addTestCircuit, double bitRate, double launchW, boolean selfOnly) {

		final double bandwidth = modulation.getBandwidthFromBitRate(bitRate);
		final double frequency = GnModelMath.centerHz(lowerFrequency, slotBandwidth, spectrumAssigned);
		final double aseCarrierHz = aseFrequencyHz(frequency);
		final double polarization = physicalLayerModel == MODEL_JOHANNISSON ? polarizationModes : 1.0;
		final ClassicalMultiBand.Params band = classicalMultiBand == null
				? null : classicalMultiBand.at(frequency);

		if (!(bandwidth > 0.0) || !(launchW > 0.0) || !Double.isFinite(launchW)) {
			return new OpticalNoiseCascade(0.0);
		}

		OpticalNoiseCascade propagation = new OpticalNoiseCascade(
				launchW / (polarization * bandwidth));

		for (int i = sourceNodeIndex; i < destinationNodeIndex; i++) {
			Link link = route.getNode(i).getOxc().linkTo(route.getNode(i + 1).getOxc());
			String cacheKey = null;

			if (QotLinkCache.enabled() && testCircuit == null && !addTestCircuit) {
				cacheKey = QotLinkCache.key(this, link, circuit, modulation, core,
						spectrumAssigned, launchW, bandwidth, frequency,
						propagation.snapshot(), selfOnly, false, typeOfAmplifierGain);
				double[] cached = qotCache().get(cacheKey);

				if (cached != null) {
					propagation.restore(cached);
					continue;
				}
			}

			TreeSet<Circuit> circuits = getCircuitList(link, core, circuit, testCircuit, addTestCircuit);

			// Active EDFA load is the total optical power, not the power
			// averaged per polarization. The victim is replaced by launchW
			// while deriving the reference power coefficient.
			double loadW = linkAmplifierLoadW(circuits, circuit, launchW, link, core);
			double linkPowerRatio = 1.0;

			// Optical MUX only. The OXC/switch is lossless by model choice.
			propagation.scale(1.0 / muxLossLinear);
			linkPowerRatio /= muxLossLinear;

			double boosterInputW = loadW * linkPowerRatio;
			double boosterGain = boosterAmp.getGainByType(boosterInputW, typeOfAmplifierGain);
			propagation.scale(boosterGain);
			linkPowerRatio *= boosterGain;

			if (activeASE) {
				double boosterAse = Amplifier.asePsd(aseCarrierHz,
						band == null ? ratioOfDB(NF) : ratioOfDB(band.noiseFigureDb),
						boosterGain, h, boosterInputW, A1, A2,
						typeOfAmplifierGain == AMP_GAIN_SATURATED);
				propagation.addAse(boosterAse / polarization);
			}

			final double[] lengths = GnModelMath.spanLengthsKm(link.getDistance(), L);
			double startKm = 0.0;

			for (int index = 0; index < lengths.length; index++) {
				final double sectionKm = lengths[index];
				final double rho = band == null ? 1.0 : isrsRatio(band, link,
						circuits, circuit, spectrumAssigned, frequency,
						sectionKm, launchW);
				final double fibreAttenuation = ratioOfDB(
						(band == null ? alpha : band.alphaDbPerKm) * sectionKm);
				final double attenuation = fibreAttenuation / rho;
				final double inputSignalPsd = propagation.getSignalPsd();
				final double localNli = activeNLI
						? calculateSectionNli(circuit, link, circuits, modulation, core,
								bandwidth, frequency, inputSignalPsd, polarization,
								startKm, sectionKm, linkPowerRatio, selfOnly, band) : 0.0;

				// NLI formula predicts PSD after perfect loss compensation;
				// restore the actual EDFA gain/loss ratio for this section.
				propagation.scale(1.0 / attenuation);
				linkPowerRatio /= attenuation;

				final boolean last = index == lengths.length - 1;
				// The final EDFA uses a locally computed residual-span
				// gain and does not mutate the shared preAmp.
				final double amplifierInputW = loadW * linkPowerRatio;
				final double targetGain = last
						? Math.max(1.0, attenuation * demuxLossLinear)
						: band == null ? lineAmp.getGainLinear() : Math.max(1.0, attenuation);
				final double gain = band != null || last
						? Amplifier.gain(targetGain, amplifierInputW,
								dBm_to_W(pSat), typeOfAmplifierGain == AMP_GAIN_SATURATED)
						: lineAmp.getGainByType(amplifierInputW, typeOfAmplifierGain);

				propagation.scale(gain);
				linkPowerRatio *= gain;
				propagation.addNli(GnModelMath.nonnegative(localNli,
						"cascade section NLI") * gain / attenuation);

				if (activeASE) {
					// The same carrier convention applies to booster, line
					// amplifiers and preamp, including MB/MB-SDM without
					// frequency-dependent EDFA gain profiles.
					double ase = Amplifier.asePsd(aseCarrierHz,
							band == null ? ratioOfDB(NF) : ratioOfDB(band.noiseFigureDb),
							gain, h, amplifierInputW, A1, A2,
							typeOfAmplifierGain == AMP_GAIN_SATURATED);
					propagation.addAse(ase / polarization);
				}

				startKm += sectionKm;
			}

			if (!selfOnly && activeXT && XTModel == XT_TOGETHER) {
				double xtPower = crosstalk.calculateCrosstalkPowerInLink(circuit,
						link, core, spectrumAssigned, testCircuit, addTestCircuit);
				double xtPsd = xtPower / (polarization * bandwidth);
				xtPsd += qkd.capacity.PichClassicalCrosstalk.noisePsd(link,
						core, spectrumAssigned, bandwidth, crosstalk.getH(),
						polarizationModes, physicalLayerModel == MODEL_JOHANNISSON);
				propagation.addXt(xtPsd);
			}

			// DEMUX at the destination node. No optical switch loss.
			propagation.scale(1.0 / demuxLossLinear);

			if (cacheKey != null) {
				qotCache().put(cacheKey, propagation.snapshot());
			}
		}

		return propagation;
	}

	private double linkAmplifierLoadW(TreeSet<Circuit> circuits, Circuit victim,
			double victimPowerW, Link link, int core) {

		double totalW = 0.0;

		for (Circuit candidate : circuits) {
			if (candidate == victim) {
				totalW += victimPowerW;
			} else if (!(candidate instanceof qkd.capacity.QKDPichCircuit)) {
				totalW += getCircuitLaunchPower(candidate, candidate.getModulation());
			}
		}

		totalW += qkd.capacity.PichClassicalNonlinearity.meanPichPowerW(link, core);

		return totalW;
	}

	/**
     * Reference a: self-channel NLI is cubic in the tested signal power.
     * All interfering sources are deliberately excluded from this term.
     */
	private double selfNliPsd(double signalPsd, double bandwidth, Modulation modulation,
			double sectionKm, ClassicalMultiBand.Params band) {

		final double localAlpha = band == null ? alphaLinear : band.alphaLinear;
		final double localGamma = band == null ? gamma : band.gamma;
		final double localBeta2 = band == null ? beta2 : band.beta2;

		if (physicalLayerModel == MODEL_JOHANNISSON) {
			double beta = Math.abs(localBeta2);
			double ro = bandwidth * bandwidth * Math.PI * Math.PI * beta / localAlpha;
			double kernel = GnModelMath.johannissonSciKernel(ro, bandwidth,
                    johannissonNliMode, nliNarrowbandGHz, false);
			double prefactor = (3.0 * 64.0 * localGamma * localGamma)
					/ (2.0 * 81.0 * Math.PI * localAlpha * beta);

			return GnModelMath.nonnegative(prefactor * signalPsd * signalPsd
					* signalPsd * kernel * GnModelMath.partialSpanFactor(sectionKm,
							L, localAlpha), "cascade Johannisson SCI");
		}

		double alfaCampo = localAlpha / 2.0;
		double lengthM = sectionKm * 1000.0;
		double effectiveM = -Math.expm1(-2.0 * alfaCampo * lengthM) / (2.0 * alfaCampo);
		double beta = Math.abs(localBeta2);
		double e = (8.0 * localGamma * localGamma * effectiveM * effectiveM * 2.0 * alfaCampo)
				/ (27.0 * Math.PI * beta);
		double ro = Math.PI * Math.PI * beta * bandwidth * bandwidth / (4.0 * alfaCampo);
		double correction = e * 5.0 * GnModelMath.phi(modulation)
				/ (3.0 * alfaCampo * lengthM);

        double original=signalPsd*signalPsd*signalPsd*(e*arcsinh(ro)-correction);
        if(NliIntegrationMode.useHabibiIntegral(habibiNliMode, false,
                bandwidth, nliNarrowbandGHz, original)) {
            Poggiolini2017GnIntegral.Channel cut=PhysicalLayer.integralChannel(
                    193.1e12,bandwidth,signalPsd*bandwidth,
                    GnModelMath.phi(modulation),habibiNliMode);
            if(cut!=null) {
                Poggiolini2017GnIntegral.Fiber fiber=new Poggiolini2017GnIntegral.Fiber(
                        localAlpha*1000.0*(10.0/Math.log(10.0)),
                        localGamma*1000.0,localBeta2*1e27,sectionKm,1);
                return PhysicalLayer.cachedPoggioliniGn(fiber,
                        java.util.Collections.singletonList(cut),
                        habibiEgnQuadratureOrder,habibiEgnReceiverOrder).sci;
            }
        }
        return GnModelMath.nonnegative(original, "cascade Habibi SCI");
	}

	private double calculateSectionNli(Circuit circuit, Link link,
			TreeSet<Circuit> circuits, Modulation modulation, int core,
			double bandwidth, double frequency, double signalPsd,
			double polarization, double startKm, double sectionKm,
			double aggressorPowerRatio, boolean selfOnly,
			ClassicalMultiBand.Params band) {

		if (selfOnly) {
			return selfNliPsd(signalPsd, bandwidth, modulation, sectionKm, band);
		}

		double powerPerPol = signalPsd * bandwidth;

		if (physicalLayerModel == MODEL_JOHANNISSON) {
			return getGnli(circuit, link, powerPerPol, bandwidth, signalPsd,
					frequency, circuits, core, startKm, sectionKm, aggressorPowerRatio, band);
		}

		return getGnli2(circuit, link, powerPerPol * polarization, bandwidth,
				signalPsd, frequency, circuits, modulation, core, startKm, sectionKm,
				aggressorPowerRatio, band);
	}

	/**
	 * This method uses ternary search to find the power that generates the maximum OSNR.
	 * Adaptive range expansion from P0, automatically detecting the maximum region.
	 * Protection against negative values ​​(there are no negative powers).
	 *
	 * @param Pstart double
	 * @param circuit Circuit
	 * @param route Route
	 * @param mod Modulation
	 * @param core int
	 * @param sa int[]
	 * @return double - liner (Watts)
	 */
	private double checkMaximumPower(double Pstart, Circuit circuit, Route route, Modulation mod, int core, int sa[]) {
		final double originalLaunchPower = circuit.getLaunchPowerLinear();

		try {
		// =============================================
		// CONSTANTES DE CONTROLE
		// =============================================
		final int    MAX_EXPANSION_ITER = 50;
		final double MIN_POWER          = 1e-9;   // por exemplo -60 dBm
		final double MAX_POWER          = 0.1;    // por exemplo +20 dBm

		// =============================================
		// Adaptive expansion of the [Pmin, Pmax] interval from P0
		// Automatically detecting the region of the maximum.
		// =============================================

		// Ensures that Pstart is within the physical limits
		double P0 = Math.max(MIN_POWER, Math.min(MAX_POWER, Pstart));

		// -------------------------------
		// Passo em dBm (0.5 dB)
		// -------------------------------
		double step_dB   = 0.5;
		double P0_dBm    = W_to_dBm(P0);
		double left_dBm  = P0_dBm - step_dB;
		double right_dBm = P0_dBm + step_dB;

		double P_left  = dBm_to_W(left_dBm);
		double P_right = dBm_to_W(right_dBm);

		P_left  = Math.max(MIN_POWER, Math.min(MAX_POWER, P_left));
		P_right = Math.max(MIN_POWER, Math.min(MAX_POWER, P_right));

		// -------------------------------
		// OSNR em P0, P_left, P_right
		// -------------------------------
		circuit.setLaunchPowerLinear(P0);
		double OSNR_central = computeOSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

		circuit.setLaunchPowerLinear(P_left);
		double OSNR_left = computeOSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

		circuit.setLaunchPowerLinear(P_right);
		double OSNR_right = computeOSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

		double Pmax = 0.0;
		double Pmin = 0.0;

		if (OSNR_left < OSNR_central && OSNR_right < OSNR_central) { // Case where P0 is already a local maximum
			Pmin = P_left;
			Pmax = P_right;
		}
		else if (OSNR_right > OSNR_central) { // Expansion to the right
			Pmin = P0;
			Pmax = P_right;

			int iter = 0;
			while (iter < MAX_EXPANSION_ITER) {

				double P_prev = Pmax;
				double P_next = dBm_to_W(W_to_dBm(Pmax) + step_dB);

				P_next = Math.max(MIN_POWER, Math.min(MAX_POWER, P_next));

				circuit.setLaunchPowerLinear(P_prev);
				double OSNR_prev = computeOSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

				circuit.setLaunchPowerLinear(P_next);
				double OSNR_next = computeOSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

				// Went past the peak to the right
				if (OSNR_next <= OSNR_prev) {
					Pmin = P_prev;
					Pmax = P_next;
					break;
				}

				Pmin = P_prev;
				Pmax = P_next;
				iter++;
			}
		}
		else if (OSNR_left > OSNR_central) { // Expansion to the left
			Pmax = P0;
			Pmin = P_left;

			int iter = 0;
			while (iter < MAX_EXPANSION_ITER) {

				double P_prev = Pmin;
				double P_next = dBm_to_W(W_to_dBm(Pmin) - step_dB);

				P_next = Math.max(MIN_POWER, Math.min(MAX_POWER, P_next));

				circuit.setLaunchPowerLinear(P_prev);
				double OSNR_prev = computeOSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

				circuit.setLaunchPowerLinear(P_next);
				double OSNR_next = computeOSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

				// Went past the peak to the left
				if (OSNR_next <= OSNR_prev) {
					Pmin = P_next;
					Pmax = P_prev;
					break;
				}

				Pmax = P_prev;
				Pmin = P_next;
				iter++;
			}
		}

		// =============================================
		// Ternary search for the OSNR maximum in the [Pmin, Pmax] interval
		// =============================================
		final int    MAX_TERNARY_ITER   = 50;
		final double MIN_POWER_TER = 1e-15;

		// Better hybrid tolerance for broad power ranges
		final double ABS_TOL = 1e-12;   // safe for very small powers (1e-12 W)
		final double REL_TOL = 1e-3;    // 0.1% relative tolerance

		for (int i = 0; i < MAX_TERNARY_ITER; i++) {

			double p1 = Pmin + (Pmax - Pmin) / 3.0;
			double p2 = Pmax - (Pmax - Pmin) / 3.0;

			// Protection against negative values
			if (p1 < MIN_POWER_TER) p1 = MIN_POWER_TER;

			if (p2 < MIN_POWER_TER) p2 = MIN_POWER_TER;

			circuit.setLaunchPowerLinear(p1);
			double OSNRp1 = computeOSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

			circuit.setLaunchPowerLinear(p2);
			double OSNRp2 = computeOSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

			if (OSNRp1 < OSNRp2) {
				Pmin = p1; // maximum is to the right
			} else {
				Pmax = p2; // maximum is to the left
			}

			double Pmid = (Pmin + Pmax) * 0.5;
			double tol = Math.max(ABS_TOL, REL_TOL * Pmid);

			if (Math.abs(Pmax - Pmin) <= tol) {
				break;
			}
		}

		// Returns the refined midpoint
		double Popt =  (Pmin + Pmax) * 0.5;

		return Popt;
		} finally {
			circuit.setLaunchPowerLinear(originalLaunchPower);
		}
	}

	/**
	 * Executes the test camada fisica operation.
	 */
	public void testCamadaFisica() {

		int totalSlots = 320;
		double distance = 1000.0;
		//int numberOfCores = 1;

		List<CoreConfig> coreList = new ArrayList<>();
		coreList.add(new CoreConfig(0, new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6))));
		coreList.add(new CoreConfig(1, new ArrayList<>(Arrays.asList(0, 2, 6))));
		coreList.add(new CoreConfig(2, new ArrayList<>(Arrays.asList(0, 1, 3))));
		coreList.add(new CoreConfig(3, new ArrayList<>(Arrays.asList(0, 2, 4))));
		coreList.add(new CoreConfig(4, new ArrayList<>(Arrays.asList(0, 3, 5))));
		coreList.add(new CoreConfig(5, new ArrayList<>(Arrays.asList(0, 4, 6))));
		coreList.add(new CoreConfig(6, new ArrayList<>(Arrays.asList(0, 1, 5))));

		Node n1 = new Node("1", 1000, 1000, 0, 100);
		Node n2 = new Node("2", 1000, 1000, 0, 100);
		n1.getOxc().addLink(new Link(n1.getOxc(), n2.getOxc(), totalSlots, slotBandwidth, distance, coreList));

		Vector<Node> listNodes = new Vector<Node>();
		listNodes.add(n1);
		listNodes.add(n2);

		Route route = new Route(listNodes);
		Pair pair = new Pair(n1, n2);

		int guardBand = 0;
		Modulation mod_BPSK = new Modulation("BPSK", 10000.0, 2.0, 5.5, 0.28, 12.5E+9, 2.0, -14.0);
		Modulation mod_QPSK = new Modulation("QPSK", 5000.0, 4.0, 8.5, 0.28, 12.5E+9, 2.0, -18.5);
		Modulation mod_8QAM = new Modulation("8QAM", 2500.0, 8.0, 12.5, 0.28, 12.5E+9, 2.0, -21.0);

		// circuit 1
		double tr1 = 100.0E+9; //bps
		int slotNumber1 = 3; //mod_QPSK.requiredSlots(tr1);
		int sa1[] = new int[2];
		sa1[0] = 1;
		sa1[1] = sa1[0] + slotNumber1 - 1;
		int indexCore1 = 0;

		RequestForConnection requestTemp1 = new RequestForConnection();
		requestTemp1.setPair(pair);
		requestTemp1.setRequiredBitRate(tr1);

		Circuit circuit1 = new Circuit();
		circuit1.setPair(pair);
		circuit1.setRoute(route);
		circuit1.setModulation(mod_QPSK);
		circuit1.setGuardBand(guardBand);
		circuit1.setIndexCore(indexCore1);
		circuit1.setSpectrumAssigned(sa1);
		circuit1.addRequest(requestTemp1);

		// circuit 2
		double tr2 = 100.0E+9; //bps
		int slotNumber2 = 3; //mod_BPSK.requiredSlots(tr2);
		int sa2[] = new int[2];
		sa2[0] = sa1[1] + 1;
		sa2[1] = sa2[0] + slotNumber2 - 1;
		int indexCore2 = 0;

		RequestForConnection requestTemp2 = new RequestForConnection();
		requestTemp2.setPair(pair);
		requestTemp2.setRequiredBitRate(tr2);

		Circuit circuit2 = new Circuit();
		circuit2.setPair(pair);
		circuit2.setRoute(route);
		circuit2.setModulation(mod_QPSK);
		circuit2.setGuardBand(guardBand);
		circuit2.setIndexCore(indexCore2);
		circuit2.setSpectrumAssigned(sa2);
		circuit2.addRequest(requestTemp2);

		// circuit 3
		double tr3 = 100.0E+9; //bps
		int slotNumber3 = 3; //mod_8QAM.requiredSlots(tr3);
		int sa3[] = new int[2];
		sa3[0] = sa2[1] + 1;
		sa3[1] = sa3[0] + slotNumber3 - 1;
		int indexCore3 = 0;

		RequestForConnection requestTemp3 = new RequestForConnection();
		requestTemp3.setPair(pair);
		requestTemp3.setRequiredBitRate(tr3);

		Circuit circuit3 = new Circuit();
		circuit3.setPair(pair);
		circuit3.setRoute(route);
		circuit3.setModulation(mod_QPSK);
		circuit3.setGuardBand(guardBand);
		circuit3.setIndexCore(indexCore3);
		circuit3.setSpectrumAssigned(sa3);
		circuit3.addRequest(requestTemp3);

		// Adding the circuits to the network
		route.getLink(0).getCore(circuit1.getIndexCore()).addCircuit(circuit1);
		route.getLink(0).getCore(circuit2.getIndexCore()).addCircuit(circuit2);
		route.getLink(0).getCore(circuit3.getIndexCore()).addCircuit(circuit3);

		//Calculating the OSNR of each circuit
		double c1_OSNR = computeOSNRSegment(circuit1, circuit1.getRoute(), 0, circuit1.getRoute().getNodeList().size() - 1, circuit1.getModulation(), circuit1.getIndexCore(), circuit1.getSpectrumAssigned(), null, false);
		double c1_OSNRdB = PhysicalLayer.ratioForDB(c1_OSNR);

		double c2_OSNR = computeOSNRSegment(circuit2, circuit2.getRoute(), 0, circuit2.getRoute().getNodeList().size() - 1, circuit2.getModulation(), circuit2.getIndexCore(), circuit2.getSpectrumAssigned(), null, false);
		double c2_OSNRdB = PhysicalLayer.ratioForDB(c2_OSNR);

		double c3_OSNR = computeOSNRSegment(circuit3, circuit3.getRoute(), 0, circuit3.getRoute().getNodeList().size() - 1, circuit3.getModulation(), circuit3.getIndexCore(), circuit3.getSpectrumAssigned(), null, false);
		double c3_OSNRdB = PhysicalLayer.ratioForDB(c3_OSNR);

		System.out.println("c1: OSNR = " + c1_OSNR + ", OSNR(dB) = " + c1_OSNRdB);
		System.out.println("c2: OSNR = " + c2_OSNR + ", OSNR(dB) = " + c2_OSNRdB);
		System.out.println("c3: OSNR = " + c3_OSNR + ", OSNR(dB) = " + c3_OSNRdB);

		System.out.println("fim");
	}

	/**
	 * Executes the test camada fisica xt operation.
	 */
	public void testCamadaFisicaXT() {

		int totalSlots = 320;
		double distance = 1000.0;

		// Core configuration (MCF 7-core)
		List<CoreConfig> coreList = new ArrayList<>();
		coreList.add(new CoreConfig(0, new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6))));
		coreList.add(new CoreConfig(1, new ArrayList<>(Arrays.asList(0, 2, 6))));
		coreList.add(new CoreConfig(2, new ArrayList<>(Arrays.asList(0, 1, 3))));
		coreList.add(new CoreConfig(3, new ArrayList<>(Arrays.asList(0, 2, 4))));
		coreList.add(new CoreConfig(4, new ArrayList<>(Arrays.asList(0, 3, 5))));
		coreList.add(new CoreConfig(5, new ArrayList<>(Arrays.asList(0, 4, 6))));
		coreList.add(new CoreConfig(6, new ArrayList<>(Arrays.asList(0, 1, 5))));

		Node n1 = new Node("1", 1000, 1000, 0, 100);
		Node n2 = new Node("2", 1000, 1000, 0, 100);
		n1.getOxc().addLink(new Link(n1.getOxc(), n2.getOxc(), totalSlots, slotBandwidth, distance, coreList));

		Vector<Node> listNodes = new Vector<>();
		listNodes.add(n1);
		listNodes.add(n2);

		Route route = new Route(listNodes);
		Pair pair = new Pair(n1, n2);

		int guardBand = 0;
		Modulation mod_BPSK = new Modulation("BPSK", 10000.0, 2.0, 5.5, 0.28, 12.5E+9, 2.0, -14.0);
		Modulation mod_QPSK = new Modulation("QPSK", 5000.0, 4.0, 8.5, 0.28, 12.5E+9, 2.0, -18.5);
		Modulation mod_8QAM = new Modulation("8QAM", 2500.0, 8.0, 12.5, 0.28, 12.5E+9, 2.0, -21.0);

		// ============================
		// Circuit 1 (reference) on core 0
		// ============================
		double tr1 = 100.0E+9; // bps
		int slotNumber1 = 3;   // p/ simplificar, 3 slots
		int sa1[] = new int[2];
		sa1[0] = 1;
		sa1[1] = sa1[0] + slotNumber1 - 1; // [1,3]
		int indexCore1 = 0;

		RequestForConnection requestTemp1 = new RequestForConnection();
		requestTemp1.setPair(pair);
		requestTemp1.setRequiredBitRate(tr1);

		Circuit circuit1 = new Circuit();
		circuit1.setPair(pair);
		circuit1.setRoute(route);
		circuit1.setModulation(mod_QPSK);
		circuit1.setGuardBand(guardBand);
		circuit1.setIndexCore(indexCore1);
		circuit1.setSpectrumAssigned(sa1);
		circuit1.addRequest(requestTemp1);

		// ============================
		// Circuits on the SAME core (NLI / intra-core interference)
		// ============================
		double tr2 = 100.0E+9;
		int slotNumber2 = 3;
		int sa2[] = new int[2];
		sa2[0] = sa1[1] + 1;
		sa2[1] = sa2[0] + slotNumber2 - 1; // [4,6]
		int indexCore2 = 0;

		RequestForConnection requestTemp2 = new RequestForConnection();
		requestTemp2.setPair(pair);
		requestTemp2.setRequiredBitRate(tr2);

		Circuit circuit2 = new Circuit();
		circuit2.setPair(pair);
		circuit2.setRoute(route);
		circuit2.setModulation(mod_QPSK);
		circuit2.setGuardBand(guardBand);
		circuit2.setIndexCore(indexCore2);
		circuit2.setSpectrumAssigned(sa2);
		circuit2.addRequest(requestTemp2);

		double tr3 = 100.0E+9;
		int slotNumber3 = 3;
		int sa3[] = new int[2];
		sa3[0] = sa2[1] + 1;
		sa3[1] = sa3[0] + slotNumber3 - 1; // [7,9]
		int indexCore3 = 0;

		RequestForConnection requestTemp3 = new RequestForConnection();
		requestTemp3.setPair(pair);
		requestTemp3.setRequiredBitRate(tr3);

		Circuit circuit3 = new Circuit();
		circuit3.setPair(pair);
		circuit3.setRoute(route);
		circuit3.setModulation(mod_QPSK);
		circuit3.setGuardBand(guardBand);
		circuit3.setIndexCore(indexCore3);
		circuit3.setSpectrumAssigned(sa3);
		circuit3.addRequest(requestTemp3);

		// ============================
		// Circuits on adjacent cores (generate XT on circuit 1)
		// ============================
		// All with the SAME c1 slot band ([1,3]) to maximize XT
		int[] saXT = new int[]{sa1[0], sa1[1]};
		double trXT = 100.0E+9;

		List<Circuit> xtNeighbors = new ArrayList<>();
		int[] adjacentCores = new int[]{1,2,3,4,5,6}; // adjacent cores of core 0

		for (int coreId : adjacentCores) {
			RequestForConnection req = new RequestForConnection();
			req.setPair(pair);
			req.setRequiredBitRate(trXT);

			Circuit cXT = new Circuit();
			cXT.setPair(pair);
			cXT.setRoute(route);
			cXT.setModulation(mod_QPSK);
			cXT.setGuardBand(guardBand);
			cXT.setIndexCore(coreId);
			cXT.setSpectrumAssigned(saXT);
			cXT.addRequest(req);

			xtNeighbors.add(cXT);
		}

		// ============================
		// Adding all circuits to the network
		// ============================
		Link link = route.getLink(0);
		// core 0
		link.getCore(circuit1.getIndexCore()).addCircuit(circuit1);
		link.getCore(circuit2.getIndexCore()).addCircuit(circuit2);
		link.getCore(circuit3.getIndexCore()).addCircuit(circuit3);

		// adjacent cores
		for (Circuit cXT : xtNeighbors) {
			link.getCore(cXT.getIndexCore()).addCircuit(cXT);
		}

		// ============================
		// Power sweep to generate OSNR x P and XT x P curves
		// ============================

		System.out.println("#P_dBm; OSNR_dB; XT_dB");

		// example: scans from -5 dBm to +5 dBm with a 0.5 dB step
		for (double P_dBm = -5.0; P_dBm <= 5.0; P_dBm += 0.5) {

			// converts dBm to Watts
			double P_Watts = dBm_to_W(P_dBm); // use your actual method

			// configures the launch power of circuit 1
			circuit1.setLaunchPowerLinear(P_Watts);

			// OSNR of circuit 1
			double c1_OSNR = computeOSNRSegment(
					circuit1,
					circuit1.getRoute(),
					0,
					circuit1.getRoute().getNodeList().size() - 1,
					circuit1.getModulation(),
					circuit1.getIndexCore(),
					circuit1.getSpectrumAssigned(),
					null,
					false
			);
			double c1_OSNRdB = PhysicalLayer.ratioForDB(c1_OSNR);

			// XT that circuit 1 experiences from neighbors (inter-core + intra-core)
			// Here you should use the actual function that already exists in your PhysicalLayer/Crosstalk
			double c1_XTdB = crosstalk.calculateCrosstalk(circuit1, null, false);

			System.out.println("P(dBm): " + P_dBm + ", OSNR(dB): " + c1_OSNRdB + ", XT(dB): " + c1_XTdB);
		}

		System.out.println("fim");
	}


	/**
	 * Habibi analytical corrections with optional numerical GN integral fallback.
	 * All Carena EGN correction integrals were deliberately removed.
	 */

private static final int MAX_CACHED = 512;
        /** GN Eq.(3) numerical quadrature cached by complete optical input state. */
    private static final Map<HabibiGnCacheKey,Poggiolini2017GnIntegral.GnResult> POGGIOLINI_CACHE =
        new LinkedHashMap<HabibiGnCacheKey,Poggiolini2017GnIntegral.GnResult>(256,0.75f,true) {
            private static final long serialVersionUID=1L;
            @Override protected boolean removeEldestEntry(Map.Entry<HabibiGnCacheKey,Poggiolini2017GnIntegral.GnResult> e) {
                return size()>MAX_CACHED;
            }
        };
    private static final AtomicLong poggioliniCalls=new AtomicLong();
    public static long poggioliniCalls(){return poggioliniCalls.get();}
    public static Poggiolini2017GnIntegral.GnResult cachedPoggioliniGn(
            Poggiolini2017GnIntegral.Fiber f,List<Poggiolini2017GnIntegral.Channel> cs,int n,int fout) {
        HabibiGnCacheKey key=new HabibiGnCacheKey(f,cs,n,fout);
        synchronized(POGGIOLINI_CACHE) {
            Poggiolini2017GnIntegral.GnResult value=POGGIOLINI_CACHE.get(key);
            if(value!=null){cacheHits.incrementAndGet();return value;}
        }
        Poggiolini2017GnIntegral.GnResult value=
                Poggiolini2017GnIntegral.evaluateGnIntegral(f,cs,0,n,fout);
        if(!Double.isFinite(value.total()) || value.total()<0)
            throw new ArithmeticException("Poggiolini GN quadrature failed: "+value.total());
        synchronized(POGGIOLINI_CACHE){POGGIOLINI_CACHE.put(key,value);cacheMisses.incrementAndGet();}
        poggioliniCalls.incrementAndGet();
        return value;
    }
    private static final AtomicLong cacheHits=new AtomicLong();
    private static final AtomicLong cacheMisses=new AtomicLong();
    private static final AtomicLong fallbacks=new AtomicLong();
    private static final AtomicLong unsupported=new AtomicLong();
    private static final AtomicBoolean warnedUnsupportedCut=new AtomicBoolean();
    public static long cacheHits(){return cacheHits.get();}
    public static long cacheMisses(){return cacheMisses.get();}
    public static long fallbackCount(){return fallbacks.get();}
    public static long unsupportedCount(){return unsupported.get();}
    public static void reset(){ synchronized(POGGIOLINI_CACHE){POGGIOLINI_CACHE.clear();}poggioliniCalls.set(0);cacheHits.set(0);cacheMisses.set(0);fallbacks.set(0);unsupported.set(0);warnedUnsupportedCut.set(false); }
    private static final class HabibiGnCacheKey {
        final long[] bits;
        HabibiGnCacheKey(Poggiolini2017GnIntegral.Fiber f,List<Poggiolini2017GnIntegral.Channel> cs,int n,int fout){
            bits=new long[7+cs.size()*4];
            bits[0]=Double.doubleToLongBits(f.alphaPowerDbKm);
            bits[1]=Double.doubleToLongBits(f.gammaPerWKm);
            bits[2]=Double.doubleToLongBits(f.beta2Ps2Km);
            bits[3]=Double.doubleToLongBits(f.spanKm);bits[4]=f.spans;
            bits[5]=n;bits[6]=fout;
            for(int i=0;i<cs.size();i++){
                Poggiolini2017GnIntegral.Channel c=cs.get(i);int p=7+4*i;
                bits[p]=Double.doubleToLongBits(c.centerHz);
                bits[p+1]=Double.doubleToLongBits(c.bandwidthHz);
                bits[p+2]=Double.doubleToLongBits(c.powerW);
                bits[p+3]=Double.doubleToLongBits(c.phi);
            }
        }
        @Override public int hashCode(){return Arrays.hashCode(bits);}
        @Override public boolean equals(Object o){return o instanceof HabibiGnCacheKey && Arrays.equals(bits,((HabibiGnCacheKey)o).bits);}
    }
    /** Report unavailable channel metadata; fallback is GN-only, never EGN. */
    public static void warnUnsupportedCut(double positivePhi,double bandwidthHz) {
        unsupported.incrementAndGet();
        if(warnedUnsupportedCut.compareAndSet(false,true))
            System.err.println("[SNetS GN] Habibi integral fallback unavailable for modulation Phi="
                    +positivePhi+", bandwidth="+(bandwidthHz/1e9)+" GHz. "
                    +"Original Habibi approximation remains in use for this modulation. "
                    +"Supported reference mappings: QPSK, 16QAM, 64QAM.");
    }
    /** Legacy Habibi modulation metadata (signed phi) for the GN fallback.
     *  Only the prior set of modulations is accepted to avoid expanding the
     *  scientific reference domain silently with this removal. */
    /** The GN spectral kernel does not depend on modulation moments. For explicit
     *  integral mode allow any finite Phi, unlike the historical restricted fallback. */
    public static Poggiolini2017GnIntegral.Channel integralChannel(double frequencyHz,
            double widthHz,double powerW,double phi,String mode) {
        if(NliIntegrationMode.INTEGRAL.equals(mode) && Double.isFinite(phi))
            return new Poggiolini2017GnIntegral.Channel(frequencyHz,widthHz,powerW,-phi);
        return supportedChannel(frequencyHz,widthHz,powerW,phi);
    }
    public static Poggiolini2017GnIntegral.Channel supportedChannel(double centerHz,
            double bandwidthHz,double powerW,double lacerdPhi) {
        if(Math.abs(lacerdPhi-1.0)<1e-8)
            return Poggiolini2017GnIntegral.Channel.qpsk(centerHz,bandwidthHz,powerW);
        if(Math.abs(lacerdPhi-17.0/25.0)<1e-8)
            return Poggiolini2017GnIntegral.Channel.qam16(centerHz,bandwidthHz,powerW);
        if(Math.abs(lacerdPhi-13.0/21.0)<1e-8)
            return Poggiolini2017GnIntegral.Channel.qam64(centerHz,bandwidthHz,powerW);
        return null;
    }
    public static final class HabibiGnDecision {
        public final double sci, xci, legacy, corrected;
        public final boolean applied;
        public final int supportedPairs, legacyPairs;
        HabibiGnDecision(double sci,double xci,double legacy,double corrected,
                 boolean applied,int supportedPairs,int legacyPairs){
            this.sci=sci;this.xci=xci;this.legacy=legacy;this.corrected=corrected;
            this.applied=applied;this.supportedPairs=supportedPairs;this.legacyPairs=legacyPairs;
        }
    }
    /** One source's legacy XCI; physically equal to the formula in getGnli2(). */
    public static double legacyXci(Poggiolini2017GnIntegral.Fiber f,
            Poggiolini2017GnIntegral.Channel cut,Poggiolini2017GnIntegral.Channel interfering,
            double interferencePositivePhi) {
        double B=cut.bandwidthHz,Bj=interfering.bandwidthHz;
        double alphaPow=f.alphaPowerDbKm*Math.log(10)/10.0; // per km
        double alphaField=alphaPow/2.0;
        double Leff=-Math.expm1(-alphaPow*f.spanKm)/alphaPow;
        double beta=Math.abs(f.beta2Ps2Km)*1e-24; // ps2/km -> s2/km
        double E=8*f.gammaPerWKm*f.gammaPerWKm*Leff*Leff*alphaPow/
                 (27*Math.PI*beta);
        double d=Math.abs(cut.centerHz-interfering.centerHz);
        if(d<=0)return Double.NaN;
        double a=Math.PI*Math.PI*beta*B/alphaPow;
        double xi=a*(d+Bj/2.0),lo=a*(d-Bj/2.0);
        double val=asinh(xi)-asinh(lo)
                -5*interferencePositivePhi*Bj/(6*alphaField*f.spanKm*d);
        return (cut.powerW/B)*Math.pow(interfering.powerW/Bj,2)*E*val;
    }
    public static double legacySci(Poggiolini2017GnIntegral.Fiber f,
            Poggiolini2017GnIntegral.Channel cut,double positivePhi){
        double alphaPow=f.alphaPowerDbKm*Math.log(10)/10.0;
        double alphaField=alphaPow/2.0;
        double leff=-Math.expm1(-alphaPow*f.spanKm)/alphaPow;
        double beta=Math.abs(f.beta2Ps2Km)*1e-24;
        double E=8*f.gammaPerWKm*f.gammaPerWKm*leff*leff*alphaPow/
                (27*Math.PI*beta);
        double arg=Math.PI*Math.PI*beta*cut.bandwidthHz*cut.bandwidthHz/(2*alphaPow);
        return Math.pow(cut.powerW/cut.bandwidthHz,3)*E*
                (asinh(arg)-5*positivePhi/(3*alphaField*f.spanKm));
    }
    private static double asinh(double x){return Math.log(x+Math.sqrt(x*x+1));}

    /**
     * Hybrid Habibi / numerical GN fallback, NOT numerical EGN.
     * Replaces SCI and the supported independent XCI terms by Poggiolini
     * (2017) Eqs. (2)-(4) when the CUT is narrow or raw Habibi is negative.
     * The remaining Habibi contributions stay unchanged; cross-interferer
     * MCI from the full comb is not incorporated in this pairwise fallback.
     * @param legacyRaw legacy total WITHOUT clipping and WITHOUT PICh terms
     */
    public static HabibiGnDecision evaluate(Poggiolini2017GnIntegral.Fiber fiber,
            Poggiolini2017GnIntegral.Channel cut,List<Poggiolini2017GnIntegral.Channel> interfering,
            double legacyRaw,double narrowbandGHz,boolean enabled,int quadratureOrder,
            int receiverOrder){
        if(cut==null || interfering==null)throw new IllegalArgumentException("channels required");
        double base=Math.max(0,legacyRaw);
        if(!enabled || (cut.bandwidthHz>narrowbandGHz*1e9 && legacyRaw>=0))
            return new HabibiGnDecision(0,0,legacyRaw,base,false,0,interfering.size());
        fallbacks.incrementAndGet();
        Poggiolini2017GnIntegral.GnResult solo=cachedPoggioliniGn(fiber,
                Arrays.asList(cut),quadratureOrder,receiverOrder);
        double legacySelf=legacySci(fiber,cut,-cut.phi);
        double corrected=legacyRaw-legacySelf+solo.sci;
        int pairs=0,unsupportedPairs=0;
        double refXci=0;
        for(Poggiolini2017GnIntegral.Channel src:interfering){
            if(src==null || Math.abs(src.centerHz-cut.centerHz)<
                        0.999*cut.bandwidthHz){
                unsupportedPairs++;continue;
            }
            Poggiolini2017GnIntegral.GnResult two=cachedPoggioliniGn(fiber,
                    Arrays.asList(cut,src),quadratureOrder,receiverOrder);
            double legacyPair=legacyXci(fiber,cut,src,-src.phi);
            corrected+=two.xci-legacyPair;
            refXci+=two.xci;pairs++;
        }
        if(unsupportedPairs>0)unsupported.addAndGet(unsupportedPairs);
        // A negative here is a genuine numerical/model problem. Never label a
        // truncated negative PSD as a valid GN integral.
        if(!Double.isFinite(corrected)||corrected<0)
            throw new ArithmeticException("Habibi/GN fallback nonphysical PSD "+corrected);
        return new HabibiGnDecision(solo.sci,refXci,legacyRaw,corrected,true,pairs,unsupportedPairs);
    }
}
