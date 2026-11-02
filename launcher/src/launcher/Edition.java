package launcher;

/**
 * Edition of this launcher (Parte 44). This file exists only in the {@code experimental} branch of SNetS-SDM-QKD:
 * it makes the experimental copy of the simulator (folder SNetS-SDM-QKD_ex) easy to tell apart from the main one
 * and lets both run at the same time.
 *
 * <ul>
 *   <li>{@link #NAME}: shown next to the launcher name;</li>
 *   <li>{@link #DEFAULT_PORT}: 8766 (the main launcher uses 8765);</li>
 *   <li>{@link #SIMULATOR_JAR}: JAR used by default to run cases and as the output of "Gerar JAR".</li>
 * </ul>
 *
 * When the experimental branch is merged into main, delete this file and use the main values (or keep it with
 * NAME = "", DEFAULT_PORT = 8765 and SIMULATOR_JAR = "SNetS-SDM-QKD.jar").
 */
final class Edition {
    static final String NAME = "experimental";
    static final int DEFAULT_PORT = 8766;
    static final String SIMULATOR_JAR = "SNetS-SDM-QKD-exp.jar";

    private Edition() {
    }
}
