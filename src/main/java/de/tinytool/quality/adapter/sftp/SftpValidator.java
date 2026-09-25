package de.tinytool.quality.adapter.sftp;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tinytool.quality.adapter.csv.CsvValidator;
import de.tinytool.quality.adapter.fixedwidth.FixedWidthValidator;
import de.tinytool.quality.adapter.jsonschema.JsonSchemaValidator;
import de.tinytool.quality.adapter.support.LocalFiles;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationException;
import de.tinytool.quality.core.ValidationResult;
import de.tinytool.quality.core.Validator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Downloads one stable SFTP file, verifies its integrity, and delegates its
 * contents to the existing JSON, CSV, or fixed-width validator.
 */
public final class SftpValidator implements Validator {

    private static final DateTimeFormatter QUARANTINE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                    .withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final SftpTransportFactory transportFactory;
    private final Validator jsonValidator;
    private final Validator csvValidator;
    private final Validator fixedWidthValidator;

    public SftpValidator() {
        this(
                new ObjectMapper(),
                new JschSftpTransportFactory(),
                new JsonSchemaValidator(),
                new CsvValidator(),
                new FixedWidthValidator());
    }

    SftpValidator(SftpTransportFactory transportFactory) {
        this(
                new ObjectMapper(),
                transportFactory,
                new JsonSchemaValidator(),
                new CsvValidator(),
                new FixedWidthValidator());
    }

    SftpValidator(
            ObjectMapper objectMapper,
            SftpTransportFactory transportFactory,
            Validator jsonValidator,
            Validator csvValidator,
            Validator fixedWidthValidator
    ) {
        this.objectMapper = objectMapper;
        this.transportFactory = transportFactory;
        this.jsonValidator = jsonValidator;
        this.csvValidator = csvValidator;
        this.fixedWidthValidator = fixedWidthValidator;
    }

    @Override
    public ValidationResult validate(Path input, Path schema) throws ValidationException {
        if (input != null) {
            throw new ValidationException("SFTP validation does not accept --input; the remotePath is in the profile");
        }

        Path profileFile = LocalFiles.requireRegularFile(schema, "SFTP profile");
        SftpProfile profile = SftpProfile.read(profileFile, objectMapper);
        Validator contentValidator = contentValidator(profile.contentFormat());

        Path temporaryDirectory = createTemporaryDirectory();
        Path temporaryFile = temporaryDirectory.resolve(fileName(profile.remotePath()));
        try {
            return downloadAndValidate(profile, contentValidator, temporaryFile);
        } finally {
            deleteTemporaryDirectory(temporaryDirectory);
        }
    }

    private ValidationResult downloadAndValidate(
            SftpProfile profile,
            Validator contentValidator,
            Path temporaryFile
    ) throws ValidationException {
        SftpTransferException lastTransferFailure = null;
        for (int attempt = 1; attempt <= profile.maxAttempts(); attempt++) {
            try (SftpTransport transport = transportFactory.open(profile)) {
                SftpRemoteMetadata before = stableMetadata(transport, profile);
                deleteTemporaryFile(temporaryFile);
                transport.download(profile.remotePath(), temporaryFile);
                SftpRemoteMetadata after = transport.stat(profile.remotePath());
                if (!before.sameContentAs(after)) {
                    throw new SftpTransferException(
                            "Remote SFTP file changed while it was being downloaded");
                }

                if (profile.expectedSha256() != null) {
                    String actualSha256 = sha256(temporaryFile);
                    if (!profile.expectedSha256().equalsIgnoreCase(actualSha256)) {
                        List<Finding> findings = List.of(new Finding(
                                "/remote/hash",
                                "/sha256",
                                "sha256",
                                "SHA-256-Prüfsumme stimmt nicht mit dem SFTP-Profil überein"));
                        quarantineIfConfigured(transport, profile);
                        return ValidationResult.invalid(findings);
                    }
                }

                ValidationResult contentResult = contentValidator.validate(
                        temporaryFile, profile.contentProfile());
                if (contentResult.isValid()) {
                    return contentResult;
                }

                quarantineIfConfigured(transport, profile);
                return prefixContentFindings(contentResult);
            } catch (SftpTransferException e) {
                lastTransferFailure = e;
                if (attempt == profile.maxAttempts()) {
                    throw e;
                }
                sleep(profile.retryDelay(), "SFTP retry delay");
            }
        }

        throw lastTransferFailure == null
                ? new ValidationException("SFTP validation did not complete")
                : lastTransferFailure;
    }

    private static SftpRemoteMetadata stableMetadata(
            SftpTransport transport,
            SftpProfile profile
    ) throws ValidationException {
        SftpRemoteMetadata first = requireRegularFile(
                transport.stat(profile.remotePath()), profile.remotePath());
        for (int check = 1; check < profile.stabilityChecks(); check++) {
            sleep(profile.stabilityDelay(), "SFTP stability check delay");
            SftpRemoteMetadata current = requireRegularFile(
                    transport.stat(profile.remotePath()), profile.remotePath());
            if (!first.sameContentAs(current)) {
                throw new SftpTransferException(
                        "Remote SFTP file is still changing; stable snapshot was not established");
            }
        }
        return first;
    }

    private static SftpRemoteMetadata requireRegularFile(
            SftpRemoteMetadata metadata,
            String remotePath
    ) throws ValidationException {
        if (metadata == null || !metadata.regularFile()) {
            throw new SftpTransferException("Remote SFTP path is not a regular file: " + remotePath);
        }
        return metadata;
    }

    private static String sha256(Path file) throws ValidationException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ValidationException("Could not calculate downloaded SFTP file hash: "
                    + LocalFiles.safeMessage(e), e);
        }
    }

    private static void quarantineIfConfigured(
            SftpTransport transport,
            SftpProfile profile
    ) throws ValidationException {
        if (profile.quarantineRemoteDirectory() == null) {
            return;
        }
        String target = joinRemoteDirectory(
                profile.quarantineRemoteDirectory(),
                fileName(profile.remotePath()))
                + "."
                + QUARANTINE_TIMESTAMP.format(Instant.now())
                + ".invalid";
        try {
            transport.move(profile.remotePath(), target);
        } catch (SftpTransferException e) {
            throw new ValidationException("Could not quarantine invalid SFTP file: "
                    + LocalFiles.safeMessage(e), e);
        }
    }

    private ValidationResult prefixContentFindings(ValidationResult result) {
        List<Finding> findings = new ArrayList<>();
        for (Finding finding : result.findings()) {
            findings.add(new Finding(
                    prefix(finding.instancePath(), "/content"),
                    prefix(finding.schemaPath(), "/content/profile"),
                    finding.keyword(),
                    finding.message()));
        }
        return ValidationResult.invalid(findings);
    }

    private Validator contentValidator(String contentFormat) throws ValidationException {
        return switch (contentFormat) {
            case "json" -> jsonValidator;
            case "csv" -> csvValidator;
            case "fixed-width" -> fixedWidthValidator;
            default -> throw new ValidationException("Unsupported SFTP content format: " + contentFormat);
        };
    }

    private static String prefix(String path, String prefix) {
        if (path == null || path.isEmpty()) {
            return prefix;
        }
        return prefix + (path.startsWith("/") ? path : "/" + path);
    }

    private static Path createTemporaryDirectory() throws ValidationException {
        try {
            return Files.createTempDirectory("zeus-sftp-");
        } catch (IOException e) {
            throw new ValidationException("Could not create temporary SFTP download directory: "
                    + LocalFiles.safeMessage(e), e);
        }
    }

    private static void deleteTemporaryFile(Path file) throws ValidationException {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new ValidationException("Could not prepare temporary SFTP download: "
                    + LocalFiles.safeMessage(e), e);
        }
    }

    private static void deleteTemporaryDirectory(Path directory) {
        try (var files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary data is best-effort cleanup and never changes the report result.
                }
            });
        } catch (IOException ignored) {
            // Temporary data is best-effort cleanup and never changes the report result.
        }
    }

    private static void sleep(java.time.Duration duration, String description)
            throws ValidationException {
        if (duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValidationException(description + " was interrupted", e);
        }
    }

    private static String fileName(String remotePath) throws ValidationException {
        int separator = remotePath.lastIndexOf('/');
        String name = separator >= 0 ? remotePath.substring(separator + 1) : remotePath;
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            throw new ValidationException("SFTP remotePath must identify a file name");
        }
        return name;
    }

    private static String joinRemoteDirectory(String directory, String fileName) {
        return "/".equals(directory) ? "/" + fileName : directory + "/" + fileName;
    }
}
