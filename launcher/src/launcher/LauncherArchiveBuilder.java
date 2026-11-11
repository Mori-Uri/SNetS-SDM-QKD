package launcher;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Builds the Launcher executable JAR using only the Java standard library.
 *
 * <p>This class deliberately does not depend on jar.exe, Maven, PowerShell,
 * or third-party libraries. The compiled classes and static resources must
 * already be staged inside the input directory.</p>
 */
public final class LauncherArchiveBuilder {
    private LauncherArchiveBuilder() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Uso: LauncherArchiveBuilder <pasta-classes> <saida.jar>");
            System.exit(2);
        }
        Path root = java.nio.file.Paths.get(args[0]).toAbsolutePath().normalize();
        Path target = java.nio.file.Paths.get(args[1]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IOException("Diretorio de classes nao encontrado: " + root);
        }
        if (!Files.isRegularFile(root.resolve("launcher/LauncherServer.class")) ||
            !Files.isRegularFile(root.resolve("web/index.html")) ||
            !Files.isRegularFile(root.resolve("templates/physicalLayer"))) {
            throw new IOException("Classes ou recursos do Launcher ausentes em: " + root);
        }
        if (target.startsWith(root)) {
            throw new IOException("O arquivo JAR deve ficar fora da pasta de classes.");
        }

        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        main.put(Attributes.Name.MAIN_CLASS, "launcher.LauncherServer");

        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream output = Files.newOutputStream(target);
             final JarOutputStream jar = new JarOutputStream(output, manifest)) {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    if (!attributes.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    String entryName = root.relativize(file).toString().replace('\\', '/');
                    // The standard JarOutputStream already created this entry.
                    if ("META-INF/MANIFEST.MF".equalsIgnoreCase(entryName)) {
                        return FileVisitResult.CONTINUE;
                    }
                    JarEntry entry = new JarEntry(entryName);
                    entry.setTime(attributes.lastModifiedTime().toMillis());
                    jar.putNextEntry(entry);
                    Files.copy(file, jar);
                    jar.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        // Verify the resulting JAR before build-launcher.bat replaces the old one.
        try (JarFile jar = new JarFile(target.toFile())) {
            if (jar.getManifest() == null ||
                !"launcher.LauncherServer".equals(jar.getManifest().getMainAttributes()
                    .getValue(Attributes.Name.MAIN_CLASS)) ||
                jar.getJarEntry("launcher/LauncherServer.class") == null ||
                jar.getJarEntry("web/index.html") == null ||
                jar.getJarEntry("templates/physicalLayer") == null) {
                throw new IOException("JAR gerado incompleto: " + target);
            }
        }
        System.out.println("[OK] JAR verificado (sem dependencia de jar.exe): " + target);
    }
}
