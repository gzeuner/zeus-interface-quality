# Validation report contract v1

[English](validation-report-v1.md) | [Deutsch](validation-report-v1.de.md)

The JSON report is intentionally small and independent of the validator library.
It is written to standard output when the CLI is called with `--report JSON`.
`--report-file PATH` additionally saves it as UTF-8; the GUI can export the same
contract. No additional JSON properties are required for GUI or CSV rules.

```json
{
  "status": "INVALID",
  "valid": false,
  "findings": [
    {
      "instancePath": "/quantity",
      "schemaPath": "/properties/quantity/type",
      "keyword": "type",
      "message": "..."
    }
  ]
}
```

The contract guarantees:

- `status` is `VALID` or `INVALID`;
- `valid` is consistent with `status`;
- `findings` is empty for a valid result;
- each finding contains JSON Pointer paths, a rule keyword (JSON Schema or
  adapter-specific), and a human-readable message;
- findings are sorted by instance path, schema path, keyword, and message;
- `metadata` is optional and appears only when the validator supplies it;
  SFTP v3 uses it for delivery identity, validated-file count, lifecycle state,
  and final paths, without including payloads or credentials;
- operational problems such as unreadable files or invalid schemas are not validation findings and return exit code `2`.

## Paths and additional rules

JSON Schema uses document/schema pointers such as the example above.
CSV uses `/rows/0/columnName` and `/columns/columnName/type`; row indexes exclude
header and marker records, and pointer-sensitive characters are escaped.
File-level findings can have an empty `instancePath`.
CSV rules include `header`, `columns`, `marker`, `required`, `type`, `pattern`,
length/numeric bounds, `minRecords`, `maxRecords` and `uniqueBy`.
Fixed-width uses `/records/0/fieldName`; HTTP and SFTP add their documented
request/response/content prefixes.

The CSV adapter's paths address its logical profile contract, not necessarily
the numeric indexes of the profile JSON's `columns` array. Use the
[CSV contract](csv-profile-v1.md) for rule semantics.

## Output and exit behavior

`0` means valid, `1` invalid, `2` operational/configuration/command error.
Operational diagnostics go to stderr; no validation report is emitted for
those failures. A failed report-file write also returns `2` without a report
on stdout. An older file may remain at the target: automation must evaluate
the exit code before treating a report as current.

The output directory must already exist. Report writes protect input/profile
files and replace other existing output files. The CLI does not prompt.
The GUI asks before replacing an existing report.

TEXT output starts with `VALID` or `INVALID`, followed by finding lines.
Finding messages are generally German; automation should use `status`,
`valid`, paths and `keyword`, not message wording or LED colors.
