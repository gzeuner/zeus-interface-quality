# Release checklist / Release-Checkliste

This checklist prepares a release candidate. It does not publish a release.
/ Diese Checkliste bereitet einen Release-Kandidaten vor; sie veröffentlicht
keinen Release.

## English

1. Agree on the release scope and version. Remove `-SNAPSHOT` from `pom.xml`
   and make the Git tag `v<version>` match the Maven version exactly.
2. Start from a clean checkout and run `./mvnw -B clean package` (or
   `.\mvnw.cmd -B clean package` on Windows). Require the full test suite to
   pass on both CI operating systems.
3. Run the packaged JAR with `--version`; confirm it prints the release version.
   Check the valid and invalid CLI examples and their expected exit codes.
4. Inspect the JAR contents for the application classes, bundled runtime
   dependencies, `LICENSE` and `NOTICE`. Keep the GUI startup check on a machine
   with a desktop session.
5. Rebuild twice from the same clean source and toolchain, then compare SHA-256
   hashes. Record the Java/Maven environment and the exact source commit with
   the candidate. A matching local hash is a useful check, not independent
   proof that every operating system produces identical bytes.
6. Review both guides, contracts, changelog, dependency notices, and known
   limitations. Only after that review should a version tag or public release
   be created.

CI rebuilds the package a second time on each operating system and compares
the archive byte for byte. It attaches the packaged JAR and SHA-256 file to each
successful Linux and Windows workflow run. These are build artifacts for
review; a GitHub Release is not created automatically.

## Deutsch

1. Releaseumfang und Version festlegen. `-SNAPSHOT` aus der Version in der
   `pom.xml` entfernen. Das Git-Tag `v<version>` muss exakt der Maven-Version
   entsprechen.
2. Von einem sauberen Checkout `.\mvnw.cmd -B clean package` (Windows) oder
   `./mvnw -B clean package` ausführen. Die vollständige Testsuite muss auf
   beiden CI-Betriebssystemen bestehen.
3. Das paketierte JAR mit `--version` starten und die Release-Version prüfen.
   Gültige und ungültige CLI-Beispiele samt erwarteten Exitcodes ausführen.
4. JAR-Inhalt prüfen: Anwendungsklassen, gebündelte Laufzeitabhängigkeiten,
   `LICENSE` und `NOTICE`. Den GUI-Start auf einem Rechner mit Desktop prüfen.
5. Zweimal aus demselben sauberen Quellstand und Toolchain bauen und die
   SHA-256-Prüfsummen vergleichen. Java-/Maven-Umgebung und Quellcommit beim
   Kandidaten dokumentieren. Gleiche lokale Prüfsummen sind ein nützlicher
   Test, aber kein unabhängiger Beleg für identische Dateien auf allen
   Betriebssystemen.
6. Beide Anleitungen, Verträge, Changelog, Abhängigkeitshinweise und bekannte
   Grenzen prüfen. Erst danach ein Versions-Tag oder einen öffentlichen Release
   erstellen.

CI baut das Paket auf jedem Betriebssystem ein zweites Mal und vergleicht die
Dateien bytegenau. JAR und SHA-256-Datei werden an erfolgreiche Linux- und
Windows-Workflowläufe angehängt. Diese Artefakte dienen der Prüfung; ein
GitHub-Release wird nicht automatisch erstellt.
