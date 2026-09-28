package io.jalias.exceptions;

/**
 * Raised when an import is syntactically valid Java but cannot be used by JAlias, or when the
 * imported class cannot be resolved.
 *
 * <p>Typical cases: the imported class does not exist on the compilation classpath, wildcard
 * aliasing ({@code import com.foo.* as Foo;}), static alias imports
 * ({@code import static com.foo.Bar as B;}) and ambiguous simple names.
 */
public class InvalidImportException extends JAliasException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message description of the invalid import
     */
    public InvalidImportException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message description of the invalid import
     * @param cause   underlying cause
     */
    public InvalidImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
