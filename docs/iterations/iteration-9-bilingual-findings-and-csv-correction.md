# Iteration 9: bilingual findings and traceable CSV correction

Status: implemented. Build verification recorded below; tests were not run.

## Scope

- Central DE/EN message catalog keyed by stable rule identifiers.
- CLI `--language de|en`; the GUI passes its selected language to local CSV,
  fixed-width and JSON Schema validators. SFTP passes it to delegated content
  validation; HTTP and transport-level diagnostic text remains adapter-owned.
- Optional CSV profile setting `correction.enabled`, defaulting to `false`.
- First correction strategy removes whole invalid CSV data records only.

## Correction transaction

The GUI asks before starting an enabled correction. The CLI follows the profile.
Only findings whose instance paths point to CSV data rows can trigger the
strategy. Header, envelope, filename, min/max count and other global findings
are left for manual handling. The input is parsed and rewritten into a sibling
temporary file; that file must pass the same profile before commit.

On success the original bytes are kept as `<input>.original.bak`, rejected
records (with the header) are saved as `<input>.rejected.csv`, and
`<input>.correction.json` records removed row numbers and the corresponding
findings. These names are never overwritten. If a sidecar already exists, the
correction is refused. If staging or revalidation fails, the original remains
unchanged. Rewriting preserves decoded record values, the configured charset
and delimiter; it may normalize CSV quoting and line endings.

## Extension point

`ValidationMessages` maps stable keys to DE/EN templates. New rules add a key
and both translations; findings retain their keyword and paths. Correction is
isolated in `CsvCorrectionService`, so future format-specific strategies can
be selected without embedding mutation into scalar validators.

## Verification

- Offline JDK 21 Maven package build succeeded with tests skipped.
- Maven tests were not run in this iteration.
