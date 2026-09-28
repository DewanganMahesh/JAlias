/**
 * JAlias - import aliasing for the Java language.
 *
 * <p>Java has no {@code as} clause, so two types that share a simple name cannot be used side by side.
 * JAlias adds that capability on top of the language:
 *
 * <pre>{@code
 * import java.util.Date as UtilDate;
 * import java.sql.Date  as SqlDate;
 *
 * class Example {
 *     UtilDate createdAt;
 *     SqlDate  databaseDate;
 *
 *     void test() {
 *         UtilDate a = new UtilDate();
 *         SqlDate  b = new SqlDate(System.currentTimeMillis());
 *     }
 * }
 * }</pre>
 *
 * <p>Aliases are resolved at build time, never faked at runtime: the code above compiles to ordinary
 * Java that refers to {@code java.util.Date} and {@code java.sql.Date} directly.
 *
 * <h2>How it works</h2>
 *
 * <ol>
 *   <li>{@link io.jalias.parser.AliasImportParser} reads the aliased imports and produces a
 *       "normalised" copy of the source in which each {@code import x as y;} statement is a comment of
 *       exactly the same length and line count - valid Java with unchanged positions.</li>
 *   <li>{@link io.jalias.compiler.AliasSourceTransformer} asks the JDK compiler to parse that copy and
 *       rewrites every identifier that refers to an alias into the fully qualified class name.</li>
 *   <li>{@link io.jalias.compiler.AliasCompiler} compiles the generated Java and returns either class
 *       files (in memory or on disk) or the compiler diagnostics.</li>
 * </ol>
 *
 * <h2>Overview of the packages</h2>
 *
 * <ul>
 *   <li>{@code io.jalias} - the {@link io.jalias.JAlias} facade.</li>
 *   <li>{@code io.jalias.model} - data: aliases, imports, source units, generated source maps.</li>
 *   <li>{@code io.jalias.parser} - reading alias syntax out of a compilation unit.</li>
 *   <li>{@code io.jalias.imports} - questions about plain Java imports.</li>
 *   <li>{@code io.jalias.resolver} - alias lookup, registries and conflict detection.</li>
 *   <li>{@code io.jalias.compiler} - transformation and compilation.</li>
 *   <li>{@code io.jalias.exceptions} - the errors JAlias reports.</li>
 *   <li>{@code io.jalias.cli} - a small command line front end.</li>
 * </ul>
 */
package io.jalias;
