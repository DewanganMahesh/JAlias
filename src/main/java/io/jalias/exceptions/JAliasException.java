package io.jalias.exceptions;

/**
 * Base type of every error raised by JAlias.
 *
 * <p>JAlias reports <em>alias level</em> problems (malformed alias syntax, duplicate aliases,
 * conflicting aliases, unresolvable import targets, unknown aliases) by throwing a subclass of
 * this exception. Problems inside the user's own Java code are <em>not</em> exceptions: they are
 * returned as ordinary compiler diagnostics by
 * {@link io.jalias.compiler.AliasCompiler#compile(io.jalias.compiler.AliasCompilationRequest)}.
 *
 * <p>The exception is unchecked so that the small public API stays usable from lambdas and
 * streams; alias declarations are compile-time pre-conditions, not recoverable runtime conditions.
 */
public class JAliasException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with the given message.
     *
     * @param message human readable description of the problem
     */
    public JAliasException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message human readable description of the problem
     * @param cause   underlying cause
     */
    public JAliasException(String message, Throwable cause) {
        super(message, cause);
    }
}
