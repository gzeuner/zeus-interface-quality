# Iteration 18: crash recovery and release readiness

Status: implemented.

- CSV correction now holds an operating-system file lock. Process exit releases
  the lock automatically, so an unlocked lock file left by a crash is reusable.
  The lock marker is kept to avoid a delete/recreate race between processes.
- CI runs the Maven tests, GUI launcher help checks and packaged CLI checks on
  Ubuntu and Windows. It repeats the package build and compares JAR bytes before
  attaching the shaded JAR and its SHA-256 checksum.
- Maven archive timestamps are normalized. The bilingual release checklist
  defines candidate checks while leaving version tagging and publishing as a
  deliberate release action.

Verification: `CsvCorrectionServiceTest.activeCorrectionLockBlocksPreviewAndIsPreserved`
checks concurrent access, `CsvCorrectionServiceTest.reusesAnUnlockedLockFileAfterAnInterruptedRun`
checks crash recovery, and the cross-platform CI matrix runs the full Maven
package build and CLI smoke tests on both operating systems.
