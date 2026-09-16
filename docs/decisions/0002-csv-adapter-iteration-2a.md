# ADR 0002: CSV als erster Dateiadapter

Status: accepted for implementation

## Entscheidung

Iteration 2a ergänzt einen lokalen CSV-Adapter. Das bestehende
`Validator`-Interface und der Report-Vertrag bleiben formatunabhängig:

```text
local CSV input + local CSV profile -> CSV adapter
                                    -> project-owned ValidationResult
                                    -> text or JSON report
```

Das Profil ist in Version 1 ein eigenes JSON-Dokument. Es beschreibt Transport
und Spaltenregeln, ist aber nicht Teil des JSON-Schema-Adapters.

## Begründung

CSV ist bei Datei-Schnittstellen verbreitet, besitzt aber keine eingebaute,
einheitliche Schema-Sprache für Zeichencodierung, Header, Spaltenreihenfolge und
fachlich relevante Typen. Ein kleines explizites Profil macht diese Annahmen
sichtbar und testbar. Der Parser behandelt insbesondere Separatoren und
quotierte Felder, während die fachlichen Findings im gemeinsamen Kernmodell
enden.

Apache Commons CSV wird nur im Adapter verwendet. Ein Spring-Boot-Modul, ein
anderer Parser oder ein anderer Auslieferungsweg kann später ergänzt werden,
ohne den Kernvertrag zu verändern.

## Abgrenzung

Version 1 prüft lokale Dateien synchron und vollständig im Speicher. Sie
unterstützt keine FTP-/SFTP-Verbindung, keine Mehrdatei-Regeln und keine
Fixed-Width- bzw. COBOL-/RPG-Layoutdefinitionen. Diese Themen bleiben eigene
Entscheidungen, damit die nächste Iteration nicht stillschweigend zu einem
universellen Formatmodell anwächst.
