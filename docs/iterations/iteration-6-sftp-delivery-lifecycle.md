# Iteration 6: correlated SFTP delivery lifecycle

Status: complete.

## Goal

Turn the single-file SFTP directory selector into a small, explicit delivery
protocol. A consumer must be able to recognize one complete multi-file delivery,
claim it safely, resume it after an interruption, and distinguish successful
archive from quarantine without changing the v1/v2 contracts.

## Delivered slice

- Added SFTP profile v3 while keeping v1 exact-path and v2 single-file semantics.
- Added a named `deliveryId` correlation pattern for one primary plus one optional
  companion file.
- Reject incomplete, duplicate, orphaned, or ambiguous deliveries instead of
  guessing.
- Added atomic remote claim moves and recovery from an already claimed delivery.
- Added successful archive moves and retained quarantine semantics for invalid
  deliveries.
- Added optional operational metadata to the shared JSON report without changing
  the existing report fields for older adapters.
- Added unit coverage and an embedded Apache MINA SSHD integration test for the
  real JSch transport.
- Corrected the checked-in v2 example so its property names match the actual
  implementation and added a runnable v3 template.

## Deliberate boundaries

The primary and companion file currently use the same content format and profile.
Mixed-format payloads, password authentication, SSH agent/config discovery, and
semantic cross-file business rules remain future work. The new contract never
selects by server order or “newest file wins”.

## Verification

```powershell
./mvnw.cmd -B clean package
```

Focused SFTP tests:

```powershell
./mvnw.cmd -B -Dtest=SftpValidatorTest,SftpValidatorIntegrationTest test
```

The contracts are documented in
[`docs/contracts/sftp-profile-v3.md`](../contracts/sftp-profile-v3.md), and the
configuration template is
[`docs/examples/sftp-correlated-lifecycle-profile.json`](../examples/sftp-correlated-lifecycle-profile.json).
