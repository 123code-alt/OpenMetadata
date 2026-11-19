/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Objects;

/** Keeps ID-based entity references and glossary tag labels consistent inside one write transaction. */
public final class ReferentialUpdates {
  private ReferentialUpdates() {}

  public static void refresh(
      EntityStore.Session tx, Kind kind, ObjectNode previous, ObjectNode current, Caller caller) {
    for (Kind containing : Kind.values()) {
      for (ObjectNode value : tx.all(containing)) {
        if (updateNode(value, kind, previous, current)) {
          tx.save(containing, Documents.begin(value, value, caller));
        }
      }
    }
  }

  private static boolean updateNode(
      JsonNode node, Kind kind, ObjectNode previous, ObjectNode current) {
    boolean changed = false;
    if (node instanceof ObjectNode object) {
      if (kind.entityType.equals(object.path("type").asText())
          && current.path("id").equals(object.path("id"))) {
        for (String key : new String[] {"name", "displayName", "fullyQualifiedName", "deleted"}) {
          JsonNode before = object.get(key);
          JsonNode after = current.get(key);
          if (!Objects.equals(before, after)) {
            if (after == null) {
              object.remove(key);
            } else {
              object.set(key, after);
            }
            changed = true;
          }
        }
      }
      if (kind == Kind.TERM
          && "Glossary".equals(object.path("source").asText())
          && previous.path("fullyQualifiedName").equals(object.path("tagFQN"))) {
        object.set("tagFQN", current.get("fullyQualifiedName"));
        changed = true;
      }
      var fields = object.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        changed |= updateNode(field.getValue(), kind, previous, current);
      }
    } else if (node.isArray()) {
      for (JsonNode item : node) {
        changed |= updateNode(item, kind, previous, current);
      }
    }
    return changed;
  }
}
