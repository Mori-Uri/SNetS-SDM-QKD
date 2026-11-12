package qkd.verification;

import java.util.List;
import java.util.Locale;
import qkd.multiband.ClassicalChannel;
import qkd.multiband.MultiBandQkdLinkModel;
import qkd.physical.QKDPhysicalParameters;

/**
 * Traceable quantitative comparison of Dibaj et al. (IWCIT 2024) Fig. 5.
 * This is a scientific discrepancy REPORT, not a calibration procedure.
 * The paper's +40% MB gain may depend on unavailable conventions; do NOT
 * change physical coefficients to force agreement with a plotted percentage.
 */
public final class QKDStage5MultibandQuantitativeVerification {
    private QKDStage5MultibandQuantitativeVerification() { }

    public static void main(String[] args) {
        MultiBandQkdLinkModel model=MultiBandQkdLinkModel.paperCompatible(new QKDPhysicalParameters());
        List<ClassicalChannel> classical=ClassicalChannel.referenceCLComb(0.0);
        System.out.println("reference,architecture,wavelength_nm,qtnd_km,hops,"+
                "skr_per_hop_mbps,skr_per_qtrx_mbps,baseline_no_tn_mbps,gain_percent,paper_gain_percent,status");
        double mbNoTrusted=model.evaluate(1380,40.0,classical).getSecretKeyRateBps()/1e6;
        double mfNoTrusted=model.evaluateDarkFiber(1550,40.0).getSecretKeyRateBps()/1e6;
        if (!(mbNoTrusted>0 && mfNoTrusted>0)) throw new AssertionError("Reference no-trusted-node SKR nonphysical");
        double bestMb=-1,bestMf=-1;
        int bestMbD=0,bestMfD=0;
        for (int d=5;d<=40;d+=5) {
            double mb=model.evaluate(1380,d,classical).getSecretKeyRateBps()/1e6;
            double mf=model.evaluateDarkFiber(1550,d).getSecretKeyRateBps()/1e6;
            int mbHops=(int)Math.ceil(40.0/d-1e-9);
            // The MF convention used in the article-base is fractional (40/d).
            // This is NOT the physical number of full QKDSpan transceivers.
            double mbPer=mb/mbHops;
            double mfPer=mf*d/40.0;
            if (mbPer>bestMb) {bestMb=mbPer;bestMbD=d;}
            if (mfPer>bestMf) {bestMf=mfPer;bestMfD=d;}
            print("MB",1380,d,mbHops,mb,mbPer,mbNoTrusted,40.0);
            print("MF_fractional",1550,d,40.0/d,mf,mfPer,mfNoTrusted,15.0);
        }
        if(bestMbD!=20 || bestMfD!=25) {
            throw new AssertionError("QTND minima/maxima inverted: MB="+bestMbD+" MF="+bestMfD);
        }
        double mbGain=100.0*(bestMb/mbNoTrusted-1.0);
        double mfGain=100.0*(bestMf/mfNoTrusted-1.0);
        if(Math.abs(mbGain-40.0)>5.0) {
            System.err.printf(Locale.ROOT,"[PENDENTE] Dibaj 2024 MB 1380nm: model optimum 20km, gain %.4f%%, article +40%%; discrepancy %.4f percentage points. Original gain denominator/source data not fully reproducible.%n",
                    mbGain, mbGain-40.0);
        }
        if(Math.abs(mfGain-15.0)>5.0) {
            System.err.printf(Locale.ROOT,"[PENDENTE] Dibaj 2024 MF 1550nm: model optimum 25km, gain %.4f%%, article +15%%; discrepancy %.4f percentage points.%n",
                    mfGain, mfGain-15.0);
        }
        System.err.printf(Locale.ROOT,"PASS QKDStage5MultibandQuantitativeVerification: QTND MB=%dkm, MF=%dkm; measured gains MB=%.3f%% MF=%.3f%%%n",
                bestMbD,bestMfD,mbGain,mfGain);
    }

    private static void print(String architecture,int nm,int d,double hops,double skr,
                              double per,double baseline,double paperGain) {
        double gain=100.0*(per/baseline-1.0);
        String status = Math.abs(gain-paperGain)<=5.0?"WITHIN_5_PERCENTAGE_POINTS":"DIFFERS_FROM_PAPER";
        System.out.printf(Locale.ROOT,"Dibaj2024Fig5,%s,%d,%d,%.8f,%.9f,%.9f,%.9f,%.6f,%.3f,%s%n",
                architecture,nm,d,hops,skr,per,baseline,gain,paperGain,status);
    }
}
