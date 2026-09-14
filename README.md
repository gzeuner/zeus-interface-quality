# ZEUS Interface Quality

Technology-agnostic interface quality tool: local JSON Schema validation first, extensible to HTTP, SFTP, CSV, XML, and legacy adapters.

The project accompanies the German [tiny-tool.de project page](https://tiny-tool.de/) and documents the development from the initial idea to a practical, automatable tool.

## Status

Iteration 1 is being implemented.

The first iteration is deliberately small:

- validate a local JSON document against a local JSON Schema Draft 2020-12 schema;
- report all structural findings with stable paths and keywords;
- provide readable text output and a machine-readable JSON report;
- return deterministic exit codes for scripts and CI;
- run offline without contacting a remote schema or service.

HTTP, SFTP, FTP, CSV, XML, YAML-specific syntax, semantic rules, and real counterpart systems are intentionally out of scope for this iteration.

## Technology baseline

- Java 21 as the minimum runtime;
- Apache Maven with Maven Wrapper;
- one Maven module for the first iteration;
- Jackson 2.x for JSON data handling;
- NetworkNT JSON Schema Validator 2.x for JSON Schema Draft 2020-12;
- Picocli for the command-line interface;
- JUnit and AssertJ for tests;
- no Spring Boot dependency in the core or CLI.

The core uses project-owned result objects and ports. JSON parsing, schema validation, reporting, and future transports remain replaceable adapters.

## Planned command shape

```text
zeus-interface-quality validate \
  --schema path/to/schema.json \
  --input path/to/input.json
```

The exact result contract is documented in `docs/decisions/0001-iteration-1.md` and will be kept independent of the transport used to start a validation run.

## Build

The Maven Wrapper is part of the repository setup. The build commands are:

```text
./mvnw test
```

On Windows:

```text
mvnw.cmd test
```

## License

The open-source license will be fixed before the first public release.
