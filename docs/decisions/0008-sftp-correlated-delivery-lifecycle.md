# ADR 0008: claim and archive a correlated SFTP delivery

## Context

Directory selection is sufficient for one generated file, but a real delivery
often consists of a primary payload and a companion file. A validator that reads
files directly from the producer's directory can see half-written deliveries,
process a delivery twice after a restart, or leave the meaning of “successful”
ambiguous. A new contract must make those side effects explicit and recoverable.

## Decision

Add SFTP profile v3. It keeps the v2 basename selector and adds an optional
companion pattern plus a required named `deliveryId` capture when correlation is
used. The validator accepts exactly one complete delivery. When claim and archive
directories are configured, it first resumes a matching delivery already in the
claim area; otherwise it atomically renames all selected files into that area,
validates the stable snapshots, and then moves all files either to the archive or
to quarantine.

The shared result gains optional metadata. Existing adapters and v1/v2 SFTP runs
continue to serialize the frozen `status`, `valid`, and `findings` shape because
empty metadata is omitted.

## Consequences

The producer-facing directory remains a short-lived discovery area and the claim
rename becomes the consumer's ownership boundary. Interrupted work can be
replayed from the claim area without selecting a different delivery. The contract
is intentionally narrow: both files share one content profile, and semantic
cross-file rules are deferred until a future version can model them explicitly.
