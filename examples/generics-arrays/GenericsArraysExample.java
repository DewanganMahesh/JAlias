import java.util.AbstractMap.SimpleEntry as JEntry;
import java.util.ArrayList as JArrayList;
import java.util.HashMap as JHashMap;
import java.util.List as JList;
import java.util.Map as JMap;

/**
 * Shows aliases used with generics, arrays, bounded type parameters and lambdas.
 */
public class GenericsArraysExample {

    private final JList<JEntry<String, Integer>> entries = new JArrayList<>();
    private final JMap<String, JList<Integer>> index = new JHashMap<>();

    /**
     * Creates the example and fills both collections.
     */
    public GenericsArraysExample() {
        entries.add(new JEntry<>("one", 1));
        entries.add(new JEntry<>("two", 2));
        for (JEntry<String, Integer> entry : entries) {
            index.computeIfAbsent(entry.getKey(), key -> new JArrayList<>()).add(entry.getValue());
        }
    }

    /**
     * Sums a list of numbers; the alias appears inside a wildcard bound.
     *
     * @param values numbers to add up
     * @return the sum
     */
    public static int sum(JList<? extends Number> values) {
        int total = 0;
        for (Number value : values) {
            total += value.intValue();
        }
        return total;
    }

    /**
     * @return the entries as an array
     */
    @SuppressWarnings("unchecked")
    public JEntry<String, Integer>[] asArray() {
        return entries.toArray(new JEntry[entries.size()]);
    }

    /**
     * Runs the example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        GenericsArraysExample example = new GenericsArraysExample();
        System.out.println("entries -> " + example.entries);
        System.out.println("index   -> " + example.index);

        JEntry<String, Integer>[] array = example.asArray();
        System.out.println("array   -> " + java.util.Arrays.toString(array) + " (length " + array.length + ")");
        System.out.println("sum     -> " + sum(example.index.get("one")));

        var byKey = new JHashMap<String, JEntry<String, Integer>>();
        byKey.put("two", array[1]);
        System.out.println("var map -> " + byKey);

        if (array[1].getClass() != java.util.AbstractMap.SimpleEntry.class) {
            throw new IllegalStateException("JEntry must resolve to java.util.AbstractMap.SimpleEntry");
        }
        System.out.println("GenericsArraysExample OK");
    }
}
