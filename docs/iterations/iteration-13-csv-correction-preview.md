# Iteration 13: CSV correction preview

Status: implemented.

`--dry-run-correction` stages eligible row removals in a temporary CSV and
revalidates the staged content. It reports preview metadata only when the
staged CSV passes. The original input and correction sidecars are not changed
or created. The command retains the original findings and exit code `1`, so
automation still sees that the supplied file is invalid. This iteration
introduced it for single-file CSV validation with TEXT and JSON reports.
[Iteration 15](iteration-15-batch-correction-preview.md) extends it to batch
CSV validation.

Verification: `CsvCorrectionServiceTest` checks eligible-row reporting,
preservation of the input bytes, and absence of correction sidecars.
