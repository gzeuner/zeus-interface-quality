# ADR 0005: HTTP-Adapter als lokale Vertragsprüfung

Status: accepted for implementation

## Entscheidung

Iteration 3 ergänzt einen HTTP-Adapter auf Basis des JDK `HttpClient`. Ein
lokales HTTP-Profil beschreibt die Anfrage und die erwartete JSON-Antwort:

```text
local JSON request + local HTTP profile -> optional request schema
                                        -> HTTP request
                                        -> status/header checks
                                        -> local JSON response schema
                                        -> project-owned ValidationResult
```

Die CLI bleibt der Einstiegspunkt. Der Adapter folgt keinen Redirects,
verwendet nur lokale Schema-Dateien und gibt weder Header-Werte noch
Response-Payloads aus.

## Begründung

Nach drei lokalen Dateiformaten ist HTTP der nächste sinnvolle Nachweis, weil
eine Schnittstelle hier nicht nur aus ihrem JSON-Body besteht. Methode, URL,
Status und Header gehören ebenfalls zum Vertrag. Der JDK-Client vermeidet eine
zusätzliche Transportbibliothek und hält die Auslieferung als einzelnes
Maven-Modul klein.

Der Request wird – sofern ein `requestSchema` angegeben ist – vor dem Versand
geprüft. Eine Vertragsverletzung der Antwort bleibt ein normales
Validierungsergebnis (`INVALID`); ein nicht erreichbarer Dienst oder ein
ungültiges Profil bleibt ein operativer Fehler (`2`). So bleibt die
Unterscheidung aus den Dateiadaptern erhalten.

## Abgrenzung

Iteration 3 ist kein OpenAPI-Parser, kein API-Testframework und kein
Credential-Manager. OAuth, SFTP/FTP, Redirect-Policies, Streaming,
Mehrdatei-Regeln und automatische Profilableitung bleiben spätere
Entscheidungen.
