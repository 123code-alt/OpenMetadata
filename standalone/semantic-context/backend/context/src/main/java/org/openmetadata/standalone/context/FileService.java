/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.context;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.openmetadata.standalone.contracts.BlobStore;
import org.openmetadata.standalone.contracts.Caller;
import org.openmetadata.standalone.contracts.Documents;
import org.openmetadata.standalone.contracts.EntityStore;
import org.openmetadata.standalone.contracts.Kind;
import org.openmetadata.standalone.contracts.Problem;

/** Local storage adapter is replaceable; immutable content IDs survive head-version changes. */
public final class FileService {
  public static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
  private static final int MAX_CANONICAL_TEXT_LENGTH = 1_000_000;
  private final EntityStore store;
  private final BlobStore blobs;

  public FileService(EntityStore store, BlobStore blobs) {
    this.store = store;
    this.blobs = blobs;
  }

  public ObjectNode upload(
      String fileId, int expectedRevision, String fileName, InputStream input, Caller caller)
      throws IOException {
    caller.requireWrite(Kind.FILE);
    String normalizedName = fileName == null ? "" : fileName.replace('\\', '/');
    String safeName = normalizedName.substring(normalizedName.lastIndexOf('/') + 1);
    if (safeName.isBlank() || safeName.length() > 255 || safeName.chars().anyMatch(c -> c < 32)) {
      throw Problem.badRequest("Invalid file name");
    }
    String extension =
        safeName.contains(".")
            ? safeName.substring(safeName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
            : "";
    if (!Set.of("txt", "md", "csv", "json").contains(extension)) {
      throw new Problem(415, "This extraction slice supports UTF-8 txt, md, csv and json files");
    }
    byte[] bytes = input.readNBytes(MAX_UPLOAD_BYTES + 1);
    if (bytes.length > MAX_UPLOAD_BYTES) {
      throw new Problem(413, "Maximum file size is 5 MiB");
    }
    String text;
    try {
      text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
    } catch (CharacterCodingException e) {
      throw Problem.badRequest("File must contain UTF-8 text");
    }
    String canonical = text.replace("\u0000", "").replace("\r\n", "\n").replace('\r', '\n').trim();
    if (canonical.length() > MAX_CANONICAL_TEXT_LENGTH) {
      canonical = canonical.substring(0, MAX_CANONICAL_TEXT_LENGTH);
    }
    String extracted = canonical;
    String contentId = UUID.randomUUID().toString();
    blobs.put(contentId, bytes);
    try {
      return store.write(
          tx -> {
            ObjectNode old = fileId == null ? null : Documents.require(tx, Kind.FILE, fileId);
            if (old != null) {
              if (!caller.admin() && !caller.owns(old)) {
                throw Problem.forbidden();
              }
              Documents.checkRevision(old, expectedRevision);
            }
            ObjectNode value =
                Documents.begin(old == null ? Documents.JSON.createObjectNode() : old, old, caller);
            value.put(
                "name", old == null ? UUID.randomUUID().toString() : old.path("name").asText());
            value.set("fullyQualifiedName", value.get("name"));
            value.put("displayName", safeName);
            value.put("fileName", safeName);
            value.put("fileExtension", extension);
            value.put("fileType", "csv".equals(extension) ? "CSV" : "Text");
            value.put("processingStatus", "Processed");
            value.put("extractedText", extracted);
            value.put("headContentId", contentId);
            value.put("size", bytes.length);
            value
                .putObject("headContent")
                .put("id", contentId)
                .put("size", bytes.length)
                .put("checksum", checksum(bytes))
                .put("createdAt", Instant.now().toEpochMilli());
            tx.save(Kind.FILE, value);
            return value;
          });
    } catch (RuntimeException e) {
      try {
        blobs.delete(contentId);
      } catch (IOException cleanup) {
        e.addSuppressed(cleanup);
      }
      throw e;
    }
  }

  public InputStream download(String fileId, String contentId, Caller caller) throws IOException {
    String key =
        store.read(
            tx -> {
              ObjectNode value = Documents.require(tx, Kind.FILE, fileId);
              if (contentId == null) {
                return value.path("headContentId").asText();
              }
              boolean belongs =
                  tx.history(Kind.FILE, fileId).stream()
                      .anyMatch(v -> contentId.equals(v.path("headContentId").asText()));
              if (!belongs) {
                throw Problem.notFound();
              }
              return contentId;
            });
    return blobs.open(key);
  }

  public ObjectNode archive(String id, int expectedRevision, Caller caller) {
    caller.requireWrite(Kind.FILE);
    return store.write(
        tx -> {
          ObjectNode old = Documents.require(tx, Kind.FILE, id);
          if (!caller.admin() && !caller.owns(old)) {
            throw Problem.forbidden();
          }
          Documents.checkRevision(old, expectedRevision);
          ObjectNode archived = Documents.begin(old, old, caller);
          archived.put("deleted", true);
          tx.save(Kind.FILE, archived);
          return archived;
        });
  }

  private static String checksum(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
