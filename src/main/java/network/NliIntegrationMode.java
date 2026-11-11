package network;

/**
 * Selects frequency-integration use independently of the underlying NLI model.
 * "approximate" means Johannisson logarithms or Habibi asinh with EGN
 * modulation correction; "integral" means Johannisson SCI/XCI kernels or
 * Poggiolini dual-polarization GN for Habibi's eligible SCI/XCI contributions.
 * "adaptive" uses integral when an approximation is nonphysical or the
 * actual transmitted channel bandwidth is below the configured threshold.
 * "legacy" preserves the original JSON boolean switches exactly.
 */
public final class NliIntegrationMode {
    private NliIntegrationMode() { }
    public static final String LEGACY="legacy";
    public static final String APPROXIMATE="approximate";
    public static final String INTEGRAL="integral";
    public static final String ADAPTIVE="adaptive";

    public static String normalize(String mode,String absent) {
        if(mode==null || mode.trim().length()==0) return absent;
        String value=mode.trim().toLowerCase(java.util.Locale.ROOT);
        if(LEGACY.equals(value) || APPROXIMATE.equals(value)
                || INTEGRAL.equals(value) || ADAPTIVE.equals(value)) return value;
        throw new IllegalArgumentException("Invalid NLI integration mode: "+mode
                +" (expected approximate, integral or adaptive)");
    }
    public static boolean useHabibiIntegral(String mode,boolean legacyEnabled,
            double actualBandwidthHz,double narrowbandGHz,double rawNli) {
        mode=normalize(mode,LEGACY);
        if(INTEGRAL.equals(mode)) return true;
        if(APPROXIMATE.equals(mode)) return false;
        return (ADAPTIVE.equals(mode) || legacyEnabled)
                && (actualBandwidthHz<=narrowbandGHz*1.0e9
                        || !Double.isFinite(rawNli) || rawNli<0.0);
    }
    public static boolean useJohannissonIntegral(String mode,boolean legacyIntegral,
            double bandwidthHz,double narrowbandGHz,boolean approximationInvalid) {
        mode=normalize(mode,LEGACY);
        if(LEGACY.equals(mode)) return legacyIntegral || approximationInvalid;
        if(INTEGRAL.equals(mode)) return true;
        if(APPROXIMATE.equals(mode)) return false;
        return bandwidthHz<=narrowbandGHz*1.0e9 || approximationInvalid;
    }
}
