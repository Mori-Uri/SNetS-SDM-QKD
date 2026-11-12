package launcher;

/**
 * Edition of this launcher (Parte 44). This file exists only in the {@code experimental} branch of SNetS-SDM-QKD:
 * it makes the experimental copy of the simulator (folder SNetS-SDM-QKD_ex) easy to tell apart from the main one
 * and lets both run at the same time.
 *
 * <ul>
 *   <li>{@link #NAME}: shown next to the launcher name;</li>
 *   <li>{@link #DEFAULT_PORT}: 8766 by default; 8765 when -Dsnets.edition=main;</li>
 *   <li>{@link #SIMULATOR_JAR}: JAR used by default to run cases and as the output of "Gerar JAR".</li>
 * </ul>
 *
 * When the experimental branch is merged into main, delete this file and use the main values (or keep it with
 * NAME = "", DEFAULT_PORT = 8765 and SIMULATOR_JAR = "SNetS-SDM-QKD.jar").
 */
final class Edition {
    /** Optional edition selector without changing the Stage-5 defaults. */
    static final boolean EXPERIMENTAL = !"main".equalsIgnoreCase(System.getProperty("snets.edition", "experimental"));
    static final String NAME = EXPERIMENTAL ? "experimental" : "";
    static final int DEFAULT_PORT = EXPERIMENTAL ? 8766 : 8765;
    static final String SIMULATOR_JAR = EXPERIMENTAL ? "SNetS-SDM-QKD-exp.jar" : "SNetS-SDM-QKD.jar";

    private Edition() {
    }
}
