# Iteration 1: lokale JSON-Schema-Validierung

Status: abgeschlossen

## Ziel

Ein erster durchgängiger Validierungspfad sollte ein lokales JSON-Dokument gegen ein lokales JSON Schema prüfen und ein für Skripte geeignetes Ergebnis liefern.

## Umgesetzt

- technologieunabhängige Ergebnisobjekte in `core`;
- deterministische Sortierung der Findings;
- JSON-Schema-Adapter für Draft 2020-12;
- explizit aktivierte Format-Assertions;
- lokale `$ref`-Auflösung ausschließlich unterhalb des Schema-Verzeichnisses;
- CLI-Unterkommando `validate`;
- Text- und JSON-Report;
- Fixtures für gültige Eingaben, falsche Datentypen und ungültige Formate;
- eingefrorener Report-Vertrag v1;
- Exit-Codes `0` für gültig, `1` für ungültig und `2` für operative Fehler;
- Ablehnung von HTTP-`$ref` ohne Netzwerkzugriff;
- operative Fehler bei kaputtem JSON;
- Findings für `additionalProperties: false` mit stabilem Root-Pfad;
- exakt getesteter `schemaPath` `/properties/quantity/type`;
- GitHub-Actions-CI für Tests und Package auf JDK 21;
- JAR-Rauchtests für Exit `0` und Exit `1`.

## Abnahmekriterien

Der CLI-Aufruf mit `valid-delivery.json` endet reproduzierbar mit Exit `0`. Der Aufruf mit `invalid-wrong-type.json` endet reproduzierbar mit Exit `1` und liefert den stabilen Finding-Pfad `/quantity`. Fehler beim Lesen von Dateien oder beim Vorbereiten des Schemas enden mit Exit `2`.

HTTP, SFTP, FTP, CSV, XML, YAML-spezifische Syntax, semantische Regeln und Spring Boot bleiben bewusst außerhalb dieser Iteration.

## Bewusst offen

- Eine Lizenzdatei wird erst vor dem ersten öffentlichen Release festgelegt.
- `--output` und weitere Ausgabeziele gehören in eine spätere Iteration.
- Zusätzliche Report-Felder wie `schemaId`, `schemaDialect` oder `findingCount` sind für später offen.
- Ein möglicher Jackson-3-Pfad wird separat bewertet.
