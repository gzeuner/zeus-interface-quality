# Batch validation report v1

English | [Deutsch](validation-batch-v1.de.md)

Directory validation with `--report json` emits an aggregate document:

```json
{
  "format": "zeus-validation-batch-v1",
  "status": "INVALID",
  "valid": false,
  "results": [
    {"file": "orders.csv", "status": "INVALID", "findings": []},
    {"file": "returns.csv", "status": "VALID", "findings": []}
  ]
}
```

Files are direct regular files matching the filename glob, processed in
lexicographic filename order. Each result has a basename and status `VALID`,
`INVALID`, or `ERROR`. Validation results contain the same finding objects and
optional metadata as [report v1](validation-report-v1.md). Error results have
an `error` message and no findings. The top-level status is `ERROR` if any
file has an operational error, otherwise `INVALID` if any file is invalid,
otherwise `VALID`; `valid` is true only for `VALID`.

Exit codes are `0` when every file is valid, `1` when at least one file is
invalid and none has an operational error, and `2` for a startup or per-file
operational error. Per-file errors do not stop later files from running.
