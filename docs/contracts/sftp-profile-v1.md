# SFTP profile contract v1

Iteration 4 validates exactly one remote SFTP file. The profile is a local
JSON document and is selected with `--schema` or `--profile`:

```json
{
  "format": "sftp",
  "version": 1,
  "host": "sftp.example.org",
  "port": 22,
  "username": "quality-check",
  "remotePath": "/inbox/delivery.json",
  "knownHosts": "known_hosts",
  "privateKey": "id_ed25519",
  "privateKeyPassphraseEnv": "SFTP_KEY_PASSPHRASE",
  "contentFormat": "json",
  "contentProfile": "delivery.schema.json",
  "quarantineRemoteDirectory": "/quarantine",
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "stabilityChecks": 2,
  "stabilityDelaySeconds": 1,
  "maxAttempts": 2,
  "retryDelaySeconds": 1,
  "connectTimeoutSeconds": 10
}
```

## Fields

- `format` is `sftp`; `version` is `1`.
- `host`, `port`, and `username` identify the SFTP endpoint. The default port
  is `22`.
- `remotePath` is an absolute POSIX path to one regular remote file. `.`, `..`,
  backslashes, NUL characters, and trailing slashes are rejected.
- `knownHosts` and `privateKey` are required relative paths. Both files must
  resolve to regular files below the profile directory. Symlinks that resolve
  outside that directory are rejected.
- `privateKeyPassphraseEnv` is optional. If present, the passphrase is read
  from that environment variable and never placed in the report.
- `contentFormat` is one of `json`, `csv`, or `fixed-width`.
- `contentProfile` is a required relative local profile for that existing
  validator. JSON uses a JSON Schema; CSV and fixed-width use their adapter
  profiles.
- `sha256` is optional and must be a 64-character hexadecimal digest. It is
  compared with the downloaded bytes before content validation.
- `stabilityChecks` controls the number of remote metadata observations. The
  adapter compares file type, size, and modification time. Defaults are `2`
  observations, `1` second between observations, `2` attempts, `1` second
  between attempts, and a `10` second connection timeout. The accepted ranges
  are deliberately bounded by the implementation.
- `quarantineRemoteDirectory` is optional. After an invalid content or hash
  result, the adapter renames the remote file into that directory with a UTC
  timestamp and an `.invalid` suffix. It does not move valid files and does
  not quarantine operational failures.

The profile intentionally does not support password authentication, an
OpenSSH config or agent, remote globbing, directory polling, multi-file
selection, or an automatic success archive. Those workflows need a separate
contract so that selection and side effects stay explicit.

## Command and result semantics

```text
java -jar target/zeus-interface-quality-0.1.0-SNAPSHOT.jar validate \
  --input-format sftp \
  --schema path/to/sftp-profile.json \
  --report json
```

Do not pass `--input`: the profile supplies the remote file. A valid remote
file and valid content produce `VALID` with exit code `0`. A hash mismatch or
content contract violation produces `INVALID` with exit code `1`; content
findings are prefixed with `/content/`, while a hash mismatch is reported at
`/remote/hash`. An invalid profile, connection problem, missing remote file,
unstable file after bounded retries, failed download, or failed quarantine is
an operational error with exit code `2`.

The download is written below a temporary local directory and removed after
the run. The report contains findings, paths, and keywords, but not the
downloaded payload, private-key passphrase, or response credentials.
