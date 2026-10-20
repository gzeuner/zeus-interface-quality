# Local examples

[English](README.md) | [Deutsch](README.de.md) | [Main guide](../../README.md)

All commands below use Windows PowerShell from the repository root after
`.\mvnw.cmd -B package`. Keep customer CSVs and generated reports in the local
ignored directories; do not commit or upload them.

## Synthetic CSV example

[Fictional bicycle rentals](fictional-bicycle-rentals.csv) and the matching
[CSV validation profile](fictional-bicycle-rentals.csv-profile.json) are
entirely invented. The five records demonstrate a quoted
semicolon, ISO dates, decimal commas, numeric limits, an allowed bike type,
email checks, and a unique rental ID.

```powershell
java '-Djava.awt.headless=true' -jar .\target\zeus-interface-quality-0.1.0-SNAPSHOT.jar validate --profile .\docs\examples\fictional-bicycle-rentals.csv-profile.json --input .\docs\examples\fictional-bicycle-rentals.csv --local-only --report json
```

The expected result is `VALID` with an empty `findings` array and exit code
`0`. The test suite also checks that a duplicate rental ID is rejected.

### Deliberately invalid CSV

[fictional-bicycle-rentals-invalid.csv](fictional-bicycle-rentals-invalid.csv)
uses the **same** [JSON rules](fictional-bicycle-rentals.csv-profile.json). It
contains five findings across three data rows:

| CSV line | Field | Finding |
|---|---|---|
| 3 | `durationMinutes` | `0` is below the minimum of `1` |
| 4 | `startDate` | `2026-13-08` is not a valid date |
| 5 | `rentalId` | `BIKE-003` duplicates line 4 |
| 5 | `bikeType` | `scooter` is not an allowed value |
| 5 | `contactEmail` | `not-an-email` is not an email address |

Create the local report directory, then run:

```powershell
New-Item -ItemType Directory -Force .\reports | Out-Null
java '-Djava.awt.headless=true' -jar .\target\zeus-interface-quality-0.1.0-SNAPSHOT.jar validate --profile .\docs\examples\fictional-bicycle-rentals.csv-profile.json --input .\docs\examples\fictional-bicycle-rentals-invalid.csv --local-only --report json --report-file .\reports\fictional-bicycle-rentals-invalid.report.json
$LASTEXITCODE  # 1: validation completed with findings
```

The report is generated locally and contains `status: INVALID` plus five
entries in `findings`. In the GUI, select the invalid CSV as the data file,
leave the JSON rules selected, and click **Validate now**. Selecting a finding
shows its exact data path, rule path and message.

At startup, the GUI selects the valid sample CSV and its matching rules. The CSV contains the records;
`fictional-bicycle-rentals.csv-profile.json` supplies the validation rules.
`delivery.schema.json` is a separate JSON Schema for JSON input, while a JSON
file saved under `reports/` is an output report.

## Work with your own data

Place your CSV and matching JSON rules in the local `data/` directory. The CLI
runs without a window and writes the report only to the selected local path:

```powershell
New-Item -ItemType Directory -Force .\data, .\reports | Out-Null
java '-Djava.awt.headless=true' -jar .\target\zeus-interface-quality-0.1.0-SNAPSHOT.jar validate --profile .\data\rules.csv-profile.json --input .\data\input.csv --local-only --report json --report-file .\reports\input.report.json
```

Use `--profile` for a JSON Schema when the input is JSON. In the GUI, select
both files and click **Validate now**. **Configure CSV rules** can create an
editable CSV profile, and **Save result** exports the findings.

## Synthetic JSON example

[delivery.schema.json](delivery.schema.json) is a small local schema. A
self-contained validation run uses the checked-in test fixtures:

```powershell
java -jar .\target\zeus-interface-quality-0.1.0-SNAPSHOT.jar validate --profile .\src\test\resources\fixtures\delivery.schema.json --input .\src\test\resources\fixtures\valid-delivery.json --local-only --report json
```

The invalid CSV command above writes a JSON report under `reports/`.
Finding messages are not the automation API; use status, paths and rule keywords.

## SFTP templates

[sftp-profile.json](sftp-profile.json) is a template, not a working production
connection. Its hostname is a placeholder. The desktop GUI and `--local-only`
reject SFTP profiles.

Only for an explicitly authorized temporary test server, configure `host`,
`username`, `remotePath` and these files below the profile directory:

- `known_hosts`: trusted host key;
- `id_ed25519`: test account private key;
- `delivery.schema.json`: content contract.

For an encrypted key, set `SFTP_KEY_PASSPHRASE` locally. Never commit keys,
passphrases, host-specific configuration or private payloads. Quarantine is
opt-in and can move invalid files on that test server.
See the [SFTP contract](../contracts/sftp-profile-v1.md).

Safe local adapter tests, without an external SFTP endpoint:

```powershell
.\mvnw.cmd -o -B '-Dtest=SftpValidatorTest,SftpValidatorIntegrationTest' test
```

The integration test starts its own temporary local server; dependencies must
already be cached for `-o`. No production SFTP access or push is performed.

The existing [v2 directory template](sftp-directory-profile.json) selects one
matching regular file from a remote directory. The
[v3 correlated-delivery template](sftp-correlated-lifecycle-profile.json)
matches a primary and companion file by delivery ID and adds claim, recovery,
and archive steps. See the [v2 contract](../contracts/sftp-profile-v2.md) and
[v3 contract](../contracts/sftp-profile-v3.md). Both remain remote CLI options
and are blocked by `--local-only`.

For v2, configure `remoteDirectory` and `filePattern` and ensure the pattern
matches exactly one regular file. For v3, configure the endpoint, content
profile, and remote directories. The claim and archive directories must be
private to the consumer. A claimed delivery is resumed after interruption;
both files are archived only after validation succeeds. Optional
`quarantineRemoteDirectory` moves invalid claimed deliveries out of the
recovery area.
