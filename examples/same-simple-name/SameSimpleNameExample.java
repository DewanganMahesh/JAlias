import com.foo.Bar as FooBar;
import com.example.Bar as ExampleBar;

import java.util.ArrayList;
import java.util.List;

/**
 * Uses two classes that share the simple name {@code Bar} at the same time.
 *
 * <p>The classes in {@code lib/} are ordinary Java. Only this file uses aliases, and both aliases end
 * up referring to the real, distinct classes - a runtime check at the end of {@code main} proves it.
 */
public class SameSimpleNameExample {

    private final FooBar foo;
    private final ExampleBar example;
    private final List<String> descriptions = new ArrayList<>();

    /**
     * Creates the example.
     */
    public SameSimpleNameExample() {
        this.foo = new FooBar("first");
        this.example = new ExampleBar(7);
        descriptions.add(foo.describe());
        descriptions.add(example.describe());
    }

    /**
     * @return the descriptions of both values
     */
    public List<String> descriptions() {
        return descriptions;
    }

    /**
     * Takes an aliased type as a parameter and returns one as well.
     *
     * @param bar the value to label
     * @return a label for the value
     */
    public String label(FooBar bar) {
        return "labelled:" + bar.label();
    }

    /**
     * Runs the example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        SameSimpleNameExample example = new SameSimpleNameExample();
        example.descriptions().forEach(System.out::println);
        System.out.println("FooBar.origin()     -> " + FooBar.origin());
        System.out.println("ExampleBar.origin() -> " + ExampleBar.origin());
        System.out.println(example.label(new FooBar("param")));

        if (example.foo.getClass() != com.foo.Bar.class) {
            throw new IllegalStateException("FooBar must resolve to com.foo.Bar");
        }
        if (example.example.getClass() != com.example.Bar.class) {
            throw new IllegalStateException("ExampleBar must resolve to com.example.Bar");
        }
        if (example.foo.getClass().getName().equals(example.example.getClass().getName())) {
            throw new IllegalStateException("the two aliases must refer to different classes");
        }
        System.out.println("SameSimpleNameExample OK");
    }
}
