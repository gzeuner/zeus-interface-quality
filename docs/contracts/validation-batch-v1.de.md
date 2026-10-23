# Batch-Pruefreport v1

[English](validation-batch-v1.md) | Deutsch

Die Verzeichnispruefung mit `--report json` liefert ein aggregiertes Dokument:

```json
{
  "format": "zeus-validation-batch-v1",
  "status": "INVALID",
  "valid": false,
  "results": [
    {"file": "orders.csv", "status": "INVALID", "findings": []},
    {"file": "returns.csv", "status": "VALID", "findings": []}
  ]
}
```

Beruecksichtigt werden passende regulaere Dateien direkt im Ordner, sortiert
nach Dateiname. Jedes Ergebnis enthaelt den Basisnamen und den Status `VALID`,
`INVALID` oder `ERROR`. Pruefergebnisse verwenden dieselben Befunde und
optionalen Metadaten wie [Report v1](validation-report-v1.de.md). Fehler
enthalten `error` und keine Befunde. Der Gesamtstatus ist `ERROR`, sobald ein
Betriebsfehler auftritt, sonst `INVALID`, wenn mindestens eine Datei ungueltig
ist, andernfalls `VALID`; `valid` ist nur bei `VALID` wahr.

Exitcodes: `0`, wenn alle Dateien gueltig sind; `1`, wenn mindestens eine
ungueltig ist und kein Betriebsfehler auftrat; `2` bei Start- oder
dateibezogenem Betriebsfehler. Ein Dateifehler stoppt die folgenden Pruefungen
nicht.
