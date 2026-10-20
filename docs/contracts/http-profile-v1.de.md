# HTTP-Profilvertrag v1

[Deutsch](http-profile-v1.de.md) | [English](http-profile-v1.md)

Dieser Adapter ist nur ueber die CLI verfuegbar: Desktop-GUI und
`validate --local-only` lehnen HTTP-Profile vor Transportzugriff ab.
Fuer lokale POS-Pruefungen wird er nicht benoetigt. Transporttests in diesem
Projekt nur gegen bewusst konfigurierte temporaere Testendpunkte ausfuehren,
nicht gegen Produktivdienste.

Der Adapter sendet einen optionalen lokalen JSON-Anfragekoerper und prueft
eine JSON-Antwort. Das lokale Profil hat `format: "http"` und `version: 1`.

```json
{
  "format": "http",
  "version": 1,
  "method": "POST",
  "url": "http://127.0.0.1:8080/deliveries",
  "requestHeaders": {
    "Content-Type": "application/json",
    "Accept": "application/json"
  },
  "requestSchema": "delivery-request.schema.json",
  "expectedStatus": 201,
  "requiredResponseHeaders": ["Content-Type", "X-Request-Id"],
  "responseContentType": "application/json",
  "responseSchema": "delivery-response.schema.json",
  "timeoutSeconds": 5
}
```

## Felder

- `method`: `GET`, `POST`, `PUT`, `PATCH` oder `DELETE`.
- `url`: `http`/`https`, mit Host, ohne eingebettete Zugangsdaten oder Fragment.
- `requestHeaders`: optional. Gueltige HTTP-Token als Namen, einzeilige
  Zeichenfolgen als Werte.
- `requestSchema`: optionaler relativer Schemapfad unterhalb des Profilordners.
  Falls gesetzt, ist `--input` erforderlich und wird vor dem Senden geprueft.
- `expectedStatus`: genau ein erwarteter Statuscode aus `100..599`.
- `requiredResponseHeaders`: optionale Liste erforderlicher Header, Vergleich
  ohne Gross-/Kleinschreibung.
- `responseContentType`: optional; vergleicht den Medientyp ohne Parameter
  wie `charset=utf-8`.
- `responseSchema`: erforderlicher relativer lokaler Schemapfad unterhalb des
  Profilordners. Der Antwortkoerper muss UTF-8-JSON sein und wird dagegen geprueft.
- `timeoutSeconds`: optional, Default `10`, Bereich `1..120`.

Schemareferenzen sind nur lokal erlaubt. Redirects werden nicht verfolgt.
Anfrageheaderwerte und Antwortkoerper werden nicht in Report oder Fehlerausgabe
geschrieben.

## Ergebnisse

Der [Reportvertrag](validation-report-v1.de.md) bleibt unveraendert.
Vertragsverletzungen liefern `INVALID`, beispielsweise mit:

- `/request/body/quantity` fuer Schemafehler der Anfrage;
- `/response/status` fuer unerwarteten Status;
- `/response/headers/X-Request-Id` fuer fehlende Antwortheader;
- `/response/body/accepted` fuer Schemafehler der Antwort.

Transportfehler, ungueltige Profile, unlesbare Dateien, fehlerhaftes Anfrage-JSON
und Zeitueberschreitungen liefern Exit `2` ohne Pruefreport.
Ein fehlerhafter Antwortkoerper ist dagegen eine Antwortvertragsverletzung
und liefert `INVALID` mit Exit `1`.
