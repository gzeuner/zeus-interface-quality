# Iteration 5: bounded SFTP directory selection

Status: complete.

## Goal

Support a common SFTP delivery shape where the producer writes a generated file name
into a known directory, while keeping the validator deterministic and fail-closed.

## Delivered slice

- Added SFTP profile v2 with `remoteDirectory` and `filePattern`.
- Added directory listing to the transport boundary and the JSch implementation.
- Ignore directories and non-matching entries; sort matching basenames deterministically.
- Retry an empty selection with the existing bounded attempt and delay settings.
- Return `INVALID` for an empty selection after the final attempt or for an ambiguous
  selection with more than one matching regular file.
- Reuse the Iteration 4 path for stable metadata, bounded download, SHA-256,
  content validation, and quarantine after one file has been selected.
- Added unit coverage and a real JSch integration test using an embedded SFTP server.

## Explicit non-goals

There is no multi-file batch, cross-file correlation, success archive, password
authentication, or implicit “newest file wins” behavior in this iteration.

## Verification

```powershell
./mvnw.cmd -B clean package
```

The focused tests are also useful while changing the SFTP boundary:

```powershell
./mvnw.cmd -B -Dtest=SftpValidatorTest,SftpValidatorIntegrationTest test
```

The runnable profile is available at
`docs/examples/sftp-directory-profile.json`; the full contract is documented in
`docs/contracts/sftp-profile-v2.md`.
