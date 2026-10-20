# SFTP profile v2: bounded directory selection

Iteration 5 adds a second SFTP profile shape for deliveries whose final file name is
created by the producer. The validator selects exactly one regular file from one
remote directory and then applies the same stability, digest, content-validation, and
quarantine checks as the fixed-path profile.

## Example

```json
{
  "format": "sftp",
  "version": 2,
  "host": "sftp.example.test",
  "port": 22,
  "username": "quality-reader",
  "remoteDirectory": "/incoming",
  "filePattern": "^delivery-[0-9]{8}\\.json$",
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

## Selection contract

- `version` must be `2`.
- `remoteDirectory` is the only remote location used for discovery.
- `filePattern` is a Java regular expression matched against the basename returned by
  the SFTP server. It must not contain a path separator or NUL character.
- Directories and other non-regular entries are ignored.
- A listing is sorted by basename before the result is evaluated, so selection is
  deterministic and does not depend on server ordering.
- Exactly one matching regular file is required. A missing match is retried using
  `maxAttempts` and `retryDelay`; after the final attempt the result is `INVALID`.
- More than one matching regular file is an immediate `INVALID` result. The validator
  does not guess which delivery is newer or safer.
- A selected file is checked for stable metadata before it is downloaded. The
  downloaded temporary file is then bounded by `maxBytes`, verified against
  `expectedSha256`, delegated to the configured JSON/CSV/fixed-width validator, and
  moved to quarantine only after a failed content or hash validation.

## Deliberate boundaries

Version 2 selects one file only. It does not add password authentication, agent/config
discovery, multi-file correlation, success archiving, or a claim/lock protocol. Those
features require separate contracts because they change the security and delivery
semantics.
