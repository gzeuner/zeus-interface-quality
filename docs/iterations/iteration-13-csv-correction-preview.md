# Iteration 13: CSV correction preview

Status: implemented.

`--dry-run-correction` stages eligible row removals in a temporary CSV and
revalidates the staged content. It reports preview metadata only when the
staged CSV passes. The original input and correction sidecars are not changed
or created. The command retains the original findings and exit code `1`, so
automation still sees that the supplied file is invalid. This option applies
to single-file CSV validation and supports TEXT and JSON reports.

Verification: `CsvCorrectionServiceTest` checks eligible-row reporting,
preservation of the input bytes, and absence of correction sidecars.
