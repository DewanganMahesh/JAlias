package io.jalias.exceptions;

/**
 * Raised when an aliased import is malformed, for example
 * {@code import com.foo.Bar as;} or an alias that is not a valid Java identifier.
 */
public class AliasSyntaxException extends JAliasException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message description of the malformed alias declaration
     */
    public AliasSyntaxException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message description of the malformed alias declaration
     * @param cause   underlying cause
     */
    public AliasSyntaxException(String message, Throwable cause) {
        super(message, cause);
    }
}
