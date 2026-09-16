# ADR 0004: Gemeinsame Tabellenprüfungen

Status: accepted

## Entscheidung

CSV- und Fixed-Width-Adapter teilen Typ- und Constraint-Prüfungen in
`de.tinytool.quality.adapter.support`. Der Kernvertrag
(`ValidationResult`, `Finding`, `Validator`) bleibt formatneutral.

Die CLI wählt den Adapter über eine `ValidatorRegistry`. Ohne explizites
`--input-format` entscheidet das Profilfeld `format` (`csv` oder
`fixed-width`). JSON Schema bleibt der Fallback.

## Begründung

Iteration 2a und 2b haben dieselben Feldregeln zweimal implementiert.
Eine gemeinsame Prüflogik verhindert Drift bei `maxLength`, `maximum`
und späteren Typen, ohne CSV- und Fixed-Width-Profile zu einem
Universalformat zu verschmelzen.

## Abgrenzung

Bytepositionen, EBCDIC und automatische Profilerzeugung bleiben eigene
Entscheidungen. `--schema` bleibt als Alias zu `--profile` erhalten.
