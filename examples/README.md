# JAlias examples

Every example below uses the `import <class> as <Alias>;` syntax and asserts at runtime that the
aliases really resolved to the underlying JDK or library classes.

| Example | Shows |
|---|---|
| `date-alias/DateAliasExample.java` | the canonical case: `java.util.Date` and `java.sql.Date` side by side |
| `same-simple-name/` | two classes called `Bar` (`com.foo.Bar`, `com.example.Bar`) used together, fields, parameters, static members |
| `generics-arrays/GenericsArraysExample.java` | generics, wildcard bounds, arrays of an aliased type, `var` |
| `nested-static/NestedStaticExample.java` | nested classes, static methods, anonymous subclasses, `instanceof` patterns, casts |

## Running them

With the build tool:

```bash
./mvnw -DskipTests package
java -cp target/jalias-0.1.0.jar io.jalias.cli.JAliasCli run examples/date-alias/DateAliasExample.java
```

Or run everything at once (PowerShell):

```powershell
./examples/run-examples.ps1
```

The same-simple-name example needs its library sources too:

```bash
java -cp target/jalias-0.1.0.jar io.jalias.cli.JAliasCli run \
     examples/same-simple-name/lib examples/same-simple-name/SameSimpleNameExample.java \
     -main SameSimpleNameExample
```

## Looking at the generated Java

```bash
java -cp target/jalias-0.1.0.jar io.jalias.cli.JAliasCli transform examples/date-alias/DateAliasExample.java
```

prints the ordinary Java that JAlias produced: the aliased imports turn into comments and every alias
reference has become a fully qualified name.
