# Example profiles

The files in this directory are safe templates for the article examples.

## SFTP profile

[`sftp-profile.json`](sftp-profile.json) is intentionally not a ready-to-run
production connection. Before using it against a real endpoint, create or
provide these files next to the profile without committing secrets:

- `known_hosts`: the trusted host-key entry for the endpoint;
- `id_ed25519`: the private key used by the CI or local test account;
- `delivery.schema.json`: the local content contract. A minimal example is
  included in this directory and can be replaced with the real contract.

Then replace `host`, `username`, and `remotePath` with the environment-specific
values. If the key is protected, set `SFTP_KEY_PASSPHRASE` in the process
environment. Do not commit private keys, passphrases, or environment-specific
host files.

The profile cannot succeed against `sftp.example.org`; that hostname is a
documentation placeholder. The guaranteed offline path is:

```text
mvnw.cmd -B -Dtest=SftpValidatorTest test
```

The tests use a deterministic transport seam and do not contact an external
SFTP server. A live SFTP run is an environment-specific integration check.
