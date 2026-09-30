# JAlias - import aliasing for Java

JAlias lets two Java types that share the same simple name be used side by side in one file:

```java
import java.util.Date as UtilDate;
import java.sql.Date as SqlDate;

class Example {
    UtilDate createdAt;
    SqlDate databaseDate;

    void test() {
        UtilDate a = new UtilDate();
        SqlDate b = new SqlDate(System.currentTimeMillis());
    }
}
```

`UtilDate` is `java.util.Date`, `SqlDate` is `java.sql.Date`, and both are *real* classes: JAlias
resolves aliases while it generates code, it never fakes a type at runtime.

- [Why JAlias exists](#why-jalias-exists)
- [Requirements](#requirements)
- [Installation](#installation)
- [The alias syntax](#the-alias-syntax)
- [Using the library](#using-the-library)
- [Using the command line tool](#using-the-command-line-tool)
- [Conflicting class names](#conflicting-class-names)
- [How the transformation works](#how-the-transformation-works)
- [Architecture](#architecture)
- [Errors](#errors)
- [Limitations](#limitations)
- [Examples](#examples)
- [Building and testing](#building-and-testing)
- [License](#license)

## Why JAlias exists

Java has no `as` clause. Two classes with the same simple name therefore cannot be imported into the
same file: the second import either hides the first one or is rejected. Developers work around this
with fully qualified names (`java.util.Date util = new java.util.Date();`), with wrapper types, or by
splitting code into artificial helper classes - all of which hurt readability.

JAlias adds the missing language feature on top of the compiler: the alias syntax is valid input for
JAlias and is turned into ordinary Java before `javac` or any other tool sees the code, so the aliases
survive type checking, IDE navigation, debugging and stack traces as the real classes.

## Requirements

* **A JDK, not just a JRE.** JAlias uses the JDK compiler API (`javax.tools`, `com.sun.source`) to parse and
  compile. `ToolProvider.getSystemJavaCompiler()` must be available.
* **Java 21 or later** (the library is compiled with `--release 21`).

JAlias itself has **no runtime dependencies**.

## Installation

Maven:

```xml
<dependency>
  <groupId>io.jalias</groupId>
  <artifactId>jalias</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle:

```groovy
implementation("io.jalias:jalias:0.1.0")
```

The jar declares `Automatic-Module-Name: io.jalias`, so it can also be used on the module path:
`requires io.jalias;` (the module needs `jdk.compiler`, which is exported by the JDK).

## The alias syntax

```java
package com.example.app;

import com.foo.Bar as FooBar;              // alias a class
import com.example.Bar as ExampleBar;     // the same simple name, a different class
import java.util.AbstractMap.SimpleEntry as JEntry;   // nested classes work too
import java.sql.Date;                     // plain imports keep working, always
import java.util.*;                       // wildcard imports too
import static java.lang.Math.max;         // and static imports

class Demo {
    FooBar foo;                           // -> com.foo.Bar
    ExampleBar example;                   // -> com.example.Bar
    JEntry<String, Integer> entry;        // -> java.util.AbstractMap.SimpleEntry
}
```

Rules:

* an aliased import is `import <fully.qualified.Name> as <Alias>;`
* it belongs in the import section, before the first type declaration
* `<Alias>` must be a valid Java identifier that is not a keyword
* an alias may not collide with a name that is already visible in the file: another alias, an import,
  a type such as `String`, or a name declared in the file
* aliasing a wildcard (`import com.foo.* as Foo;`) or a static member
  (`import static com.foo.Bar.X as X;`) is not supported and is reported as an error
* Javadoc `{@link FooBar}` references are *not* rewritten (they are not code); document the class name
  instead

Everything else behaves exactly as in Java: constructors, methods, fields, static members,
inheritance, interfaces, generics, annotations, arrays, exceptions, nested classes, parameters, return
types, casts, `instanceof`, patterns, records, sealed hierarchies, method references and lambdas.

## Using the library

The facade covers the common cases:

```java
import io.jalias.JAlias;
import io.jalias.compiler.CompilationResult;
import io.jalias.compiler.TransformResult;
import io.jalias.model.SourceUnit;

// 1. look at the generated Java, without compiling anything
TransformResult transformed = JAlias.transform("""
        import java.util.Date as UtilDate;

        class Example {
            UtilDate created = new UtilDate(0L);
        }
        """);
System.out.println(transformed.transformedSource());

// 2. compile alias sources in memory and run them
CompilationResult result = JAlias.compile(SourceUnit.of("Example", """
        import java.util.Date as UtilDate;

        public class Example {
            public String describe() { return new UtilDate(0L).getClass().getName(); }
        }
        """));
if (!result.success()) {
    result.problems().forEach(System.err::println);
}
Object example = result.classLoader().loadClass("Example").getDeclaredConstructor().newInstance();
```

Compiling files from disk, with a classpath and an output directory:

```java
CompilationResult result = JAlias.compile(
        List.of(SourceUnit.fromPath(Path.of("src/Example.java"))),
        Path.of("target/classes"),          // where class files go, null keeps them in memory
        List.of("libs/dependency.jar"));    // classpath used to resolve imports
```

For full control use `AliasCompiler` and `AliasCompilationRequest` directly:

```java
AliasOptions options = AliasOptions.defaults()
        .withEmitAliasComments(false)          // blank out the alias lines instead of commenting them
        .withCheckVisibleTypeConflicts(false); // skip the "alias clashes with a visible type" check

AliasCompiler compiler = new AliasCompiler(options);
CompilationResult result = compiler.compile(AliasCompilationRequest.builder()
        .sources(SourceUnit.of("com.example.app.Example", source))
        .addClasspath(Path.of("libs/dependency.jar"))
        .outputDirectory(Path.of("target/classes"))
        .javacOptions("-nowarn")
        .build());
```

The public API is small and documented with javadoc:

| Type | Purpose |
|---|---|
| `io.jalias.JAlias` | facade for the common operations |
| `io.jalias.compiler.AliasCompiler` | parse, transform and compile alias sources |
| `io.jalias.compiler.AliasCompilationRequest` | what to compile, classpath, output directory |
| `io.jalias.compiler.CompilationResult` | diagnostics, generated sources, classes, class loader |
| `io.jalias.compiler.AliasSourceTransformer` | the transformation on its own |
| `io.jalias.compiler.TransformResult` | generated Java source plus what was rewritten |
| `io.jalias.compiler.AliasClassLoader` | loads classes compiled in memory |
| `io.jalias.model.SourceUnit` | a compilation unit to compile |
| `io.jalias.model.ImportAlias` | one `import x as y;` declaration |
| `io.jalias.model.AliasOptions` | strictness and output options |
| `io.jalias.resolver.AliasRegistry` | alias declarations, conflict detection, `resolveClass` |
| `io.jalias.resolver.AliasResolver` | read only alias lookup |
| `io.jalias.parser.AliasImportParser` | reads alias syntax out of a compilation unit |
| `io.jalias.exceptions.*` | the errors described below |

## Using the command line tool

```bash
# run the jar directly
java -cp target/jalias-0.1.0.jar io.jalias.cli.JAliasCli <command>
```

| Command | Description |
|---|---|
| `transform <file.java ...>` | print the generated Java (alias imports become comments) |
| `transform --no-comments -o out.java <file.java>` | write the generated Java to a file |
| `compile [-d <dir>] [-cp <classpath>] <file.java\|dir ...>` | compile alias sources; without `-d` classes stay in memory |
| `run [-cp <classpath>] [-main <class>] <file.java ...> [-- args]` | compile and run a program |
| `version`, `help` | the usual |

Examples:

```bash
java -cp target/jalias-0.1.0.jar io.jalias.cli.JAliasCli transform examples/date-alias/DateAliasExample.java
java -cp target/jalias-0.1.0.jar io.jalias.cli.JAliasCli run examples/date-alias/DateAliasExample.java
```

## Conflicting class names

`java.util.Date` and `java.sql.Date` are the classic example, and everything in this README was verified
against them. Given

```java
import java.util.Date as UtilDate;
import java.sql.Date as SqlDate;

class Example {
    UtilDate createdAt;
    SqlDate databaseDate;

    void test() {
        UtilDate a = new UtilDate();
        SqlDate b = new SqlDate(System.currentTimeMillis());
    }
}
```

JAlias generates

```java
// import java.util.Date as UtilDate;
// import java.sql.Date as SqlDate;

class Example {
    java.util.Date createdAt;
    java.sql.Date databaseDate;

    void test() {
        java.util.Date a = new java.util.Date();
        java.sql.Date b = new java.sql.Date(System.currentTimeMillis());
    }
}
```

After compilation `createdAt.getClass()` is `java.util.Date` and `databaseDate.getClass()` is
`java.sql.Date` - the aliases were resolved at build time, and the generated code references the real
classes directly.

The same technique works for two application classes called `Bar` in different packages; see
[`examples/same-simple-name`](examples/same-simple-name).

## Using JAlias in a Maven project (including Spring Boot)

**The jar on its own is not enough.** `javac` has no `as` clause, so a file that still contains
`import com.foo.Bar as FooBar;` fails with `';' expected` - in Maven, in Gradle and in the IDE. That error
is the compiler rejecting the syntax, not the library failing: a build step has to run JAlias *before* the
compiler does.

### The Maven plugin (recommended)

Keep the files that use alias syntax outside the compiled source directory, by convention in
`src/main/jalias`, and let the plugin transform them during `generate-sources`:

```xml
<dependency>
  <groupId>io.jalias</groupId>
  <artifactId>jalias</artifactId>
  <version>0.1.0</version>
</dependency>

<build>
  <plugins>
    <plugin>
      <groupId>io.jalias</groupId>
      <artifactId>jalias-maven-plugin</artifactId>
      <version>0.1.0</version>
      <executions>
        <execution>
          <goals>
            <goal>transform</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

```
src/main/java/com/example/demo/App.java        <- ordinary Java, uses the generated class
src/main/jalias/com/example/demo/Aliased.java  <- uses "import java.util.Date as UtilDate;"
```

`mvn compile`, `mvn package` and `mvn spring-boot:repackage` then work unchanged: the goal runs in
`generate-sources`, writes `target/generated-sources/jalias/com/example/demo/Aliased.java` and registers
that directory as a compile source root, so `javac` never sees an `as` line:

```
[INFO] --- jalias:0.1.0:transform (default) @ jalias-demo ---
[INFO] JAlias transformed 1 file(s), 1 of them using aliases {UtilDate=java.util.Date, SqlDate=java.sql.Date} -> target/generated-sources/jalias
[INFO]   AliasedReport.java -> {UtilDate=java.util.Date, SqlDate=java.sql.Date} (4 reference(s) rewritten)
[INFO] --- compiler:3.14.1:compile (default-compile) @ jalias-demo ---
[INFO] Compiling 2 source files with javac [debug release 21] to target\classes
[INFO] BUILD SUCCESS
```

Goal parameters:

| Parameter | Property | Default | Meaning |
|---|---|---|---|
| `sourceDirectory` | `jalias.sourceDirectory` | `${project.basedir}/src/main/jalias` | where the alias sources live |
| `outputDirectory` | `jalias.outputDirectory` | `${project.build.directory}/generated-sources/jalias` | where the generated Java is written |
| `addSourceRoot` | `jalias.addSourceRoot` | `true` | register the output directory as a compile source root |
| `emitAliasComments` | `jalias.emitAliasComments` | `true` | keep the aliased imports in the generated sources as comments |
| `verify` | `jalias.verify` | `false` | compile the generated sources during `generate-sources` to fail early |
| `skip` | `jalias.skip` | `false` | skip the transformation |

If an alias file accidentally stays in a compiled source directory, the plugin stops the build with an
explanation instead of letting `javac` report the cryptic error:

```
[ERROR] JAlias: .../src/main/java/com/example/demo/AliasedReport.java uses alias syntax but is inside the
compiled source root .../src/main/java, so javac sees it and reports "';' expected" on the 'as' line.
Move the file to .../src/main/jalias (or configure <sourceDirectory>) so that JAlias transforms it before
the compiler runs.
```

### The IDE

An editor parses Java with its own compiler, so it will flag every `import x as y;` line - that cannot be
removed, because the syntax is JAlias' input rather than Java's. The practical setup:

* keep alias sources in `src/main/jalias`, which is outside the module's source roots, so they are not
  compiled by the IDE (IDEA's Maven import marks only `src/main/java` and
  `target/generated-sources/jalias` as source roots);
* read and navigate the *generated* code when you need the real type names - IDEA indexes
  `target/generated-sources/jalias` automatically;
* if the IDE still inspects the alias directory, exclude it from inspections
  (IDEA: *Project Structure → Modules → Sources*, or *Settings → Editor → Inspections*).

### Gradle and other build tools

Use the CLI in a task that runs before `compileJava`, or call the library API. The tree mode of the CLI
mirrors the Maven plugin:

```bash
java -cp jalias-0.1.0.jar io.jalias.cli.JAliasCli transform \
     --source-dir src/main/jalias --output-dir build/generated-sources/jalias
```

```groovy
tasks.register('transformAliases', JavaExec) {
    classpath = files('libs/jalias-0.1.0.jar')
    mainClass = 'io.jalias.cli.JAliasCli'
    args 'transform', '--source-dir', 'src/main/jalias',
         '--output-dir', "$buildDir/generated-sources/jalias"
}
compileJava.dependsOn transformAliases
sourceSets.main.java.srcDir "$buildDir/generated-sources/jalias"
```

Or call `SourceTreeTransformer` / `AliasCompiler` directly from a build script written in Java.

## How the transformation works

Java cannot parse `import com.foo.Bar as FooBar;`, so JAlias rewrites the source before the compiler
sees it. The interesting part is *how*, because a naive text substitution would corrupt string literals
and comments, and a hand written Java parser would have to be kept in sync with every language feature.

JAlias therefore uses the JDK's own compiler as its parser, in four steps:

1. **Read the aliases.** `io.jalias.parser.AliasImportParser` scans the package and import section with a
   small comment/string aware scanner and collects the aliased imports. It also produces a *normalised*
   copy of the file in which every `import x as y;` statement is replaced by a comment that occupies
   exactly the same characters and lines. The normalised file is valid Java and every other position in
   it is byte for byte where the developer put it.
2. **Parse with javac.** `AliasSourceTransformer` asks `JavacTask.parse()` to parse that file. Because this
   is the real parser, all of Java 21 is understood - generics, records, patterns, sealed types, text
   blocks - without JAlias implementing a grammar.
3. **Rewrite identifiers.** A `TreePathScanner` walks the syntax tree and replaces every identifier that
   stands for an alias with the fully qualified name of the class. Two positions are treated specially:
   the *member name* of a qualified access is never rewritten (so `Map.Entry` stays intact even when
   `Entry` is an alias), and an alias used as a method name or as a `case` label is reported as an error
   instead of being guessed at. Offsets from the tree are mapped back onto the original text, so string
   literals, comments and Javadoc are never touched.
4. **Compile.** `AliasCompiler` compiles the generated Java with the requested classpath, either into
   memory (returning the class files so they can be loaded and executed immediately) or into an output
   directory. Alias resolution is over: what is compiled is ordinary Java that refers to
   `java.util.Date` and `java.sql.Date` directly, so IDEs, debuggers, stack traces and reflection all see
   the real classes.

Before transforming, JAlias validates the aliases: duplicate aliases, circular targets, collisions with
imports, collisions with visible types and unresolvable import targets are reported before anything is
generated.

### Why not a different mechanism?

| Approach | Why it was not chosen |
|---|---|
| Annotation processor | Runs after parsing and cannot introduce new names into a compilation unit's scope; `as` never reaches the processor because the file does not parse. |
| Bytecode transformation / Java agent | The source has to compile first, which is exactly what it cannot do. It would also mean faking types at runtime, which JAlias explicitly does not do. |
| Hand written parser + code generator | Reimplements a Java parser and printer, and drifts with every language version. The JDK ships a compiler-grade parser. |
| `com.sun.tools.javac.*` internals, `-Xplugin`, Lombok-style patching | Non-API, version locked, needs `--add-exports`/`--add-opens`, and breaks on newer JDKs. JAlias only uses the public, exported `javax.tools` and `com.sun.source` API. |
| Custom DSL | Still needs a transformer, and adds a language to learn on top of the aliasing idea. |

## Architecture

```
io.jalias              JAlias                 - facade for the common operations
io.jalias.model        ImportAlias, SourceUnit, CompilationUnitModel, TypeReference, AliasOptions,
                       LineIndex, SourceMap, TypeNames, ImportStatement, ImportKind
io.jalias.parser       AliasImportParser      - reads alias syntax, normalises the source
                       AliasSourceScanner     - the small comment/literal aware scanner underneath
io.jalias.imports      ImportResolver         - plain imports, wildcard imports, collisions
io.jalias.resolver     AliasResolver          - read only alias lookup (interface)
                       AliasRegistry          - declarations, conflict detection, resolveClass
                       DefaultAliasResolver   - immutable snapshot used during transformation
                       TypeNameResolver       - name validity helpers
io.jalias.compiler     AliasCompiler          - parse, validate, transform, compile
                       AliasSourceTransformer - AST based rewriting
                       AliasDeclarationConflictValidator - rejects shadowed alias names
                       AliasClassLoader       - loads classes compiled in memory
                       JavacSupport           - thin wrapper around the compiler API
io.jalias.exceptions   JAliasException and the specific error types
io.jalias.cli          JAliasCli, CliRunner   - the command line tool
```

Alias resolution is deliberately isolated: the transformer only ever talks to the `AliasResolver`
interface, the parser only produces model data, and the compiler is the only class that drives javac.

## Errors

Alias level problems throw a subclass of `JAliasException`; problems in your own code are reported as
ordinary compiler diagnostics in `CompilationResult.problems()`. The messages are meant to be readable:

| Situation | Message |
|---|---|
| duplicate alias | `Alias 'FooBar' is already mapped to com.foo.Bar` |
| same aliased import twice | `Duplicate aliased import: alias 'FooBar' is already mapped to com.foo.Bar (Example.java:1)` |
| alias clashes with a visible type | `Alias 'String' conflicts with existing type 'java.lang.String' (Example.java:1)` |
| alias clashes with an import | `Alias 'Date' conflicts with the import 'java.util.Date' at line 1 (Example.java:2)` |
| alias shadows a declared name | `Alias 'FooBar' conflicts with the variable 'FooBar' declared at line 5 in Example.java: an alias name must not also be declared in the same file` |
| unknown alias | `Unknown alias 'UtilDtae'. Known aliases: [UtilDate]` |
| invalid import target | `Cannot resolve import 'com.foo.Nope' for alias 'Foo' (Example.java:1): the class is not on the compilation classpath` |
| circular resolution | `Circular alias resolution detected: import target 'FooBar' is itself an alias for com.foo.Bar (declared in Example.java:2)` |
| malformed alias | `Missing alias name after 'as' in 'import com.foo.Bar as;' in Example.java at line 1` |
| unsupported import | `Wildcard alias imports are not supported: 'import com.foo.* as Foo;' in Example.java at line 1. An alias can only refer to a single type` |
| alias used as a method name | `Alias 'FooBar' is used as a method name at line 5 in Example.java: an alias names a type, so it cannot also name a method` |
| alias used as a case label | `Alias 'FooBar' is used as a case label at line 6 in Example.java: qualify the constant or use a different alias name` |
| broken source | returned as a diagnostic, for example `/Example.java:5:13: ERROR: cannot find symbol` |

The exception types are `AliasSyntaxException`, `AliasConflictException`, `AliasNotFoundException`,
`InvalidImportException`, `CircularAliasException` and `AliasCompilationException`.

## Limitations

* **Aliases are file scoped**, just like imports: two files may alias the same name to different classes.
* **An alias name must not also be declared in the same file.** If a local variable, field, parameter,
  method, label, nested type, type parameter or enum constant carries the same name as one of the file's
  aliases, JAlias cannot tell the two meanings apart and refuses the file with an explicit message
  instead of guessing. `AliasOptions.withAllowDeclarationNameConflicts(true)` disables that check for
  sources you know are unambiguous, but the alias then wins in *every* position.
* **Wildcard and static aliasing are not supported** (`import com.foo.* as Foo;`,
  `import static com.foo.Bar.X as X;`). Aliases name types.
* **Javadoc is not rewritten.** `{@link FooBar}` is a doc comment, not code; write the real class name
  there.
* **The generated source is verbose** - aliases become fully qualified names - so a debugger or a stack
  trace shows `java.util.Date`, which is the point, but generated sources are less pleasant to read than
  the original. Enable `--no-comments` to drop the trace comments.
* **A JDK is required at runtime** to compile alias sources, since the JDK compiler API does the parsing.
* **Aliases after the first type declaration** are rejected, because Java only allows imports in the
  import section.
* **A build step is required.** `javac`, Gradle's and Maven's compiler plugins, Spring Boot's build and
  every IDE parse Java themselves and report `';' expected` on an `as` line; no jar on the classpath can
  change that. Run the transformation first - the
  [Maven plugin](#using-jalias-in-a-maven-project-including-spring-boot) or the
  [CLI tree mode](#gradle-and-other-build-tools) - and let the build compile the generated sources.
* **The IDE still flags the alias files**, because an editor cannot be taught the syntax by a dependency.
  Keep them in `src/main/jalias` so they are not compiled, and read the generated code for real type
  names.

## Examples

Runnable programs live in [`examples/`](examples) and are executed by the test suite:

| Example | Shows |
|---|---|
| [`date-alias/DateAliasExample.java`](examples/date-alias/DateAliasExample.java) | `java.util.Date` and `java.sql.Date` side by side |
| [`same-simple-name/`](examples/same-simple-name) | two `Bar` classes from different packages, fields, parameters, static members |
| [`generics-arrays/GenericsArraysExample.java`](examples/generics-arrays/GenericsArraysExample.java) | generics, wildcard bounds, arrays, `var` |
| [`nested-static/NestedStaticExample.java`](examples/nested-static/NestedStaticExample.java) | nested classes, static methods, anonymous subclasses, patterns, casts |

```bash
./examples/run-examples.ps1     # PowerShell: builds the jar and runs every example
```

## Building and testing

```bash
./mvnw clean install                        # core library: build, test, install (io.jalias:jalias)
./mvnw -f jalias-maven-plugin/pom.xml clean install   # Maven plugin (needs the core installed first)
./mvnw -DskipTests package                  # build the jar only
java -jar target/jalias-0.1.0.jar help      # the command line tool
```

The suite contains unit tests for every layer (parser, resolver, registry, transformer, tree
transformer, compiler, class loader, CLI, Maven plugin) plus integration tests that compile *and execute*
sources written with aliases, covering the specification's scenarios: one alias, two classes with the
same simple name, several aliases, fields, parameters, return types, generics, arrays, static members,
nested classes, constructors, `instanceof`, casts, alias conflicts, missing aliases and plain Java
imports continuing to work. The examples under `examples/` are compiled and run by the test suite as
well.

The Maven Wrapper is checked in, so no local Maven installation is needed; the build only needs a JDK
21+ (`JAVA_HOME`).

Project layout:

```
pom.xml                     the library (io.jalias:jalias)
src/main/java/io/jalias     library sources
src/test/java/io/jalias     unit, integration and example tests
jalias-maven-plugin/        the Maven plugin (io.jalias:jalias-maven-plugin)
examples/                   runnable example programs
README.md, LICENSE, mvnw    documentation, license, Maven wrapper
```

## License

Apache License 2.0 - see [LICENSE](LICENSE).
