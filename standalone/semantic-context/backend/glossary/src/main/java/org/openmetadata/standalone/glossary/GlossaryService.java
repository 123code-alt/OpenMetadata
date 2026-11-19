/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.glossary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.openmetadata.standalone.contracts.Caller;
import org.openmetadata.standalone.contracts.DescriptionSanitizer;
import org.openmetadata.standalone.contracts.Documents;
import org.openmetadata.standalone.contracts.EntityStore;
import org.openmetadata.standalone.contracts.Fqn;
import org.openmetadata.standalone.contracts.Kind;
import org.openmetadata.standalone.contracts.Problem;
import org.openmetadata.standalone.contracts.ReferentialUpdates;
import org.openmetadata.standalone.contracts.SchemaCatalog;

/** Glossary aggregate: hierarchy, relatedTo edges, synonyms, references, revisions and rename. */
public final class GlossaryService {
  private final EntityStore store;
  private final SchemaCatalog schemas;

  public GlossaryService(EntityStore store, SchemaCatalog schemas) {
    this.store = store;
    this.schemas = schemas;
  }

  public ObjectNode create(Kind kind, ObjectNode command, Caller caller) {
    requireKind(kind);
    caller.requireWrite(kind);
    schemas.validate(kind, command);
    return store.write(tx -> save(tx, kind, command, null, caller));
  }

  public ObjectNode update(
      Kind kind, String id, int expectedRevision, ObjectNode command, Caller caller) {
    requireKind(kind);
    caller.requireWrite(kind);
    schemas.validate(kind, command);
    return store.write(
        tx -> {
          ObjectNode old = Documents.require(tx, kind, id);
          Documents.checkRevision(old, expectedRevision);
          return save(tx, kind, command, old, caller);
        });
  }

  private ObjectNode save(
      EntityStore.Session tx, Kind kind, ObjectNode command, ObjectNode old, Caller caller) {
    ObjectNode value = Documents.begin(command, old, caller);
    value.put("description", DescriptionSanitizer.sanitize(command.path("description").asText()));
    String name = Documents.requiredText(value, "name");
    value.put("entityStatus", "Approved");
    if (!value.path("reviewers").isEmpty()) {
      throw new Problem(
          501,
          "Approval workflow requires a host platform adapter; reviewers are not supported in this slice");
    }
    if (kind == Kind.GLOSSARY) {
      value.put("fullyQualifiedName", Fqn.quote(name));
    } else {
      ObjectNode glossary =
          Documents.requireName(tx, Kind.GLOSSARY, command.path("glossary").asText());
      value.set("glossary", Documents.reference(Kind.GLOSSARY, glossary));
      String parentFqn = command.path("parent").asText("");
      ObjectNode parent =
          parentFqn.isEmpty() ? null : Documents.requireName(tx, Kind.TERM, parentFqn);
      if (parent != null) {
        if (!parent.path("glossary").path("id").equals(glossary.path("id"))) {
          throw Problem.badRequest("Parent must belong to the same glossary");
        }
        if (old != null
            && (old.path("id").equals(parent.path("id"))
                || Fqn.descendant(parentFqn, old.path("fullyQualifiedName").asText()))) {
          throw Problem.badRequest("A term cannot be moved under itself or its descendants");
        }
        value.set("parent", Documents.reference(Kind.TERM, parent));
      } else {
        value.remove("parent");
      }
      value.put(
          "fullyQualifiedName",
          Fqn.child(
              parent == null ? glossary.path("fullyQualifiedName").asText() : parentFqn, name));
      ArrayNode related = value.putArray("relatedTerms");
      Set<String> seen = new HashSet<>();
      for (JsonNode relatedName : command.path("relatedTerms")) {
        ObjectNode term = Documents.requireName(tx, Kind.TERM, relatedName.asText());
        if (term.path("id").equals(value.path("id"))) {
          throw Problem.badRequest("A term cannot relate to itself");
        }
        if (seen.add(term.path("id").asText())) {
          related
              .addObject()
              .put("relationType", "relatedTo")
              .set("term", Documents.reference(Kind.TERM, term));
        }
      }
    }
    validateGlossaryTags(tx, value);
    Documents.unique(tx, kind, value);
    tx.save(kind, value);
    if (old != null) {
      cascadeRename(tx, kind, old, value, caller);
    }
    if (kind == Kind.TERM) {
      syncRelations(tx, value, caller);
    }
    return Documents.require(tx, kind, value.path("id").asText());
  }

  public static void validateGlossaryTags(EntityStore.Session tx, ObjectNode value) {
    Set<String> exclusiveParents = new HashSet<>();
    for (JsonNode tag : value.path("tags")) {
      if (!"Glossary".equals(tag.path("source").asText())) {
        continue;
      }
      ObjectNode term = Documents.requireName(tx, Kind.TERM, tag.path("tagFQN").asText());
      JsonNode parentRef = term.has("parent") ? term.path("parent") : term.path("glossary");
      Kind parentKind = term.has("parent") ? Kind.TERM : Kind.GLOSSARY;
      ObjectNode parent = Documents.require(tx, parentKind, parentRef.path("id").asText());
      if (parent.path("mutuallyExclusive").asBoolean()
          && !exclusiveParents.add(parent.path("id").asText())) {
        throw Problem.badRequest(
            "Glossary tags from a mutually exclusive group cannot be combined");
      }
    }
  }

  private static void cascadeRename(
      EntityStore.Session tx, Kind kind, ObjectNode old, ObjectNode current, Caller caller) {
    String oldFqn = old.path("fullyQualifiedName").asText();
    String newFqn = current.path("fullyQualifiedName").asText();
    if (!oldFqn.equals(newFqn)) {
      for (ObjectNode child : tx.all(Kind.TERM)) {
        child = tx.find(Kind.TERM, child.path("id").asText()).orElseThrow(Problem::notFound);
        String childFqn = child.path("fullyQualifiedName").asText();
        if (!Fqn.descendant(childFqn, oldFqn)) {
          continue;
        }
        ObjectNode changed = Documents.begin(child, child, caller);
        changed.put("fullyQualifiedName", newFqn + childFqn.substring(oldFqn.length()));
        if (kind == Kind.TERM) {
          changed.set("glossary", current.path("glossary"));
        }
        Documents.unique(tx, Kind.TERM, changed);
        tx.save(Kind.TERM, changed);
        ReferentialUpdates.refresh(tx, Kind.TERM, child, changed, caller);
      }
    }
    ReferentialUpdates.refresh(tx, kind, old, current, caller);
  }

  private static void syncRelations(EntityStore.Session tx, ObjectNode value, Caller caller) {
    Set<String> wanted = new HashSet<>();
    value.path("relatedTerms").forEach(r -> wanted.add(r.path("term").path("id").asText()));
    for (ObjectNode other : tx.all(Kind.TERM)) {
      if (other.path("id").equals(value.path("id"))) {
        continue;
      }
      ArrayNode edges = Documents.JSON.createArrayNode();
      boolean had = false;
      for (JsonNode edge : other.path("relatedTerms")) {
        if (edge.path("term").path("id").equals(value.path("id"))) {
          had = true;
        } else {
          edges.add(edge);
        }
      }
      boolean need = wanted.contains(other.path("id").asText());
      if (need) {
        edges
            .addObject()
            .put("relationType", "relatedTo")
            .set("term", Documents.reference(Kind.TERM, value));
      }
      if (had != need) {
        ObjectNode changed = Documents.begin(other, other, caller);
        changed.set("relatedTerms", edges);
        tx.save(Kind.TERM, changed);
      }
    }
  }

  public ObjectNode archive(
      Kind kind, String id, int expectedRevision, boolean recursive, Caller caller) {
    requireKind(kind);
    caller.requireWrite(kind);
    return store.write(
        tx -> {
          ObjectNode old = Documents.require(tx, kind, id);
          Documents.checkRevision(old, expectedRevision);
          List<ObjectNode> children =
              tx.all(Kind.TERM).stream()
                  .filter(
                      t ->
                          !t.path("deleted").asBoolean()
                              && Fqn.descendant(
                                  t.path("fullyQualifiedName").asText(),
                                  old.path("fullyQualifiedName").asText()))
                  .toList();
          if (!recursive && !children.isEmpty()) {
            throw Problem.conflict("Entity has children; recursive archive is required");
          }
          for (ObjectNode child : children) {
            markArchived(
                tx,
                Kind.TERM,
                tx.find(Kind.TERM, child.path("id").asText()).orElseThrow(Problem::notFound),
                caller);
          }
          return markArchived(tx, kind, old, caller);
        });
  }

  private static ObjectNode markArchived(
      EntityStore.Session tx, Kind kind, ObjectNode old, Caller caller) {
    ObjectNode archived = Documents.begin(old, old, caller);
    archived.put("deleted", true);
    tx.save(kind, archived);
    ReferentialUpdates.refresh(tx, kind, old, archived, caller);
    return archived;
  }

  private static void requireKind(Kind kind) {
    if (kind != Kind.GLOSSARY && kind != Kind.TERM) {
      throw Problem.badRequest("Not a glossary entity");
    }
  }
}
