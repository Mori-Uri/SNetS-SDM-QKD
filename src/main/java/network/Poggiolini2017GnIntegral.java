package network;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * Numerical GN (not EGN) spectral integral of Poggiolini & Jiang (2017),
 * Eqs. (2)-(4), for rectangular channels, identical lumped-gain spans
 * and the dual-polarization Manakov factor 16/27. No fourth/sixth-order
 * modulation-moment corrections from Carena 2014 are present.
 *
 * The integrator is a frequency-domain reference/fallback, not a substitute
 * for spatially resolved nonlinear propagation or a complete EGN model.
 */
public final class Poggiolini2017GnIntegral {
    private Poggiolini2017GnIntegral() { }

    /** The phi attribute is metadata used only by legacy Habibi's modulation
     * correction; the GN integral itself is independent of modulation moments. */
    public static final class Channel {
        public final double centerHz, bandwidthHz, powerW, phi;
        public Channel(double centerHz,double bandwidthHz,double powerW,double legacySignedPhi) {
            if (!Double.isFinite(centerHz) || !(bandwidthHz>0.0) ||
                !Double.isFinite(bandwidthHz) || !(powerW>=0.0) ||
                !Double.isFinite(powerW) || !Double.isFinite(legacySignedPhi))
                throw new IllegalArgumentException("invalid GN channel");
            this.centerHz=centerHz;this.bandwidthHz=bandwidthHz;
            this.powerW=powerW;this.phi=legacySignedPhi;
        }
        public static Channel qpsk(double f,double b,double p) {return new Channel(f,b,p,-1.0);}
        public static Channel qam8(double f,double b,double p) {return new Channel(f,b,p,-2.0/3.0);}
        public static Channel qam32(double f,double b,double p) {return new Channel(f,b,p,-69.0/100.0);}
        public static Channel qam16(double f,double b,double p) {return new Channel(f,b,p,-17.0/25.0);}
        public static Channel qam64(double f,double b,double p) {return new Channel(f,b,p,-13.0/21.0);}
    }
    public static final class Fiber {
        public final double alphaPowerDbKm,gammaPerWKm,beta2Ps2Km,spanKm;
        public final int spans;
        public Fiber(double alphaPowerDbKm,double gammaPerWKm,double beta2Ps2Km,
                     double spanKm,int spans) {
            if (!(alphaPowerDbKm>0.0)||!(gammaPerWKm>0.0)||
                !Double.isFinite(alphaPowerDbKm)||!Double.isFinite(gammaPerWKm)||
                !Double.isFinite(beta2Ps2Km)||beta2Ps2Km==0.0||
                !(spanKm>0.0)||!Double.isFinite(spanKm)||spans<1)
                throw new IllegalArgumentException("invalid GN fiber");
            this.alphaPowerDbKm=alphaPowerDbKm;this.gammaPerWKm=gammaPerWKm;
            this.beta2Ps2Km=beta2Ps2Km;this.spanKm=spanKm;this.spans=spans;
        }
    }

private static final double PI = Math.PI;
    private static final double THZ = 1e12;
    private static final int MAX_CHANNELS = 16;

    public static final class GnResult {
        public final double sci, xci, mci;
        GnResult(double sci, double xci, double mci) {
            this.sci=sci; this.xci=xci; this.mci=mci;
        }
        public double total() { return sci+xci+mci; }
    }

    private static final class Grid {
        final double[] nodes, weights;
        Grid(double center, double width, int order) {
            nodes = new double[order]; weights = new double[order];
            for (int a=0; a<(order+1)/2; a++) {
                double z=Math.cos(Math.PI*(a+.75)/(order+.5));
                for(int it=0;it<60;it++) {
                    double[] p=legendre(order,z);
                    double next=z-p[0]/p[1];
                    if(Math.abs(next-z)<1e-15){z=next;break;}
                    z=next;
                }
                double d=legendre(order,z)[1];
                double w=width/((1-z*z)*d*d);
                nodes[a]=center-z*width/2;
                nodes[order-1-a]=center+z*width/2;
                weights[a]=w; weights[order-1-a]=w;
            }
        }
        private static double[] legendre(int n,double x){
            double p=1, prev=0;
            for(int j=1;j<=n;j++){
                double next=((2*j-1)*x*p-(j-1)*prev)/j;
                prev=p;p=next;
            }
            return new double[]{p,n*(x*p-prev)/(x*x-1)};
        }
    }

    private static double mu2(Fiber fib,
                               double f1,double f2,double f) {
        // With frequencies in THz and beta2 in ps^2/km, phase is 1/km.
        final double phase=4*PI*PI*fib.beta2Ps2Km*(f1-f)*(f2-f);
        final double loss=fib.alphaPowerDbKm*Math.log(10.0)/10.0;
        final double t=phase*fib.spanKm;
        final double atten=Math.exp(-loss*fib.spanKm);
        final double numerator=(1-atten*Math.cos(t))*(1-atten*Math.cos(t))
                + atten*atten*Math.sin(t)*Math.sin(t);
        final double denominator=loss*loss+phase*phase;
        // |sum_{n=0}^{Ns-1} exp(i n phase Ls)|^2 from Eq. (4).
        final double sincDen=Math.sin(t/2);
        final double array=(Math.abs(sincDen)<1e-12) ? fib.spans :
                Math.sin(fib.spans*t/2)/sincDen;
        return fib.gammaPerWKm*fib.gammaPerWKm * numerator/denominator
                * array*array;
    }

    public static GnResult evaluateGnIntegral(Fiber fib,
                                  List<Channel> channels,
                                  int cutIndex,int order,int receiverOrder) {
        if(fib==null || channels==null || channels.isEmpty() ||
                channels.size()>MAX_CHANNELS || cutIndex<0 ||cutIndex>=channels.size())
            throw new IllegalArgumentException("Poggiolini GN integral: invalid channel set");
        if(order<3 || order>64 || receiverOrder<1 || receiverOrder>32)
            throw new IllegalArgumentException("Poggiolini quadrature order out of range");
        Channel cut=channels.get(cutIndex);
        final int nch=channels.size();
        final double[] center=new double[nch], bandwidth=new double[nch], psd=new double[nch];
        final Grid[] grids=new Grid[nch];
        for(int i=0;i<nch;i++){
            Channel c=channels.get(i);
            bandwidth[i]=c.bandwidthHz/THZ;
            center[i]=(c.centerHz-cut.centerHz)/THZ;
            psd[i]=c.powerW/c.bandwidthHz;
            grids[i]=new Grid(center[i],bandwidth[i],order);
        }
        Grid receiver=new Grid(0,bandwidth[cutIndex],receiverOrder);
        double sci=0,xci=0,mci=0;
        for(int r=0;r<receiver.nodes.length;r++) {
            final double f=receiver.nodes[r], wOut=receiver.weights[r];
            for(int i=0;i<nch;i++)for(int j=0;j<nch;j++)for(int k=0;k<nch;k++){
                if(psd[i]==0 ||psd[j]==0 ||psd[k]==0)continue;
                Grid a=grids[i],b=grids[j];
                double integration=0;
                for(int ia=0;ia<order;ia++)for(int ib=0;ib<order;ib++){
                    double f1=a.nodes[ia],f2=b.nodes[ib];
                    double generated=f1+f2-f;
                    if(Math.abs(generated-center[k])>bandwidth[k]/2 + 1e-14)
                        continue;
                    integration+=a.weights[ia]*b.weights[ib]*mu2(fib,f1,f2,f);
                }
                double value=wOut*(16.0/27.0)*psd[i]*psd[j]*psd[k]
                        *integration*THZ*THZ;
                // Generated density is W/Hz: integration measures f1,f2 in THz,
                // hence 1e24 converts the two dimensions from THz to Hz.
                // Divide receiver integration by the CUT bandwidth in THz.
                int others=0;
                if(i!=cutIndex)others|=1<<i;
                if(j!=cutIndex)others|=1<<j;
                if(k!=cutIndex)others|=1<<k;
                if(others==0) sci+=value;
                else if(Integer.bitCount(others)==1)xci+=value;
                else mci+=value;
            }
        }
        double receiverAverage=1.0/bandwidth[cutIndex];
        GnResult out=new GnResult(sci*receiverAverage,xci*receiverAverage,
                mci*receiverAverage);
        if(!Double.isFinite(out.total()) || out.sci<0 ||out.xci<0 ||out.mci<0)
            throw new ArithmeticException("Poggiolini GN integral nonfinite");
        return out;
    }

    private static void check(boolean yes,String message) {
        if (!yes) throw new AssertionError(message);
    }
    private static List<Channel> exampleChannels(double bwGHz,int n) {
        final double b=bwGHz*1e9;
        List<Channel> out=new ArrayList<Channel>();
        out.add(Channel.qpsk(193.1e12,b,.001));
        if (n>=2) out.add(Channel.qpsk(193.1e12+1.05*b,b,.001));
        if (n>=3) out.add(Channel.qpsk(193.1e12-1.05*b,b,.001));
        return out;
    }
    /** Standalone GN-only validation, no EGN/Carena reference involved. */
    public static void main(String[] args) throws Exception {
        Fiber fib=new Fiber(.2,1.3,-21.3,100,1);
        File out=new File(args.length>0?args[0]:"poggiolini_gn_integral.csv");
        File parent=out.getAbsoluteFile().getParentFile();
        if(parent!=null)parent.mkdirs();
        int ncases=0;
        try(PrintWriter pw=new PrintWriter(new OutputStreamWriter(new FileOutputStream(out),"UTF-8"))){
            pw.println("bandwidth_GHz,channels,GN_SCI_W_Hz,GN_XCI_W_Hz,GN_MCI_W_Hz,GN_total_W_Hz");
            for(double b:new double[]{12.5,25.0,32.0,37.5,50.0}) {
                for(int n=1;n<=3;n++) {
                    GnResult value=evaluateGnIntegral(fib,exampleChannels(b,n),0,20,8);
                    check(value.sci>0 && Double.isFinite(value.total()) && value.total()>0,
                          "GN integral nonphysical output at "+b+" GHz");
                    if(n==1)check(value.xci==0 && value.mci==0,"1-channel has no XCI/MCI");
                    pw.printf(Locale.ROOT,"%.3f,%d,%.12e,%.12e,%.12e,%.12e%n",
                              b,n,value.sci,value.xci,value.mci,value.total());
                    ncases++;
                }
            }
            List<Channel> heterogeneous=new ArrayList<Channel>();
            heterogeneous.add(Channel.qpsk(193.1e12,12.5e9,.001));
            heterogeneous.add(Channel.qpsk(193.14e12,25e9,.001));
            heterogeneous.add(Channel.qpsk(193.18e12,32e9,.001));
            heterogeneous.add(Channel.qpsk(193.23e12,50e9,.001));
            check(evaluateGnIntegral(fib,heterogeneous,0,12,6).total()>0,
                  "heterogeneous-band GN integral failed");
        }
        System.out.println("PASS Poggiolini 2017 GN-only: "+ncases+" combinations; "+out);
    }
}
