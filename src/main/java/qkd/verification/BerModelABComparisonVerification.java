package qkd.verification;

import network.PhysicalLayer;

/** Controlled A/B numeric comparison. Does NOT assert equal BER models. */
public final class BerModelABComparisonVerification {
    private BerModelABComparisonVerification() { }
    public static void main(String[] args) {
        System.out.println("modulation,osnr_db,osnr_linear,ber_jurandir,ber_legacy,absolute_difference");
        for (double m : new double[] {4.0, 8.0, 16.0, 32.0, 64.0}) {
            for (double osnrDb : new double[] {10.0, 14.1, 16.0, 20.0, 24.0, 30.0}) {
                double osnrLinear = Math.pow(10.0, osnrDb / 10.0);
                double jurandir = PhysicalLayer.getBER(osnrLinear, m, 2.0);
                double legacy = PhysicalLayer.getBERLegacyRectangularAwgn(osnrLinear, m);
                if (!Double.isFinite(jurandir) || !Double.isFinite(legacy))
                    throw new AssertionError("invalid numeric result");
                System.out.printf(java.util.Locale.ROOT, "%.0f,%.1f,%.12g,%.12g,%.12g,%.12g%n",
                        m, osnrDb, osnrLinear, jurandir, legacy, Math.abs(jurandir - legacy));
            }
        }
    }
}
