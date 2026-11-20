/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.openmetadata.standalone.context.ContextAssembler;
import org.openmetadata.standalone.context.ContextService;
import org.openmetadata.standalone.context.FileService;
import org.openmetadata.standalone.context.MemoryVisibility;
import org.openmetadata.standalone.contracts.Caller;
import org.openmetadata.standalone.contracts.Documents;
import org.openmetadata.standalone.contracts.EntityStore;
import org.openmetadata.standalone.contracts.Kind;
import org.openmetadata.standalone.contracts.Problem;
import org.openmetadata.standalone.glossary.GlossaryService;

@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
public final class SemanticResource {
  private static final String COLLECTION =
      "{collection:glossaries|glossaryTerms|pages|contextCenter/memories|contextCenter/drive/files}";
  private final EntityStore store;
  private final GlossaryService glossary;
  private final ContextService context;
  private final FileService files;
  private final ContextAssembler assembler;
  private final List<Caller> principals;

  public SemanticResource(
      EntityStore store,
      GlossaryService glossary,
      ContextService context,
      FileService files,
      ContextAssembler assembler,
      List<Caller> principals) {
    this.store = store;
    this.glossary = glossary;
    this.context = context;
    this.files = files;
    this.assembler = assembler;
    this.principals = List.copyOf(principals);
  }

  @GET
  @Path("users")
  public ObjectNode users() {
    ObjectNode result = Documents.JSON.createObjectNode();
    var data = result.putArray("data");
    principals.forEach(
        principal ->
            data.addObject()
                .put("id", principal.id())
                .put("name", principal.name())
                .put("type", "user"));
    return result;
  }

  @GET
  @Path("session")
  public ObjectNode session(@Context SecurityContext security) {
    Caller caller = caller(security);
    ObjectNode session =
        Documents.JSON
            .createObjectNode()
            .put("id", caller.id())
            .put("name", caller.name())
            .put("isAdmin", caller.admin());
    session
        .putArray("scopes")
        .addAll(caller.scopes().stream().map(Documents.JSON.getNodeFactory()::textNode).toList());
    session.put("sourceVersion", "2.0.8-release");
    return session;
  }

  @GET
  @Path("capabilities")
  public ObjectNode capabilities(@Context SecurityContext security) {
    ObjectNode value = Documents.JSON.createObjectNode();
    value
        .put("glossary", true)
        .put("articles", true)
        .put("memories", true)
        .put("textDocuments", true)
        .put("explicitContextPacking", true)
        .put("semanticSearch", false)
        .put("workflowApproval", false)
        .put("mcp", false)
        .put("assetProvider", false)
        .put("ontology", false);
    return value;
  }

  @GET
  @Path(COLLECTION)
  public ObjectNode list(
      @PathParam("collection") String collection,
      @QueryParam("limit") @DefaultValue("100") int limit,
      @QueryParam("after") String after,
      @QueryParam("q") @DefaultValue("") String query,
      @QueryParam("glossary") String glossaryFqn,
      @QueryParam("parent") String parent,
      @QueryParam("include") @DefaultValue("non-deleted") String include,
      @Context SecurityContext security) {
    Kind kind = kind(collection);
    if (limit < 0 || limit > 1000) {
      throw Problem.badRequest("limit must be between 0 and 1000");
    }
    if (query.length() > 256) {
      throw Problem.badRequest("Search query exceeds 256 characters");
    }
    if (!Set.of("all", "deleted", "non-deleted").contains(include)) {
      throw Problem.badRequest("Invalid include value");
    }
    int start = offset(after);
    Caller caller = caller(security);
    return store.read(
        tx -> {
          List<ObjectNode> visible =
              tx.all(kind).stream()
                  .filter(v -> kind != Kind.MEMORY || MemoryVisibility.canRead(v, caller))
                  .filter(
                      v ->
                          "all".equals(include)
                              || v.path("deleted").asBoolean() == "deleted".equals(include))
                  .filter(
                      v ->
                          glossaryFqn == null
                              || glossaryFqn.equals(
                                  v.path("glossary").path("fullyQualifiedName").asText()))
                  .filter(
                      v ->
                          parent == null
                              || parent.equals(
                                  v.path("parent").path("fullyQualifiedName").asText()))
                  .filter(v -> matches(v, query))
                  .toList();
          ObjectNode response = Documents.JSON.createObjectNode();
          var data = response.putArray("data");
          visible.stream().skip(start).limit(limit).forEach(v -> data.add(decorate(tx, kind, v)));
          ObjectNode paging = response.putObject("paging").put("total", visible.size());
          if (limit > 0 && (long) start + limit < visible.size()) {
            paging.put(
                "after",
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                        Integer.toString(start + limit).getBytes(StandardCharsets.UTF_8)));
          }
          return response;
        });
  }

  @GET
  @Path(COLLECTION + "/name/{fqn:.+}")
  public ObjectNode byName(
      @PathParam("collection") String collection,
      @PathParam("fqn") String fqn,
      @Context SecurityContext security) {
    Kind kind = kind(collection);
    return store.read(
        tx -> {
          ObjectNode value = Documents.requireName(tx, kind, fqn);
          ContextService.enforceRead(kind, value, caller(security));
          return decorate(tx, kind, value);
        });
  }

  @GET
  @Path(COLLECTION + "/{id}")
  public ObjectNode get(
      @PathParam("collection") String collection,
      @PathParam("id") String id,
      @Context SecurityContext security) {
    Kind kind = kind(collection);
    return store.read(
        tx -> {
          ObjectNode value = Documents.require(tx, kind, id);
          ContextService.enforceRead(kind, value, caller(security));
          return decorate(tx, kind, value);
        });
  }

  @GET
  @Path(COLLECTION + "/{id}/versions")
  public ObjectNode versions(
      @PathParam("collection") String collection,
      @PathParam("id") String id,
      @Context SecurityContext security) {
    Kind kind = kind(collection);
    return store.read(
        tx -> {
          ObjectNode current = tx.find(kind, id).orElseThrow(Problem::notFound);
          ContextService.enforceRead(kind, current, caller(security));
          ObjectNode response = Documents.JSON.createObjectNode();
          var versions = response.putArray("versions");
          tx.history(kind, id).stream()
              .filter(v -> kind != Kind.MEMORY || MemoryVisibility.canRead(v, caller(security)))
              .forEach(versions::add);
          return response;
        });
  }

  @POST
  @Path(COLLECTION)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response create(
      @PathParam("collection") String collection,
      ObjectNode command,
      @Context SecurityContext security) {
    if (command == null) {
      throw Problem.badRequest("JSON body required");
    }
    Kind kind = kind(collection);
    ObjectNode value =
        switch (kind) {
          case GLOSSARY, TERM -> glossary.create(kind, command, caller(security));
          case PAGE, MEMORY -> context.create(kind, command, caller(security));
          case FILE -> throw Problem.badRequest("Use the files/upload endpoint");
        };
    return Response.status(201).entity(value).build();
  }

  @PUT
  @Path(COLLECTION + "/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  public ObjectNode update(
      @PathParam("collection") String collection,
      @PathParam("id") String id,
      @QueryParam("revision") @DefaultValue("-1") int revision,
      ObjectNode command,
      @Context SecurityContext security) {
    if (command == null) {
      throw Problem.badRequest("JSON body required");
    }
    return switch (kind(collection)) {
      case GLOSSARY, TERM -> glossary.update(
          kind(collection), id, revision, command, caller(security));
      case PAGE, MEMORY -> context.update(
          kind(collection), id, revision, command, caller(security));
      case FILE -> throw Problem.badRequest("Use the files/{id}/content endpoint");
    };
  }

  @DELETE
  @Path(COLLECTION + "/{id}")
  public ObjectNode archive(
      @PathParam("collection") String collection,
      @PathParam("id") String id,
      @QueryParam("revision") @DefaultValue("-1") int revision,
      @QueryParam("recursive") @DefaultValue("false") boolean recursive,
      @Context SecurityContext security) {
    return switch (kind(collection)) {
      case GLOSSARY, TERM -> glossary.archive(
          kind(collection), id, revision, recursive, caller(security));
      case PAGE, MEMORY -> context.archive(kind(collection), id, revision, caller(security));
      case FILE -> files.archive(id, revision, caller(security));
    };
  }

  @POST
  @Path("contextCenter/drive/files/upload")
  @Consumes(MediaType.APPLICATION_OCTET_STREAM)
  public Response upload(
      @HeaderParam("X-File-Name") String encodedName,
      InputStream input,
      @Context SecurityContext security)
      throws IOException {
    return Response.status(201)
        .entity(files.upload(null, -1, decodeFileName(encodedName), input, caller(security)))
        .build();
  }

  @PUT
  @Path("contextCenter/drive/files/{id}/content")
  @Consumes(MediaType.APPLICATION_OCTET_STREAM)
  public ObjectNode replace(
      @PathParam("id") String id,
      @QueryParam("revision") @DefaultValue("-1") int revision,
      @HeaderParam("X-File-Name") String encodedName,
      InputStream input,
      @Context SecurityContext security)
      throws IOException {
    return files.upload(id, revision, decodeFileName(encodedName), input, caller(security));
  }

  @GET
  @Path("contextCenter/drive/files/{id}/content")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response download(@PathParam("id") String id, @Context SecurityContext security)
      throws IOException {
    return Response.ok(files.download(id, null, caller(security)))
        .header("Content-Disposition", "attachment")
        .header("X-Content-Type-Options", "nosniff")
        .build();
  }

  @GET
  @Path("contextCenter/drive/files/{id}/content/{contentId}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response downloadRevision(
      @PathParam("id") String id,
      @PathParam("contentId") String contentId,
      @Context SecurityContext security)
      throws IOException {
    return Response.ok(files.download(id, contentId, caller(security)))
        .header("Content-Disposition", "attachment")
        .header("X-Content-Type-Options", "nosniff")
        .build();
  }

  @POST
  @Path("ai/context")
  @Consumes(MediaType.APPLICATION_JSON)
  public ObjectNode assemble(ObjectNode input, @Context SecurityContext security) {
    if (input == null) {
      throw Problem.badRequest("JSON body required");
    }
    return assembler.assemble(
        input.path("sources"), input.path("maxChars").asInt(8000), caller(security));
  }

  @GET
  @Path("ai/find")
  public ObjectNode find(@QueryParam("q") String query, @Context SecurityContext security) {
    if (query == null || query.isBlank() || query.length() > 256) {
      throw Problem.badRequest("q is required (maximum 256 characters)");
    }
    return store.read(
        tx -> {
          ObjectNode result = Documents.JSON.createObjectNode().put("retrieval", "lexical");
          var hits = result.putArray("data");
          for (Kind kind : new Kind[] {Kind.TERM, Kind.PAGE}) {
            tx.all(kind).stream()
                .filter(v -> !v.path("deleted").asBoolean() && matches(v, query))
                .limit(100)
                .forEach(v -> hits.add(Documents.reference(kind, v)));
          }
          return result;
        });
  }

  private static Caller caller(SecurityContext security) {
    return (Caller) security.getUserPrincipal();
  }

  private static Kind kind(String collection) {
    return Arrays.stream(Kind.values())
        .filter(k -> k.path.equals(collection))
        .findFirst()
        .orElseThrow(Problem::notFound);
  }

  private static boolean matches(JsonNode value, String query) {
    if (query.isBlank()) {
      return true;
    }
    String needle = query.toLowerCase(Locale.ROOT);
    for (String field :
        new String[] {
          "name",
          "displayName",
          "description",
          "title",
          "question",
          "answer",
          "synonyms",
          "extractedText"
        }) {
      if (value.path(field).toString().toLowerCase(Locale.ROOT).contains(needle)) {
        return true;
      }
    }
    return false;
  }

  private static ObjectNode decorate(EntityStore.Session tx, Kind kind, ObjectNode value) {
    if (kind == Kind.GLOSSARY) {
      value.put(
          "termCount",
          tx.all(Kind.TERM).stream()
              .filter(
                  t ->
                      !t.path("deleted").asBoolean()
                          && t.path("glossary").path("id").equals(value.path("id")))
              .count());
    }
    if (kind == Kind.TERM) {
      var children = value.putArray("children");
      tx.all(Kind.TERM).stream()
          .filter(
              t ->
                  !t.path("deleted").asBoolean()
                      && t.path("parent").path("id").equals(value.path("id")))
          .forEach(t -> children.add(Documents.reference(Kind.TERM, t)));
    }
    return value;
  }

  private static int offset(String after) {
    if (after == null) {
      return 0;
    }
    try {
      int offset =
          Integer.parseInt(
              new String(Base64.getUrlDecoder().decode(after), StandardCharsets.UTF_8));
      if (offset < 0) {
        throw new IllegalArgumentException();
      }
      return offset;
    } catch (IllegalArgumentException e) {
      throw Problem.badRequest("Invalid paging cursor");
    }
  }

  private static String decodeFileName(String encoded) {
    if (encoded == null) {
      throw Problem.badRequest("X-File-Name is required");
    }
    try {
      return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw Problem.badRequest("Invalid file name encoding");
    }
  }
}
