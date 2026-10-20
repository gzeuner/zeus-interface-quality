# SFTP-Profilvertrag v1

[Deutsch](sftp-profile-v1.de.md) | [English](sftp-profile-v1.md)

Nur CLI: Die Desktop-GUI und `validate --local-only` lehnen SFTP-Profile vor
Transportzugriff ab. Fuer lokale POS-Pruefungen ist SFTP nicht erforderlich.
Hierfuer keinen produktiven Server verwenden; Transporttests duerfen gegen
eine bewusst konfigurierte temporaere Testinstanz erfolgen.
Explizit aktivierte Quarantaene veraendert entfernte Dateien.

Der Adapter prueft genau eine entfernte SFTP-Datei. Das Profil liegt lokal und
wird mit `--schema` oder `--profile` ausgewaehlt:

```json
{
  "format": "sftp",
  "version": 1,
  "host": "sftp.example.org",
  "port": 22,
  "username": "quality-check",
  "remotePath": "/inbox/delivery.json",
  "knownHosts": "known_hosts",
  "privateKey": "id_ed25519",
  "privateKeyPassphraseEnv": "SFTP_KEY_PASSPHRASE",
  "contentFormat": "json",
  "contentProfile": "delivery.schema.json",
  "quarantineRemoteDirectory": "/quarantine",
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "stabilityChecks": 2,
  "stabilityDelaySeconds": 1,
  "maxAttempts": 2,
  "retryDelaySeconds": 1,
  "connectTimeoutSeconds": 10
}
```

## Felder

- `format`: `sftp`; `version`: `1`.
- `host`, `port`, `username`: Testendpunkt und Anmeldung; Standardport `22`.
- `remotePath`: absoluter POSIX-Pfad zu genau einer regulaeren entfernten
  Datei. `.`, `..`, Backslashes, NUL und abschliessende Slashes werden abgelehnt.
- `knownHosts`, `privateKey`: erforderliche relative Pfade auf regulaere
  Dateien unterhalb des Profilordners. Nach aussen zeigende Symlinks sind
  nicht erlaubt.
- `privateKeyPassphraseEnv`: optionale Umgebungsvariable fuer die Passphrase;
  deren Wert wird nicht in den Report geschrieben.
- `contentFormat`: `json`, `csv` oder `fixed-width`.
- `contentProfile`: erforderliches relatives lokales Inhaltsprofil;
  JSON verwendet JSON Schema, CSV/Fixed-Width ihren jeweiligen Profilvertrag.
- `sha256`: optionaler hexadezimaler Digest aus 64 Zeichen; wird vor der
  Inhaltsvalidierung mit den heruntergeladenen Bytes verglichen.
- `stabilityChecks`: Anzahl Beobachtungen von Dateityp, Groesse und
  Aenderungszeit. Defaults: `2` Beobachtungen, `1` Sekunde Beobachtungsabstand,
  `2` Versuche, `1` Sekunde Wiederholungsabstand, `10` Sekunden Verbindungszeit.
  Akzeptierte Wertebereiche sind durch die Implementierung begrenzt.
- `quarantineRemoteDirectory`: optional. Nach ungueltigem Inhalt oder Hash
  wird die entfernte Datei in diesen Ordner umbenannt, mit UTC-Zeitstempel
  und `.invalid`-Suffix. Gueltige Dateien und Betriebsfehler werden nicht
  in Quarantaene verschoben.

Passwortanmeldung, OpenSSH-Konfiguration/-Agent, entfernte Globs,
Verzeichnis-Polling, Mehrdateiauswahl und automatisches Erfolgsarchiv sind
nicht Teil dieses Vertrags.

## Befehl und Ergebnisse

Windows PowerShell, nur fuer eine konfigurierte temporaere Testinstanz:

```powershell
java -jar .\target\zeus-interface-quality-0.1.0-SNAPSHOT.jar validate --input-format sftp --profile .\test-sftp\sftp-profile.json --report json
```

Kein `--input`: Der entfernte Dateipfad kommt aus dem Profil.
Gueltige Datei und gueltiger Inhalt ergeben `VALID`, Exit `0`.
Hashabweichung oder Inhaltsverletzung ergeben `INVALID`, Exit `1`.
Inhaltsbefunde erhalten `/content/` als Praefix; Hashfehler stehen unter
`/remote/hash`. Profil-, Verbindungs-, Datei-, Stabilitaets-, Download- oder
Quarantaenefehler sind Betriebsfehler mit Exit `2`.

Der Download wird temporaer lokal gespeichert und nach dem Lauf entfernt.
Der Report enthaelt Befunde, Pfade und Regelkennungen, nicht den vollstaendigen
heruntergeladenen Inhalt, die Schluesselpassphrase oder Zugangsdaten.
