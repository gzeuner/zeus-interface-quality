# Iteration 10: CSV correction verification

Status: implemented.

## Scope

- Add direct tests for the traceable CSV correction transaction.
- Cover invocation from the CLI and the GUI's local validation service.
- Keep all tests local and deterministic; this iteration adds no container
  runtime or external service dependency.

## Covered behavior

- Invalid data rows are removed only when correction is enabled and every
  finding points to a data row.
- The corrected input is revalidated before it replaces the original.
- The original bytes, rejected rows and audit report are retained on success.
- The audit captures logical data-row and source-record numbers plus findings.
- Disabled correction, file-level findings, failed revalidation and existing
  sidecar artifacts do not alter the input. Existing artifacts are preserved.
- CLI reports the successful corrected result; GUI service returns the same
  result and creates the same trace artifacts.
- Interpolated values in bilingual `MessageFormat` findings remain visible,
  including quoted CSV values and filename-pattern details.

## Verification

Run the focused regression suite with:

```powershell
.\mvnw.cmd -B '-Dtest=CsvCorrectionServiceTest,CsvValidatorTest,ValidateCommandTest,LocalValidationServiceTest' test
```

The standard Maven test lifecycle remains local and does not require Rancher
Desktop, Docker or Testcontainers.

Verification completed with JDK 21:

- Focused CSV/CLI/GUI regression suite: 59 tests passed.
- Full suite (`.\mvnw.cmd -B test`): 142 tests, 0 failures, 0 errors,
  2 skipped.
