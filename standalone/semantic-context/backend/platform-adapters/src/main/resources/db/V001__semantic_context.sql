CREATE TABLE IF NOT EXISTS sc_write_lock (id INTEGER PRIMARY KEY);
INSERT INTO sc_write_lock (id) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM sc_write_lock WHERE id = 1);
CREATE TABLE IF NOT EXISTS sc_entity (
  entity_type VARCHAR(40) NOT NULL,
  id VARCHAR(36) NOT NULL,
  fqn TEXT NOT NULL,
  fqn_hash VARCHAR(64) NOT NULL,
  revision INTEGER NOT NULL,
  body TEXT NOT NULL,
  PRIMARY KEY (entity_type, id),
  UNIQUE (entity_type, fqn_hash)
);
CREATE TABLE IF NOT EXISTS sc_history (
  entity_type VARCHAR(40) NOT NULL,
  id VARCHAR(36) NOT NULL,
  revision INTEGER NOT NULL,
  body TEXT NOT NULL,
  PRIMARY KEY (entity_type, id, revision)
);
CREATE TABLE IF NOT EXISTS sc_outbox (
  event_id VARCHAR(36) PRIMARY KEY,
  entity_type VARCHAR(40) NOT NULL,
  entity_id VARCHAR(36) NOT NULL,
  revision INTEGER NOT NULL,
  occurred_at BIGINT NOT NULL,
  UNIQUE (entity_type, entity_id, revision)
);
