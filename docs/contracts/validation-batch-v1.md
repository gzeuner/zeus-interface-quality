# Batch validation report v1

English | [Deutsch](validation-batch-v1.de.md)

Directory validation with `--report json` emits an aggregate document:

```json
{
  "format": "zeus-validation-batch-v1",
  "status": "INVALID",
  "valid": false,
  "results": [
    {"file": "orders.csv", "relativePath": "orders.csv", "status": "INVALID", "findings": []},
    {"file": "returns.csv", "relativePath": "nested/returns.csv", "status": "VALID", "findings": []}
  ]
}
```

By default, files are direct regular files matching the filename glob. With
`--recursive`, matching files at any depth are included. Results are sorted by
slash-normalized relative path. Each result has the basename in `file`, the
path from the input root in `relativePath`, and status `VALID`, `INVALID`, or
`ERROR`. Validation results contain the same finding objects and
optional metadata as [report v1](validation-report-v1.md). Error results have
an `error` message and no findings. The top-level status is `ERROR` if any
file has an operational error, otherwise `INVALID` if any file is invalid,
otherwise `VALID`; `valid` is true only for `VALID`.

Exit codes are `0` when every file is valid, `1` when at least one file is
invalid and none has an operational error, and `2` for a startup or per-file
operational error. Per-file errors do not stop later files from running.

`--dry-run-correction` can add `correctionPreview` metadata to CSV results
without changing inputs or creating sidecars. Batch SARIF output is one SARIF
2.1.0 run with per-finding input artifact URIs; operational errors use the
`zeus.operationalError` rule. SARIF output is separate from this JSON contract.
