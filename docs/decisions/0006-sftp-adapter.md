# ADR 0006: bounded SFTP adapter with explicit trust and side effects

Status: accepted

## Context

The project already validates local files and HTTP exchanges. Many production
interfaces deliver files through SFTP, but treating a remote file as if it were
already a local snapshot would hide partial uploads, host-key mistakes, and
transport failures. The next vertical slice needs to preserve the existing
`VALID`/`INVALID`/operational distinction without introducing a large workflow
engine.

## Decision

Implement a one-remote-file SFTP adapter with these rules. Directory selection
is intentionally reserved for the version-two contract in Iteration 5:

1. Use the maintained JSch library as a small transport dependency.
2. Require an explicit `known_hosts` file and enable strict host-key checking.
3. Support private-key authentication only; an optional passphrase comes from
   an environment variable.
4. Observe remote type, size, and modification time until the file is stable,
   with bounded retries and delays.
5. Download to a temporary local file, optionally verify SHA-256, and delegate
   content validation to the existing adapters.
6. Make remote quarantine opt-in and perform it only for an invalid content or
   hash result.
7. Keep the transport behind a small package-local port so unit tests can use
   deterministic fakes, and add a separate embedded Apache MINA SSHD test to
   exercise the production JSch transport without a live SFTP account.

## Consequences

The adapter is useful in CI without requiring an external SFTP server in every
test run, and its security assumptions are visible in the profile. The
embedded integration test still exercises the real transport path locally. Version one
does not cover password auth, OpenSSH agent/config discovery, directory polling,
multi-file correlation, or success archiving. Directory selection is added by the
separate version-two contract; multi-file correlation and success archiving remain
out of scope until a later, explicit contract.
