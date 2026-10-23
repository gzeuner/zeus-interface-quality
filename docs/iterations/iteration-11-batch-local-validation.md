# Iteration 11: local batch validation

Status: implemented.

`validate --input-dir DIR --glob PATTERN` checks matching regular files in the
selected directory (not subdirectories), in filename order, against one local
profile. HTTP and SFTP profiles are rejected. JSON output uses the separate
`zeus-validation-batch-v1` envelope; text output includes per-file results and
a summary. Validation continues after a file-level operational error. Exit
codes are `0` when all files pass, `1` when at least one file is invalid, and
`2` when the batch cannot start or a file has an operational error.

SARIF and correction preview are intentionally single-file operations.
Cross-file comparison and recursive discovery are outside this iteration.

Verification: batch results are covered by `ValidateCommandTest`, including
stable ordering and aggregate invalid status.
