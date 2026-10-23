package de.tinytool.quality.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationResult;

import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashSet;

/** Writes validation findings using SARIF 2.1.0 for CI code-scanning systems. */
public final class SarifReportWriter {
    private final ObjectMapper mapper = new ObjectMapper();

    public String write(ValidationResult result, Path input) throws JsonProcessingException {
        return writeBatch(List.of(new FileResult(input, result, null)), result.status().name());
    }

    public String writeBatch(List<FileResult> files, String status) throws JsonProcessingException {
        ObjectNode root = mapper.createObjectNode();
        root.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        root.put("version", "2.1.0");
        ObjectNode run = root.putArray("runs").addObject();
        ObjectNode tool = run.putObject("tool").putObject("driver");
        tool.put("name", "ZEUS Interface Quality");
        tool.put("informationUri", "https://tiny-tool.de/");
        var ruleIds = new LinkedHashSet<String>();
        for (FileResult file : files) {
            if (file.errorMessage() != null) ruleIds.add("zeus.operationalError");
            if (file.result() != null) {
                for (Finding finding : file.result().findings()) ruleIds.add(finding.keyword());
            }
        }
        ArrayNode rules = tool.putArray("rules");
        for (String ruleId : ruleIds) {
            String description = "zeus.operationalError".equals(ruleId) ? "Operational validation error" : ruleId;
            rules.addObject().put("id", ruleId).putObject("shortDescription").put("text", description);
        }
        run.putObject("properties").put("status", status);
        ArrayNode results = run.putArray("results");
        for (FileResult file : files) {
            if (file.result() == null) {
                addOperationalError(results, file, ruleIds);
                continue;
            }
            for (Finding finding : file.result().findings()) addFinding(results, file.input(), finding, ruleIds);
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private static void addFinding(ArrayNode results, Path input, Finding finding, LinkedHashSet<String> ruleIds) {
            ObjectNode item = results.addObject();
            item.put("ruleId", finding.keyword());
            item.put("ruleIndex", indexOf(ruleIds, finding.keyword()));
            item.put("level", "error");
            item.putObject("message").put("text", finding.message());
            item.putObject("properties").put("instancePath", finding.instancePath())
                    .put("schemaPath", finding.schemaPath());
            ObjectNode location = addLocation(item, input);
            location.putArray("logicalLocations").addObject()
                    .put("fullyQualifiedName", finding.instancePath());
    }

    private static void addOperationalError(ArrayNode results, FileResult file, LinkedHashSet<String> ruleIds) {
        ObjectNode item = results.addObject();
        item.put("ruleId", "zeus.operationalError");
        item.put("ruleIndex", indexOf(ruleIds, "zeus.operationalError"));
        item.put("level", "error");
        item.putObject("message").put("text", file.errorMessage());
        addLocation(item, file.input());
    }

    private static ObjectNode addLocation(ObjectNode item, Path input) {
        ObjectNode location = item.putArray("locations").addObject();
        if (input != null) {
            location.putObject("physicalLocation").putObject("artifactLocation")
                    .put("uri", input.toAbsolutePath().normalize().toUri().toASCIIString());
        }
        return location;
    }

    private static int indexOf(LinkedHashSet<String> ruleIds, String key) {
        int index = 0;
        for (String ruleId : ruleIds) {
            if (ruleId.equals(key)) return index;
            index++;
        }
        throw new IllegalArgumentException("Unknown SARIF rule " + key);
    }

    public record FileResult(Path input, ValidationResult result, String errorMessage) { }
}
