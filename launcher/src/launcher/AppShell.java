package launcher;

import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parte 59: the launcher as an application.
 *
 * <ul>
 *   <li>its own window: the interface opens in Edge or Chrome in app mode (no tabs, no address bar), or in the
 *       default browser when neither is found or the setting {@code appWindow} is off;</li>
 *   <li>an icon in the system tray (Windows), with the runs in progress, "Abrir", "Encerrar quando as simulações
 *       terminarem" and "Encerrar tudo";</li>
 *   <li>what happens when the last window of the interface is closed (setting {@code closePolicy}): stay in the
 *       tray with the simulations running ({@code tray}, default), stop when the simulations end
 *       ({@code exitWhenDone}) or stop everything now ({@code exit}: the simulations save their state and stop,
 *       as with Cancelar);</li>
 *   <li>a notice in the tray when a simulation ends.</li>
 * </ul>
 * Without a graphical environment (or without tray support) the launcher works as before.
 */
final class AppShell {

    private final Settings settings;
    private final Runner runner;
    private final String url;
    private TrayIcon icon;
    private CheckboxMenuItem exitWhenDoneItem;
    private MenuItem statusItem;

    /** Interface pages that pinged recently (id -> time of the last ping). */
    private final Map<String, Long> pages = new HashMap<String, Long>();
    private volatile boolean everConnected;
    private volatile long lastGone;
    private volatile boolean exitWhenDone;
    private final Map<String, String> lastStates = new HashMap<String, String>();

    AppShell(Settings settings, Runner runner, String url) {
        this.settings = settings;
        this.runner = runner;
        this.url = url;
    }

    // ------------------------------------------------------------------ start

    void start() {
        installTray();
        ensureShortcut(settings.launcherDir()); // Parte 61
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                watch();
            }
        }, "snets-app-watch");
        t.setDaemon(true);
        t.start();
    }

    private static boolean trayAvailable() {
        try {
            return !GraphicsEnvironment.isHeadless() && SystemTray.isSupported();
        } catch (Throwable e) {
            return false;
        }
    }

    boolean hasTray() {
        return icon != null;
    }

    private void installTray() {
        if (!trayAvailable() || "false".equalsIgnoreCase(settings.get("tray", "true"))) {
            return;
        }
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Abrir o SNetS Launcher");
            open.addActionListener(e -> openWindow(settings, url));
            statusItem = new MenuItem("Nenhuma simulação em execução");
            statusItem.setEnabled(false);
            exitWhenDoneItem = new CheckboxMenuItem("Encerrar quando as simulações terminarem");
            exitWhenDoneItem.addItemListener(e -> {
                exitWhenDone = exitWhenDoneItem.getState();
                if (exitWhenDone) notice("O launcher será encerrado quando as simulações terminarem.");
            });
            MenuItem quit = new MenuItem("Encerrar tudo (salva e para as simulações)");
            quit.addActionListener(e -> exitNow("menu da bandeja"));
            menu.add(open);
            menu.addSeparator();
            menu.add(statusItem);
            menu.add(exitWhenDoneItem);
            menu.addSeparator();
            menu.add(quit);
            String title = "SNetS Launcher" + (Edition.NAME.isEmpty() ? "" : " (" + Edition.NAME + ")");
            icon = new TrayIcon(drawIcon(), title, menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> openWindow(settings, url)); // double click
            SystemTray.getSystemTray().add(icon);
        } catch (Throwable e) {
            icon = null;
            System.out.println("[launcher] bandeja do sistema indisponível: " + e);
        }
    }

    /** Parte 61: the tray shows the same icon as the window of the interface (favicon of index.html). */
    private static Image drawIcon() {
        return appIcon(64);
    }

    /**
     * Parte 61: the icon of the interface (the favicon of index.html, drawn in a 32x32 box): a teal disc with a white
     * dot in the middle and six light dots around it (the same in both editions).
     */
    static BufferedImage appIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double k = size / 32.0;
        g.setColor(new Color(0x15, 0x5e, 0x75));
        g.fill(circle(16, 16, 14, k));
        g.setColor(new Color(0xa5, 0xf3, 0xfc));
        double[][] dots = { { 16, 8.5 }, { 16, 23.5 }, { 9.5, 12.2 }, { 22.5, 12.2 }, { 9.5, 19.8 }, { 22.5, 19.8 } };
        for (double[] d : dots) {
            g.fill(circle(d[0], d[1], 3, k));
        }
        g.setColor(Color.WHITE);
        g.fill(circle(16, 16, 3, k));
        g.dispose();
        return img;
    }

    private static Ellipse2D circle(double cx, double cy, double r, double k) {
        return new Ellipse2D.Double((cx - r) * k, (cy - r) * k, 2 * r * k, 2 * r * k);
    }

    static List<Image> appIcons() {
        List<Image> list = new ArrayList<Image>();
        for (int n : new int[] { 16, 24, 32, 48, 64 }) list.add(appIcon(n));
        return list;
    }

    /** Parte 61: Windows icon file (PNG images inside an .ico, accepted since Windows Vista). */
    static void writeIco(File out) throws IOException {
        int[] sizes = { 16, 24, 32, 48, 64, 128, 256 };
        List<byte[]> pngs = new ArrayList<byte[]>();
        for (int n : sizes) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(appIcon(n), "png", b);
            pngs.add(b.toByteArray());
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bos);
        o.writeShort(0);
        o.writeShort(Short.reverseBytes((short) 1));
        o.writeShort(Short.reverseBytes((short) sizes.length));
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int n = sizes[i];
            o.writeByte(n >= 256 ? 0 : n);
            o.writeByte(n >= 256 ? 0 : n);
            o.writeByte(0);
            o.writeByte(0);
            o.writeShort(Short.reverseBytes((short) 1));
            o.writeShort(Short.reverseBytes((short) 32));
            o.writeInt(Integer.reverseBytes(pngs.get(i).length));
            o.writeInt(Integer.reverseBytes(offset));
            offset += pngs.get(i).length;
        }
        for (byte[] b : pngs) o.write(b);
        o.flush();
        try (FileOutputStream f = new FileOutputStream(out)) {
            f.write(bos.toByteArray());
        }
    }

    /**
     * Parte 61: a .bat file cannot have its own icon, so the launcher keeps next to it a shortcut with the icon of the
     * interface ("SNetS Launcher.lnk"; "SNetS Launcher (experimental).lnk" in that edition). It runs SNetS-Launcher.bat
     * minimized and can be copied to the desktop or pinned to the taskbar. Created only when missing (delete it to
     * have it made again); -Dsnets.shortcut=false turns this off.
     */
    static void ensureShortcut(final File launcherDir) {
        if (!Settings.isWindows() || "false".equalsIgnoreCase(System.getProperty("snets.shortcut", "true"))) return;
        final File bat = new File(launcherDir, "SNetS-Launcher.bat");
        if (!bat.isFile()) return;
        final File ico = new File(launcherDir, "snets-launcher.ico");
        final File lnk = new File(launcherDir, "SNetS Launcher" + (Edition.NAME.isEmpty() ? "" : " (" + Edition.NAME + ")") + ".lnk");
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    writeIco(ico); // always rewritten: cheap, and follows changes of the drawing
                    if (lnk.isFile()) return;
                    String cmd = System.getenv("ComSpec");
                    if (cmd == null || cmd.isEmpty()) cmd = "cmd.exe";
                    String ps = "$s=(New-Object -ComObject WScript.Shell).CreateShortcut(" + q(lnk) + ");"
                            + "$s.TargetPath=" + q(cmd) + ";"
                            + "$s.Arguments=" + q("/c \"\"" + bat.getAbsolutePath() + "\"\"") + ";"
                            + "$s.WorkingDirectory=" + q(launcherDir) + ";"
                            + "$s.IconLocation=" + q(ico.getAbsolutePath() + ",0") + ";"
                            + "$s.WindowStyle=7;"
                            + "$s.Description='SNetS Launcher';"
                            + "$s.Save()";
                    Process pr = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                            "-Command", ps).redirectErrorStream(true).start();
                    ByteArrayOutputStream outp = new ByteArrayOutputStream();
                    byte[] buf = new byte[2048];
                    int n;
                    while ((n = pr.getInputStream().read(buf)) > 0) outp.write(buf, 0, n);
                    if (pr.waitFor() == 0 && lnk.isFile()) {
                        System.out.println("[launcher] atalho com ícone criado: " + lnk.getAbsolutePath());
                    } else {
                        System.out.println("[launcher] não foi possível criar o atalho: " + outp.toString("UTF-8").trim());
                    }
                } catch (Exception e) {
                    System.out.println("[launcher] não foi possível criar o atalho: " + e);
                }
            }
        }, "snets-shortcut");
        t.setDaemon(true);
        t.start();
    }

    private static String q(File f) {
        return q(f.getAbsolutePath());
    }

    /** PowerShell single-quoted string. */
    private static String q(String v) {
        return "'" + v.replace("'", "''") + "'";
    }

    private void notice(String text) {
        if (icon != null) {
            try {
                icon.displayMessage("SNetS Launcher", text, TrayIcon.MessageType.INFO);
            } catch (Throwable ignored) {
                // no notification
            }
        }
    }

    // ------------------------------------------------------------------ pages of the interface

    /** A page of the interface is open (called every few seconds by the page). */
    synchronized void ping(String pageId) {
        pages.put(pageId == null ? "?" : pageId, System.currentTimeMillis());
        everConnected = true;
    }

    /** A page was closed or reloaded (sendBeacon on pagehide). */
    synchronized void bye(String pageId) {
        pages.remove(pageId == null ? "?" : pageId);
        if (pages.isEmpty()) lastGone = System.currentTimeMillis();
    }

    private synchronized boolean anyPageOpen(long now) {
        pages.values().removeIf(t -> now - t > 15000L);
        return !pages.isEmpty();
    }

    Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("tray", hasTray());
        m.put("closePolicy", closePolicy());
        m.put("exitWhenDone", exitWhenDone);
        m.put("active", activeRuns());
        return m;
    }

    String closePolicy() {
        String p = settings.get("closePolicy", "tray");
        return "exit".equals(p) || "exitWhenDone".equals(p) ? p : "tray";
    }

    // ------------------------------------------------------------------ watcher

    private int activeRuns() {
        int n = 0;
        for (Object o : runner.list()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) o;
            String st = String.valueOf(r.get("state"));
            if ("running".equals(st) || "queued".equals(st)) n++;
        }
        return n;
    }

    private void watch() {
        boolean closedHandled = false;
        while (true) {
            try {
                Thread.sleep(3000L);
            } catch (InterruptedException e) {
                return;
            }
            long now = System.currentTimeMillis();
            int active = activeRuns();
            noticeFinishedRuns();
            if (statusItem != null) {
                statusItem.setLabel(active == 0 ? "Nenhuma simulação em execução"
                        : active == 1 ? "1 simulação em execução ou na fila" : active + " simulações em execução ou na fila");
            }
            if (exitWhenDone && active == 0) {
                exitNow("simulações terminadas");
                return;
            }
            boolean open = anyPageOpen(now);
            if (open) {
                closedHandled = false;
                continue;
            }
            // every window of the interface was closed (reloads come back within a few seconds)
            if (everConnected && !closedHandled && now - Math.max(lastGone, 0) > 8000L) {
                closedHandled = true;
                String p = closePolicy();
                if ("exit".equals(p)) {
                    exitNow("janela fechada");
                    return;
                } else if ("exitWhenDone".equals(p)) {
                    if (active == 0) {
                        exitNow("janela fechada");
                        return;
                    }
                    exitWhenDone = true;
                    if (exitWhenDoneItem != null) exitWhenDoneItem.setState(true);
                    notice("A janela foi fechada. O launcher encerra quando as " + active + " simulações terminarem.");
                } else if (icon != null) {
                    notice(active == 0 ? "O launcher continua na bandeja. Clique no ícone para abrir."
                            : "O launcher continua na bandeja com " + active + " simulação(ões) em execução.");
                }
            }
        }
    }

    private void noticeFinishedRuns() {
        List<Object> list = runner.list();
        for (Object o : list) {
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) o;
            String id = String.valueOf(r.get("id"));
            String st = String.valueOf(r.get("state"));
            String before = lastStates.put(id, st);
            if (before != null && "running".equals(before) && !"running".equals(st)) {
                String label = String.valueOf(r.get("label"));
                notice(("done".equals(st) || "finished".equals(st) ? "Terminou: " : "Parou (" + st + "): ") + label);
            }
        }
    }

    /** Stops everything: the shutdown hook asks the simulations to save their state and stop (Runner.stopAll). */
    void exitNow(String why) {
        System.out.println("[launcher] encerrando (" + why + ")");
        if (icon != null) {
            try {
                SystemTray.getSystemTray().remove(icon);
            } catch (Throwable ignored) {
                // closing anyway
            }
        }
        new Thread(() -> System.exit(0), "snets-exit").start();
    }

    void exitWhenDone() {
        exitWhenDone = true;
        if (exitWhenDoneItem != null) exitWhenDoneItem.setState(true);
    }

    // ------------------------------------------------------------------ window

    /** Opens the interface: Edge or Chrome in app mode when allowed and found, otherwise the default browser. */
    static void openWindow(Settings settings, String url) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win") && !"false".equalsIgnoreCase(settings.get("appWindow", "true"))) {
            String[] candidates = {
                System.getenv("ProgramFiles(x86)") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                System.getenv("ProgramFiles") + "\\Microsoft\\Edge\\Application\\msedge.exe",
                System.getenv("ProgramFiles") + "\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("ProgramFiles(x86)") + "\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe",
            };
            for (String c : candidates) {
                if (c != null && !c.startsWith("null") && new File(c).isFile()) {
                    try {
                        new ProcessBuilder(c, "--app=" + url, "--new-window").start();
                        return;
                    } catch (IOException ignored) {
                        // next candidate
                    }
                }
            }
        }
        LauncherServer.openBrowser(url);
    }
}
