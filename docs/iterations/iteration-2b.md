# Iteration 2b: Fixed-Width- und Legacy-Dateien prüfen

Status: abgeschlossen

## Ziel

Viele ältere Schnittstellen tauschen Datensätze mit festen Positionen aus.
Diese Iteration ergänzt dafür einen lokalen Adapter, ohne COBOL, RPG oder ein
bestimmtes Mainframe- beziehungsweise IBM-Produkt zur Voraussetzung zu machen.

## Umgesetzt

- eigener Fixed-Width-Adapter außerhalb des `core`-Pakets;
- Version-1-Profil mit Zeichencodierung und exakter Datensatzlänge;
- 1-basierte Startpositionen und Feldlängen;
- Erkennung überlappender oder außerhalb des Datensatzes liegender Felder;
- Standard-Paddingbehandlung über `trim` pro Feld;
- Regeln für `string`, `integer`, `decimal` und `email`;
- Pflichtwerte, Mindestlängen, Mindestwerte und reguläre Ausdrücke;
- stabile Findings mit Pfaden wie `/records/0/quantity`;
- strikte Zeichendecodierung, damit kaputte Eingaben nicht stillschweigend
  ersetzt werden;
- optionale Herkunftsmetadaten wie `sourceLanguage: COBOL` oder
  `sourceLanguage: RPG`;
- CLI-Auswahl über `--input-format fixed-width`;
- 55 automatisierte Tests erfolgreich.

## Bewusste Technologieoffenheit

`sourceLanguage` ist Dokumentation und keine Verzweigung im Prüfcode. Das
Profil kann deshalb eine aus COBOL- oder RPG-Definitionen abgeleitete Struktur
beschreiben, ohne dass ZEUS im ersten Schritt einen vollständigen Compiler oder
Parser für diese Sprachen benötigt.

Die Positionen werden in Version 1 nach der Zeichendecodierung gemessen. Das ist
für die getesteten zeichenbasierten Dateien eindeutig. Bytepositionen, EBCDIC-
spezifische Layoutregeln und die automatische Ableitung aus DDL-, COBOL- oder
RPG-Quelltext gehören bewusst in eine spätere Erweiterung.

## Reifegrad für Subartikel 2

CSV und Fixed-Width sind jetzt als zwei konkrete, lokale Dateipfade vorhanden.
Beide nutzen denselben Exit-Code- und Report-Vertrag, während ihre Profile die
jeweiligen Formatannahmen sichtbar machen. Damit ist die technische Grundlage
für den zweiten Subartikel belastbar genug. Transportadapter und automatische
Profilerzeugung bleiben dort als nächste Schritte beziehungsweise Ausblick
klar abgegrenzt.
