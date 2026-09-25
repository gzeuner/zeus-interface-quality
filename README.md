# ZEUS Interface Quality

Technology-agnostic interface quality tool: local JSON Schema validation first, extensible to HTTP, SFTP, CSV, XML, and legacy adapters.

The project accompanies the German [tiny-tool.de project page](https://tiny-tool.de/) and documents the development from the initial idea to a practical, automatable tool.

## Status

Iteration 4 is complete. The current mainline validates local JSON, CSV, fixed-width, HTTP, and key-authenticated SFTP exchanges with one shared result contract.

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

SFTP profiles are the one-remote-file extension in Iteration 4. They verify a stable remote snapshot, optionally compare SHA-256, download into a temporary directory, delegate content validation to the existing JSON, CSV, or fixed-width adapter, and can move invalid deliveries into a configured remote quarantine directory. FTP, XML, YAML-specific syntax, semantic rules, and multi-file remote workflows remain intentionally out of scope.

## Technology baseline

- Java 21 as the minimum runtime;
- Apache Maven with Maven Wrapper;
- one Maven module for the first iteration;
- Jackson 2.x for JSON data handling;
- NetworkNT JSON Schema Validator 2.x for JSON Schema Draft 2020-12;
- Apache Commons CSV 1.14.1 for quoted, delimited text parsing;
- JDK `java.net.http.HttpClient` for the bounded HTTP adapter;
- JSch 2.28.7 for the bounded, key-authenticated SFTP adapter;
- Apache MINA SSHD 2.16.0 in test scope for an embedded SFTP integration server;
- Picocli for the command-line interface;
- JUnit and AssertJ for tests;
- no Spring Boot dependency in the core or CLI.

The core uses project-owned result objects and ports. JSON parsing, schema validation, reporting, and future transports remain replaceable adapters.

## Quick start: first successful run

The repository is built for Java 21. Maven itself does not need to be installed:
the checked-in Maven Wrapper downloads the configured Maven distribution on the
first run. From the repository root, use one of these commands.

On Windows PowerShell:

```text
mvnw.cmd -B test
java -jar target/zeus-interface-quality-0.1.0-SNAPSHOT.jar validate \
  --input-format json \
  --schema src/test/resources/fixtures/delivery.schema.json \
  --input src/test/resources/fixtures/valid-delivery.json \
  --report json
```

On macOS or Linux:

```text
./mvnw -B test
java -jar target/zeus-interface-quality-0.1.0-SNAPSHOT.jar validate \
  --input-format json \
  --schema src/test/resources/fixtures/delivery.schema.json \
  --input src/test/resources/fixtures/valid-delivery.json \
  --report json
```

The first command ends with a successful test summary. The second command
prints a `VALID` JSON report and exits with `0`. The repository only requires a
JDK 21 installation and network access for the initial Maven distribution and
dependency downloads.

## Command shape

```text
zeus-interface-quality validate \
  --schema path/to/schema.json \
  --input path/to/input.json
```

The exact result contract is documented in `docs/contracts/validation-report-v1.md` and is independent of the transport used to start a validation run.

`--input-format AUTO` is the default. The CLI reads the profile `format`
field and selects JSON Schema, CSV, fixed-width, HTTP, or SFTP. Explicit
`--input-format json|csv|fixed-width|http|sftp` still overrides detection.
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

SFTP input uses `--input-format sftp` or a profile with `format: sftp`. The
profile is passed as `--schema` or `--profile`; `--input` is deliberately not
used because the adapter obtains exactly one remote file. `known_hosts` and
the private key must be regular files below the profile directory. Host-key
checking is strict, authentication is key-only, and an optional passphrase is
read from an environment variable. Before download, the adapter checks that
the remote path is a regular file and that its size and modification time stay
stable for the configured number of observations. The downloaded snapshot is
then checked against an optional SHA-256 value and delegated to the selected
JSON, CSV, or fixed-width validator. See
[`docs/contracts/sftp-profile-v1.md`](docs/contracts/sftp-profile-v1.md) and
[`docs/iterations/iteration-4-sftp.md`](docs/iterations/iteration-4-sftp.md).

An invalid content or hash result has exit code `1` and a report. An invalid
profile, unavailable SFTP server, unstable file after bounded retries, or
other operational failure has exit code `2`. Quarantine is opt-in and is only
attempted after an invalid content or hash result; valid files are not moved.
The checked-in file [`docs/examples/sftp-profile.json`](docs/examples/sftp-profile.json)
is a configuration template, not a self-contained local demo: it requires a
real endpoint, a matching `known_hosts` file, a private key, and the local
content profile described in [`docs/examples/README.md`](docs/examples/README.md).
The first SFTP success path that works offline is
`mvnw.cmd -B -Dtest=SftpValidatorTest test` (or `./mvnw -B -Dtest=SftpValidatorTest test`).

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

HTTP profiles combine a local JSON request schema, an HTTP method and URL,
an expected response status, optional response-header requirements, and a
local JSON response schema. The request body is validated before the network
call. A response with the wrong status, headers, or body is `INVALID`; a
timeout, connection failure, malformed profile, or malformed request is an
operational error with exit code `2`. Redirects are not followed and all
schema references remain local to the profile directory. See
[`docs/contracts/http-profile-v1.md`](docs/contracts/http-profile-v1.md) and
[`docs/iterations/iteration-3-http.md`](docs/iterations/iteration-3-http.md).

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
