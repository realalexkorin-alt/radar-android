-- Radar uses the existing Avito Neon database (neondb).
-- Enable cron.database_name=neondb in the Neon compute settings before applying.
-- No Avito tables are dropped or modified. Never run FLUSH/DROP on this instance.
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE SCHEMA IF NOT EXISTS radar_presence;
CREATE TABLE IF NOT EXISTS radar_presence.points (
 id text PRIMARY KEY CHECK (id ~ '^[0-9a-f]{64}$'),
 revision bigint NOT NULL,
 point jsonb,
 expires_at timestamptz,
 revision_until timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS radar_points_expiry ON radar_presence.points (expires_at) WHERE point IS NOT NULL;
CREATE TABLE IF NOT EXISTS radar_presence.limits (
 id text PRIMARY KEY, count integer NOT NULL, expires_at timestamptz NOT NULL
);
CREATE OR REPLACE FUNCTION radar_presence.cleanup() RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 PERFORM pg_advisory_xact_lock(72915015);
 -- Allow a 10-second scheduler interval inside the 120-second retention limit.
 UPDATE radar_presence.points SET point=NULL, expires_at=NULL WHERE expires_at <= clock_timestamp();
 DELETE FROM radar_presence.points WHERE revision_until <= clock_timestamp();
 DELETE FROM radar_presence.limits WHERE expires_at <= clock_timestamp();
 IF NOT EXISTS (SELECT 1 FROM radar_presence.points WHERE point IS NOT NULL) THEN
  PERFORM cron.unschedule(jobid) FROM cron.job WHERE jobname='radar-presence-expiry';
 END IF;
END $$;
CREATE OR REPLACE FUNCTION radar_presence.write_point(p_id text,p_revision bigint,p_point jsonb,p_observed bigint) RETURNS integer LANGUAGE plpgsql AS $$
DECLARE accepted integer;
BEGIN
 PERFORM pg_advisory_xact_lock(72915015);
 INSERT INTO radar_presence.points AS p (id,revision,point,expires_at,revision_until)
 VALUES(p_id,p_revision,p_point,CASE WHEN p_point IS NULL THEN NULL ELSE LEAST(to_timestamp(p_observed/1000.0),clock_timestamp()) + interval '110 seconds' END,clock_timestamp()+interval '24 hours')
 ON CONFLICT(id) DO UPDATE SET revision=EXCLUDED.revision,point=EXCLUDED.point,expires_at=EXCLUDED.expires_at,revision_until=EXCLUDED.revision_until
 WHERE p.revision < EXCLUDED.revision;
 GET DIAGNOSTICS accepted = ROW_COUNT;
 IF accepted > 0 AND p_point IS NOT NULL AND NOT EXISTS(SELECT 1 FROM cron.job WHERE jobname='radar-presence-expiry') THEN
  PERFORM cron.schedule('radar-presence-expiry','10 seconds','SELECT radar_presence.cleanup()');
 END IF;
 RETURN accepted;
END $$;
