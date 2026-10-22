# Iteration 8: optionale Dateinamenkonventionen

Status: umgesetzt und in GUI, CLI, SFTP und CSV-Profilvertrag integriert.

## Ziel

Ein CSV-Profil soll neben dem Dateiinhalt auch den Namen der Eingabedatei
prüfen können. Dieselbe Regel gilt für Desktop-GUI und CLI und ist
standardmäßig ausgeschaltet, damit bestehende Profile ihr Verhalten behalten.

## JSON-Konfiguration

```json
"fileNamePattern": "^fictional-bicycle-rentals(-invalid)?\\.csv$"
```

`fileNamePattern` ist optional und ein Java-regulärer Ausdruck. Er wird auf
den Dateinamen ohne Verzeichnispfad angewendet und muss den gesamten Namen
treffen. Ein nicht passender Name erzeugt einen normalen Validierungsbefund
mit Regel `fileNamePattern` und Profilpfad `/fileNamePattern`; ein ungültiger
Ausdruck ist ein Profilfehler. Kein konfigurierter Ausdruck bedeutet keine
Namensprüfung. In dieser Iteration gilt der Check für lokal ausgewählte
CSV-Dateien, unabhängig davon, ob GUI oder CLI sie prüft. Bei SFTP-
Inhaltsprüfung gilt der Name der entfernten Datei, nicht der lokale
Zwischenspeichername.

## Geplanter Umfang

- CSV-Profilvertrag v1 in Deutsch und Englisch um das optionale Feld ergänzen.
- Vorhandene Profilvalidierung um Regex-Prüfung und klare Fehlertexte
  erweitern.
- Dateinamenbefund in die gemeinsame CSV-Validierung aufnehmen, sodass GUI,
  lokale CLI und SFTP-Inhaltsprüfung dasselbe Ergebnis sehen.
- Beispielprofil so konfigurieren, dass die gültige und absichtlich
  fehlerhafte Beispiel-CSV beide dem vereinbarten Namensmuster entsprechen.
- Beispielanleitung mit einem gültigen und einem nicht passenden Dateinamen
  für GUI und CLI ergänzen.
- GUI-Regel-Editor um ein optionales Feld für das Dateinamenmuster ergänzen,
  einschließlich Speichern und Wiederladen des portablen JSON-Profils.
- Bestehende Version-1-Profile ohne Feld unverändert akzeptieren.

## Abnahmekriterien

- Gleiches Profil und gleicher Pfad liefern in GUI und CLI denselben
  `fileNamePattern`-Befund.
- Die Meldung nennt den abgewiesenen Basisnamen; der Befund verwendet den
  leeren Datenpfad, `/fileNamePattern` und die Regelkennung `fileNamePattern`.
- Der Vergleich verwendet nur den Basisnamen und ist vollständig sowie
  standardmäßig case-sensitive; bei SFTP zählt der entfernte Basisname.
- Fehlender Dateiname-Check lässt bestehende Profile unverändert bestehen.
- Ein syntaktisch ungültiges Muster wird als Profilfehler behandelt und nicht
  stillschweigend ignoriert.
- Der visuelle CSV-Editor kann das Muster erstellen, bearbeiten und erneut
  laden.
- Dokumentation erklärt Regex-Syntax, Basisnamenverhalten und Beispiele für
  GUI/CLI.

## Umsetzungsschritte

1. Befundvertrag und Regex-Semantik festlegen; bestehende Profilkompatibilität
   absichern.
2. Profilparser und Validator erweitern.
3. CSV-Editor und GUI-Befundansicht anbinden.
4. Beispielprofile und CLI-/GUI-Anleitung aktualisieren.
5. Iterationsnachweis und Screenshots nach der Umsetzung aktualisieren.

## Naechste sinnvolle Ausbaustufen

Nach dem Dateinamencheck ist ein lokaler Stapellauf der nächste größere
Nutzen für beide Oberflächen:

- **CLI:** mehrere Dateien oder einen Ordner mit einem Profil prüfen,
  maschinenlesbare Einzelergebnisse plus Zusammenfassung schreiben und einen
  klar dokumentierten aggregierten Exitcode liefern.
- **GUI:** mehrere CSV-Dateien oder einen Ordner auswählen, Status und Anzahl
  der Befunde je Datei in einer Tabelle zeigen und Details für die
  ausgewählte Datei anbieten.
- **Gemeinsamer Vertrag:** Reihenfolge, Fehler beim Lesen einzelner Dateien,
  Abbruchverhalten und Reportformat einmal festlegen und von beiden
  Oberflächen nutzen.

Danach bieten sich Profilprüfung ohne Dateneingabe (CLI lint und GUI
Profilvorschau) sowie bessere Hilfe und Beispiele für Regex-Muster an.

## Ausserhalb des Umfangs

- Regeln für JSON-Schema- oder Fixed-Width-Dateien.
- Wildcards oder Glob-Muster neben regulären Ausdrücken.
- Groß-/Kleinschreibungsnormalisierung des Dateinamens.
- Änderung des Reportvertrags außer dem neuen stabilen Regelcode.
