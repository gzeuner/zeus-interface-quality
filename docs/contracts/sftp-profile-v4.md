# SFTP profile contract v4: role-specific delivery validation

Version 4 extends the correlated delivery lifecycle from v3. A delivery has
exactly one primary file and one companion file, each checked with its own
content format and profile. The existing claim, archive, quarantine, stable
download, and delivery ID behavior applies unchanged.

## Example

```json
{
  "format": "sftp",
  "version": 4,
  "host": "sftp.example.test",
  "username": "quality-reader",
  "remoteDirectory": "/incoming",
  "filePattern": "^delivery-[A-Z0-9-]+\\.json$",
  "companionPattern": "^delivery-[A-Z0-9-]+\\.meta\\.csv$",
  "deliveryIdPattern": "^delivery-(?<deliveryId>[A-Z0-9-]+)(?:\\.meta)?\\.(?:json|csv)$",
  "claimRemoteDirectory": "/claim",
  "archiveRemoteDirectory": "/archive",
  "quarantineRemoteDirectory": "/quarantine",
  "knownHosts": "known_hosts",
  "privateKey": "id_ed25519",
  "contentFormat": "json",
  "contentProfile": "delivery.schema.json",
  "sha256": "<64-character primary SHA-256>",
  "companionContentFormat": "csv",
  "companionContentProfile": "delivery-metadata.csv-profile.json",
  "companionSha256": "<64-character companion SHA-256>",
  "stabilityChecks": 2,
  "stabilityDelaySeconds": 1,
  "maxAttempts": 2,
  "retryDelaySeconds": 1,
  "connectTimeoutSeconds": 10
}
```

Replace the hash placeholders with actual hexadecimal values or omit either
property when no digest is expected.

## Selection and validation

- `version` must be `4`; `format` must be `sftp`.
- `filePattern`, `companionPattern`, and a `deliveryIdPattern` with the named
  `deliveryId` group are required. Exactly one regular primary and one regular
  companion with the same non-empty ID must be selected.
- `contentFormat` and `contentProfile` validate the primary file.
  `companionContentFormat` and `companionContentProfile` validate the companion.
  Supported formats are `json`, `csv`, and `fixed-width`. Both profile paths
  must resolve to regular files inside the SFTP profile directory.
- `sha256` applies to the primary file. Optional `companionSha256` applies to
  the companion. Each digest is checked against the downloaded bytes before
  content validation. A mismatch is an `INVALID` finding; when configured, the
  full claimed delivery is quarantined.
- A v4 profile requires a companion. Profiles v1, v2, and v3 retain their
  existing selection and validation behavior.

## Lifecycle and reports

When `claimRemoteDirectory` and `archiveRemoteDirectory` are configured, the v3
claim, interrupted-claim recovery, archive, and quarantine rules apply. The
claim and archive directories must be configured together. The claim directory
must remain private to the consumer.

The JSON report retains the existing status and finding fields. A claimed run
also reports delivery identity, validated file count, lifecycle, and final
paths. A companion digest mismatch uses instance path `/remote/companion/hash`
and schema path `/companionSha256`.

## Command and exit semantics

```text
java -jar target/zeus-interface-quality-0.1.0-SNAPSHOT.jar validate \
  --input-format sftp --schema path/to/sftp-profile-v4.json --report json
```

Do not pass `--input`. A valid delivery returns exit code `0`, an invalid
selection, digest, or content returns `1`, and invalid profiles or transport
failures return `2`.
