# ADR 0007: Select one SFTP delivery from a bounded directory listing

## Context

The fixed-path SFTP contract is safe when the producer and consumer agree on the
complete remote path. Many real deliveries instead use a date or run identifier in
the file name. Selecting a file by “latest” or by server order would make a test run
non-deterministic and could validate the wrong delivery.

## Decision

Add SFTP profile v2 with an explicit remote directory and basename regular expression.
List only that directory, ignore non-regular entries, sort matching basenames, and
require exactly one match. Retry only the empty case with the already bounded polling
settings. Treat an ambiguous listing as `INVALID` immediately. Once one file is
selected, reuse the existing stable-snapshot, size, SHA-256, content-validation, and
quarantine pipeline.

## Consequences

The selector is deterministic, bounded, and easy to exercise through both a fake
transport and the real JSch transport. It deliberately does not guess between
multiple deliveries and does not silently expand into a batch-processing protocol.
The profile version makes the new directory semantics explicit while preserving the
fixed-path v1 contract.
