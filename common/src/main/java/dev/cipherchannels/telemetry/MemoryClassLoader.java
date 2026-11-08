package dev.cipherchannels.telemetry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/** Defines diagnostics classes straight from a jar held in memory. */
final class MemoryClassLoader extends ClassLoader {
    private static final int MAX_CLASSES = 4096;
    private static final int MAX_CLASS_BYTES = 1_048_576;

    private final Map<String, byte[]> classes = new HashMap<>();

    MemoryClassLoader(byte[] jar, ClassLoader parent) throws IOException {
        super(parent);
        int count = 0;
        try (JarInputStream input = new JarInputStream(new ByteArrayInputStream(jar))) {
            JarEntry entry;
            while ((entry = input.getNextJarEntry()) != null && count < MAX_CLASSES) {
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                byte[] bytes = input.readNBytes(MAX_CLASS_BYTES);
                if (bytes.length == 0) continue;
                String name = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
                classes.put(name, bytes);
                count++;
            }
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classes.get(name);
        if (bytes == null) throw new ClassNotFoundException(name);
        return defineClass(name, bytes, 0, bytes.length);
    }
}
