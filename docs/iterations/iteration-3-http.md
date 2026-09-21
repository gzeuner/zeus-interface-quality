# Iteration 3: HTTP-Adapter

Status: abgeschlossen

## Ziel

Die bisherige Prüfung lokaler JSON-, CSV- und Fixed-Width-Dateien um einen
reproduzierbaren HTTP-Vertrag erweitern. Dabei sollen Transport und
Datenvertrag gemeinsam sichtbar werden, ohne den Kern an Spring Boot oder
eine externe OpenAPI-Laufzeit zu binden.

## Umgesetzt

- HTTP-Adapter auf Basis des JDK `java.net.http.HttpClient`;
- HTTP-Profil Version 1 mit Methode, URL, Request-Headern, Status, Response-
  Headern, Timeout und lokalen Schema-Pfaden;
- optionale Request-Schema-Prüfung vor dem Netzwerkaufruf;
- verpflichtende JSON-Response-Schema-Prüfung;
- UTF-8- und JSON-Prüfung der Response;
- keine automatische Redirect-Verfolgung;
- HTTP-Status-, Header- und Response-Schema-Verletzungen als `INVALID`;
- Transport-, Profil- und Request-Fehler als Exit `2`;
- bestehender Text-/JSON-Report und Exit-Code-Vertrag unverändert;
- AUTO-Erkennung über `format: "http"`;
- lokale Loopback-Tests für gültige Requests, Request-Fehler, falschen Status,
  fehlende Header, falschen Response-Typ, kaputtes Response-JSON und
  Transportfehler.

## Bewusste Grenzen

- kein OpenAPI-Parser und keine automatische Ableitung eines HTTP-Profils;
- keine SFTP-/FTP-Transporte;
- keine OAuth- oder Secret-Verwaltung im Profil;
- keine Redirects, kein Streaming und keine Mehrdatei-Regeln;
- Response-Verträge sind in Iteration 3 lokale JSON Schemas, nicht ein
  vollständiger OpenAPI-Vertrag;
- Reporte enthalten keine Request-Header-Werte und keine Response-Payloads.

## Abnahmekriterien

Eine gültige lokale HTTP-Interaktion endet mit Exit `0`. Ein gültiger Request
mit falschem Status, fehlendem Pflicht-Header oder nicht passender JSON-
Response endet mit Exit `1` und stabilen HTTP-Pfaden. Ein ungültiger Request
wird vor dem Versand abgewiesen. Netzwerk-, Timeout-, Profil- und Datei-
fehler enden mit Exit `2` ohne fachlichen Report. Der bestehende Testbestand
bleibt grün; die Iteration ergänzt sechs HTTP-Tests.
