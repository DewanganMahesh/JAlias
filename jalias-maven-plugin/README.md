# JAlias Maven Plugin

Runs the JAlias transformation during `generate-sources` so that Maven (and Spring Boot's build) can
compile sources that use `import com.foo.Bar as FooBar;`.

```xml
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
```

Alias sources live in `src/main/jalias` by default, the generated Java is written to
`target/generated-sources/jalias` and registered as a compile source root.

Build it with:

```bash
../mvnw clean install                      # the library first (io.jalias:jalias)
./mvnw clean install                       # then this plugin
```

Goal parameters and usage are documented in the [main README](../README.md#using-jalias-in-a-maven-project-including-spring-boot).
