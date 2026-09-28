package com.example;

/**
 * The second of the two unrelated classes that share the simple name {@code Bar}.
 *
 * <p>Compiled without aliases and referenced through the alias {@code ExampleBar}.
 */
public class Bar {

    private final int number;

    /**
     * Creates the instance.
     *
     * @param number number describing this Bar
     */
    public Bar(int number) {
        this.number = number;
    }

    /**
     * @return the number
     */
    public int number() {
        return number;
    }

    /**
     * @return a description that identifies the package this type comes from
     */
    public String describe() {
        return "com.example.Bar(" + number + ")";
    }

    /**
     * Example of a static member reached through an alias.
     *
     * @return the package of this type
     */
    public static String origin() {
        return "com.example";
    }
}
