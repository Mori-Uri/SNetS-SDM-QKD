package verification;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import simulationControl.parsers.SimulationRequest;
import simulator.SaveStateStore;

/**
 * Regression check for JSON-source checkpoint hashing.
 *
 * Neither changes to the runtime checkpoint interval nor to the number of
 * worker threads may invalidate a checkpoint. Changes to the experiment's
 * physics, quantum physics and request count MUST invalidate it.
 */
public final class SaveStateInputHashVerification {
    private SaveStateInputHashVerification() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("snets-input-hash-");
        Path folder = Files.createDirectory(root.resolve("case"));
        try {
            write(folder.resolve("network"), "{\"links\":[],\"nodes\":[]}");
            write(folder.resolve("traffic"), "{\"requestGenerators\":[]}");
            write(folder.resolve("physicalLayer"), "{\"fiberLoss\":0.2}");
            write(folder.resolve("others"), "{}");
            write(folder.resolve("simulation"),
                    "{\"requests\":100,\"threads\":1,\"saveState\":{\"intervalMinutes\":10}}");

            SimulationRequest request = new SimulationRequest();
            request.setPath(root.toString());
            request.setName("case");
            String original = hash(request);
            write(folder.resolve("simulation"),
                    "{\"requests\":100,\"threads\":8,\"saveState\":{\"enabled\":false}}");
            check(original.equals(hash(request)),
                    "Changing threads or saveState must not invalidate checkpoints");

            write(folder.resolve("physicalLayer"), "{\"fiberLoss\":0.25}");
            String modifiedPhysical = hash(request);
            check(!original.equals(modifiedPhysical),
                    "Physical-layer change must invalidate checkpoints");

            write(folder.resolve("qkdPhysicalLayer"), "{\"meanPhotonNumber\":0.6}");
            String qkdModified = hash(request);
            check(!modifiedPhysical.equals(qkdModified),
                    "Independent QKD-physical input must invalidate checkpoints");

            write(folder.resolve("simulation"),
                    "{\"requests\":101,\"threads\":8,\"saveState\":{\"enabled\":false}}");
            check(!qkdModified.equals(hash(request)),
                    "Request-count change must invalidate checkpoints");

            // The reader chooses network.json over the extensionless network.
            write(folder.resolve("network.json"), "{\"links\":[],\"nodes\":[],\"guardBand\":1}");
            String preferred = hash(request);
            write(folder.resolve("network"), "{\"guardBand\":200}");
            check(preferred.equals(hash(request)),
                    "Ignored extensionless file changed the checkpoint input hash");
            write(folder.resolve("network.json"), "{\"links\":[],\"nodes\":[],\"guardBand\":2}");
            check(!preferred.equals(hash(request)),
                    "Effective .json input change did not invalidate checkpoints");

            System.out.println("PASS SaveStateInputHashVerification");
        } finally {
            try (Stream<Path> all = Files.walk(root)) {
                all.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    private static String hash(SimulationRequest request) throws Exception {
        Method method = SaveStateStore.class.getDeclaredMethod("inputHash", SimulationRequest.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(null, request);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        }
    }

    private static void write(Path file, String text) throws Exception {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
