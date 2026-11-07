package launcher;

import java.io.File;

/**
 * Edition of this launcher (Parte 44; automatic since Parte 55). The same file is in the {@code main} and in the
 * {@code experimental} branch, so the merge of the branches needs no change here.
 *
 * <p>The copy is the experimental one when the launcher folder has the local file {@value #MARKER} (it is not
 * versioned: see .gitignore) or when the launcher is started with {@code -Dsnets.edition=experimental}
 * ({@code -Dsnets.edition=main} forces the main one). Otherwise it is the main edition.</p>
 *
 * <ul>
 *   <li>{@link #NAME}: shown next to the launcher name ("" in the main edition);</li>
 *   <li>{@link #DEFAULT_PORT}: 8765 (main) or 8766 (experimental), so both launchers can run together;</li>
 *   <li>{@link #SIMULATOR_JAR}: JAR used by default to run cases and as the output of "Gerar JAR".</li>
 * </ul>
 */
final class Edition {
    /** Local marker file of the experimental folder (launcher/EDICAO_EXPERIMENTAL). */
    static final String MARKER = "EDICAO_EXPERIMENTAL";
    static final boolean EXPERIMENTAL = detect();
    static final String NAME = EXPERIMENTAL ? "experimental" : "";
    static final int DEFAULT_PORT = EXPERIMENTAL ? 8766 : 8765;
    static final String SIMULATOR_JAR = EXPERIMENTAL ? "SNetS-SDM-QKD-exp.jar" : "SNetS-SDM-QKD.jar";

    private Edition() {
    }

    private static boolean detect() {
        String p = System.getProperty("snets.edition", "").trim().toLowerCase();
        if ("experimental".equals(p)) {
            return true;
        }
        if ("main".equals(p)) {
            return false;
        }
        try {
            // snets-launcher.jar is in launcher/; when run from a classes folder, look a few levels up
            File d = new File(Edition.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (int i = 0; i < 4 && d != null; i++, d = d.getParentFile()) {
                if (new File(d, MARKER).isFile()) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // no code source: main edition
        }
        return false;
    }
}
