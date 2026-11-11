package launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Regression test: removed EGN and orphan experimental classes must NOT be copied from the original simulator jar. */
public final class JarBuilderRemovedClassesTest {
    private JarBuilderRemovedClassesTest() { }
    private static void check(boolean b,String msg) { if (!b) throw new AssertionError(msg); }
    public static void main(String[] args) throws Exception {
        String[] obsolete={
            "network/EgnFullReference.class",
            "network/EgnFullReference$Components.class",
            "network/Poggiolini2017EgnReference.class",
            "network/Poggiolini2017EgnReference$GnResult.class",
            "verification/HabibiEgnComparisonVerification.class",
            "network/PhysicalLayer$HabibiEgnDecision.class",
            "network/PhysicalLayer$HabibiEgnCacheKey.class",
            "network/TrackedRequestList.class",
            "qkd/multiband/ClassicalChannel$Direction.class"
        };
        String[] preserve={
            "network/PhysicalLayer.class",
            "network/PhysicalLayer$HabibiGnDecision.class",
            "network/Poggiolini2017GnIntegral.class",
            "verification/HabibiGnComparisonVerification.class",
            "com/google/gson/Gson.class",
            "qkd/multiband/ClassicalChannel.class"
        };
        for (String s:obsolete) check(JarBuilder.isRetiredClass(s),"missed obsolete entry "+s);
        for (String s:preserve) check(!JarBuilder.isRetiredClass(s),"unexpected obsolete entry "+s);
        File tmp=Files.createTempDirectory("snets-pruning-test").toFile();
        try {
            File base=new File(tmp,"base.jar");
            try (JarOutputStream out=new JarOutputStream(new FileOutputStream(base))) {
                for (String name:obsolete) {
                    out.putNextEntry(new ZipEntry(name)); out.write(0);out.closeEntry();
                }
                for (String name:preserve) {
                    out.putNextEntry(new ZipEntry(name));out.write(0);out.closeEntry();
                }
            }
            File classes=new File(tmp,"classes");check(classes.mkdir(),"mkdir classes");
            File packed=new File(tmp,"packed.jar");
            Method pack=JarBuilder.class.getDeclaredMethod("pack",File.class,File.class,File.class,Set.class,Set.class);
            pack.setAccessible(true);
            Set<String> tops=new HashSet<String>(Arrays.asList(
                "network/PhysicalLayer","network/Poggiolini2017GnIntegral",
                "verification/HabibiGnComparisonVerification", "qkd/multiband/ClassicalChannel"));
            Set<String> pkgs=new HashSet<String>(Arrays.asList("network","verification","qkd/multiband"));
            pack.invoke(null,base,classes,packed,tops,pkgs);
            Set<String> found=new HashSet<String>();
            try (ZipFile z=new ZipFile(packed)) {
                Enumeration<? extends ZipEntry> es=z.entries();
                while(es.hasMoreElements())found.add(es.nextElement().getName());
            }
            for (String s:obsolete)check(!found.contains(s),"obsolete class retained in jar: "+s);
            for (String s:preserve)check(found.contains(s),"active class removed from jar: "+s);
        } finally {
            File[] fs=tmp.listFiles();if(fs!=null)for(File f:fs)f.delete();tmp.delete();
        }
        System.out.println("PASS JarBuilderRemovedClassesTest: retired EGN/experimental .class pruned, GN classes retained");
    }
}
