# Iteration 12: SARIF reports

Status: implemented.

Single-file validation accepts `--report sarif` and emits SARIF 2.1.0. Each
finding maps to a SARIF result with its rule keyword, localized message,
absolute input URI, and ZEUS instance/schema paths as properties. The run
properties include the validation status. Existing TEXT and JSON output
contracts remain unchanged. SARIF is currently not available for directory
batches or remote transports without a local input path.

Verification: `ValidateCommandTest.emitsSarifForSingleInput` checks version,
tool, rule, and finding path serialization.
