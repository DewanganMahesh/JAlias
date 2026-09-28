package io.jalias.model;

/**
 * The kind of an import statement found in a compilation unit.
 */
public enum ImportKind {

    /** {@code import com.foo.Bar;} */
    SINGLE,

    /** {@code import com.foo.*;} */
    WILDCARD,

    /** {@code import static com.foo.Bar.BAZ;} */
    STATIC,

    /** {@code import static com.foo.Bar.*;} */
    STATIC_WILDCARD,

    /** {@code import com.foo.Bar as FooBar;} */
    ALIAS
}
