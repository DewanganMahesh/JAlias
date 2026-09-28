package com.foo;

/**
 * One of the two unrelated classes that share the simple name {@code Bar}.
 *
 * <p>This class is ordinary Java; JAlias never modifies it. It is compiled without aliases and then
 * referenced through the alias {@code FooBar}.
 */
public class Bar {

    private final String label;

    /**
     * Creates the instance.
     *
     * @param label label describing this Bar
     */
    public Bar(String label) {
        this.label = label;
    }

    /**
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * @return a description that identifies the package this type comes from
     */
    public String describe() {
        return "com.foo.Bar(" + label + ")";
    }

    /**
     * Example of a static member reached through an alias.
     *
     * @return the package of this type
     */
    public static String origin() {
        return "com.foo";
    }
}
