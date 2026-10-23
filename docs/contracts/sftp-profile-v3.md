# SFTP profile contract v3: correlated delivery lifecycle

Version 3 builds on the bounded directory selector from version 2. It is for a
delivery that consists of one primary file and, optionally, one companion file
whose names carry the same delivery identity. When claim and archive directories
are configured, the validator makes the delivery lifecycle explicit:

```text
/incoming  --atomic rename-->  /claim  --valid-->  /archive
                                      \--invalid--> /quarantine
```

## Example

```json
{
  "format": "sftp",
  "version": 3,
  "host": "sftp.example.test",
  "port": 22,
  "username": "quality-reader",
  "remoteDirectory": "/incoming",
  "filePattern": "^delivery-[A-Z0-9-]+\\.json$",
  "companionPattern": "^delivery-[A-Z0-9-]+\\.meta\\.json$",
  "deliveryIdPattern": "^delivery-(?<deliveryId>[A-Z0-9-]+)(?:\\.meta)?\\.json$",
  "claimRemoteDirectory": "/claim",
  "archiveRemoteDirectory": "/archive",
  "knownHosts": "known_hosts",
  "privateKey": "id_ed25519",
  "contentFormat": "json",
  "contentProfile": "delivery.schema.json",
  "quarantineRemoteDirectory": "/quarantine",
  "stabilityChecks": 2,
  "stabilityDelaySeconds": 1,
  "maxAttempts": 2,
  "retryDelaySeconds": 1,
  "connectTimeoutSeconds": 10
}
```

## Selection and correlation

- `version` must be `3` and `format` must be `sftp`.
- `remoteDirectory` and `filePattern` select the primary file. Both are
  basename-oriented; path separators and NUL characters are rejected.
- `companionPattern` is optional. When present, exactly one regular primary and
  exactly one regular companion must form one complete delivery.
- `deliveryIdPattern` must contain the named capture group `deliveryId`. It is
  matched against both file names. Every selected file must produce the same,
  non-empty delivery ID.
- Missing files, orphan companions, duplicate files, and multiple complete
  deliveries are `INVALID`; the validator never chooses “the newest” file.
- A v3 profile without `companionPattern` retains the v2 one-file selection
  semantics. `deliveryIdPattern` may still be used to report a delivery ID.

## Claim and recovery

`claimRemoteDirectory` and `archiveRemoteDirectory` are optional, but they must
be configured together. The validator first checks the claim directory for an
already claimed delivery. If it finds a complete delivery, it resumes it and
does not re-select or re-claim the incoming files. If a process stopped between
the two file renames, the validator can complete a partial claim when the
remaining incoming file has the same delivery ID and the combined files form
exactly one complete delivery. It moves only the incoming file into the claim
directory. Otherwise, it atomically renames every selected file from
`remoteDirectory` into the claim directory before downloading anything.

This creates an idempotent recovery point for an interrupted run. A failed claim
is reported as an operational error and the validator attempts to roll back any
partially completed claim. Incomplete, conflicting, or ambiguous files across
the claim and incoming directories produce an `INVALID` selection without
downloading or moving files. The claim directory should be private to the
consumer; the producer must not write into it.

## Validation and lifecycle

Each selected file is checked for regular-file type and stable size/mtime, then
downloaded to a temporary local directory and validated with the configured
content profile. `sha256`, when present, applies to the primary file. A content
failure or digest mismatch quarantines every claimed file when
`quarantineRemoteDirectory` is configured. Without a quarantine directory, an
invalid claimed delivery remains in the claim area for investigation.

Only after every selected file is valid are claimed files renamed into the
archive directory. Archive and quarantine names receive a UTC timestamp, a short
collision-avoidance suffix, and `.validated` or `.invalid` respectively.

For a v3 run with a claim directory, the JSON report contains an optional
`metadata` object with the delivery ID, number of validated files, lifecycle
state, and final paths. The existing `status`, `valid`, and `findings` fields are
unchanged. The report does not contain payloads, private-key passphrases, or
credentials.

## Boundaries

The companion file uses the same `contentFormat` and `contentProfile` as the
primary file. Use [profile v4](sftp-profile-v4.md) when the companion requires a
different format or profile, or when it needs its own SHA-256 expectation.
Password authentication, SSH agent/config discovery, remote globbing, and
semantic cross-file business rules remain outside this contract.

## Command and exit semantics

```text
java -jar target/zeus-interface-quality-0.1.0-SNAPSHOT.jar validate \
  --input-format sftp \
  --schema path/to/sftp-lifecycle-profile.json \
  --report json
```

Do not pass `--input`. A valid, archived delivery returns `VALID` with exit code
`0`; a selection, hash, or content violation returns `INVALID` with exit code
`1`. Invalid profiles, connection failures, failed claims, failed downloads, or
failed final moves are operational errors with exit code `2`.
