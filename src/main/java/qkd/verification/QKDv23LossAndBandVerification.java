package qkd.verification;

import java.util.Arrays;
import java.util.Vector;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ClassicalMultiBand;
import network.GnModelMath;
import network.Mesh;
import network.Node;
import network.Pair;
import network.PhysicalLayer;
import qkd.multiband.ClassicalPowerEvolution;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;
import simulationControl.parsers.NetworkConfig.ModulationConfig;
import simulationControl.parsers.NetworkConfig.NodeConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.TrafficConfig;

/** Non-mutating directed-link optical boundary and v2 multiband verification. */
public final class QKDv23LossAndBandVerification {

	private QKDv23LossAndBandVerification() { }

	private static void check(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void close(double actual, double expected, String message) {
		double relative = Math.abs(actual - expected) / Math.max(1e-15, Math.abs(expected));
		check(Double.isFinite(actual) && relative < 1e-8,
				message + ": " + actual + " != " + expected + " (relative " + relative + ")");
	}

	private static final class Scenario {
		final Mesh mesh;
		final Route route;
		final Circuit victim;
		final Modulation modulation;
		Scenario(Mesh m, Route r, Circuit c, Modulation mod) {
			mesh=m; route=r; victim=c; modulation=mod;
		}
	}

	private static Scenario scenario(int hops, double muxLoss, double demuxLoss,
			ClassicalMultiBand.Config band, boolean ase, boolean nli) {

		NetworkConfig n = new NetworkConfig();
		n.setNodes(Arrays.asList(new NodeConfig("A",4,4,0),
				new NodeConfig("B",4,4,0), new NodeConfig("C",4,4,0)));
		n.setCores(Arrays.asList(new CoreConfig(0,Arrays.asList(1)),
				new CoreConfig(1,Arrays.asList(0))));
		n.setLinks(Arrays.asList(new LinkConfig("A","B",64,12.5e9,80.0),
				new LinkConfig("B","C",64,12.5e9,80.0)));
		n.setModulations(Arrays.asList(new ModulationConfig("QPSK",4.0,10000.0,-100.0,-10.0)));

		PhysicalLayerConfig p = new PhysicalLayerConfig();
		p.setPhysicalLayerModel(PhysicalLayer.MODEL_JOHANNISSON);
		p.setPower(0.0);
		p.setSpanLength(80.0);
		p.setFiberLoss(0.2);
		p.setFiberNonlinearity(1.3e-3);
		p.setFiberDispersion(16.7e-6);
		p.setCenterFrequency(193.4e12);
		p.setConstantOfPlanck(6.62607015e-34);
		p.setNoiseFigureOfOpticalAmplifier(5.0);
		p.setPowerSaturationOfOpticalAmplifier(18.0);
		p.setAmplificationFrequency(193.4e12);
		p.setTypeOfAmplifierGain(PhysicalLayer.AMP_GAIN_FIXED);
		p.setActiveASE(ase);
		p.setActiveNLI(nli);
		p.setActiveXT(false);
		p.setPolarizationModes(2.0);
		p.setSwitchInsertionLoss(9.0); // Legacy field MUST NOT become switch loss.
		p.setMuxInsertionLoss(muxLoss);
		p.setDemuxInsertionLoss(demuxLoss);
		p.setMultiBand(band);

		Mesh mesh = new Mesh(n,new TrafficConfig(),p,new OthersConfig(),null,new Util());
		Vector<Node> nodes = new Vector<Node>();
		nodes.add(mesh.getNodeList().get(0));
		nodes.add(mesh.getNodeList().get(1));

		if (hops == 2) nodes.add(mesh.getNodeList().get(2));
		Route route = new Route(nodes);
		Modulation mod = mesh.getAvaliableModulations().get(0);
		Circuit c = new Circuit();
		c.setRoute(route);
		c.setPair(new Pair(route.getSource(),route.getDestination()));
		c.setModulation(mod);
		c.setIndexCore(0);
		c.setSpectrumAssigned(new int[] {12,15});
		c.setLaunchPowerLinear(1e-3);
		RequestForConnection req = new RequestForConnection();
		req.setPair(c.getPair());
		req.setRequiredBitRate(mod.potentialBitRate(4));
		c.addRequest(req);

		return new Scenario(mesh,route,c,mod);
	}

	private static double osnr(Scenario s) {
		return s.mesh.getPhysicalLayer().computeOSNRSegment(s.victim,s.route,
				0,s.route.getNodeList().size()-1,s.modulation,0,
				s.victim.getSpectrumAssigned(),null,false);
	}

	private static void lossBalance(int hops, double mux, double demux) {
		Scenario s=scenario(hops,mux,demux,null,false,false);
		double bw=s.modulation.getBandwidthFromBitRate(s.victim.getRequiredBitRate());
		double psd=s.victim.getLaunchPowerLinear()/(2.0*bw);
		// No noise sources: the noise floor of computeSNRSegment is 1e-30 W/Hz.
		close(osnr(s)*1e-30,psd,"legacy passive-loss compensation, hops="+hops);

		double frequencyStart=193.4e12-64*12.5e9/2.0;
		double alphaKm=0.2*Math.log(10.0)/10.0;
		s.mesh.getPhysicalLayer().enableLongitudinalAmplifierQoT(
				new ClassicalPowerEvolution(80.0,16.0),frequencyStart,alphaKm);
		close(osnr(s)*1e-30,psd,"longitudinal passive-loss compensation, hops="+hops);

		System.out.printf(java.util.Locale.US,
				"PASS passive losses %d hop(s) mux=%.1f demux=%.1f dB%n",hops,mux,demux);
	}

	private static void bandAndIntegralCache() {
		Scenario plain=scenario(1,2,2,null,true,true);
		double a=osnr(plain);
		ClassicalMultiBand.Config cfg=new ClassicalMultiBand.Config();
		cfg.setEnabled(true);
		cfg.setAttenuationModel("constant");
		cfg.setIsrs(false);
		cfg.setGammaScalesWithFrequency(false);
		cfg.setDispersionSlopePsNm2Km(0.0);
		Scenario opt=scenario(1,2,2,cfg,true,true);
		double b=osnr(opt);
		check(Math.abs(b-a)/a < 0.005,
				"constant fibre multiBand should remain close to the legacy baseline");
		cfg.setNoiseFigureDb(java.util.Collections.singletonMap("C",9.0));
		Scenario changed=scenario(1,2,2,cfg,true,true);
		check(osnr(changed)<a,"higher band NF should reduce OSNR");

		// The imported v2 spectral model must respond to frequency and ISRS,
		// while it remains opt-in and independent of the QKD longitudinal mode.
		ClassicalMultiBand.Config spectral = new ClassicalMultiBand.Config();
		spectral.setEnabled(true);
		spectral.setAttenuationModel("g652d");
		spectral.setIsrs(true);
		ClassicalMultiBand bands = new ClassicalMultiBand(spectral, 0.2,
				1.3e-3, 16.7e-6, 5.0, 193.4e12);
		ClassicalMultiBand.Params cBand = bands.at(193.4e12);
		ClassicalMultiBand.Params lBand = bands.at(187.0e12);
		check(Double.isFinite(cBand.alphaDbPerKm)
						&& Double.isFinite(lBand.alphaDbPerKm)
						&& cBand.alphaDbPerKm != lBand.alphaDbPerKm,
				"G.652.D loss should depend on wavelength");
		double low = bands.isrsRatio(190e12, 190e12, 195e12, 0.1,
				cBand.alphaLinear, 80.0);
		double high = bands.isrsRatio(195e12, 190e12, 195e12, 0.1,
				cBand.alphaLinear, 80.0);
		check(Double.isFinite(low) && Double.isFinite(high)
						&& low > high && low > 0.0 && high > 0.0,
				"ISRS should redistribute power across bands");

		GnModelMath.clearIntegralKernelCache();
		double kernel=GnModelMath.johannissonXciKernel(75e9,12.5e9,12.5e9,
				4.0*Math.PI*Math.PI*21.3e-27/(0.2*Math.log(10.0)/10.0/1000.0));
		check(Double.isFinite(kernel) && kernel>=0.0,"integral kernel nonnegative");
		double repeated=GnModelMath.johannissonXciKernel(75e9,12.5e9,12.5e9,
				4.0*Math.PI*Math.PI*21.3e-27/(0.2*Math.log(10.0)/10.0/1000.0));
		check(kernel==repeated && GnModelMath.integralKernelCacheHits()>0,
				"integral kernel cache must preserve bitwise result");
		System.out.println("PASS optional v2 band model and safe GN integral cache");
	}

	private static void longitudinalPowerSearch() {
		Scenario s=scenario(2,5.0,5.0,null,true,true);
		double frequencyStart=193.4e12-64*12.5e9/2.0;
		double alphaKm=0.2*Math.log(10.0)/10.0;
		PhysicalLayer layer=s.mesh.getPhysicalLayer();
		layer.enableLongitudinalAmplifierQoT(
				new ClassicalPowerEvolution(80.0,16.0),frequencyStart,alphaKm);
		double old=s.victim.getLaunchPowerLinear();
		double optimum=layer.computePowerThatMaximizesOSNR(s.victim,
				s.victim.getRequiredBitRate(),s.route,0,2,s.modulation,0,
				s.victim.getSpectrumAssigned());
		check(Double.isFinite(optimum) && optimum>=1e-9 && optimum<=0.1,
				"longitudinal active-model optimum must be bounded");
		check(s.victim.getLaunchPowerLinear()==old,
				"longitudinal optimum must restore original power");
		s.victim.setLaunchPowerLinear(optimum);
		double atMax=osnr(s);

		if (optimum>2e-9 && optimum<0.05) {
			s.victim.setLaunchPowerLinear(optimum*0.9);
			double below=osnr(s);
			s.victim.setLaunchPowerLinear(optimum*1.1);
			double above=osnr(s);
			check(atMax>=below && atMax>=above,
					"longitudinal optimum must improve nearby OSNR");
		}

		s.victim.setLaunchPowerLinear(old);
		System.out.println("PASS longitudinal active-curve power optimum");
	}

	public static void main(String[] args) {
		lossBalance(1,5.0,5.0);
		lossBalance(2,5.0,5.0);
		lossBalance(1,1.0,3.0);
		lossBalance(2,1.0,3.0);
		bandAndIntegralCache();
		longitudinalPowerSearch();
		System.out.println("PASS QKDv23LossAndBandVerification");
	}
}
