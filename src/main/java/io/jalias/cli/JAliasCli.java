package io.jalias.cli;

/**
 * Entry point of the {@code jalias} command line tool.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * jalias transform [--no-comments] [-o <file|dir>] <source.java ...>
 * jalias compile   [-d <dir>] [-cp <classpath>] <source.java|dir ...>
 * jalias run       [-cp <classpath>] [-main <class>] <source.java ...> [-- <program args>]
 * jalias version | help
 * }</pre>
 */
public final class JAliasCli {

    private JAliasCli() {
    }

    /**
     * Runs the tool and exits with its status code.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        int status = new CliRunner().run(args, System.out, System.err);
        System.exit(status);
    }
}
