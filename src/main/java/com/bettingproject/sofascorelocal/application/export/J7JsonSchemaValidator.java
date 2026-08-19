package com.bettingproject.sofascorelocal.application.export;

import com.bettingproject.sofascorelocal.domain.export.J7ExportError;
import com.bettingproject.sofascorelocal.domain.export.J7ExportException;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;

@Component
public final class J7JsonSchemaValidator {

    private static final String SCHEMA_RESOURCE =
            "/schemas/j7-canonical-event-export-v1.schema.json";

    private final ObjectMapper mapper;
    private final Schema schema;

    public J7JsonSchemaValidator() {
        mapper = JsonMapper.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder
                        .schemaRegistryConfig(SchemaRegistryConfig.builder()
                                .formatAssertionsEnabled(true)
                                .failFast(false)
                                .build())
                        .schemaLoader(loader -> loader.fetchRemoteResources(false)));
        try (InputStream input = J7JsonSchemaValidator.class.getResourceAsStream(
                SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("J7 JSON Schema is missing from the classpath");
            }
            schema = registry.getSchema(input, InputFormat.JSON);
            if (!J7ExportContract.SCHEMA_ID.equals(schema.getId())) {
                throw new IllegalStateException("J7 JSON Schema id does not match its contract");
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("J7 JSON Schema cannot be loaded", exception);
        }
    }

    public ObjectNode parseAndValidate(byte[] bytes) {
        try {
            JsonNode parsed = mapper.readTree(bytes);
            if (!(parsed instanceof ObjectNode objectNode)) {
                throw invalidSchema();
            }
            validate(objectNode);
            return objectNode;
        }
        catch (JacksonException exception) {
            throw invalidSchema();
        }
    }

    public void validate(JsonNode envelope) {
        if (envelope == null || !schema.validate(envelope).isEmpty()) {
            throw invalidSchema();
        }
    }

    private static J7ExportException invalidSchema() {
        return new J7ExportException(J7ExportError.INVALID_SCHEMA);
    }
}
