package launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parte 59: updates from the GitHub Releases of the repository.
 *
 * <p>A release of the main edition has a tag {@code main-v<version>} and the experimental one {@code exp-v<version>}
 * (made by {@code .github/workflows/release.yml} when such a tag is pushed). Its asset {@code SNetS-SDM-QKD-<tag>.zip}
 * has the files of the repository at that tag (the {@code simulations/} folder keeps only what is in git).</p>
 *
 * <p>The launcher checks the newest release of its edition and compares its version with its own. "Baixar" saves the
 * zip in {@code launcher/.update/aplicar.zip}; the next start ({@code SNetS-Launcher.bat}) extracts it over the
 * project folder before starting Java, so the running jar is never replaced. A folder that is a git clone
 * ({@code .git}) is not updated this way: there the update is a pull in GitHub Desktop.</p>
 */
final class AppUpdate {

    private final Settings settings;

    AppUpdate(Settings settings) {
        this.settings = settings;
    }

    String repo() {
        return settings.get("updateRepo", "Mori-Uri/SNetS-SDM-QKD");
    }

    private String channel() {
        return Edition.NAME.isEmpty() ? "main" : "exp";
    }

    boolean isGitClone() {
        return new File(settings.projectRoot(), ".git").exists();
    }

    private HttpURLConnection open(String u, String accept) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", accept);
        c.setRequestProperty("User-Agent", "SNetS-Launcher");
        String token = settings.get("updateToken", "");
        if (!token.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + token);
        }
        c.setInstanceFollowRedirects(true);
        return c;
    }

    private static String read(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Numbers of a version ("1.20.8" -> [1, 20, 8]); compares part by part. */
    static int compare(String a, String b) {
        String[] x = a.replaceAll("[^0-9.]", "").split("\\.");
        String[] y = b.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0;
            int q = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    /** The newest release of this edition and whether it is newer than the running launcher. */
    @SuppressWarnings("unchecked")
    Map<String, Object> check(String currentVersion) throws IOException {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("current", currentVersion);
        m.put("channel", channel());
        m.put("repo", repo());
        m.put("gitClone", isGitClone());
        m.put("pending", new File(settings.launcherDir(), ".update/aplicar.zip").isFile());
        HttpURLConnection c = open("https://api.github.com/repos/" + repo() + "/releases?per_page=30", "application/vnd.github+json");
        int code = c.getResponseCode();
        if (code == 404 || code == 401 || code == 403) {
            throw new IOException("O GitHub respondeu " + code + ". Se o repositório for privado, informe um token em Configurações.");
        }
        if (code != 200) {
            throw new IOException("O GitHub respondeu " + code);
        }
        Object parsed = new Parser(read(c.getInputStream())).value();
        String prefix = channel() + "-v";
        Map<String, Object> best = null;
        String bestVersion = null;
        for (Object o : (List<Object>) parsed) {
            Map<String, Object> r = (Map<String, Object>) o;
            String tag = String.valueOf(r.get("tag_name"));
            if (Boolean.TRUE.equals(r.get("draft")) || !tag.startsWith(prefix)) continue;
            String v = tag.substring(prefix.length());
            if (bestVersion == null || compare(v, bestVersion) > 0) {
                best = r;
                bestVersion = v;
            }
        }
        if (best == null) {
            m.put("latest", null);
            m.put("newer", false);
            return m;
        }
        m.put("latest", bestVersion);
        m.put("tag", best.get("tag_name"));
        m.put("name", best.get("name"));
        m.put("notes", best.get("body"));
        m.put("url", best.get("html_url"));
        m.put("published", best.get("published_at"));
        m.put("newer", compare(bestVersion, currentVersion) > 0);
        String asset = null;
        for (Object o : (List<Object>) best.get("assets")) {
            Map<String, Object> a = (Map<String, Object>) o;
            String n = String.valueOf(a.get("name"));
            if (n.endsWith(".zip")) {
                asset = String.valueOf(a.get("url")); // API url: works with a token for private repositories
                m.put("assetName", n);
                m.put("assetSize", a.get("size"));
                break;
            }
        }
        m.put("asset", asset);
        return m;
    }

    /** Downloads the zip of the newest release to launcher/.update/aplicar.zip (applied at the next start). */
    String download(String assetApiUrl) throws IOException {
        if (isGitClone()) {
            throw new IllegalStateException("Esta pasta é um clone do git: atualize pelo GitHub Desktop (Fetch/Pull).");
        }
        if (assetApiUrl == null || !assetApiUrl.startsWith("https://api.github.com/repos/" + repo() + "/releases/assets/")) {
            throw new IllegalArgumentException("Arquivo de atualização inválido");
        }
        File dir = new File(settings.launcherDir(), ".update");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Não foi possível criar " + dir);
        }
        File part = new File(dir, "aplicar.zip.part");
        HttpURLConnection c = open(assetApiUrl, "application/octet-stream");
        if (c.getResponseCode() != 200) {
            throw new IOException("Download respondeu " + c.getResponseCode());
        }
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        File zip = new File(dir, "aplicar.zip");
        if (zip.exists() && !zip.delete()) {
            throw new IOException("Não foi possível substituir " + zip);
        }
        if (!part.renameTo(zip)) {
            throw new IOException("Não foi possível gravar " + zip);
        }
        return zip.getAbsolutePath();
    }

    /** Minimal JSON reader for the answers of the GitHub API (objects, arrays, strings, numbers, true/false/null). */
    static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object value() {
            ws();
            if (i >= s.length()) throw new IllegalArgumentException("JSON incompleto");
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                i++;
                ws();
                if (s.charAt(i) == '}') { i++; return m; }
                while (true) {
                    ws();
                    String k = string();
                    ws();
                    i++; // ':'
                    m.put(k, value());
                    ws();
                    char d = s.charAt(i++);
                    if (d == '}') return m;
                }
            }
            if (c == '[') {
                List<Object> l = new java.util.ArrayList<Object>();
                i++;
                ws();
                if (s.charAt(i) == ']') { i++; return l; }
                while (true) {
                    l.add(value());
                    ws();
                    char d = s.charAt(i++);
                    if (d == ']') return l;
                }
            }
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.valueOf(s.substring(st, i));
        }

        private String string() {
            StringBuilder b = new StringBuilder();
            i++; // opening quote
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c != '\\') { b.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            }
        }
    }
}
