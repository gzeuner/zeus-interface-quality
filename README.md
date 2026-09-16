# ZEUS Interface Quality

Technology-agnostic interface quality tool: local JSON Schema validation first, extensible to HTTP, SFTP, CSV, XML, and legacy adapters.

The project accompanies the German [tiny-tool.de project page](https://tiny-tool.de/) and documents the development from the initial idea to a practical, automatable tool.

## Status

Iteration 2b is complete. Shared tabular checks and CLI auto-detection live on the improvements branch.

The first iteration is deliberately small:

- validate a local JSON document against a local JSON Schema Draft 2020-12 schema;
- report all structural findings with stable paths and keywords;
- provide readable text output and a machine-readable JSON report;
- return deterministic exit codes for scripts and CI;
- run offline without contacting a remote schema or service;
- verify tests, packaging, and the executable JAR in GitHub Actions.

The implemented CLI command is:

```text
java -jar target/zeus-interface-quality-0.1.0-SNAPSHOT.jar validate \
  --schema path/to/schema.json \
  --input path/to/input.json \
  --report json
```

Exit codes are deterministic: `0` means valid, `1` means validation failed, and `2` means an operational or command error. The JSON report contract is documented in [`docs/contracts/validation-report-v1.md`](docs/contracts/validation-report-v1.md); an invalid example is available at [`docs/examples/validation-report-invalid-wrong-type.json`](docs/examples/validation-report-invalid-wrong-type.json).

HTTP, SFTP, FTP, XML, YAML-specific syntax, semantic rules, and real counterpart systems remain intentionally out of scope for this iteration.

## Technology baseline

- Java 21 as the minimum runtime;
- Apache Maven with Maven Wrapper;
- one Maven module for the first iteration;
- Jackson 2.x for JSON data handling;
- NetworkNT JSON Schema Validator 2.x for JSON Schema Draft 2020-12;
- Apache Commons CSV 1.14.1 for quoted, delimited text parsing;
- Picocli for the command-line interface;
- JUnit and AssertJ for tests;
- no Spring Boot dependency in the core or CLI.

The core uses project-owned result objects and ports. JSON parsing, schema validation, reporting, and future transports remain replaceable adapters.

## Command shape

```text
zeus-interface-quality validate \
  --schema path/to/schema.json \
  --input path/to/input.json
```

The exact result contract is documented in `docs/contracts/validation-report-v1.md` and is independent of the transport used to start a validation run.

`--input-format AUTO` is the default. The CLI reads the profile `format`
field and selects JSON Schema, CSV, or fixed-width. Explicit
`--input-format json|csv|fixed-width` still overrides detection.
`--profile` is an alias for `--schema`.

Version 1 CSV profiles define the encoding, one-character delimiter, exact
header order, and column rules for strings, integers, decimals, e-mail
addresses, required values, min/max length, min/max numeric bounds, and
regular expressions. Additional columns and malformed records use the same
VALID/INVALID result contract; syntactically broken CSV files remain
operational errors with exit 2.

Fixed-width input uses `--input-format fixed-width` or a profile with
`format: fixed-width`. Profiles define `recordLength` and one-based column
positions. `sourceLanguage` may document COBOL, RPG, or another origin; it
does not select a language-specific parser. Positions are measured in decoded
characters. An empty fixed-width file is INVALID (`minRecords`).

## Input and schema checks

Each file must contain exactly one JSON document. Empty files, trailing content, and duplicate object keys are operational errors (exit `2`). These checks also apply to referenced schema files. Schemas are checked against the bundled Draft 2020-12 meta-schema before use.

Schema references are restricted to regular files inside the real directory of the selected root schema. Both normalized paths and resolved symlink targets are checked. References to remote hosts are rejected before retrieval. Use a controlled local schema directory; this CLI is not a sandbox for hostile inputs or concurrently modified files.

Finding messages use German to preserve the published v1 example independently of the host locale. Automation should use `status`, paths, and `keyword`; message wording can change with a deliberate validator upgrade.

CSV profiles are deliberately a small adapter contract, not a replacement for
JSON Schema. The profile format can evolve independently, and future adapters
can use their own native schemas while returning the same project-owned result
objects.

The fixed-width profile is likewise an adapter-specific contract. It makes
legacy layout assumptions explicit without pretending to parse COBOL- or
RPG-Quelltexte. DDL-to-profile generation, transport access, and fachliche
Mehrdatei-Regeln remain topics for later iterations.

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
