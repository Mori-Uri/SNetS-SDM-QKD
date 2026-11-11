package qkd.verification;

import network.Link;
import network.Mesh;
import qkd.capacity.QKDPichCircuit;
import qkd.network.QKDSpan;

/** Co-propagating, longitudinal IC-XT with actual PICh source bounds. */
public final class QKDLongitudinalXtVerification {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Mesh mesh=QKDPichSharedClassicalQoTVerification.mesh(false,false,true);
        Link link=mesh.getLinkList().get(0);
        QKDSpan a=new QKDSpan("A","B",0,2,0,20,"A","TN");
        QKDSpan b=new QKDSpan("A","B",1,2,20,40,"TN","B");
        QKDPichCircuit pich=new QKDPichCircuit(link,a,0,new int[]{1,1},0,1e-3);
        pich.assignModulation(mesh.getAvaliableModulations().get(0));
        link.getCore(0).addAuxiliaryClassicalCircuit(pich);
        try {
            double first=mesh.getPhysicalLayer().qkdLongitudinalXtReceiverPower(
                    link,1,new int[]{1,1},a,0.0,null);
            double attenuated=mesh.getPhysicalLayer().qkdLongitudinalXtReceiverPower(
                    link,1,new int[]{1,1},a,0.2,null);
            double separated=mesh.getPhysicalLayer().qkdLongitudinalXtReceiverPower(
                    link,1,new int[]{1,1},b,0.2,null);
            double nonoverlap=mesh.getPhysicalLayer().qkdLongitudinalXtReceiverPower(
                    link,1,new int[]{2,2},a,0.2,null);
            check(Double.isFinite(first) && first>0, "XT must reach the QCh receiver");
            check(attenuated>0 && attenuated<first,"quantum attenuation must affect receiver XT");
            check(separated==0.0,"PICh must not produce XT past its own physical span");
            check(nonoverlap==0.0,"zero spectral overlap must yield zero XT");
            System.out.println("PASS QKDLongitudinalXtVerification first="+first+
                    " attenuated="+attenuated);
        } finally {
            link.getCore(0).removeAuxiliaryClassicalCircuit(pich);
        }
    }
}
