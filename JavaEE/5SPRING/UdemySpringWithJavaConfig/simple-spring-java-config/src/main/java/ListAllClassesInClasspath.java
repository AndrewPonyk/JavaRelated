import java.io.File;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

// List all classes in classpath (interesting to see which classes Spring contains)
public class ListAllClassesInClasspath {
    public static void main(String[] args) {
        final AtomicInteger counter = new AtomicInteger();

        findClasses(new Visitor<String>() {
            public boolean visit(String s) {
                System.out.println(counter.get() + ")" + s);
                counter.incrementAndGet();
                return true;
            }
        });
    }

    public static void findClasses(Visitor<String> visitor) {
        String classpath = System.getProperty("java.class.path");
        System.out.println(System.getProperty("java.class.path"));
        String[] paths = classpath.split(System.getProperty("path.separator"));

        String javaHome = System.getProperty("java.home");
        File file = new File(javaHome + File.separator + "lib");

        if (file.exists()) {
            findClasses(file, file, true, visitor);
        }

        for (String path : paths) {
            file = new File(path);
            if (file.exists()) {
                findClasses(file, file, true, visitor);
            }
        }
    }

    private static boolean findClasses(File root, File file, boolean includeJars, Visitor<String> visitor) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                if (!findClasses(root, child, includeJars, visitor)) {
                    return false;
                }
            }
        } else {
            if (file.getName().toLowerCase().endsWith(".jar") && includeJars) {
                JarFile jar = null;
                try {
                    jar = new JarFile(file);
                } catch (Exception ex) {

                }
                if (jar != null) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        int extIndex = name.lastIndexOf(".class");
                        if (extIndex > 0) {
                            if (!visitor.visit(name.substring(0, extIndex).replace("/", "."))) {
                                return false;
                            }
                        }
                    }
                }
            }
            else if (file.getName().toLowerCase().endsWith(".class")) {
                if (!visitor.visit(createClassName(root, file))) {
                    return false;
                }
            }
        }

        return true;
    }

    private static String createClassName(File root, File file) {
        StringBuffer sb = new StringBuffer();
        String fileName = file.getName();
        sb.append(fileName.substring(0, fileName.lastIndexOf(".class")));
        file = file.getParentFile();
        while (file != null && !file.equals(root)) {
            sb.insert(0, '.').insert(0, file.getName());
            file = file.getParentFile();
        }
        return sb.toString();
    }

    public interface Visitor<T> {
        /**
         * @return {@code true} if the algorithm should visit more results,
         * {@code false} if it should terminate now.
         */
        public boolean visit(T t);
    }

}
