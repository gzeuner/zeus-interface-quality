# Iteration 15: batch CSV correction preview

Status: implemented.

`--dry-run-correction` now works with `--input-dir` for CSV profiles. Each
candidate is staged and revalidated independently. Eligible removals appear as
`correctionPreview` metadata in that file's batch result; input files and
correction sidecars remain untouched. The batch retains `INVALID` status and
exit `1` when any previewed input has findings. Preview cannot be combined
with SARIF output.

Verification: `ValidateCommandTest.batchCorrectionPreviewDoesNotModifyInputs`
checks preview metadata, source-byte preservation and the absence of sidecars.
