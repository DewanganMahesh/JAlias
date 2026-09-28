package io.jalias.exceptions;

/**
 * Raised when a compilation unit that uses alias syntax cannot be parsed or compiled by the
 * underlying Java compiler, or when the compiler infrastructure itself is unavailable.
 *
 * <p>Errors in the <em>user's</em> code are reported as diagnostics by
 * {@link io.jalias.compiler.AliasCompiler} rather than thrown; this exception covers failures that
 * prevent JAlias from even producing compilable Java source.
 */
public class AliasCompilationException extends JAliasException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message description of the failure
     */
    public AliasCompilationException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message description of the failure
     * @param cause   underlying cause
     */
    public AliasCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
