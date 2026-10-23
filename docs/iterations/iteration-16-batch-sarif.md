# Iteration 16: SARIF reports for batches

Status: implemented.

Directory validation accepts `--report sarif` and emits one SARIF 2.1.0 run.
Findings retain their rule IDs and ZEUS paths, and each result points to the
absolute URI of its input file. Per-file operational errors are represented by
the `zeus.operationalError` rule. Run properties carry the aggregate `VALID`,
`INVALID` or `ERROR` status. JSON and text batch reports remain supported.

Verification: `ValidateCommandTest.sarifBatchReportsFindingsWithPerFileLocations`
checks a finding's artifact URI and aggregate status.
