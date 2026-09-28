package io.jalias.exceptions;

/**
 * Raised when an alias is looked up but has never been declared, for example
 * {@code AliasRegistry.require("FooBar")} or resolving {@code TypeReference} for an unknown name.
 */
public class AliasNotFoundException extends JAliasException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message description of the unknown alias
     */
    public AliasNotFoundException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message description of the unknown alias
     * @param cause   underlying cause
     */
    public AliasNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
