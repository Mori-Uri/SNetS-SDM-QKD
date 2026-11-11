package qkd.verification;

import gprmcsa.modulation.Modulation;
import network.Circuit;
import network.PhysicalLayer;
import simulationControl.parsers.NetworkConfig;
import com.google.gson.annotations.SerializedName;

/** Checks canonical OSNR APIs and legacy wire/API aliases, without changing the physics. */
public final class OSNRNamingVerification {
    private OSNRNamingVerification() { }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        NetworkConfig.ModulationConfig config = new NetworkConfig.ModulationConfig("4QAM", 4, 100, 10.5, -20);
        check(config.getOSNRthresholdDb() == 10.5, "network threshold is OSNR");
        check(config.getSNR() == 10.5, "legacy network getter");
        SerializedName jsonName = NetworkConfig.ModulationConfig.class
                .getDeclaredField("OSNR").getAnnotation(SerializedName.class);
        check(jsonName != null && "OSNR".equals(jsonName.value())
                && java.util.Arrays.asList(jsonName.alternate()).contains("SNR"), "legacy JSON alias");
        SerializedName pichName = NetworkConfig.QKDConfig.class
                .getDeclaredField("pichOsnrCheckEnabled").getAnnotation(SerializedName.class);
        check(pichName != null && "pichOsnrCheckEnabled".equals(pichName.value())
                && java.util.Arrays.asList(pichName.alternate()).contains("pichSnrCheckEnabled"),
                "PICh gate JSON alias");
        NetworkConfig.QKDConfig q = new NetworkConfig.QKDConfig();
        q.setPichOsnrCheckEnabled(false);
        check(!q.isPichOsnrCheckEnabled() && !q.isPichSnrCheckEnabled(), "PICh old and new accessors");
        Modulation modulation = new Modulation("4QAM", 100, 4, 10.5, .1, 12.5e9, 2, -20);
        check(modulation.getOSNRthreshold() == modulation.getSNRthreshold(), "legacy modulation getter");
        check(modulation.getOSNRthresholdLinear() == modulation.getSNRthresholdLinear(), "legacy linear threshold");
        Circuit circuit = new Circuit();
        circuit.setOSNR(18.3);
        check(circuit.getOSNR() == circuit.getSNR(), "legacy circuit getter");
        check(circuit.getOSNRlinear() == circuit.getSNRlinear(), "legacy circuit linear getter");
        circuit.setOSNRlinear(10.0);
        check(Math.abs(circuit.getOSNR() - 10.0) < 1e-12, "OSNR scale conversion");
        check(PhysicalLayer.class.getDeclaredMethod("computeSNRSegment", Circuit.class,
                gprmcsa.routing.Route.class, int.class, int.class, Modulation.class, int.class,
                int[].class, Circuit.class, boolean.class).isAnnotationPresent(Deprecated.class),
                "binary API compatibility");
        boolean obsolete = false;
        try { PhysicalLayer.class.getDeclaredMethod("isAdmissible2", double.class, Modulation.class, double.class); }
        catch (NoSuchMethodException expected) { obsolete = true; }
        check(obsolete, "unused auxiliary QoT entry point removed");
        System.out.println("PASS OSNRNamingVerification");
    }
}
