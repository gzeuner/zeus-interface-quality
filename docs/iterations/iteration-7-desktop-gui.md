# Iteration 7: local desktop GUI and reproducible first run

Status: complete.

## Goal

Make the existing validator immediately useful to a reader who has cloned the
repository but does not yet have a SFTP server, HTTP endpoint, or custom rule
file. The first run should be local, visible, and reproducible.

## Delivered slice

- Added a local Swing desktop entry point through `gui` and `start-gui.cmd`.
- Added a bundled bicycle-rental sample from the test lab and matching CSV
  profile as the default selection.
- Added background validation with input/profile controls locked while a run
  is active.
- Added LEDs for idle, running, valid, invalid, and error states, plus separate
  file-existence indicators for input and profile.
- Added a findings table and detail view with data path, profile path, rule,
  message, and elapsed time.
- Added a bilingual German/US-English interface without changing the shared
  validator result or fixture data.
- Added a visual CSV profile editor that saves portable JSON profiles without
  overwriting the bundled example.
- Added UTF-8 JSON/text report export with input/profile protection and explicit
  confirmation before replacing another report.
- Kept HTTP and SFTP outside the local GUI workflow; remote adapters remain CLI
  features and can be blocked explicitly with `--local-only`.

## User-visible proof

The valid example returns `VALID` and exit code `0`. The deliberately invalid
CSV uses the same profile and produces five findings with exit code `1`. The
full Java-21 package build completes with 138 tests, 0 failures, 0 errors, and
2 expected Windows symlink skips.

## Deliberate boundaries

The GUI is a local desktop workflow, not a remote orchestration console. It
does not contact HTTP or SFTP endpoints. Finding messages remain validator
output and may still be German when the interface language is English; machine
automation should use the stable status, paths, and rule keywords.
