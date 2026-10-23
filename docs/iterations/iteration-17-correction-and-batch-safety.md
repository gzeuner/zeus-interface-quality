# Iteration 17: correction and batch safety

Status: implemented.

- Batch discovery excludes symbolic links so recursive CSV correction cannot
  modify a target outside the selected input directory.
- CSV correction uses an exclusive `.correction.lock` while it stages and
  commits its backup, rejected-row and audit files. Concurrent runs fail fast;
  rollback removes only artifacts owned by the current run.
- Correction preview reports `eligible: false` and `blockedBy` when an existing
  sidecar prevents application.

Verification: `ValidateCommandTest.batchSkipsFileSymlinksSoCorrectionCannotModifyOutsideInputRoot`
checks the batch boundary where the operating system supports file symlinks;
`CsvCorrectionServiceTest.previewExplainsWhenExistingArtifactsBlockCorrection`
checks preview reporting and preservation of the existing artifact;
`CsvCorrectionServiceTest.activeCorrectionLockBlocksPreviewAndIsPreserved`
checks active-lock handling.
