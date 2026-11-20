/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import org.openmetadata.standalone.contracts.Caller;
import org.openmetadata.standalone.contracts.DescriptionSanitizer;
import org.openmetadata.standalone.contracts.Documents;
import org.openmetadata.standalone.contracts.EntityStore;
import org.openmetadata.standalone.contracts.Fqn;
import org.openmetadata.standalone.contracts.Kind;
import org.openmetadata.standalone.contracts.Problem;
import org.openmetadata.standalone.contracts.ReferentialUpdates;
import org.openmetadata.standalone.contracts.SchemaCatalog;
import org.openmetadata.standalone.contracts.TagPolicy;

public final class ContextService {
  private final EntityStore store;
  private final SchemaCatalog schemas;
  private final TagPolicy tagPolicy;

  public ContextService(EntityStore store, SchemaCatalog schemas, TagPolicy tagPolicy) {
    this.store = store;
    this.schemas = schemas;
    this.tagPolicy = tagPolicy;
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
          enforceRead(kind, old, caller);
          Documents.checkRevision(old, expectedRevision);
          if (kind == Kind.MEMORY) {
            if (!MemoryVisibility.canEdit(old, caller)) {
              throw Problem.forbidden();
            }
            if (!caller.admin() && !caller.owns(old)) {
              for (String protectedField : new String[] {"owners", "shareConfig"}) {
                if (!Objects.equals(command.get(protectedField), old.get(protectedField))) {
                  throw Problem.forbidden();
                }
              }
            }
          }
          return save(tx, kind, command, old, caller);
        });
  }

  private ObjectNode save(
      EntityStore.Session tx, Kind kind, ObjectNode command, ObjectNode old, Caller caller) {
    ObjectNode value = Documents.begin(command, old, caller);
    value.put("description", DescriptionSanitizer.sanitize(command.path("description").asText("")));
    String name = Documents.requiredText(command, "name");
    value.put("fullyQualifiedName", Fqn.quote(name));
    tagPolicy.validate(tx, value);
    if (kind == Kind.MEMORY) {
      if (old == null && !caller.admin()) {
        value
            .putArray("owners")
            .addObject()
            .put("id", caller.id())
            .put("name", caller.name())
            .put("type", "user");
      }
      if (!value.has("shareConfig")) {
        value.putObject("shareConfig").put("visibility", "Private");
      }
      if (!value.has("status")) {
        value.put("status", "Active");
      }
      ObjectNode provenance =
          old != null && old.get("sourceProvenance") instanceof ObjectNode recorded
              ? recorded.deepCopy()
              : Documents.JSON.createObjectNode();
      for (String field :
          new String[] {"sourceFile", "sourceEntity", "rootMemory", "parentMemory"}) {
        if (value.hasNonNull(field)) {
          JsonNode inputRef = value.path(field);
          Kind sourceKind = Kind.fromType(inputRef.path("type").asText());
          if (("sourceFile".equals(field) && sourceKind != Kind.FILE)
              || (("rootMemory".equals(field) || "parentMemory".equals(field))
                  && sourceKind != Kind.MEMORY)) {
            throw Problem.badRequest("Invalid source type for " + field);
          }
          ObjectNode source = validateSource(tx, inputRef, caller);
          value.set(field, Documents.reference(sourceKind, source));
          if (old == null
              || !inputRef.path("id").equals(old.path(field).path("id"))
              || !inputRef.path("type").equals(old.path(field).path("type"))) {
            ObjectNode snapshot =
                provenance
                    .putObject(field)
                    .put("type", sourceKind.entityType)
                    .put("id", source.path("id").asText())
                    .put("revision", source.path("revision").asInt());
            if (source.has("headContentId")) {
              snapshot.set("contentId", source.get("headContentId"));
            }
          }
        } else {
          provenance.remove(field);
        }
      }
      value.set("sourceProvenance", provenance);
    } else {
      if (!value.path("reviewers").isEmpty()
          || !"Approved".equals(value.path("entityStatus").asText("Approved"))) {
        throw new Problem(501, "Article approval requires a host workflow adapter");
      }
      value.put("entityStatus", "Approved");
      if (command.hasNonNull("parent")) {
        ObjectNode parent =
            Documents.require(tx, Kind.PAGE, command.path("parent").path("id").asText());
        if (old != null
            && (old.path("id").equals(parent.path("id"))
                || Fqn.descendant(
                    parent.path("fullyQualifiedName").asText(),
                    old.path("fullyQualifiedName").asText()))) {
          throw Problem.badRequest("Article hierarchy would contain a cycle");
        }
        value.set("parent", Documents.reference(Kind.PAGE, parent));
        value.put(
            "fullyQualifiedName", Fqn.child(parent.path("fullyQualifiedName").asText(), name));
      }
    }
    Documents.unique(tx, kind, value);
    tx.save(kind, value);
    if (old != null) {
      if (kind == Kind.PAGE) {
        String oldFqn = old.path("fullyQualifiedName").asText();
        String newFqn = value.path("fullyQualifiedName").asText();
        if (!oldFqn.equals(newFqn)) {
          for (ObjectNode child : tx.all(Kind.PAGE)) {
            if (Fqn.descendant(child.path("fullyQualifiedName").asText(), oldFqn)) {
              ObjectNode before = Documents.require(tx, Kind.PAGE, child.path("id").asText());
              ObjectNode after = Documents.begin(before, before, caller);
              after.put(
                  "fullyQualifiedName",
                  newFqn + before.path("fullyQualifiedName").asText().substring(oldFqn.length()));
              Documents.unique(tx, Kind.PAGE, after);
              tx.save(Kind.PAGE, after);
              ReferentialUpdates.refresh(tx, Kind.PAGE, before, after, caller);
            }
          }
        }
      }
      ReferentialUpdates.refresh(tx, kind, old, value, caller);
    }
    return Documents.require(tx, kind, value.path("id").asText());
  }

  private static ObjectNode validateSource(EntityStore.Session tx, JsonNode ref, Caller caller) {
    Kind sourceKind = Kind.fromType(ref.path("type").asText());
    ObjectNode source = Documents.require(tx, sourceKind, ref.path("id").asText());
    enforceRead(sourceKind, source, caller);
    return source;
  }

  public ObjectNode archive(Kind kind, String id, int expectedRevision, Caller caller) {
    requireKind(kind);
    caller.requireWrite(kind);
    return store.write(
        tx -> {
          ObjectNode old = Documents.require(tx, kind, id);
          enforceRead(kind, old, caller);
          if (kind == Kind.MEMORY && !caller.admin() && !caller.owns(old)) {
            throw Problem.forbidden();
          }
          Documents.checkRevision(old, expectedRevision);
          if (kind == Kind.PAGE
              && tx.all(Kind.PAGE).stream()
                  .anyMatch(
                      p ->
                          !p.path("deleted").asBoolean()
                              && p.path("parent").path("id").equals(old.path("id")))) {
            throw Problem.conflict("Archive child articles first");
          }
          ObjectNode archived = Documents.begin(old, old, caller);
          archived.put("deleted", true);
          tx.save(kind, archived);
          ReferentialUpdates.refresh(tx, kind, old, archived, caller);
          return archived;
        });
  }

  public static void enforceRead(Kind kind, ObjectNode value, Caller caller) {
    if (kind == Kind.MEMORY && !MemoryVisibility.canRead(value, caller)) {
      throw Problem.notFound();
    }
  }

  private static void requireKind(Kind kind) {
    if (kind != Kind.PAGE && kind != Kind.MEMORY) {
      throw Problem.badRequest("Not an article or memory");
    }
  }
}
