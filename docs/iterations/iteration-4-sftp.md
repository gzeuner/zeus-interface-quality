# Iteration 4 — SFTP delivery validation

Status: complete.

## Goal

Extend the shared validation result contract to a common integration case:
one file arrives through SFTP, may still be changing, and must be checked
before downstream processing sees it. The adapter should make transport,
integrity, content, and quarantine outcomes distinguishable in CI.

## Delivered slice

- version-one SFTP profile with strict local path resolution;
- JSch transport using strict `known_hosts` verification and private-key
  authentication;
- bounded connect, stability, and retry settings;
- stable remote-file check using regular-file type, byte size, and modification
  time;
- temporary local download followed by optional SHA-256 verification;
- delegation to the existing JSON Schema, CSV, and fixed-width validators;
- `/content/...` prefixes for delegated findings and `/remote/hash` for a hash
  mismatch;
- optional remote quarantine for invalid content or hash results;
- deterministic exit behavior: `0` valid, `1` invalid, `2` operational error;
- an embedded Apache MINA SSHD test server exercising the production JSch
  transport with strict host-key verification and private-key authentication.

The implementation is deliberately one-file-per-run. It does not poll a
directory, choose among multiple arrivals, archive valid files, or implement
password authentication.

## Safety boundaries

Host-key verification is mandatory. The profile cannot point `known_hosts`,
the private key, or the content profile outside its own directory, including
through a symlink. The remote path is absolute POSIX syntax without traversal
segments. Passphrases are read only from the named environment variable.

The downloaded bytes live in a temporary directory and are removed after the
run on a best-effort basis. Reports do not contain the payload or credentials.
Quarantine is opt-in and applies only after a contract or hash failure; a
transport failure is not turned into a remote move.

## Verification

The SFTP unit tests use a transport seam, so they do not contact a real
server. They cover valid stable JSON, prefixed content findings, hash mismatch,
remote quarantine, instability retry, transient connection retry, unsafe
remote paths, and temporary-download cleanup.

The integration test starts an embedded Apache MINA SSHD SFTP server and runs
the production JSch transport against it, including host-key verification,
private-key login, download, and remote quarantine.

For readers following the series, this is the first guaranteed green command;
it needs no SFTP account:

```text
mvnw.cmd -B -Dtest=SftpValidatorTest test
mvnw.cmd -B -Dtest=SftpValidatorIntegrationTest test
mvnw.cmd -B test
```

The profile under `docs/examples/sftp-profile.json` is a real-profile template,
not a local demo. It only becomes runnable after the endpoint, trusted
`known_hosts`, private key, and content profile have been supplied as described
in `docs/examples/README.md`.

The full suite currently passes with 76 tests and two pre-existing skipped
tests. A live-server integration test is intentionally left for a future
environment-specific test profile with managed credentials and host keys.
