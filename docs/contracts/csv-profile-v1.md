# CSV profile v1: GUI and CLI

[English](csv-profile-v1.md) | [Deutsch](csv-profile-v1.de.md)

The same UTF-8 JSON profile is consumed by the desktop GUI, the headless CLI,
and the CSV content adapter used by other transports. No GUI-only rule state
is required. Existing version-1 profiles retain their behavior.

## Example

```json
{
  "format": "csv",
  "version": 1,
  "encoding": "windows-1252",
  "delimiter": ";",
  "decimalSeparator": ",",
  "header": ["receipt", "line", "amount"],
  "envelope": {"begin": "BOF", "end": "EOF"},
  "minRecords": 1,
  "maxRecords": 100000,
  "uniqueBy": ["receipt", "line"],
  "fileNamePattern": "^orders-[0-9]{8}\\.csv$",
  "correction": { "enabled": false },
  "columns": [
    {"name": "receipt", "enabled": true, "type": "integer", "required": true},
    {"name": "line", "enabled": true, "type": "integer", "required": true},
    {"name": "amount", "enabled": true, "type": "decimal", "required": true,
     "minimum": -9999999.99, "maximum": 9999999.99,
     "pattern": "^-?([0-9]{1,7},[0-9]{2}|,[0-9]{2})$"}
  ]
}
```

## File rules

| Property | Meaning |
|---|---|
| `format`, `version` | Required: `csv`, integer `1`. |
| `encoding` | Required Java charset name, e.g. `UTF-8` or `windows-1252`. Decoding errors are operational errors. |
| `delimiter` | Required single character other than quote, CR or LF. Quoting is handled by Commons CSV. |
| `decimalSeparator` | Optional `.` (default) or `,`. An explicitly set separator must differ from the delimiter. Bounds inside the JSON profile always use JSON numbers with a decimal point. |
| `header` | Required non-empty array of unique names, in exact CSV order. |
| `columns` | Required one definition per header name, in the same order. |
| `envelope` | Optional object with distinct non-empty `begin` and `end` marker strings. Begin record precedes the header; end record is the last CSV record. Each marker record must have the full column count and empty remaining fields. |
| `minRecords`, `maxRecords` | Optional non-negative integers, inclusive. `minRecords` must not exceed `maxRecords`. Count data records only, excluding header and envelope. A wrong-width data record still counts. These are validation rules, not resource limits or truncation settings. |
| `uniqueBy` | Optional non-empty array of distinct existing column names, forming one composite key. Omit to disable. |
| `fileNamePattern` | Optional Java regular expression matched against the complete input file basename, case-sensitively. Omit to disable. An invalid expression is a profile error. |
| `correction` | Optional object `{ "enabled": boolean }`, default `false`. When enabled, local CSV validation may remove invalid data records. CLI `--dry-run-correction` previews eligible removals after staging validation without changing files. The GUI asks before changing the file; CLI follows the profile without prompting. The original bytes are backed up as `.original.bak`, removed rows plus the header go to `.rejected.csv`, and `.correction.json` records row numbers and findings. Existing artifacts are never overwritten. Only row-scoped findings are eligible; the staged result must pass validation before replacement. Rewriting preserves decoded field values, charset and delimiter; quoting and line endings may be normalized. |
| `$id`, `$schema` | Optional metadata; not retrieved from the network by the CSV adapter. |

Header-only files, or begin/header/end files without data, remain valid by default.
Set `minRecords: 1` only when a particular test requires data. Record limits and
uniqueness must be enabled explicitly in the profile. A missing header is still
invalid.

`uniqueBy` compares **exact decoded strings**, including leading zeros,
whitespace and case; no trimming or numeric coercion occurs. Empty key fields
participate like other strings; configure `required: true` separately to
disallow them. A duplicate is reported for every repetition after the first,
with the first record number and the repeated data-row path. Wrong-width
records cannot form a key and are reported through `columns`.
The implementation retains one entry per distinct composite key, so enabling
uniqueness uses memory proportional to the distinct key count.

## Column rules

| Property | Meaning |
|---|---|
| `name` | Required, must match the corresponding header name. |
| `type` | Required: `string`, `integer`, `decimal`, `email`, `date`, `time`. |
| `enabled` | Optional boolean, default `true`. `false` skips all value rules for this column, including type and required. It does not remove the CSV column, bypass file/header rules or disable an explicit `uniqueBy`. The column configuration is still checked for errors. |
| `required` | Optional boolean, default `false`. Rejects the empty string. Whitespace is not an empty value. |
| `minLength`, `maxLength` | Optional non-negative inclusive Unicode code-point lengths, for `string` only. |
| `minimum`, `maximum` | Optional inclusive JSON numeric bounds, for `integer` or `decimal` only. |
| `pattern` | Optional Java regular expression, matching the entire non-empty original field. Can also constrain decimal scale or allowed values. |

Optional empty fields skip all value rules. `date` checks calendar validity
using ISO local dates; `time` accepts strict `HH:mm`, not seconds or `24:00`.
The decimal-comma mode accepts e.g. `-12,30`, `,30`, `-,30` or `12`;
it rejects decimal points, grouping, whitespace and scientific notation.
The existing decimal-point mode retains Java `BigDecimal` syntax.
Patterns apply to the unnormalized CSV text, even when numeric comparison
uses the configured separator.

Unknown properties, invalid names/types, non-boolean switches, invalid regex
and conflicting bounds are configuration errors, not ignored options.

## Results and automation

The [v1 report contract](validation-report-v1.md) remains unchanged.
Additional finding keywords are `minRecords`, `maxRecords`, and `uniqueBy`;
file-level paths are `/minRecords`, `/maxRecords`, and `/uniqueBy`.
`fileNamePattern` findings use the empty instance path, `/fileNamePattern`, and
keyword `fileNamePattern`; the report contract does not include local file
paths. The same CSV profile checks a local file's basename in the GUI and CLI;
the SFTP validator checks the original remote basename, not its temporary
download name.
Column findings continue to use `/rows/<zero-based-data-row>/<column-name>`.
Malformed CSV syntax and invalid profiles return operational error exit `2`;
valid input returns `0`, rule violations return `1`.

The GUI profile export refuses to overwrite existing files. Edit the JSON with
any editor or select it in the GUI and click **CSV-Regeln konfigurieren**.
The GUI allows editing the file format, markers, row limits, filename pattern, composite key,
column names/definitions (with add/remove), types, enable/required flags, lengths,
bounds and regex. It validates the configuration before saving and switching
to that profile. Save confirmation is required before replacing an existing
profile, and the data input cannot be replaced by the editor.

For a duplicate-key test, add the following explicitly:

```json
"uniqueBy": ["QUITTUNGSCODE", "NR_LISTENZEILE"]
```

Selecting a different input/profile invalidates the GUI's previous result.
The status LED supplements text (not color alone): grey means no current
result, amber means running, green means valid, red means invalid/operational
error. Small input/profile LEDs indicate file existence only, not validated
content. JSON Schema and fixed-width profiles are still supported by both
interfaces; the visual rule editor is specifically for CSV profiles.
