package io.jalias.integration;

import io.jalias.model.SourceUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * The library classes used by the integration tests: two unrelated classes that share the simple name
 * {@code Bar}. They are compiled together with the alias using code, so no classpath setup is needed.
 */
final class BarFixtures {

    static final String FOO_BAR = """
            package com.foo;

            public class Bar implements Comparable<Bar> {
                public static final String CONSTANT = "foo-constant";
                private final String label;

                public Bar() { this("foo-default"); }

                public Bar(String label) { this.label = label; }

                public String label() { return label; }

                public String describe() { return "com.foo.Bar(" + label + ")"; }

                public int compareTo(Bar other) { return label.compareTo(other.label); }

                public static String origin() { return "com.foo"; }

                public static class Nested {
                    public String describe() { return "com.foo.Bar.Nested"; }
                }
            }
            """;

    static final String EXAMPLE_BAR = """
            package com.example;

            public class Bar {
                public static final int CONSTANT = 42;
                private final int number;

                public Bar() { this(0); }

                public Bar(int number) { this.number = number; }

                public int number() { return number; }

                public String describe() { return "com.example.Bar(" + number + ")"; }

                public static String origin() { return "com.example"; }

                public static class Nested {
                    public String describe() { return "com.example.Bar.Nested"; }
                }
            }
            """;

    private BarFixtures() {
    }

    static List<SourceUnit> library() {
        return List.of(SourceUnit.of("com.foo.Bar", FOO_BAR), SourceUnit.of("com.example.Bar", EXAMPLE_BAR));
    }

    static List<SourceUnit> with(SourceUnit... units) {
        List<SourceUnit> all = new ArrayList<>(library());
        all.addAll(List.of(units));
        return all;
    }

    static List<SourceUnit> with(String binaryName, String source) {
        return with(SourceUnit.of(binaryName, source));
    }
}
