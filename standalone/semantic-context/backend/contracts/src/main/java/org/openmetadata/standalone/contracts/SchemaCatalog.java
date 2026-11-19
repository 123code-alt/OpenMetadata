/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/** Uses the selected, unmodified 2.0.8 schemas; no network schema resolution is allowed. */
public final class SchemaCatalog {
  private final SchemaRegistry registry;
  private final Map<Kind, JsonNode> schemas = new EnumMap<>(Kind.class);

  public SchemaCatalog(ObjectMapper mapper) {
    registry =
        SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_7,
            builder ->
                builder.schemas(
                    uri -> {
                      if (!uri.startsWith("https://open-metadata.org/schema/")) {
                        throw new IllegalArgumentException("Unregistered schema: " + uri);
                      }
                      String path = uri.substring("https://open-metadata.org/schema/".length());
                      try (InputStream input =
                          getClass().getResourceAsStream("/upstream-schema/" + path)) {
                        if (input == null) {
                          throw new IllegalArgumentException("Missing schema: " + path);
                        }
                        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                      } catch (IOException e) {
                        throw new IllegalStateException(e);
                      }
                    }));
    for (Kind kind : Kind.values()) {
      if (kind.createSchema != null) {
        try (InputStream input =
            getClass().getResourceAsStream("/upstream-schema/" + kind.createSchema)) {
          schemas.put(kind, mapper.readTree(input));
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      }
    }
  }

  public JsonNode properties(Kind kind) {
    return schemas.get(kind).path("properties");
  }

  public void validate(Kind kind, JsonNode command) {
    if (kind.createSchema == null) {
      return;
    }
    var errors =
        registry
            .getSchema(SchemaLocation.of("https://open-metadata.org/schema/" + kind.createSchema))
            .validate(command);
    if (!errors.isEmpty()) {
      throw Problem.badRequest(errors.toString());
    }
  }
}
