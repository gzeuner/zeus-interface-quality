# ADR 0003: Fixed-Width als neutraler Legacy-Adapter

Status: accepted for implementation

## Entscheidung

Fixed-Width-Dateien werden über ein eigenes Version-1-Profil geprüft. Das
Profil beschreibt Datensatzlänge, Positionen, Feldtypen und Regeln. Eine
optionale Herkunftsangabe wie COBOL oder RPG bleibt Metadatum:

```text
local fixed-width input + local layout profile -> fixed-width adapter
                                             -> project-owned ValidationResult
                                             -> text or JSON report
```

Der Adapter kennt keine COBOL- oder RPG-Syntax. Dadurch bleibt der Kern
unabhängig von einer konkreten Legacy-Plattform und kann später mit Profilen
aus DDL, Copybooks, DDS oder RPGLE-Definitionen verwendet werden.

## Begründung

Die gemeinsame technische Eigenschaft dieser Dateien ist zunächst das
Layout: feste Positionen und feste Längen. Ein profilbasiertes Modell deckt
diesen stabilen Kern ab, ohne vorschnell unterschiedliche Quellsprachen in
einem Parser zu vermischen. Die Typ- und Pflichtwertregeln werden in Findings
übersetzt, die mit JSON und CSV identisch weiterverarbeitet werden können.

## Abgrenzung

Version 1 liest lokale Dateien vollständig und misst Positionen nach der
Zeichendecodierung. Sie liefert keine automatische DDL-/Copybook-/DDS-Analyse,
keine Bytepositionssemantik und keinen FTP-/SFTP-Zugriff. Diese Erweiterungen
können später als eigene Adapter oder Profilgeneratoren ergänzt werden.
