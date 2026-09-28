import java.util.AbstractMap.SimpleEntry as JEntry;
import java.util.Collections as JCollections;
import java.util.Map as JMap;
import java.util.TreeMap as JTreeMap;

/**
 * Shows aliases used with nested classes, static members, an anonymous subclass, {@code instanceof}
 * patterns and casts.
 */
public class NestedStaticExample {

    /**
     * Runs the example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        JMap<String, Integer> map = new JTreeMap<>();
        map.put("a", 1);
        map.put("b", 2);

        JMap<String, Integer> frozen = JCollections.unmodifiableMap(map);
        System.out.println("frozen     -> " + frozen);
        System.out.println("empty list -> " + JCollections.emptyList());

        JMap<String, Integer> anonymous = new JTreeMap<>() {
        };
        anonymous.put("c", 3);
        System.out.println("anonymous  -> " + anonymous.getClass().getName() + " " + anonymous);

        Object value = new JEntry<>("k", 9);
        if (value instanceof JEntry<?, ?> entry) {
            System.out.println("pattern    -> " + entry.getKey() + "=" + entry.getValue());
        }

        @SuppressWarnings("unchecked")
        JEntry<String, Integer> cast = (JEntry<String, Integer>) value;
        System.out.println("cast       -> " + cast);

        if (!(value instanceof java.util.Map.Entry)) {
            throw new IllegalStateException("a SimpleEntry is a Map.Entry");
        }
        if (value.getClass() != java.util.AbstractMap.SimpleEntry.class) {
            throw new IllegalStateException("JEntry must resolve to java.util.AbstractMap.SimpleEntry");
        }
        System.out.println("NestedStaticExample OK");
    }
}
