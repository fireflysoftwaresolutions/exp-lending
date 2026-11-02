/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.experience.lending.web.openapi;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Yaml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OriginalExperienceLendingProducerCoverageIT {
    private static final Set<String> VERBS=Set.of("get","post","put","patch","delete","head","options","trace");
    private static final List<String> ADDITION=List.of("/api/v1/experience/lending/application-intakes","post","createDraftApplicationIntake");
    @Test void preservesEveryOriginalPathVerbAndOperationIdWithExactlyOneIntakeAddition() throws Exception {
        var mapper=Yaml.mapper().copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        var original=mapper.readTree(bounded("lending.experience.producer.original"));
        var produced=mapper.readTree(bounded("lending.experience.producer.candidate"));
        var before=operations(original);var after=operations(produced);
        assertThat(before).isNotEmpty().doesNotContain(ADDITION);
        var expected=new HashSet<>(before);assertThat(expected.add(ADDITION)).isTrue();
        assertThat(after).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(produced.path("components").path("schemas").path("DraftApplicationReceipt").path("properties")
            .path("intentDigest").path("type").asText()).isEqualTo("string");
        var operation=produced.path("paths").path(ADDITION.getFirst()).path("post");
        assertThat(operation.path("security").findValues("lendingExperienceIntakeBearer")).hasSize(1);
        var responses=operation.path("responses");
        assertThat(responses.properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("200","201");
        for(String status:List.of("200","201"))assertThat(responses.path(status).path("content").path("application/json")
            .path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/DraftApplicationReceipt");
        var prior=mapper.readTree(bounded("lending.experience.producer.priorProduced"));
        var expectedDocument=(com.fasterxml.jackson.databind.node.ObjectNode)prior.deepCopy();
        var actualDocument=(com.fasterxml.jackson.databind.node.ObjectNode)produced.deepCopy();
        expectedDocument.remove("servers");actualDocument.remove("servers");
        ((com.fasterxml.jackson.databind.node.ObjectNode)actualDocument.path("paths").path(ADDITION.getFirst()).path("post"))
            .set("responses",expectedDocument.path("paths").path(ADDITION.getFirst()).path("post").path("responses").deepCopy());
        assertThat(actualDocument).isEqualTo(expectedDocument);
        var receipt=new LinkedHashMap<String,Object>();receipt.put("originalOperations",before);receipt.put("producedOperations",after);
        receipt.put("exactNewOperation",ADDITION);receipt.put("nativeYamlSemanticComparison",true);receipt.put("exactTypedCreate201Replay200",true);receipt.put("onlySuccessResponseMapAndServerUrlChanged",true);
        Files.writeString(Path.of(required("lending.experience.producer.coverage")),new com.fasterxml.jackson.databind.ObjectMapper()
            .writerWithDefaultPrettyPrinter().writeValueAsString(receipt)+"\n");
    }
    private static Set<List<String>> operations(JsonNode document) {
        assertThat(document.path("openapi").asText()).startsWith("3.");
        assertThat(document.path("paths").isObject()).isTrue();var result=new TreeSet<List<String>>(Comparator.comparing(Object::toString));
        document.path("paths").properties().forEach(path->path.getValue().properties().forEach(operation->{
            if(VERBS.contains(operation.getKey())) {
                String id=operation.getValue().path("operationId").asText();assertThat(id).isNotBlank();
                assertThat(result.add(List.of(path.getKey(),operation.getKey(),id))).isTrue();
            }
        }));return result;
    }
    private static byte[] bounded(String property) throws Exception {
        var path=Path.of(required(property));assertThat(Files.size(path)).isBetween(1L,2097152L);return Files.readAllBytes(path);
    }
    private static String required(String property) {String value=System.getProperty(property);assertThat(value).isNotBlank();return value;}
}
