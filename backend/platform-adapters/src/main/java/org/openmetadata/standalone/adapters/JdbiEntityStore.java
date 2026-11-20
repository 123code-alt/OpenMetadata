/* Licensed under the Apache License, Version 2.0. See LICENSE. */
package org.openmetadata.standalone.adapters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.openmetadata.standalone.contracts.Documents;
import org.openmetadata.standalone.contracts.EntityStore;
import org.openmetadata.standalone.contracts.Kind;
import org.openmetadata.standalone.contracts.Problem;

/** PostgreSQL adapter. A database lock protects graph-wide mutations across application instances. */
public final class JdbiEntityStore implements EntityStore {
  public static final int MAX_SCAN_ROWS = 5000;
  private final Jdbi jdbi;

  public JdbiEntityStore(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  public void initialize() {
    try (var input = getClass().getResourceAsStream("/db/V001__semantic_context.sql")) {
      if (input == null) {
        throw new IllegalStateException("Missing database migration");
      }
      String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      jdbi.useTransaction(
          handle -> {
            for (String statement : sql.split(";")) {
              if (!statement.isBlank()) {
                handle.execute(statement);
              }
            }
          });
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public <T> T read(Function<Session, T> work) {
    return jdbi.inTransaction(
        TransactionIsolationLevel.REPEATABLE_READ, handle -> work.apply(new SqlSession(handle)));
  }

  @Override
  public <T> T write(Function<Session, T> work) {
    return jdbi.inTransaction(
        TransactionIsolationLevel.READ_COMMITTED,
        handle -> {
          handle
              .createQuery("SELECT id FROM sc_write_lock WHERE id = 1 FOR UPDATE")
              .mapTo(Integer.class)
              .one();
          return work.apply(new SqlSession(handle));
        });
  }

  private record SqlSession(Handle handle) implements Session {
    @Override
    public Optional<ObjectNode> find(Kind kind, String id) {
      return handle
          .createQuery("SELECT body FROM sc_entity WHERE entity_type = :type AND id = :id")
          .bind("type", kind.entityType)
          .bind("id", id)
          .mapTo(String.class)
          .findOne()
          .map(SqlSession::decode);
    }

    @Override
    public Optional<ObjectNode> byName(Kind kind, String fqn) {
      return handle
          .createQuery(
              "SELECT body FROM sc_entity WHERE entity_type = :type AND fqn_hash = :hash AND fqn = :fqn")
          .bind("type", kind.entityType)
          .bind("fqn", fqn)
          .bind("hash", hash(fqn))
          .mapTo(String.class)
          .findOne()
          .map(SqlSession::decode);
    }

    @Override
    public List<ObjectNode> all(Kind kind) {
      List<String> rows =
          handle
              .createQuery(
                  "SELECT body FROM sc_entity WHERE entity_type = :type ORDER BY fqn LIMIT :limit")
              .bind("type", kind.entityType)
              .bind("limit", MAX_SCAN_ROWS + 1)
              .mapTo(String.class)
              .list();
      return bounded(rows);
    }

    @Override
    public List<ObjectNode> history(Kind kind, String id) {
      List<String> rows =
          handle
              .createQuery(
                  "SELECT body FROM sc_history WHERE entity_type = :type AND id = :id ORDER BY revision DESC LIMIT :limit")
              .bind("type", kind.entityType)
              .bind("id", id)
              .bind("limit", MAX_SCAN_ROWS + 1)
              .mapTo(String.class)
              .list();
      return bounded(rows);
    }

    @Override
    public void save(Kind kind, ObjectNode value) {
      String id = value.path("id").asText();
      int revision = value.path("revision").asInt();
      String fqn = value.path("fullyQualifiedName").asText();
      if (fqn.length() > 3072) {
        throw Problem.badRequest("Fully qualified name exceeds 3072 characters");
      }
      String body = value.toString();
      if (revision == 1) {
        handle
            .createUpdate(
                "INSERT INTO sc_entity(entity_type, id, fqn, fqn_hash, revision, body) VALUES(:type, :id, :fqn, :hash, :revision, :body)")
            .bind("type", kind.entityType)
            .bind("id", id)
            .bind("fqn", fqn)
            .bind("hash", hash(fqn))
            .bind("revision", revision)
            .bind("body", body)
            .execute();
      } else {
        int changed =
            handle
                .createUpdate(
                    "UPDATE sc_entity SET fqn=:fqn, fqn_hash=:hash, revision=:revision, body=:body WHERE entity_type=:type AND id=:id AND revision=:previous")
                .bind("type", kind.entityType)
                .bind("id", id)
                .bind("fqn", fqn)
                .bind("hash", hash(fqn))
                .bind("revision", revision)
                .bind("body", body)
                .bind("previous", revision - 1)
                .execute();
        if (changed != 1) {
          throw Problem.conflict("Concurrent or stale entity update");
        }
      }
      handle
          .createUpdate(
              "INSERT INTO sc_history(entity_type, id, revision, body) VALUES(:type, :id, :revision, :body)")
          .bind("type", kind.entityType)
          .bind("id", id)
          .bind("revision", revision)
          .bind("body", body)
          .execute();
      handle
          .createUpdate(
              "INSERT INTO sc_outbox(event_id, entity_type, entity_id, revision, occurred_at) VALUES(:event, :type, :id, :revision, :time)")
          .bind("event", UUID.randomUUID().toString())
          .bind("type", kind.entityType)
          .bind("id", id)
          .bind("revision", revision)
          .bind("time", value.path("updatedAt").asLong())
          .execute();
    }

    private static List<ObjectNode> bounded(List<String> rows) {
      if (rows.size() > MAX_SCAN_ROWS) {
        throw new Problem(
            503,
            "Extraction prototype scan limit reached; install a paged query/search adapter before expanding the dataset");
      }
      return rows.stream().map(SqlSession::decode).toList();
    }

    private static ObjectNode decode(String body) {
      try {
        return (ObjectNode) Documents.JSON.readTree(body);
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("Invalid persisted entity", e);
      }
    }

    private static String hash(String fqn) {
      try {
        return HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256").digest(fqn.getBytes(StandardCharsets.UTF_8)));
      } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
