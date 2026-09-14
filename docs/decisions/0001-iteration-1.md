# ADR 0001: Technology baseline for iteration 1

Status: accepted for implementation

## Decision

Iteration 1 is implemented as a local Java 21 command-line application in one Maven module.

The first validation path is:

```text
local JSON input -> JSON adapter -> JSON Schema Draft 2020-12 validator
                  -> project-owned result objects -> text or JSON report
```

The core does not depend on Spring Boot, transport clients, or validator-specific result types.

## Selected components

| Concern | Choice |
|---|---|
| Runtime | Java 21 |
| Build | Apache Maven and Maven Wrapper |
| JSON model | Jackson 2.22.1 |
| JSON Schema | NetworkNT JSON Schema Validator 2.0.7 |
| CLI | Picocli 4.7.7 |
| Tests | JUnit Jupiter 6.1.3 and AssertJ 3.27.7 |
| Packaging | Executable Uber-JAR with Maven Shade Plugin 3.6.2 |

Versions are pinned centrally in `pom.xml` and must be updated deliberately.

## Boundaries

- The core owns `ValidationResult`, `Finding`, `Status`, and the relevant ports.
- JSON parsing and JSON Schema integration stay behind the JSON adapter.
- Finding order is normalized by the application, not inherited from a library collection.
- Local schema references are allowed; remote retrieval is not part of iteration 1.
- Format assertions are enabled explicitly and covered by tests.
- Spring Boot may be introduced later in a separate delivery module if a long-running service or worker is justified.

## Why not Spring Boot now?

The first proof is a local CLI. It does not need an HTTP server, application context, database, actuator endpoints, or profile-based service configuration. Adding Spring Boot at this point would increase the runtime and framework surface without improving the validation contract.

The separation above keeps a future Spring Boot module possible without moving the core into Spring.

## References

- [NetworkNT JSON Schema Validator](https://github.com/networknt/json-schema-validator)
- [Picocli](https://picocli.info/)
- [JUnit](https://docs.junit.org/current/user-guide/)
- [Apache Maven Shade Plugin](https://maven.apache.org/plugins/maven-shade-plugin/)
