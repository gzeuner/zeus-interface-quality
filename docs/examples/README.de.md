# Lokale Beispiele

[Deutsch](README.de.md) | [English](README.md) | [Hauptanleitung](../../README.de.md)

Die folgenden Befehle gelten für Windows PowerShell im Repository-
Hauptverzeichnis nach `.\mvnw.cmd -B package`. Lokale CSV-Dateien und erzeugte
Reports bleiben in den von Git ignorierten Ordnern.

## CSV-Beispiel aus dem Testlabor

[Fiktive Fahrradvermietungen](fictional-bicycle-rentals.csv) und das passende
[CSV-Validierungsprofil](fictional-bicycle-rentals.csv-profile.json) sind
vollständig erfunden. Die fünf Datensätze zeigen ein
gequotetes Semikolon, ISO-Daten, Dezimalkommas, Zahlengrenzen, erlaubte
Fahrradtypen, E-Mail-Prüfung und eine eindeutige Miet-ID.

```powershell
java '-Djava.awt.headless=true' -jar .\target\zeus-interface-quality-0.1.0-SNAPSHOT.jar validate --profile .\docs\examples\fictional-bicycle-rentals.csv-profile.json --input .\docs\examples\fictional-bicycle-rentals.csv --local-only --report json
```

Erwartet wird `VALID` mit leerem `findings`-Array und Exitcode `0`. Die Tests
prüfen außerdem, dass eine doppelte Miet-ID abgewiesen wird.

### Absichtlich fehlerhafte CSV

[fictional-bicycle-rentals-invalid.csv](fictional-bicycle-rentals-invalid.csv)
verwendet **dieselbe** [JSON-Regeldatei](fictional-bicycle-rentals.csv-profile.json).
Die Prüfung meldet fünf Befunde aus drei Datenzeilen:

| CSV-Zeile | Feld | Befund |
|---|---|---|
| 3 | `durationMinutes` | `0` unterschreitet den Mindestwert `1` |
| 4 | `startDate` | `2026-13-08` ist kein gültiges Datum |
| 5 | `rentalId` | `BIKE-003` steht bereits in Zeile 4 |
| 5 | `bikeType` | `scooter` ist nicht erlaubt |
| 5 | `contactEmail` | `not-an-email` ist keine E-Mail-Adresse |

Lokalen Reportordner anlegen und die CLI ausführen:

```powershell
New-Item -ItemType Directory -Force .\reports | Out-Null
java '-Djava.awt.headless=true' -jar .\target\zeus-interface-quality-0.1.0-SNAPSHOT.jar validate --profile .\docs\examples\fictional-bicycle-rentals.csv-profile.json --input .\docs\examples\fictional-bicycle-rentals-invalid.csv --local-only --report json --report-file .\reports\fictional-bicycle-rentals-invalid.report.json
$LASTEXITCODE  # 1: Prüfung mit Befunden abgeschlossen
```

Der lokale Report enthält `status: INVALID` und fünf Einträge unter `findings`.
In der GUI die fehlerhafte CSV als Datendatei wählen, die JSON-Regeln beibehalten
und **Jetzt prüfen** anklicken. Nach Auswahl eines Befunds erscheinen Datenpfad,
Regelpfad und vollständige Meldung.

Auch der Dateiname wird vor dem CSV-Inhalt geprüft. Zum Gegencheck eine Datei
mit einem absichtlich unpassenden Namen kopieren und lokal validieren:

```powershell
New-Item -ItemType Directory -Force .\data | Out-Null
Copy-Item .\docs\examples\fictional-bicycle-rentals.csv .\data\unexpected.csv
java '-Djava.awt.headless=true' -jar .\target\zeus-interface-quality-0.1.0-SNAPSHOT.jar validate --profile .\docs\examples\fictional-bicycle-rentals.csv-profile.json --input .\data\unexpected.csv --local-only --report json
```

Der Report meldet `keyword: fileNamePattern` und `schemaPath:
/fileNamePattern`; der CLI-Exitcode ist `1`. In der GUI dieselbe Datei bei
ausgewähltem Beispielprofil öffnen. Das optionale Muster lässt sich im Dialog
**CSV-Regeln konfigurieren** bearbeiten.

Beim Start wählt die GUI die gültige Beispiel-CSV und die passenden Regeln.
Die CSV enthält die Datensätze;
`fictional-bicycle-rentals.csv-profile.json` liefert die Prüfregeln.
`delivery.schema.json` ist ein getrenntes JSON Schema für JSON-Daten;
eine unter `reports/` gespeicherte JSON-Datei ist ein Ergebnisreport.

## Eigene Daten prüfen

CSV und passende JSON-Regeln im lokalen Ordner `data/` ablegen. Die CLI läuft
ohne Fenster und schreibt den Report nur an den gewählten lokalen Pfad:

```powershell
New-Item -ItemType Directory -Force .\data, .\reports | Out-Null
java '-Djava.awt.headless=true' -jar .\target\zeus-interface-quality-0.1.0-SNAPSHOT.jar validate --profile .\data\rules.csv-profile.json --input .\data\input.csv --local-only --report json --report-file .\reports\input.report.json
```

Für JSON-Daten mit `--profile` ein JSON Schema angeben. In der GUI beide
Dateien auswählen und **Jetzt prüfen** anklicken. Mit **CSV-Regeln
konfigurieren** lässt sich ein bearbeitbares CSV-Profil erstellen;
**Ergebnis speichern** exportiert die Befunde.

## JSON-Beispiel aus dem Testlabor

[delivery.json](delivery.json) ist ein gültiges Beispiel;
[delivery-invalid.json](delivery-invalid.json) enthält absichtlich eine leere
Liefer-ID, eine Menge von null und eine nicht deklarierte Eigenschaft. Beide
Dateien verwenden das lokale [JSON Schema](delivery.schema.json). Es verlangt
eine nicht leere `deliveryId`, eine positive ganzzahlige `quantity` und lehnt
zusätzliche Eigenschaften ab.

Gültiges Beispiel prüfen:

```powershell
java '-Djava.awt.headless=true' -jar .\target\zeus-interface-quality-0.1.0-SNAPSHOT.jar validate --profile .\docs\examples\delivery.schema.json --input .\docs\examples\delivery.json --local-only --report json
$LASTEXITCODE  # 0: VALID
```

Absichtlich fehlerhaftes Beispiel prüfen:

```powershell
java '-Djava.awt.headless=true' -jar .\target\zeus-interface-quality-0.1.0-SNAPSHOT.jar validate --profile .\docs\examples\delivery.schema.json --input .\docs\examples\delivery-invalid.json --local-only --report json
$LASTEXITCODE  # 1: INVALID mit drei Befunden
```

Das Schema meldet die leere ID, die Menge unterhalb des Mindestwerts und die
zusätzliche Eigenschaft. Für Automatisierung Status, Pfade und Regelkennungen
statt des Meldungstexts auswerten.

## SFTP-Vorlagen

[sftp-profile.json](sftp-profile.json) ist eine Vorlage, keine funktionierende
Produktivverbindung. Der Hostname ist ein Platzhalter. Die GUI und
`--local-only` lehnen SFTP-Profile ab.

Nur für einen ausdrücklich freigegebenen temporären Testserver `host`,
`username`, `remotePath` sowie folgende Dateien unterhalb des Profilordners
konfigurieren:

- `known_hosts`: vertrauenswürdiger Hostkey;
- `id_ed25519`: privater Schlüssel des Testkontos;
- `delivery.schema.json`: Inhaltsvertrag.

Bei verschlüsseltem Schlüssel `SFTP_KEY_PASSPHRASE` lokal setzen.
Schlüssel, Passphrasen, hostspezifische Konfiguration und private Nutzdaten
niemals committen. Quarantäne ist optional und kann ungültige Dateien auf
dem Testserver verschieben. Siehe [SFTP-Vertrag](../contracts/sftp-profile-v1.de.md).

Lokale Adaptertests ohne externen SFTP-Endpunkt:

```powershell
.\mvnw.cmd -o -B '-Dtest=SftpValidatorTest,SftpValidatorIntegrationTest' test
```

Der Integrationstest startet einen eigenen temporären lokalen Server.
Abhängigkeiten müssen für `-o` bereits geladen sein.
Es erfolgen weder produktiver SFTP-Zugriff noch Push.

Die vorhandene [v2-Verzeichnisvorlage](sftp-directory-profile.json) wählt
eine passende reguläre Datei aus einem entfernten Verzeichnis. Die
[v3-Vorlage für korrelierte Lieferungen](sftp-correlated-lifecycle-profile.json)
ordnet Primär- und Begleitdatei über die Liefer-ID zu und ergänzt Claim-,
Recovery- und Archivschritte. Siehe [v2-Vertrag](../contracts/sftp-profile-v2.md)
und [v3-Vertrag](../contracts/sftp-profile-v3.md). Beide sind entfernte CLI-
Optionen und durch `--local-only` gesperrt.

Für v2 `remoteDirectory` und `filePattern` so konfigurieren, dass genau eine
reguläre Datei passt. Für v3 Endpunkt, Inhaltsprofil und entfernte
Verzeichnisse anpassen. Claim- und Archivverzeichnis müssen exklusiv für
diesen Consumer sein. Eine bereits geclaimte Lieferung wird nach Unterbrechung
fortgesetzt; archiviert wird erst nach erfolgreicher Prüfung beider Dateien.
`quarantineRemoteDirectory` kann ungültige geclaimte Lieferungen aus dem
Recovery-Bereich entfernen.
