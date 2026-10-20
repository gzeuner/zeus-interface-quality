# Reportvertrag v1

[Deutsch](validation-report-v1.de.md) | [English](validation-report-v1.md)

Der JSON-Report ist bewusst klein und unabhaengig von der Validatorbibliothek.
`--report JSON` schreibt ihn nach stdout, `--report-file PATH` zusaetzlich
als UTF-8-Datei. Die GUI exportiert denselben Vertrag.
GUI und CSV-Regeln erfordern keine weiteren JSON-Eigenschaften.

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

Garantien:

- `status` ist `VALID` oder `INVALID`;
- `valid` stimmt mit `status` ueberein;
- `findings` ist bei gueltigem Ergebnis leer;
- jeder Befund enthaelt JSON-Pointer-Pfade, eine Regelkennung aus JSON Schema
  oder dem Adapter sowie eine lesbare Meldung;
- Befunde werden nach Datenpfad, Profilpfad, Regelkennung und Meldung sortiert;
- `metadata` ist optional und erscheint nur, wenn der Validator sie liefert;
  SFTP v3 nutzt sie fuer Liefer-ID, Anzahl gepruefter Dateien, Lebenszyklusstatus
  und Zielpfade, ohne Nutzdaten oder Zugangsdaten aufzunehmen;
- Betriebsfehler wie unlesbare Dateien oder ungueltige Schemas sind keine
  Datenbefunde und liefern Exit `2`.

## Pfade und zusaetzliche Regeln

JSON Schema nutzt Dokument-/Schemapfade wie im Beispiel.
CSV nutzt `/rows/0/columnName` und `/columns/columnName/type`.
Datensatzindizes zaehlen ohne Kopf- und Markersaetze; pointerrelevante Zeichen
werden escaped. Dateibefunde koennen einen leeren `instancePath` besitzen.
CSV-Regeln umfassen `header`, `columns`, `marker`, `required`, `type`,
`pattern`, Laengen-/Zahlengrenzen, `minRecords`, `maxRecords` und `uniqueBy`.
Fixed-Width nutzt `/records/0/fieldName`; HTTP und SFTP ergaenzen ihre
dokumentierten Anfrage-/Antwort-/Inhaltspraefixe.

CSV-Pfade beziehen sich auf den logischen Profilvertrag, nicht zwingend auf
numerische Indizes im JSON-Array `columns`.
Regeldetails stehen im [CSV-Vertrag](csv-profile-v1.de.md).

## Ausgabe und Exitverhalten

`0` = gueltig, `1` = ungueltig, `2` = Betriebs-/Konfigurations-/Befehlsfehler.
Operative Diagnosen gehen nach stderr; bei solchen Fehlern wird kein
Pruefreport ausgegeben. Auch ein Report-Schreibfehler liefert `2` ohne Report
auf stdout. Eine alte Zieldatei kann erhalten bleiben: Automatisierung muss
den Exitcode pruefen, bevor sie einen Report als aktuell behandelt.

Das Zielverzeichnis muss existieren. Reports duerfen Eingabe und Profil nicht
ersetzen; andere vorhandene Ausgabedateien werden ersetzt. Die CLI fragt nicht
nach, die GUI bestaetigt das Ersetzen vorhandener Reports.

TEXT beginnt mit `VALID` oder `INVALID`, danach folgen Befundzeilen.
Meldungen sind ueberwiegend Deutsch. Automatisierung sollte `status`, `valid`,
Pfade und `keyword` statt Meldungstext oder LED-Farbe auswerten.
