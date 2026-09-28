package io.jalias.exceptions;

/**
 * Raised when an alias collides with something else that is already visible in the same
 * compilation unit: another alias, an import, a visible type, or a name declared by the source.
 *
 * <p>Example message: {@code Alias 'FooBar' is already mapped to com.foo.Bar}.
 */
public class AliasConflictException extends JAliasException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message description of the conflict
     */
    public AliasConflictException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message description of the conflict
     * @param cause   underlying cause
     */
    public AliasConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
