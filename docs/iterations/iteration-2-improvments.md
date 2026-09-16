# Iteration 2 improvements

Status: in progress on `feature/iteration-2-csv-legacy-improvments`

## Ziel

Den fertigen CSV- und Fixed-Width-Stand härten, ohne neue Transporte
einzuführen.

## Umgesetzt

- gemeinsame Support-Klassen für Dateiprüfung, JSON Pointer, Profil-JSON
  und Skalarprüfungen;
- `maxLength` und `maximum` in beiden Tabellenprofilen;
- leere Fixed-Width-Dateien als INVALID mit Keyword `minRecords`;
- CLI-Registry mit `--input-format AUTO` und Profilerkennung;
- `--profile` als Alias für `--schema`;
- Version aus dem JAR-Manifest statt hartcodierter Picocli-Version;
- CI auch auf `feature/**`.

## Kompatibilität

Bestehende Aufrufe mit `--input-format json|csv|fixed-width` bleiben
gültig. Ohne Flag erkennt die CLI CSV- und Fixed-Width-Profile am
Feld `format` und fällt sonst auf JSON Schema zurück.
