package launcher;

import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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

    /** A small round icon drawn here (no image file needed): blue for the main edition, orange for experimental. */
    private static Image drawIcon() {
        int s = 64;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Edition.NAME.isEmpty() ? new Color(0x0f, 0x6e, 0x8c) : new Color(0xd9, 0x6a, 0x12));
        g.fillOval(2, 2, s - 4, s - 4);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
        int w = g.getFontMetrics().stringWidth("S");
        g.drawString("S", (s - w) / 2, 47);
        g.dispose();
        return img;
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
