package de.tinytool.quality.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tinytool.quality.core.Finding;
import de.tinytool.quality.core.ValidationResult;

import java.nio.file.Path;
import java.util.LinkedHashSet;

/** Writes validation findings using SARIF 2.1.0 for CI code-scanning systems. */
public final class SarifReportWriter {
    private final ObjectMapper mapper = new ObjectMapper();

    public String write(ValidationResult result, Path input) throws JsonProcessingException {
        ObjectNode root = mapper.createObjectNode();
        root.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        root.put("version", "2.1.0");
        ObjectNode run = root.putArray("runs").addObject();
        ObjectNode tool = run.putObject("tool").putObject("driver");
        tool.put("name", "ZEUS Interface Quality");
        tool.put("informationUri", "https://tiny-tool.de/");
        var ruleIds = new LinkedHashSet<String>();
        for (Finding finding : result.findings()) ruleIds.add(finding.keyword());
        ArrayNode rules = tool.putArray("rules");
        for (String ruleId : ruleIds) {
            rules.addObject().put("id", ruleId).putObject("shortDescription").put("text", ruleId);
        }
        run.putObject("properties").put("status", result.status().name());
        ArrayNode results = run.putArray("results");
        for (Finding finding : result.findings()) {
            ObjectNode item = results.addObject();
            item.put("ruleId", finding.keyword());
            item.put("ruleIndex", indexOf(ruleIds, finding.keyword()));
            item.put("level", "error");
            item.putObject("message").put("text", finding.message());
            item.putObject("properties").put("instancePath", finding.instancePath())
                    .put("schemaPath", finding.schemaPath());
            ObjectNode location = item.putArray("locations").addObject();
            ObjectNode physical = location.putObject("physicalLocation");
            if (input != null) {
                physical.putObject("artifactLocation").put("uri", input.toAbsolutePath().normalize().toUri().toASCIIString());
            }
            location.putArray("logicalLocations").addObject()
                    .put("fullyQualifiedName", finding.instancePath());
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private static int indexOf(LinkedHashSet<String> ruleIds, String key) {
        int index = 0;
        for (String ruleId : ruleIds) {
            if (ruleId.equals(key)) return index;
            index++;
        }
        throw new IllegalArgumentException("Unknown SARIF rule " + key);
    }
}
