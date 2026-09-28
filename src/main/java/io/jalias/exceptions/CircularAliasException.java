package io.jalias.exceptions;

/**
 * Raised when alias resolution would be circular, for example when the target of one aliased
 * import is itself an alias:
 *
 * <pre>{@code
 * import com.foo.Bar as FooBar;
 * import FooBar as Bar;          // circular: FooBar is an alias, not a class
 * }</pre>
 */
public class CircularAliasException extends JAliasException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message description of the circular resolution
     */
    public CircularAliasException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message description of the circular resolution
     * @param cause   underlying cause
     */
    public CircularAliasException(String message, Throwable cause) {
        super(message, cause);
    }
}
