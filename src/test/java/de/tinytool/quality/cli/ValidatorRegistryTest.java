package de.tinytool.quality.cli;

import de.tinytool.quality.adapter.csv.CsvValidator;
import de.tinytool.quality.adapter.fixedwidth.FixedWidthValidator;
import de.tinytool.quality.adapter.http.HttpValidator;
import de.tinytool.quality.adapter.jsonschema.JsonSchemaValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatorRegistryTest {

    private final ValidatorRegistry registry = new ValidatorRegistry(
            new JsonSchemaValidator(),
            new CsvValidator(),
            new FixedWidthValidator());

    @Test
    void detectsCsvFixedWidthHttpAndSftpFromProfileFormat(@TempDir Path directory) throws Exception {
        Path csv = Files.writeString(directory.resolve("csv.json"), "{\"format\":\"csv\"}");
        Path fixed = Files.writeString(directory.resolve("fw.json"), "{\"format\":\"fixed-width\"}");
        Path http = Files.writeString(directory.resolve("http.json"), "{\"format\":\"http\"}");
        Path sftp = Files.writeString(directory.resolve("sftp.json"), "{\"format\":\"sftp\"}");
        Path json = Files.writeString(directory.resolve("schema.json"), "{\"type\":\"object\"}");

        assertThat(registry.detect(csv)).isEqualTo(InputFormat.CSV);
        assertThat(registry.detect(fixed)).isEqualTo(InputFormat.FIXED_WIDTH);
        assertThat(registry.detect(http)).isEqualTo(InputFormat.HTTP);
        assertThat(registry.detect(sftp)).isEqualTo(InputFormat.SFTP);
        assertThat(registry.detect(json)).isEqualTo(InputFormat.JSON);
    }

    @Test
    void explicitFormatWinsOverProfileDetection(@TempDir Path directory) throws Exception {
        Path csv = Files.writeString(directory.resolve("csv.json"), "{\"format\":\"csv\"}");

        assertThat(registry.validatorFor(InputFormat.JSON, csv))
                .isInstanceOf(JsonSchemaValidator.class);
        assertThat(registry.validatorFor(InputFormat.AUTO, csv))
                .isInstanceOf(CsvValidator.class);
        assertThat(registry.validatorFor(InputFormat.HTTP, csv))
                .isInstanceOf(HttpValidator.class);
        assertThat(registry.validatorFor(InputFormat.SFTP, csv))
                .isInstanceOf(de.tinytool.quality.adapter.sftp.SftpValidator.class);
    }

    @Test
    void fallsBackToJsonForUnreadableProfileContent(@TempDir Path directory) throws Exception {
        Path malformed = Files.writeString(directory.resolve("malformed.json"), "{");

        assertThat(registry.detect(malformed)).isEqualTo(InputFormat.JSON);
        assertThat(registry.validatorFor(InputFormat.AUTO, malformed))
                .isInstanceOf(JsonSchemaValidator.class);
    }
}
