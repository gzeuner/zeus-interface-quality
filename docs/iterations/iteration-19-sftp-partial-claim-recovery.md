# Iteration 19: SFTP partial claim recovery

Status: implemented.

When a process stops after moving one file of a correlated v3 delivery into the
claim directory, the validator now checks the incoming directory for the
matching partner. It resumes only when the combined files form exactly one
primary and one companion with the same delivery ID, claims the remaining
file, and then validates and archives both files normally.

Incomplete or conflicting claim and incoming contents return an invalid
selection without downloading or moving files. Complete claims continue to
resume without another claim move. This assumes the claim directory is private
to the consumer, as required by the v3 contract.

Regression coverage verifies both successful partial-claim completion and
refusal to combine an incomplete claim with a different delivery. The full
package build and cross-platform CI are the release-level verification.
