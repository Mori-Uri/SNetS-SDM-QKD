package launcher;

import java.awt.GraphicsEnvironment;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Parte 60: folder or file chosen in the file explorer of the computer (the interface runs in a browser and cannot
 * read real paths). The dialog opens in front of the other windows; the call waits until it is closed.
 */
final class Picker {

    private static boolean lookAndFeelSet;

    private Picker() {
    }

    /**
     * @param mode {@code folder} or {@code file}
     * @param start folder (or file) where the dialog starts; may be empty
     * @return the absolute path chosen, or null when the dialog was cancelled
     */
    static synchronized String pick(final String mode, final String start, final String title) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("Sem ambiente gráfico para abrir o explorador de arquivos: digite o caminho.");
        }
        final String[] out = { null };
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                if (!lookAndFeelSet) {
                    try {
                        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    } catch (Exception ignored) {
                        // default look
                    }
                    lookAndFeelSet = true;
                }
                final JFrame owner = new JFrame(title == null ? "SNetS Launcher" : title);
                owner.setUndecorated(true);
                owner.setIconImages(AppShell.appIcons()); // Parte 61
                owner.setAlwaysOnTop(true);
                owner.setSize(1, 1);
                owner.setLocationRelativeTo(null);
                owner.setVisible(true);
                owner.toFront();
                try {
                    File s = start == null || start.trim().isEmpty() ? null : new File(start.trim());
                    while (s != null && !s.exists()) s = s.getParentFile();
                    final JFileChooser fc = new JFileChooser();
                    if (s != null) {
                        if (s.isDirectory()) fc.setCurrentDirectory(s); else fc.setSelectedFile(s);
                    }
                    final boolean folder = !"file".equals(mode);
                    fc.setFileSelectionMode(folder ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
                    fc.setDialogTitle(title == null || title.isEmpty() ? (folder ? "Escolher pasta" : "Escolher arquivo") : title);
                    fc.setAcceptAllFileFilterUsed(true);
                    if (fc.showDialog(owner, "Selecionar") == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) {
                        out[0] = fc.getSelectedFile().getAbsolutePath();
                    }
                } finally {
                    owner.dispose();
                }
            }
        });
        return out[0];
    }
}
