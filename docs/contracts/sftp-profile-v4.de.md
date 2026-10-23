# SFTP-Profilvertrag v4: dateispezifische Lieferungsprüfung

Version 4 erweitert den korrelierten Lieferungsablauf aus v3. Eine Lieferung
besteht aus genau einer Hauptdatei und einer Begleitdatei. Beide werden mit
eigenem Inhaltsformat und Prüfprofil geprüft. Claim, Archivierung, Quarantäne,
stabiler Download und Liefer-ID verhalten sich wie in v3.

## Beispiel

```json
{
  "format": "sftp",
  "version": 4,
  "host": "sftp.example.test",
  "username": "quality-reader",
  "remoteDirectory": "/incoming",
  "filePattern": "^delivery-[A-Z0-9-]+\\.json$",
  "companionPattern": "^delivery-[A-Z0-9-]+\\.meta\\.csv$",
  "deliveryIdPattern": "^delivery-(?<deliveryId>[A-Z0-9-]+)(?:\\.meta)?\\.(?:json|csv)$",
  "claimRemoteDirectory": "/claim",
  "archiveRemoteDirectory": "/archive",
  "quarantineRemoteDirectory": "/quarantine",
  "knownHosts": "known_hosts",
  "privateKey": "id_ed25519",
  "contentFormat": "json",
  "contentProfile": "delivery.schema.json",
  "sha256": "<SHA-256 der Hauptdatei mit 64 Hex-Zeichen>",
  "companionContentFormat": "csv",
  "companionContentProfile": "delivery-metadata.csv-profile.json",
  "companionSha256": "<SHA-256 der Begleitdatei mit 64 Hex-Zeichen>",
  "stabilityChecks": 2,
  "stabilityDelaySeconds": 1,
  "maxAttempts": 2,
  "retryDelaySeconds": 1,
  "connectTimeoutSeconds": 10
}
```

Die Hash-Platzhalter müssen durch echte Hexadezimalwerte ersetzt oder die
jeweilige Eigenschaft weggelassen werden.

## Auswahl und Prüfung

- `version` muss `4`, `format` muss `sftp` sein.
- `filePattern`, `companionPattern` und ein `deliveryIdPattern` mit der
  benannten Gruppe `deliveryId` sind erforderlich. Ausgewählt werden genau eine
  reguläre Hauptdatei und eine reguläre Begleitdatei mit derselben nicht
  leeren ID.
- `contentFormat` und `contentProfile` prüfen die Hauptdatei.
  `companionContentFormat` und `companionContentProfile` prüfen die
  Begleitdatei. Unterstützt werden `json`, `csv` und `fixed-width`. Beide
  Profilpfade müssen auf reguläre Dateien innerhalb des SFTP-Profilordners
  zeigen.
- `sha256` gilt für die Hauptdatei. Das optionale `companionSha256` gilt für
  die Begleitdatei. Jeder Hash wird vor der Inhaltsprüfung mit den geladenen
  Bytes verglichen. Eine Abweichung ist ein `INVALID`-Befund; bei konfigurierter
  Quarantäne wird die gesamte beanspruchte Lieferung verschoben.
- Ein v4-Profil benötigt eine Begleitdatei. v1-, v2- und v3-Profile behalten
  ihr bisheriges Auswahl- und Prüfverhalten.

## Ablauf und Reports

Mit `claimRemoteDirectory` und `archiveRemoteDirectory` gelten die Regeln aus
v3 für Claim, Wiederaufnahme eines unterbrochenen Claims, Archivierung und
Quarantäne. Claim- und Archivordner müssen gemeinsam konfiguriert sein. Der
Claim-Ordner bleibt dem Verbraucher vorbehalten.

Der JSON-Report behält Status- und Befundfelder bei. Ein beanspruchter Lauf
enthält außerdem Liefer-ID, Anzahl geprüfter Dateien, Lebenszyklus und
Zielpfade. Ein falscher Companion-Hash verwendet den Datenpfad
`/remote/companion/hash` und den Profilpfad `/companionSha256`.

## Aufruf und Exitcodes

```text
java -jar target/zeus-interface-quality-0.1.0-SNAPSHOT.jar validate \
  --input-format sftp --schema path/to/sftp-profile-v4.json --report json
```

`--input` darf nicht angegeben werden. Eine gültige Lieferung liefert Exitcode
`0`; eine ungültige Auswahl, Prüfsumme oder Inhaltsprüfung liefert `1`;
ungueltige Profile und Transportfehler liefern `2`.
