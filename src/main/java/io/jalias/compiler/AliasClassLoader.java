package io.jalias.compiler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Class loader over classes that were compiled in memory.
 *
 * <p>It exists so that code written with aliases can be executed straight after compilation - in
 * tests or in a REPL - while still being loaded as the <em>real</em> underlying classes: an alias
 * only ever changes how a class is spelled in source code, never what is on the class path.
 */
public final class AliasClassLoader extends ClassLoader {

    private final Map<String, byte[]> classes;

    /**
     * Creates a loader delegating to the loader that loaded JAlias itself.
     *
     * @param classes binary name to class file bytes
     */
    public AliasClassLoader(Map<String, byte[]> classes) {
        this(classes, AliasClassLoader.class.getClassLoader());
    }

    /**
     * Creates a loader.
     *
     * @param classes binary name to class file bytes
     * @param parent  parent loader used as fallback
     */
    public AliasClassLoader(Map<String, byte[]> classes, ClassLoader parent) {
        super(parent);
        this.classes = new LinkedHashMap<>(classes);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classes.get(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name + " (JAlias compiled: " + classes.keySet() + ")");
        }
        return defineClass(name, bytes, 0, bytes.length);
    }

    /**
     * @return the binary names of the classes this loader can define
     */
    public Set<String> definedClassNames() {
        return Set.copyOf(classes.keySet());
    }

    /**
     * @return the class file bytes this loader was created from
     */
    public Map<String, byte[]> classBytes() {
        return Map.copyOf(classes);
    }
}
