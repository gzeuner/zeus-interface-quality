# CSV-Profil v1: GUI und CLI

[Deutsch](csv-profile-v1.de.md) | [English](csv-profile-v1.md)

Dasselbe UTF-8-JSON-Profil wird von Desktop-GUI, headless CLI und CSV-
Inhaltsadapter anderer Transporte verwendet. Es ist kein GUI-spezifischer
Regelzustand notwendig. Bestehende Version-1-Profile behalten ihr Verhalten.

## Beispiel

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

## Dateiregeln

| Eigenschaft | Bedeutung |
|---|---|
| `format`, `version` | Erforderlich: `csv`, Ganzzahl `1`. |
| `encoding` | Erforderlicher Java-Zeichensatz, etwa `UTF-8` oder `windows-1252`. Dekodierungsfehler sind Betriebsfehler. |
| `delimiter` | Genau ein Zeichen ausser Anfuehrungszeichen, CR oder LF. Commons CSV verarbeitet Quoting. |
| `decimalSeparator` | Optional `.` (Default) oder `,`. Ein expliziter Wert muss sich vom Trennzeichen unterscheiden. Grenzen im JSON-Profil bleiben JSON-Zahlen mit Dezimalpunkt. |
| `header` | Erforderliches nichtleeres Array eindeutiger Namen in exakter CSV-Reihenfolge. |
| `columns` | Genau eine Definition je Kopfspaltenname, in derselben Reihenfolge. |
| `envelope` | Optionales Objekt mit verschiedenen nichtleeren Zeichenfolgen `begin` und `end`. Anfangssatz vor der Kopfzeile, Endsatz als letzter CSV-Satz. Beide benoetigen die vollstaendige Spaltenanzahl und sonst leere Felder. |
| `minRecords`, `maxRecords` | Optionale nicht-negative Ganzzahlen, inklusive Grenzen; Minimum darf Maximum nicht uebersteigen. Nur Datensaetze zaehlen, keine Kopf-/Markersaetze. Falsche Spaltenanzahl zaehlt trotzdem als Datensatz. Keine Ressourcenlimits oder Abschneideoptionen. |
| `uniqueBy` | Optionales nichtleeres Array verschiedener vorhandener Spaltennamen als gemeinsamer Schluessel. Zum Abschalten weglassen. |
| `fileNamePattern` | Optionaler Java-regulaerer Ausdruck, der zum vollstaendigen Basisnamen der Eingabedatei passen muss; Gross-/Kleinschreibung wird beachtet. Zum Abschalten weglassen. Ein ungueltiger Ausdruck ist ein Profilfehler. |
| `correction` | Optionales Objekt `{ "enabled": boolean }`, Default `false`. Bei Aktivierung darf die lokale CSV-Pruefung fehlerhafte Datensaetze entfernen. CLI `--dry-run-correction` zeigt passende Entfernungen nach temporaerer Pruefung ohne Dateiaenderung an. Bei vorhandenen Begleitdateien oder einer laufenden Korrektur meldet die Vorschau `eligible: false` und `blockedBy`. Die GUI fragt vor der Aenderung nach; die CLI folgt dem Profil ohne Rueckfrage. Die Originalbytes werden als `.original.bak` gesichert, entfernte Zeilen samt Kopfzeile kommen in `.rejected.csv`, und `.correction.json` protokolliert Zeilennummern und Befunde. Vorhandene Artefakte werden nie ueberschrieben. Eine exklusive `.correction.lock` verhindert parallele Laeufe und wird beim regulären Ende entfernt. Nach hartem Prozessabbruch muss eine verbliebene Sperrdatei vor einem neuen Versuch geprueft und entfernt werden. Nur Befunde auf Datensatzebene sind geeignet; die temporaere Datei muss die Pruefung bestehen, bevor sie das Original ersetzt. Feldwerte, Zeichensatz und Trennzeichen bleiben erhalten; Quotes und Zeilenenden koennen normalisiert werden. |
| `$id`, `$schema` | Optionale Metadaten; der CSV-Adapter ruft sie nicht aus dem Netzwerk ab. |

Eine Datei nur mit Kopfzeile bzw. mit Anfangsmarker/Kopfzeile/Endmarker bleibt
standardmaessig gueltig. `minRecords: 1` nur setzen, wenn der konkrete Test
Daten verlangt. Anzahlgrenzen und Eindeutigkeit müssen im Profil ausdrücklich
aktiviert werden. Fehlende Kopfzeile bleibt ungueltig.

`uniqueBy` vergleicht **exakte dekodierte Zeichenfolgen**, einschliesslich
fuehrender Nullen, Leerzeichen und Gross-/Kleinschreibung. Es gibt weder
Trimmen noch numerische Normalisierung. Leere Schluesselfelder nehmen am
Vergleich teil; mit `required: true` separat verbieten. Jede Wiederholung nach
dem ersten Vorkommen erzeugt einen Befund mit erster Satznummer und Pfad des
wiederholten Datensatzes. Saetze mit falscher Spaltenanzahl koennen keinen
Schluessel bilden; sie werden mit `columns` gemeldet.
Der Speicherbedarf waechst mit der Anzahl verschiedener Schluessel.

## Spaltenregeln

| Eigenschaft | Bedeutung |
|---|---|
| `name` | Erforderlich; entspricht dem zugehoerigen Kopfspaltennamen. |
| `type` | Erforderlich: `string`, `integer`, `decimal`, `email`, `date`, `time`. |
| `enabled` | Optionaler Wahrheitswert, Default `true`. `false` ueberspringt alle Wertregeln dieser Spalte, auch Typ/Pflicht. Spalte, Strukturpruefung und explizites `uniqueBy` bleiben bestehen. Die Konfiguration wird weiterhin auf Fehler geprueft. |
| `required` | Optionaler Wahrheitswert, Default `false`. Verbietet die leere Zeichenfolge, nicht reine Leerzeichen. |
| `minLength`, `maxLength` | Optionale nicht-negative inklusive Unicode-Codepunkt-Laengen; nur fuer `string`. |
| `minimum`, `maximum` | Optionale inklusive JSON-Zahlengrenzen; nur fuer `integer`/`decimal`. |
| `pattern` | Optionaler regulaerer Java-Ausdruck, muss zum gesamten nichtleeren Originalfeld passen. Auch fuer Nachkommastellen oder erlaubte Werte verwendbar. |

Optionale leere Felder ueberspringen alle Wertregeln. `date` prueft
Kalendergueltigkeit mit ISO-Lokaldaten; `time` erlaubt strikt `HH:mm`, keine
Sekunden und kein `24:00`. Bei Dezimalkomma sind etwa `-12,30`, `,30`, `-,30`
und `12` zulaessig, aber kein Dezimalpunkt, keine Gruppierung, Leerzeichen
oder wissenschaftliche Schreibweise. Der bestehende Dezimalpunktmodus behaelt
die Java-`BigDecimal`-Syntax. Muster gelten fuer den unnormalisierten CSV-Text,
auch wenn Zahlenvergleiche das konfigurierte Dezimalzeichen beruecksichtigen.

Unbekannte Eigenschaften, ungueltige Namen/Typen, nicht-boolesche Schalter,
ungueltige Regex und widerspruechliche Grenzen sind Konfigurationsfehler,
keine stillschweigend ignorierten Optionen.

## Ergebnisse und Automatisierung

Der [Reportvertrag v1](validation-report-v1.de.md) bleibt unveraendert.
Weitere Regelkennungen sind `minRecords`, `maxRecords` und `uniqueBy`;
Profilpfade dafuer sind `/minRecords`, `/maxRecords` und `/uniqueBy`.
`fileNamePattern` verwendet den leeren Datenpfad, den Profilpfad
`/fileNamePattern` und die Regelkennung `fileNamePattern`. Der Report enthaelt
keine lokalen Dateipfade. GUI und CLI pruefen denselben lokalen Basisnamen;
bei SFTP gilt der urspruengliche entfernte Basisname, nicht der temporaere
Downloadname.
Spaltenbefunde verwenden `/rows/<nullbasierter-datensatz>/<spaltenname>`.
Fehlerhafte CSV-Syntax/Profile liefern Exit `2`, gueltige Eingaben `0`,
Regelverletzungen `1`.

Der Profilexport aus der GUI ueberschreibt keine vorhandene Datei. JSON beliebig bearbeiten oder
das Profil in der GUI auswaehlen und **CSV-Regeln konfigurieren** oeffnen.
Der Editor unterstuetzt Format, Marker, Anzahlgrenzen, Dateinamenmuster und Fachschluessel,
Spaltennamen/-definitionen, Typen, Aktiv-/Pflichtschalter, Laengen, Grenzen
und Regex. Die Konfiguration wird vor dem Speichern geprueft.
Vorhandene Profile werden nur nach Bestaetigung ersetzt; die Dateneingabe
kann nicht durch den Editor ueberschrieben werden.

Für eine Dublettenprüfung auf Wurzelebene explizit einfügen
(JSON-Ausschnitt, kein vollstaendiges Profil):

```json
"uniqueBy": ["QUITTUNGSCODE", "NR_LISTENZEILE"]
```

Ein Eingabe-/Profilwechsel verwirft das GUI-Ergebnis.
Die LED ergaenzt Klartext: grau = kein aktuelles Ergebnis, gelb = laufend,
gruen = gueltig, rot = ungueltig/Betriebsfehler.
Die kleinen Datei-/Profil-LEDs zeigen nur Dateiexistenz.
Beide Oberflaechen unterstuetzen auch JSON Schema und Fixed-Width; der visuelle
Regelkonfigurator ist speziell fuer CSV.
