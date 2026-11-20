/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Set;
import org.openmetadata.standalone.contracts.AssetContextPort;
import org.openmetadata.standalone.contracts.Caller;
import org.openmetadata.standalone.contracts.Documents;
import org.openmetadata.standalone.contracts.EntityStore;
import org.openmetadata.standalone.contracts.Kind;
import org.openmetadata.standalone.contracts.Problem;

/** Explicit, authorized context packing. Semantic retrieval and LLM generation are separate ports. */
public final class ContextAssembler {
  private final EntityStore store;
  private final AssetContextPort assets;

  public ContextAssembler(EntityStore store, AssetContextPort assets) {
    this.store = store;
    this.assets = assets;
  }

  public ObjectNode assemble(JsonNode selections, int maxChars, Caller caller) {
    if (!selections.isArray() || selections.isEmpty() || selections.size() > 100) {
      throw Problem.badRequest("Select between 1 and 100 sources");
    }
    if (maxChars < 256 || maxChars > 100_000) {
      throw Problem.badRequest("maxChars must be between 256 and 100000");
    }
    return store.read(
        tx -> {
          ObjectNode result = Documents.JSON.createObjectNode();
          var sources = result.putArray("sources");
          var omitted = result.putArray("omitted");
          StringBuilder markdown = new StringBuilder("# AI Context\n\n");
          BudgetTracker budget = new BudgetTracker(maxChars);
          budget.tryConsume(markdown.toString());
          Set<String> seen = new HashSet<>();
          for (JsonNode selection : selections) {
            String type = Documents.requiredText(selection, "type");
            String id = Documents.requiredText(selection, "id");
            if (!seen.add(type + ":" + id)) {
              continue;
            }
            ObjectNode value = null;
            Kind kind;
            try {
              kind = Kind.fromType(type);
            } catch (Problem unsupported) {
              ObjectNode external =
                  assets
                      .resolve(type, id, caller)
                      .orElseThrow(
                          () -> new Problem(501, "Asset provider is not configured for " + type));
              value = external;
              kind = null;
            }
            if (kind != null) {
              value = Documents.require(tx, kind, id);
              ContextService.enforceRead(kind, value, caller);
              if (kind == Kind.MEMORY && !"Active".equals(value.path("status").asText("Active"))) {
                throw Problem.badRequest("Inactive memories cannot enter AI context");
              }
            }
            String content =
                kind == Kind.MEMORY
                    ? value.path("question").asText() + "\n" + value.path("answer").asText()
                    : kind == Kind.FILE
                        ? value.path("extractedText").asText()
                        : value.path("description").asText();
            String title = title(value);
            String citation = "[" + type + ":" + id + "@" + value.path("revision").asInt() + "]";
            String section =
                "## " + title + " " + citation + "\n\n" + PromptText.forPrompt(content) + "\n\n";
            if (!budget.tryConsume(section)) {
              omitted.addObject().put("id", id).put("type", type).put("reason", "character_budget");
              continue;
            }
            markdown.append(section);
            ObjectNode source =
                sources
                    .addObject()
                    .put("id", id)
                    .put("type", type)
                    .put("displayName", title)
                    .put("revision", value.path("revision").asInt())
                    .put("fullyQualifiedName", value.path("fullyQualifiedName").asText());
            if (value.has("headContentId")) {
              source.set("contentId", value.get("headContentId"));
            }
            if (value.has("sourceProvenance")) {
              source.set("derivedFrom", value.get("sourceProvenance"));
            }
          }
          result.put("markdown", markdown.toString());
          result.put("usedChars", markdown.length());
          result.put("maxChars", maxChars);
          result.put("retrieval", "explicit-selection");
          return result;
        });
  }

  private static String title(ObjectNode value) {
    for (String field : new String[] {"title", "displayName", "name"}) {
      String text = value.path(field).asText("");
      if (!text.isBlank()) {
        return text.replace('\n', ' ').replace('\r', ' ');
      }
    }
    return "";
  }
}
