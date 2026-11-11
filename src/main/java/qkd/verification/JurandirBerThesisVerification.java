package qkd.verification;

import network.PhysicalLayer;

/** Numeric references: Jurandir Lacerda Jr., Thesis, Ch. 3,
 * Tables 2, 3 and 4, Eqs. 3.4-3.12. No JUnit dependency required. */
public final class JurandirBerThesisVerification {
    private JurandirBerThesisVerification() { }
    private static final double[] ORDERS = {4, 8, 16, 32, 64};
    private static final double[] TARGETS = {1.0e-3, 3.8e-3, 4.0e-3, 1.0e-2};
    private static final double[][] TABLE2_BIT_DB = {
        {6.79, 5.52, 5.46, 4.32},
        {9.03, 7.83, 7.78, 6.73},
        {10.52, 9.17, 9.11, 7.88},
        {12.57, 11.23, 11.17, 9.94},
        {14.77, 13.34, 13.28, 11.95}
    };
    private static final double[][] TABLE3_OSNR_DB_WITH_2DB_MARGIN = {
        {11.80, 10.53, 10.47, 9.33},
        {15.80, 14.60, 14.55, 13.50},
        {18.54, 17.19, 17.13, 15.90},
        {21.56, 20.22, 20.16, 18.93},
        {24.55, 23.12, 23.06, 21.73}
    };
    private static final double[] TABLE4_OSNR_DB_AT_BER_013_AND_MARGIN = {
        8.95, 13.15, 15.49, 18.51, 21.28
    };
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
    private static double requiredSnrBit(double m, double targetBer) {
        double low = 0.0, high = 1e6;
        for (int i = 0; i < 120; i++) {
            double middle = (low + high) / 2.0;
            if (PhysicalLayer.getBERFromSnrPerBit(middle, m) > targetBer)
                low = middle;
            else
                high = middle;
        }
        return (low + high) / 2.0;
    }
    public static void main(String[] args) {
        int cases = 0;
        for (int i = 0; i < ORDERS.length; i++) {
            double m = ORDERS[i];
            for (int j = 0; j < TARGETS.length; j++) {
                double snrBit = requiredSnrBit(m, TARGETS[j]);
                double snrBitDb = 10.0 * Math.log10(snrBit);
                double osnrIdealDb = 10.0 * Math.log10(snrBit * (Math.log(m)/Math.log(2.0)));
                check(Math.abs(snrBitDb - TABLE2_BIT_DB[i][j]) < 0.015,
                      "Thesis Table 2 M=" + m + ", BER=" + TARGETS[j] + ", obtained=" + snrBitDb);
                check(Math.abs(osnrIdealDb + 2.0 - TABLE3_OSNR_DB_WITH_2DB_MARGIN[i][j]) < 0.015,
                      "Thesis Table 3 M=" + m + ", BER=" + TARGETS[j]);
                double osnr = snrBit * (Math.log(m)/Math.log(2.0));
                check(Math.abs(PhysicalLayer.getBER(osnr, m, 2.0) - TARGETS[j]) < 1e-9,
                      "OSNR->BER Eq. 3.10 M=" + m);
                cases++;
            }
            double snrBit013 = requiredSnrBit(m, 1.3e-2);
            double osnrDb013 = 10.0 * Math.log10(snrBit013 * (Math.log(m)/Math.log(2.0))) + 2.0;
            check(Math.abs(osnrDb013 - TABLE4_OSNR_DB_AT_BER_013_AND_MARGIN[i]) < 0.015,
                  "Thesis Table 4 M=" + m + " obtained=" + osnrDb013);
            cases++;
        }
        check(Double.isNaN(PhysicalLayer.getBER(10.0, 128.0, 2.0)),
                "unsupported odd QAM geometry must not be guessed");
        System.out.println("PASS JurandirBerThesisVerification: " + cases
                + " thesis cases, OSNR Eqs. 3.8-3.11; 2 dB used ONLY to match published tables");
    }
}
