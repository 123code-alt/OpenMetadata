/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openmetadata.standalone.contracts.Documents;

class SemanticApiTest {
  @RegisterExtension
  static final DropwizardAppExtension<SemanticContextConfiguration> APP =
      new DropwizardAppExtension<>(
          SemanticContextApplication.class, ResourceHelpers.resourceFilePath("test-config.yaml"));

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final String MEMORIES = "contextCenter/memories";
  private static final String BOB_ID = "00000000-0000-0000-0000-00000000000b";

  @Test
  void rejectsMalformedCommandsAndUnauthorizedWrites() throws Exception {
    assertEquals(401, call("GET", "glossaries", null, null).statusCode());
    assertEquals(401, call("GET", "session", null, "invalid").statusCode());
    assertEquals(
        400,
        call("POST", "glossaries", json("{\"name\":\"missing-description\"}"), "alice")
            .statusCode());
    ObjectNode invalid = glossaryCommand(unique());
    invalid.put("unknownField", true);
    assertEquals(400, call("POST", "glossaries", invalid, "alice").statusCode());
    assertEquals(403, call("POST", "glossaries", glossaryCommand(unique()), "reader").statusCode());
    ObjectNode reviewers = glossaryCommand(unique());
    reviewers.putArray("reviewers").addObject().put("id", BOB_ID).put("type", "user");
    assertEquals(501, call("POST", "glossaries", reviewers, "alice").statusCode());
  }

  @Test
  void renamesQuotedGlossaryAndKeepsHierarchyRelationsAndTagReferences() throws Exception {
    String name = unique() + ".业务";
    ObjectNode glossary = create("glossaries", glossaryCommand(name));
    ObjectNode root = create("glossaryTerms", termCommand(glossary, "收入.金额", null));
    ObjectNode child = create("glossaryTerms", termCommand(glossary, "净收入", root));
    ObjectNode peerCommand = termCommand(glossary, "成本", null);
    peerCommand.putArray("relatedTerms").add(root.path("fullyQualifiedName").asText());
    ObjectNode peer = create("glossaryTerms", peerCommand);
    ObjectNode articleCommand = articleCommand(unique());
    articleCommand
        .putArray("tags")
        .addObject()
        .put("tagFQN", root.path("fullyQualifiedName").asText())
        .put("source", "Glossary")
        .put("labelType", "Manual")
        .put("state", "Confirmed");
    ObjectNode article = create("pages", articleCommand);
    ObjectNode rename = glossaryCommand(name + ".更名");
    ObjectNode renamed =
        body(call("PUT", updatePath("glossaries", glossary), rename, "alice"), 200);
    ObjectNode movedRoot = get("glossaryTerms", root);
    ObjectNode movedChild = get("glossaryTerms", child);
    assertEquals(root.path("id"), movedRoot.path("id"));
    assertTrue(
        movedRoot
            .path("fullyQualifiedName")
            .asText()
            .startsWith(renamed.path("fullyQualifiedName").asText() + "."));
    assertEquals(
        movedRoot.path("fullyQualifiedName"), movedChild.path("parent").path("fullyQualifiedName"));
    assertEquals(
        renamed.path("fullyQualifiedName"), movedChild.path("glossary").path("fullyQualifiedName"));
    assertEquals(
        movedRoot.path("fullyQualifiedName"),
        get("glossaryTerms", peer)
            .path("relatedTerms")
            .get(0)
            .path("term")
            .path("fullyQualifiedName"));
    assertEquals(
        movedRoot.path("fullyQualifiedName"),
        get("pages", article).path("tags").get(0).path("tagFQN"));
    assertEquals(peer.path("id"), movedRoot.path("relatedTerms").get(0).path("term").path("id"));
    assertTrue(
        body(
                    call(
                        "GET",
                        "glossaryTerms/" + root.path("id").asText() + "/versions",
                        null,
                        "alice"),
                    200)
                .path("versions")
                .size()
            >= 2);
  }

  @Test
  void movesSubtreeAndRejectsCyclesDuplicateNamesAndStaleWrites() throws Exception {
    ObjectNode first = create("glossaries", glossaryCommand(unique()));
    ObjectNode second = create("glossaries", glossaryCommand(unique()));
    ObjectNode root = create("glossaryTerms", termCommand(first, "root", null));
    ObjectNode child = create("glossaryTerms", termCommand(first, "child", root));
    ObjectNode grandchild = create("glossaryTerms", termCommand(first, "grandchild", child));
    assertEquals(
        400,
        call(
                "PUT",
                updatePath("glossaryTerms", root),
                termCommand(first, "root", grandchild),
                "alice")
            .statusCode());
    ObjectNode moved =
        body(
            call(
                "PUT",
                updatePath("glossaryTerms", root),
                termCommand(second, "root", null),
                "alice"),
            200);
    assertEquals(second.path("id"), get("glossaryTerms", grandchild).path("glossary").path("id"));
    assertEquals(
        moved.path("fullyQualifiedName").asText() + ".child.grandchild",
        get("glossaryTerms", grandchild).path("fullyQualifiedName").asText());
    assertEquals(
        409,
        call(
                "PUT",
                updatePath("glossaryTerms", root),
                termCommand(second, "overwritten", null),
                "alice")
            .statusCode());
    assertEquals(
        409,
        call("POST", "glossaries", glossaryCommand(first.path("name").asText()), "alice")
            .statusCode());
    assertEquals(
        moved.path("fullyQualifiedName"), get("glossaryTerms", root).path("fullyQualifiedName"));
  }

  @Test
  void serializesCompetingUpdatesAndKeepsOneWinner() throws Exception {
    ObjectNode glossary = create("glossaries", glossaryCommand(unique()));
    String path = updatePath("glossaries", glossary);
    var first =
        CompletableFuture.supplyAsync(
            () -> uncheckedCall("PUT", path, glossaryCommand(unique()), "alice"));
    var second =
        CompletableFuture.supplyAsync(
            () -> uncheckedCall("PUT", path, glossaryCommand(unique()), "alice"));
    assertEquals(
        List.of(200, 409),
        List.of(first.join().statusCode(), second.join().statusCode()).stream().sorted().toList());
    assertEquals(2, get("glossaries", glossary).path("revision").asInt());
  }

  @Test
  void enforcesMemoryVisibilityAcrossDetailsListsHistoryFinderAndPacking() throws Exception {
    String name = unique();
    ObjectNode command = memoryCommand(name, "private-original-secret");
    ObjectNode memory = create(MEMORIES, command);
    String id = memory.path("id").asText();
    assertEquals(404, call("GET", MEMORIES + "/" + id, null, "bob").statusCode());
    assertEquals(404, call("GET", MEMORIES + "/name/" + name, null, "bob").statusCode());
    assertEquals(404, call("GET", MEMORIES + "/" + id + "/versions", null, "bob").statusCode());
    assertEquals(
        0,
        body(call("GET", MEMORIES + "?q=" + name + "&limit=1", null, "bob"), 200)
            .path("paging")
            .path("total")
            .asInt());
    ObjectNode selection = Documents.JSON.createObjectNode();
    selection.putArray("sources").addObject().put("type", "contextMemory").put("id", id);
    assertEquals(404, call("POST", "ai/context", selection, "bob").statusCode());
    assertTrue(body(call("GET", "ai/find?q=" + name, null, "alice"), 200).path("data").isEmpty());
    command.put("answer", "shared-current-content");
    share(command, "Viewer");
    ObjectNode shared = body(call("PUT", updatePath(MEMORIES, memory), command, "alice"), 200);
    assertEquals(200, call("GET", MEMORIES + "/" + id, null, "bob").statusCode());
    JsonNode history =
        body(call("GET", MEMORIES + "/" + id + "/versions", null, "bob"), 200).path("versions");
    assertEquals(1, history.size());
    assertFalse(history.toString().contains("private-original-secret"));
    assertEquals(403, call("PUT", updatePath(MEMORIES, shared), command, "bob").statusCode());
    share(command, "Editor");
    ObjectNode editable = body(call("PUT", updatePath(MEMORIES, shared), command, "alice"), 200);
    command.set("owners", editable.path("owners"));
    command.put("answer", "edited-by-bob");
    ObjectNode edited = body(call("PUT", updatePath(MEMORIES, editable), command, "bob"), 200);
    command.putObject("shareConfig").put("visibility", "Entity");
    assertEquals(403, call("PUT", updatePath(MEMORIES, edited), command, "bob").statusCode());
  }

  @Test
  void defaultsMissingMemoryVisibilityToPrivateAndResolvesTypedTeamShares() throws Exception {
    ObjectNode command = memoryCommand(unique(), "team memory");
    command
        .putObject("shareConfig")
        .put("visibility", "Shared")
        .putArray("sharedWith")
        .addObject()
        .put("role", "Viewer")
        .putObject("principal")
        .put("id", "00000000-0000-0000-0000-00000000000c")
        .put("type", "team");
    ObjectNode memory = create(MEMORIES, command);
    assertEquals(
        200, call("GET", MEMORIES + "/" + memory.path("id").asText(), null, "bob").statusCode());
    assertEquals(
        404, call("GET", MEMORIES + "/" + memory.path("id").asText(), null, "reader").statusCode());
  }

  @Test
  void retainsImmutableFileRevisionsAndRejectsBadOrStaleUploads() throws Exception {
    ObjectNode file =
        body(
            upload(
                "POST",
                "contextCenter/drive/files/upload",
                "note.md",
                "原始文本\r\nline two".getBytes(StandardCharsets.UTF_8)),
            201);
    String id = file.path("id").asText();
    String oldContentId = file.path("headContentId").asText();
    ObjectNode replaced =
        body(
            upload(
                "PUT",
                "contextCenter/drive/files/" + id + "/content?revision=1",
                "note.md",
                "replacement".getBytes(StandardCharsets.UTF_8)),
            200);
    assertNotEquals(oldContentId, replaced.path("headContentId").asText());
    assertEquals(
        "原始文本\r\nline two",
        call("GET", "contextCenter/drive/files/" + id + "/content/" + oldContentId, null, "alice")
            .body());
    assertEquals(
        "replacement",
        call("GET", "contextCenter/drive/files/" + id + "/content", null, "alice").body());
    long storedBefore;
    try (var entries = Files.list(Path.of(APP.getConfiguration().blobDirectory))) {
      storedBefore = entries.count();
    }
    assertEquals(
        409,
        upload(
                "PUT",
                "contextCenter/drive/files/" + id + "/content?revision=1",
                "note.md",
                "stale".getBytes(StandardCharsets.UTF_8))
            .statusCode());
    try (var entries = Files.list(Path.of(APP.getConfiguration().blobDirectory))) {
      assertEquals(storedBefore, entries.count());
    }
    assertEquals(
        415,
        upload(
                "POST",
                "contextCenter/drive/files/upload",
                "unsafe.html",
                "x".getBytes(StandardCharsets.UTF_8))
            .statusCode());
    assertEquals(
        400,
        upload("POST", "contextCenter/drive/files/upload", "invalid.txt", new byte[] {(byte) 0xff})
            .statusCode());
    assertEquals(
        413,
        upload(
                "POST",
                "contextCenter/drive/files/upload",
                "large.txt",
                new byte[5 * 1024 * 1024 + 1])
            .statusCode());
    assertEquals(
        replaced.path("headContentId"),
        get("contextCenter/drive/files", file).path("headContentId"));
  }

  @Test
  void pinsSourceContentRevisionWhenAFileIsReplaced() throws Exception {
    ObjectNode file =
        body(
            upload(
                "POST",
                "contextCenter/drive/files/upload",
                "source.md",
                "original evidence".getBytes(StandardCharsets.UTF_8)),
            201);
    ObjectNode command = memoryCommand(unique(), "guidance derived from original evidence");
    command.putObject("sourceEntity").put("type", "contextFile").set("id", file.get("id"));
    ObjectNode memory = create(MEMORIES, command);
    ObjectNode replaced =
        body(
            upload(
                "PUT",
                "contextCenter/drive/files/" + file.path("id").asText() + "/content?revision=1",
                "source.md",
                "new evidence".getBytes(StandardCharsets.UTF_8)),
            200);
    assertNotEquals(file.get("headContentId"), replaced.get("headContentId"));
    command.put("answer", "edited guidance, retaining its original evidence revision");
    ObjectNode edited = body(call("PUT", updatePath(MEMORIES, memory), command, "alice"), 200);
    assertEquals(
        file.get("headContentId"),
        edited.path("sourceProvenance").path("sourceEntity").get("contentId"));
    assertEquals(1, edited.path("sourceProvenance").path("sourceEntity").path("revision").asInt());
  }

  @Test
  void appliesGlossaryTagMutualExclusionToContextArticlesToo() throws Exception {
    ObjectNode glossaryCommand = glossaryCommand(unique()).put("mutuallyExclusive", true);
    ObjectNode glossary = create("glossaries", glossaryCommand);
    ObjectNode first = create("glossaryTerms", termCommand(glossary, "first", null));
    ObjectNode second = create("glossaryTerms", termCommand(glossary, "second", null));
    ObjectNode article = articleCommand(unique());
    var tags = article.putArray("tags");
    for (ObjectNode term : List.of(first, second)) {
      tags.addObject()
          .put("tagFQN", term.path("fullyQualifiedName").asText())
          .put("source", "Glossary")
          .put("labelType", "Manual")
          .put("state", "Confirmed");
    }
    assertEquals(400, call("POST", "pages", article, "alice").statusCode());
    tags.remove(1);
    assertEquals(201, call("POST", "pages", article, "alice").statusCode());
  }

  @Test
  void usesOwnerIdsRatherThanUntrustedDisplayNames() throws Exception {
    ObjectNode command = memoryCommand(unique(), "owner-id authorization");
    ObjectNode memory = create(MEMORIES, command);
    command
        .putArray("owners")
        .addObject()
        .put("id", BOB_ID)
        .put("type", "user")
        .put("name", "alice");
    ObjectNode transferred = body(call("PUT", updatePath(MEMORIES, memory), command, "alice"), 200);
    assertEquals(
        404,
        call("GET", MEMORIES + "/" + transferred.path("id").asText(), null, "alice").statusCode());
    assertEquals(
        200,
        call("GET", MEMORIES + "/" + transferred.path("id").asText(), null, "bob").statusCode());
  }

  @Test
  void packsAuthorizedSourcesWithProvenanceAndAHardCharacterBudget() throws Exception {
    ObjectNode articleCommand = articleCommand(unique());
    articleCommand.put(
        "description",
        "<p class=\"layout\">有效定义</p><img src=\"data:image/png;base64,"
            + "A".repeat(2000)
            + "\"><script>bad()</script>");
    ObjectNode article = create("pages", articleCommand);
    ObjectNode memory = create(MEMORIES, memoryCommand(unique(), "B".repeat(2000)));
    ObjectNode command = Documents.JSON.createObjectNode().put("maxChars", 512);
    var sources = command.putArray("sources");
    sources.addObject().put("type", "page").set("id", article.get("id"));
    sources.addObject().put("type", "contextMemory").set("id", memory.get("id"));
    ObjectNode result = body(call("POST", "ai/context", command, "alice"), 200);
    String markdown = result.path("markdown").asText();
    assertTrue(markdown.length() <= 512);
    assertEquals(markdown.length(), result.path("usedChars").asInt());
    assertTrue(markdown.contains("有效定义"));
    assertFalse(markdown.contains("base64"));
    assertFalse(markdown.contains("script"));
    assertEquals(1, result.path("sources").size());
    assertEquals(article.path("revision"), result.path("sources").get(0).path("revision"));
    assertEquals(1, result.path("omitted").size());
  }

  @Test
  void usesMemoryTitleAndFallsBackFromEmptyDisplayNameInContextHeadings() throws Exception {
    ObjectNode glossary = create("glossaries", glossaryCommand(unique()));
    ObjectNode term =
        create("glossaryTerms", termCommand(glossary, "净收入", null).put("displayName", ""));
    ObjectNode memory = create(MEMORIES, memoryCommand(unique(), "收入核算约定").put("title", "收入统计约定"));
    ObjectNode command = Documents.JSON.createObjectNode().put("maxChars", 2048);
    var sources = command.putArray("sources");
    sources.addObject().put("type", "glossaryTerm").set("id", term.get("id"));
    sources.addObject().put("type", "contextMemory").set("id", memory.get("id"));
    ObjectNode result = body(call("POST", "ai/context", command, "alice"), 200);
    assertTrue(result.path("markdown").asText().contains("## 净收入 ["));
    assertTrue(result.path("markdown").asText().contains("## 收入统计约定 ["));
    assertEquals("收入统计约定", result.path("sources").get(1).path("displayName").asText());
  }

  @Test
  void archivesAnEntireGlossaryWithoutRestoringArchivedChildrenDuringReferenceUpdates()
      throws Exception {
    ObjectNode glossary = create("glossaries", glossaryCommand(unique()));
    ObjectNode term = create("glossaryTerms", termCommand(glossary, "child", null));
    ObjectNode descendant = create("glossaryTerms", termCommand(glossary, "descendant", term));
    assertEquals(
        409, call("DELETE", updatePath("glossaries", glossary), null, "alice").statusCode());
    assertEquals(
        200,
        call("DELETE", updatePath("glossaries", glossary) + "&recursive=true", null, "alice")
            .statusCode());
    assertEquals(
        404, call("GET", "glossaryTerms/" + term.path("id").asText(), null, "alice").statusCode());
    assertEquals(
        404,
        call("GET", "glossaryTerms/" + descendant.path("id").asText(), null, "alice").statusCode());
  }

  private static ObjectNode glossaryCommand(String name) {
    return Documents.JSON
        .createObjectNode()
        .put("name", name)
        .put("description", "business definitions");
  }

  private static ObjectNode termCommand(ObjectNode glossary, String name, ObjectNode parent) {
    ObjectNode command = glossaryCommand(name);
    command.set("glossary", glossary.get("fullyQualifiedName"));
    if (parent != null) {
      command.set("parent", parent.get("fullyQualifiedName"));
    }
    return command;
  }

  private static ObjectNode articleCommand(String name) {
    ObjectNode command = glossaryCommand(name).put("pageType", "Article");
    command.putObject("page");
    return command;
  }

  private static ObjectNode memoryCommand(String name, String answer) {
    return Documents.JSON
        .createObjectNode()
        .put("name", name)
        .put("title", name)
        .put("question", "How should we use this context?")
        .put("answer", answer);
  }

  private static void share(ObjectNode command, String role) {
    command
        .putObject("shareConfig")
        .put("visibility", "Shared")
        .putArray("sharedWith")
        .addObject()
        .put("role", role)
        .putObject("principal")
        .put("id", BOB_ID)
        .put("type", "user");
  }

  private static ObjectNode create(String collection, ObjectNode command) throws Exception {
    return body(call("POST", collection, command, "alice"), 201);
  }

  private static ObjectNode get(String collection, ObjectNode value) throws Exception {
    return body(call("GET", collection + "/" + value.path("id").asText(), null, "alice"), 200);
  }

  private static String updatePath(String collection, ObjectNode value) {
    return collection
        + "/"
        + value.path("id").asText()
        + "?revision="
        + value.path("revision").asInt();
  }

  private static String unique() {
    return "test_" + UUID.randomUUID();
  }

  private static ObjectNode json(String text) throws Exception {
    return (ObjectNode) Documents.JSON.readTree(text);
  }

  private static ObjectNode body(HttpResponse<String> response, int status) throws Exception {
    assertEquals(status, response.statusCode(), response.body());
    return json(response.body());
  }

  private static HttpResponse<String> call(String method, String path, JsonNode body, String user)
      throws Exception {
    var request =
        HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + APP.getLocalPort() + "/api/v1/" + path))
            .header("Content-Type", "application/json");
    if (user != null) {
      request.header(
          "Authorization", "Bearer " + ("invalid".equals(user) ? user : "fixture-" + user));
    }
    return HTTP.send(
        request
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body.toString()))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static HttpResponse<String> uncheckedCall(
      String method, String path, JsonNode body, String user) {
    try {
      return call(method, path, body, user);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static HttpResponse<String> upload(String method, String path, String name, byte[] bytes)
      throws Exception {
    return HTTP.send(
        HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + APP.getLocalPort() + "/api/v1/" + path))
            .header("Content-Type", "application/octet-stream")
            .header("X-File-Name", URLEncoder.encode(name, StandardCharsets.UTF_8))
            .header("Authorization", "Bearer fixture-alice")
            .method(method, HttpRequest.BodyPublishers.ofByteArray(bytes))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
