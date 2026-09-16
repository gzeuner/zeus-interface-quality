# Iteration 2a: CSV-Dateien lokal prüfen

Status: abgeschlossen

## Ziel

Der erste Dateiaustausch außerhalb von JSON sollte automatisch prüfbar werden,
ohne den technologieoffenen Kern mit CSV-Details zu belasten. Eine CSV-Datei
braucht dafür neben dem Inhalt ein maschinenlesbares Profil, das ihren Aufbau
beschreibt.

## Umgesetzt

- eigener CSV-Adapter außerhalb des `core`-Pakets;
- Apache Commons CSV für Trennzeichen, Quoting und Datensätze;
- Version-1-Profil mit Zeichencodierung, einem Zeichen als Trennzeichen und
  exakter Kopfzeile;
- Spaltenregeln für `string`, `integer`, `decimal` und `email`;
- Pflichtwerte, Mindestlängen, Mindestwerte und reguläre Ausdrücke;
- Findings mit stabilen Pfaden wie `/rows/0/quantity`;
- derselbe Text- und JSON-Report wie bei JSON;
- bestehende JSON-Aufrufe bleiben ohne zusätzliche Option unverändert;
- CLI-Auswahl über `--input-format json|csv`;
- operative Fehler bei unlesbaren oder ungültigen Profilen sowie syntaktisch
  kaputten CSV-Dateien;
- Fixtures für gültige Dateien, Typfehler, fehlende Pflichtwerte, falsche
  Kopfzeilen, falsche Spaltenanzahl und kaputte Quoting-Syntax;
- 47 automatisierte Tests erfolgreich.

## Profilbeispiel

```json
{
  "format": "csv",
  "version": 1,
  "delimiter": ";",
  "encoding": "UTF-8",
  "header": ["deliveryId", "quantity", "email"],
  "columns": [
    {"name": "deliveryId", "type": "string", "required": true, "minLength": 1},
    {"name": "quantity", "type": "integer", "required": true, "minimum": 1},
    {"name": "email", "type": "email", "required": true}
  ]
}
```

Das Profil ist absichtlich ein eigenes CSV-Profil und kein als JSON Schema
getarntes Universalformat. So bleibt Raum für spätere Adapter, etwa für
XML/XSD, Fixed-Width-Dateien oder Legacy-Formate mit COBOL- und RPG-Definitionen.

## Abnahmekriterien

Eine gültige CSV-Datei endet mit Exit `0`, eine strukturell oder inhaltlich
ungültige Datei mit Exit `1`. Nicht lesbare Eingaben, ungültige Profile und
syntaktisch kaputte CSV-Dateien enden mit Exit `2`. Alle drei Fälle liefern bei
Bedarf denselben maschinenlesbaren Report-Vertrag wie der JSON-Adapter.

## Bewusst offen

- Fixed-Width-Dateien und daraus abgeleitete Legacy-Profile;
- FTP-/SFTP-Transport und Authentifizierung;
- fachliche Regeln, die mehrere Zeilen oder Dateien benötigen;
- Streaming, Größenlimits und Schutz vor gleichzeitig veränderten Dateien;
- Ausgabeziele jenseits von Standardausgabe und dem bestehenden Report.
