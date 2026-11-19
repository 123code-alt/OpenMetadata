/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class Documents {
  public static final ObjectMapper JSON = new ObjectMapper();

  private Documents() {}

  public static ObjectNode reference(Kind kind, JsonNode value) {
    ObjectNode ref = JSON.createObjectNode();
    for (String key : new String[] {"id", "name", "displayName", "fullyQualifiedName", "deleted"}) {
      if (value.has(key)) {
        ref.set(key, value.get(key));
      }
    }
    ref.put("type", kind.entityType);
    return ref;
  }

  public static ObjectNode begin(ObjectNode command, ObjectNode old, Caller caller) {
    ObjectNode entity = command.deepCopy();
    entity.put("id", old == null ? UUID.randomUUID().toString() : old.path("id").asText());
    int revision = old == null ? 1 : old.path("revision").asInt() + 1;
    entity.put("revision", revision);
    entity.put("version", BigDecimal.valueOf(revision, 1));
    entity.put("updatedAt", Instant.now().toEpochMilli());
    entity.put("updatedBy", caller.name());
    entity.put("deleted", old != null && old.path("deleted").asBoolean());
    if (!entity.hasNonNull("owners") || entity.path("owners").isEmpty()) {
      entity
          .putArray("owners")
          .addObject()
          .put("id", caller.id())
          .put("name", caller.name())
          .put("type", "user");
    }
    return entity;
  }

  public static ObjectNode require(EntityStore.Session tx, Kind kind, String id) {
    ObjectNode value = tx.find(kind, id).orElseThrow(Problem::notFound);
    if (value.path("deleted").asBoolean()) {
      throw Problem.notFound();
    }
    return value;
  }

  public static ObjectNode requireName(EntityStore.Session tx, Kind kind, String name) {
    ObjectNode value = tx.byName(kind, name).orElseThrow(Problem::notFound);
    if (value.path("deleted").asBoolean()) {
      throw Problem.notFound();
    }
    return value;
  }

  public static String requiredText(JsonNode value, String field) {
    String text = value.path(field).asText("");
    if (text.isBlank()) {
      throw Problem.badRequest(field + " is required");
    }
    return text;
  }

  public static void checkRevision(ObjectNode old, int expected) {
    if (old.path("revision").asInt() != expected) {
      throw Problem.conflict("Entity changed; reload before saving");
    }
  }

  public static void unique(EntityStore.Session tx, Kind kind, ObjectNode entity) {
    tx.byName(kind, entity.path("fullyQualifiedName").asText())
        .ifPresent(
            found -> {
              if (!found.path("id").equals(entity.path("id"))) {
                throw Problem.conflict("Name already exists (including archived entities)");
              }
            });
  }
}
