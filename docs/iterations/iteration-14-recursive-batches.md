# Iteration 14: recursive local batches

Status: implemented.

`validate --input-dir DIR --recursive` includes matching files at any depth.
The glob still applies to each filename. Every batch result now carries
`relativePath` from the input root alongside the original basename field
`file`; the relative path is slash-normalized and results sort by it. Default
discovery remains one directory level.

Verification: `ValidateCommandTest.recursiveBatchIncludesAndReportsRelativePaths`
checks nested discovery and deterministic ordering.
