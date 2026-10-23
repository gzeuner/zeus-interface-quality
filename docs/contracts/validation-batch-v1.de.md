# Batch-Pruefreport v1

[English](validation-batch-v1.md) | Deutsch

Die Verzeichnispruefung mit `--report json` liefert ein aggregiertes Dokument:

```json
{
  "format": "zeus-validation-batch-v1",
  "status": "INVALID",
  "valid": false,
  "results": [
    {"file": "orders.csv", "relativePath": "orders.csv", "status": "INVALID", "findings": []},
    {"file": "returns.csv", "relativePath": "nested/returns.csv", "status": "VALID", "findings": []}
  ]
}
```

Standardmaessig gelten passende regulaere Dateien direkt im Ordner. Mit
`--recursive` werden passende Dateien aller Unterordner einbezogen. Die
Sortierung folgt dem Schraegstrich-normalisierten relativen Pfad. Jedes
Ergebnis enthaelt den Basisnamen in `file`, den Pfad relativ zum Eingabeordner
in `relativePath` und den Status `VALID`, `INVALID` oder `ERROR`.
Pruefergebnisse verwenden dieselben Befunde und
optionalen Metadaten wie [Report v1](validation-report-v1.de.md). Fehler
enthalten `error` und keine Befunde. Der Gesamtstatus ist `ERROR`, sobald ein
Betriebsfehler auftritt, sonst `INVALID`, wenn mindestens eine Datei ungueltig
ist, andernfalls `VALID`; `valid` ist nur bei `VALID` wahr.

Symbolische Links werden bei der Batchsuche ausgeschlossen, auch bei
rekursiven Laeufen. Die Verzeichnissuche folgt keinen verlinkten Ordnern;
verlinkte Dateien werden weder geprueft noch durch Batchkorrekturen geaendert.

Exitcodes: `0`, wenn alle Dateien gueltig sind; `1`, wenn mindestens eine
ungueltig ist und kein Betriebsfehler auftrat; `2` bei Start- oder
dateibezogenem Betriebsfehler. Ein Dateifehler stoppt die folgenden Pruefungen
nicht.

`--dry-run-correction` kann `correctionPreview` ergaenzen, ohne Eingaben oder
Belege zu aendern. Batch-SARIF liefert einen SARIF-2.1.0-Lauf mit URI je
Befund; Betriebsfehler verwenden die Regel `zeus.operationalError`. SARIF ist
separat vom JSON-Vertrag.
