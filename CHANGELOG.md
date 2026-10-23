# Changelog / Änderungsprotokoll

[English guide](README.md) | [Deutsche Anleitung](README.de.md)

This log records user-visible changes. / Dieses Protokoll beschreibt
anwenderrelevante Änderungen.

## Unreleased / Unveröffentlicht

### In this update / In diesem Update

- Added valid and invalid JSON delivery samples for the checked-in JSON Schema,
  with matching CLI walkthroughs in both language guides.
- `JsonExampleTest` checks the valid sample and the three expected schema
  findings in the invalid sample.
- Full Maven package build: 161 tests, 0 failures, 0 errors, 3 skipped
  (2026-10-23); both JSON CLI examples return their documented exit codes.
- Details: [Iteration 21](docs/iterations/iteration-21-json-schema-showcase.md).

- SFTP profile v4 validates a required companion file with its own JSON, CSV, or
  fixed-width profile, and supports a separate companion SHA-256 expectation.
- Focused SFTP verification: 17 tests, 0 failures, 0 errors, 0 skipped.
- Details: [Iteration 20](docs/iterations/iteration-20-sftp-v4-mixed-format-delivery.md).

- SFTP v3 can finish a delivery claim interrupted between its primary and
  companion renames when the matching file remains in the incoming directory.
  Ambiguous or conflicting deliveries are left untouched.
- Focused SFTP verification: 14 tests, 0 failures, 0 errors, 0 skipped.
- Details: [Iteration 19](docs/iterations/iteration-19-sftp-partial-claim-recovery.md).

- Full Maven package build: 159 tests, 0 failures, 0 errors, 3 skipped
  (2026-10-23). Windows skipped the file-symlink test because symlink creation
  is unavailable; CI runs that test on Linux.
- Correction sidecar conflicts, active locks and recovery from an unlocked
  crash-left lock file have regression coverage. Two clean Windows package
  builds produced the same SHA-256 checksum. The packaged GUI launcher help,
  version, bundled dependencies, LICENSE and NOTICE were checked locally.

### Correction and batch safety

- Batch discovery skips symbolic links, preventing recursive correction from
  following linked files outside the selected input directory.
- CSV correction uses an OS file lock while staging and committing its backup,
  rejected-row and audit files. OS lock release allows a new run to reuse the
  lock file after process interruption.
- Correction preview identifies existing sidecars in `blockedBy` and marks the
  proposed correction ineligible.
- Details: [Iteration 17](docs/iterations/iteration-17-correction-and-batch-safety.md).

### Crash recovery and release readiness

- OS file locks make correction runs exclusive and become reusable after a
  process exits, including a crash.
- CI is configured for Linux and Windows package, CLI smoke and repeat-build
  checks; successful runs attach the JAR and SHA-256 checksum.
- Added a bilingual release checklist. No version tag or release is created.
- Details: [Iteration 18](docs/iterations/iteration-18-crash-safe-correction-and-release-readiness.md).

### Recursive batch, preview, and SARIF

- Added optional recursive batch discovery with stable relative paths.
- Added read-only CSV correction preview for batches.
- Added one-run SARIF 2.1.0 output for batch findings and per-file operational errors.
- Details: [Iterations 14–16](docs/iterations/iteration-14-recursive-batches.md).

### Local batch validation

- Added deterministic `--input-dir`/`--glob` validation for local files, with
  per-file continuation, aggregate JSON reports and stable exit codes.
- Contract: [Batch report v1](docs/contracts/validation-batch-v1.md);
  implementation notes: [Iteration 11](docs/iterations/iteration-11-batch-local-validation.md).

### SARIF output

- Added single-file SARIF 2.1.0 reports for CI code-scanning integrations.
- Details: [Iteration 12](docs/iterations/iteration-12-sarif-reports.md).

### CSV correction preview

- Added `--dry-run-correction` to validate proposed removals in a temporary
  file without modifying input or creating correction artifacts.
- Details: [Iteration 13](docs/iterations/iteration-13-csv-correction-preview.md).

### Bilingual findings and traceable CSV correction

- Added a central DE/EN validation message catalog with stable keys and the CLI
  option `--language de|en`.
- Added an optional CSV profile correction setting, disabled by default. When
  enabled, row-scoped invalid records are removed only after the staged file
  passes validation. The GUI asks before modifying the input; the CLI follows
  the profile.
- Successful correction keeps the original as `.original.bak`, writes removed
  records to `.rejected.csv` and records findings and row references in
  `.correction.json`. Existing artifacts are never overwritten.
- Implementation notes: [Iteration 9](docs/iterations/iteration-9-bilingual-findings-and-csv-correction.md).

Documentation and verification status: **2026-10-23**.
Current Maven version: **0.1.0-SNAPSHOT**. This entry documents the working
version and does not announce a release.

### English

#### Verified in this update

- Full Maven suite: 156 tests, 0 failures, 0 errors, 3 skipped (2026-10-23).
- Batch ordering/aggregation and per-file error continuation, recursive discovery,
  batch SARIF mapping, symlink exclusion, correction preview eligibility and
  active correction locks are covered by tests.

#### Added

- Deliberately invalid bicycle-rental sample from the test lab with five verified findings
  and a local JSON-report walkthrough using the existing rule file.
- GUI screenshots captured during tests, workflow diagrams, and a refreshed
  English/German quick start.
- Apache License 2.0, `NOTICE`, Maven license metadata, and tiny-tool.de
  attribution in the project Java sources.
- Local Swing desktop GUI via `gui` and the Windows `start-gui.cmd` launcher.
  Background validation, input/profile selection, findings table, detail view,
  elapsed time and JSON/text report export.
- LED indicators: idle/running/valid/invalid/error, with explanatory text;
  separate file-existence LEDs for input and profile.
- Test-lab CSV example with an explicit local profile for repeatable workflows.
- Backward-compatible CSV options: `decimalSeparator`, `envelope`,
  `minRecords`, `maxRecords`, composite `uniqueBy` and per-column `enabled`.
  Shared scalar types now include `date` and `time`.
- Optional CSV `fileNamePattern` for full, case-sensitive basename checks in
  the GUI, local CLI and SFTP content validation; profiles without it retain
  their prior behavior.
- Visual CSV profile editor for portable configurations used unchanged by
  the GUI and CLI.
- CLI `--report-file` and `--local-only` for repeatable local workflows.
- Sample CSV fixtures, profile/pipeline tests, GUI smoke tests and
  headless-process verification.
- Separate English/German guides, examples, CSV/report/HTTP/SFTP contracts and this
  bilingual changelog.

#### Changed

- GUI refuses HTTP/SFTP profiles. CLI `--local-only` blocks remote adapters
  before transport access; existing CLI transport support remains available.
- GUI input/profile changes invalidate stale results. Busy operations lock
  relevant controls; a missing graphical desktop produces explicit exit `2`.
- Shared staged UTF-8 output protects input/profile files and existing aliases.
  CLI report-write failure produces exit `2` without stdout success output.
  Profile export refuses to overwrite existing files.
- Documentation distinguishes current functionality from historical iteration
  scope and fixes the quick start to build a JAR with `package`, not just `test`.

#### Verified

- Full offline build after the example and license update: 138 tests,
  0 failures, 0 errors, 2 skipped.
- 2026-10-22 package build with tests skipped, after the filename-pattern
  changes; GUI editor screenshots regenerated at 2× scale.
- 2026-10-22 clean package build after bilingual findings and CSV correction;
  Maven tests skipped for this iteration.
- Invalid CSV sample: `INVALID`, five findings, exit `1`; JSON report saved
  only in the local ignored `reports/` directory.
- Headless CLI exit codes `0`/`1`/`2`, report writing, GUI configuration and
  LED transitions, packaged GUI startup/shutdown.
- No production SFTP access in the local example path.

### Deutsch

#### Absturzwiederherstellung und Release-Vorbereitung

- Betriebssystem-Dateilocks machen Korrekturläufe exklusiv und nach
  Prozessabbruch wiederverwendbar.
- CI ist für Linux und Windows mit Paket-, CLI-Smoke- und Wiederholungsbuilds
  konfiguriert; erfolgreiche Läufe stellen JAR und SHA-256 bereit.
- Eine zweisprachige Release-Checkliste ergänzt den Ablauf. Es wird kein Tag
  und kein Release erstellt.
- Details: [Iteration 18](docs/iterations/iteration-18-crash-safe-correction-and-release-readiness.md).

#### Korrektur- und Batch-Sicherheit

- Batchlaeufe ueberspringen symbolische Links, auch bei rekursiver Suche.
- Eine exklusive Sperrdatei schuetzt Korrekturen vor parallelen Laeufen;
  Vorschauen melden vorhandene Artefakte und aktive Sperren.
- Details: [Iteration 17](docs/iterations/iteration-17-correction-and-batch-safety.md).

#### Rekursiver Batch, Vorschau und SARIF

- Optionale rekursive Dateisuche mit stabilen relativen Pfaden.
- Schreibgeschützte CSV-Korrekturvorschau für Batchläufe.
- Ein SARIF-2.1.0-Lauf für Batchbefunde und dateibezogene Betriebsfehler.
- Details: [Iterationen 14–16](docs/iterations/iteration-14-recursive-batches.md).

#### In diesem Update verifiziert

- Vollstaendiger Maven-Paketlauf: 154 Tests, 0 Fehler, 0 Fehlschlaege,
  3 uebersprungen (2026-10-23). Windows uebersprang den Symlinktest, weil
  Datei-Symlinks dort nicht angelegt werden konnten; CI prueft ihn unter Linux.
- Regressionstests pruefen Batchsortierung, rekursive Suche, Fehlerfortsetzung,
  SARIF, Symlink-Ausschluss, Vorschaukonflikte und aktive Korrektursperren.

### Batchpruefung, SARIF und Korrekturvorschau

- Lokale Batchpruefung mit `--input-dir`/`--glob` und aggregiertem Report;
  [Vertrag](docs/contracts/validation-batch-v1.de.md), [Iteration 11](docs/iterations/iteration-11-batch-local-validation.md).
- SARIF-2.1.0-Ausgabe fuer Einzeldatei-CI-Laeufe ([Iteration 12](docs/iterations/iteration-12-sarif-reports.md)).
- `--dry-run-correction` zur temporaer revalidierten Vorschau ([Iteration 13](docs/iterations/iteration-13-csv-correction-preview.md)).



#### Zweisprachige Befunde und nachvollziehbare CSV-Korrektur

- Zentraler DE/EN-Meldungskatalog mit stabilen Schluesseln und CLI-Option
  `--language de|en`.
- Optionale CSV-Profiloption `correction.enabled`, standardmaessig aus.
  Zeilenbezogene fehlerhafte Datensaetze werden erst entfernt, wenn die
  temporaere Datei danach die Pruefung besteht. Die GUI fragt vor der Aenderung;
  die CLI folgt der Profileinstellung.
- Bei Erfolg bleibt das Original als `.original.bak`, verworfene Datensaetze
  stehen in `.rejected.csv`, und `.correction.json` protokolliert Zeilen und
  Befunde. Vorhandene Belege werden nicht ueberschrieben.
- Details: [Iteration 9](docs/iterations/iteration-9-bilingual-findings-and-csv-correction.md).

#### Hinzugefügt

#### Hinzugefügt

- Absichtlich fehlerhafte Fahrradverleih-Beispieldatei aus dem Testlabor mit fünf geprüften
  Befunden und lokalem JSON-Report mit derselben Regeldatei.
- GUI-Screenshots aus Tests, Ablaufgrafiken und überarbeiteter Schnellstart auf
  Deutsch und Englisch.
- Apache License 2.0, `NOTICE`, Maven-Lizenzdaten und tiny-tool.de als Urheber
  in den Java-Quelldateien.
- Lokale Swing-Desktop-GUI ueber `gui` und Windows-Starter `start-gui.cmd`.
  Hintergrundpruefung, Datei-/Profilauswahl, Befundtabelle, Details, Laufzeit
  und JSON-/Text-Reportexport.
- LED-Anzeigen fuer bereit/laufend/gueltig/ungueltig/Fehler mit Klartext;
  separate Dateiexistenzanzeigen fuer Eingabe und Profil.
- CSV-Beispiel aus dem Testlabor mit einem ausdrücklichen lokalen Profil für
  wiederholbare Abläufe.
- Rueckwaertskompatible CSV-Optionen: `decimalSeparator`, `envelope`,
  `minRecords`, `maxRecords`, zusammengesetztes `uniqueBy` und `enabled`
  je Spalte. Gemeinsame skalare Typen um `date`/`time` erweitert.
- Optionales CSV-`fileNamePattern` fuer vollstaendige, case-sensitive
  Basisnamenpruefung in GUI, lokaler CLI und SFTP-Inhaltspruefung; Profile
  ohne dieses Feld behalten ihr bisheriges Verhalten.
- Visueller CSV-Regeleditor fuer unveraendert in GUI/CLI verwendbare Profile.
- CLI `--report-file` und `--local-only` für wiederholbare lokale Abläufe.
- CSV-Beispieldateien, Profil-/Pipelinetests, GUI-Smoke-Tests und
  headless Prozesspruefung.
- Getrennte deutsche/englische Anleitungen, Beispiele, CSV-/Report-/HTTP-/SFTP-Vertraege
  und dieses zweisprachige Changelog.

#### Geändert

- GUI lehnt HTTP/SFTP ab. CLI-Schalter `--local-only` sperrt Remoteadapter
  vor Transportzugriff; bisherige CLI-Transportfunktionen bleiben erhalten.
- Eingabe-/Profilwechsel verwerfen alte GUI-Ergebnisse. Relevante
  Bedienelemente sind waehrend des Vorgangs gesperrt. Fehlender grafischer
  Desktop fuehrt explizit zu Exit `2`.
- Gemeinsame gestufte UTF-8-Ausgabe schuetzt Eingabe/Profil und vorhandene
  Dateialiasse. CLI-Schreibfehler liefern `2` ohne Erfolgsausgabe auf stdout.
  Profilexport ueberschreibt keine vorhandenen Dateien.
- Dokumentation trennt aktuellen Stand von historischen Iterationsgrenzen;
  der Schnellstart erzeugt mit `package` statt nur `test` ein JAR.

#### Verifiziert

- Vollständiger Offline-Build nach Beispiel- und Lizenzergänzung:
  138 Tests, 0 Fehlschläge, 0 Fehler, 2 übersprungen.
- 2026-10-22 sauberer Paket-Build nach zweisprachigen Befunden und CSV-Korrektur;
  Maven-Tests fuer diese Iteration uebersprungen.
- Fehlerhafte CSV-Beispieldatei: `INVALID`, fünf Befunde, Exit `1`; JSON-Report
  nur im lokalen ignorierten Ordner `reports/` gespeichert.
- Headless CLI-Exitcodes `0`/`1`/`2`, Reportausgabe, GUI-Konfiguration,
  LED-Wechsel und Start/Ende der paketierten GUI.
- Kein produktiver SFTP-Zugriff im lokalen Beispielpfad.

## Earlier development / Bisherige Entwicklung

These are historical milestones, not numbered releases. / Historische
Meilensteine, keine nummerierten Releases.

| Date / Datum | English | Deutsch | Record / Dokument |
|---|---|---|---|
| 2026-09-25 | Iteration 4: stable single-file SFTP validation, strict host keys, hash and opt-in quarantine | Iteration 4: stabile SFTP-Einzeldatei, strikte Hostkeys, Hash und optionale Quarantaene | [Iteration 4](docs/iterations/iteration-4-sftp.md) |
| 2026-09-21 | Iteration 3: HTTP request/response validation | Iteration 3: HTTP-Anfrage-/Antwortvalidierung | [Iteration 3](docs/iterations/iteration-3-http.md) |
| 2026-09-16 | CSV and fixed-width integration; shared tabular checks and format detection | CSV-/Fixed-Width-Integration; gemeinsame Tabellenregeln und Formaterkennung | [CSV](docs/iterations/iteration-2a.md), [Fixed-width](docs/iterations/iteration-2b.md), [shared checks](docs/decisions/0004-shared-tabular-checks.md) |

The initial local JSON Schema CLI and report/exit-code contract are documented
in [Iteration 1](docs/iterations/iteration-1.md).
Die urspruengliche lokale JSON-Schema-CLI samt Report-/Exitcodevertrag ist in
dieser Iteration dokumentiert; hier wird kein unbekanntes Releasedatum ergaenzt.
