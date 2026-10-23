# Iteration 20: SFTP v4 mixed-format delivery

Status: implemented.

SFTP profile v4 extends the correlated v3 delivery with three role-specific
capabilities:

1. The required companion file has its own `contentFormat` and
   `contentProfile`, supporting JSON, CSV, and fixed-width independently of the
   primary file.
2. Optional `companionSha256` verifies the companion's downloaded bytes;
   `sha256` continues to verify the primary.
3. Existing v1, v2, and v3 profiles keep their previous behavior, while v4
   requires a companion and delivery identity pattern.

Hash and content findings continue through the existing report format. A
companion hash mismatch uses `/remote/companion/hash` and
`/companionSha256`; configured quarantine moves the whole claimed delivery.

The English and German v4 contracts describe profile fields and exit behavior.
Focused regression tests cover a JSON primary with a CSV companion and
quarantine after a companion hash mismatch. Full package and cross-platform CI
are the release-level verification.
