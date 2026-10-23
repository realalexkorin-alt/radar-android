import {database} from './postgres.js';
let ready;
export async function initialize(sql=database()){
 if(!ready)ready=sql.transaction([
  sql`SELECT pg_advisory_xact_lock(72915030)`,
  sql.query(`CREATE TABLE IF NOT EXISTS radar_presence.diagnostics(report_id uuid PRIMARY KEY,user_id integer NOT NULL REFERENCES radar_presence.accounts(id) ON DELETE CASCADE,received_at timestamptz NOT NULL DEFAULT clock_timestamp(),expires_at timestamptz NOT NULL DEFAULT clock_timestamp()+interval '7 days',payload jsonb NOT NULL)`),
  sql.query(`CREATE INDEX IF NOT EXISTS radar_diagnostics_expiry ON radar_presence.diagnostics(expires_at)`),
  sql.query(`CREATE OR REPLACE FUNCTION radar_presence.cleanup_diagnostics() RETURNS void LANGUAGE sql AS $$ DELETE FROM radar_presence.diagnostics WHERE expires_at<=clock_timestamp(); $$`),
  sql.query(`SELECT cron.schedule('radar-diagnostics-expiry','*/10 * * * *','SELECT radar_presence.cleanup_diagnostics()') WHERE NOT EXISTS(SELECT 1 FROM cron.job WHERE jobname='radar-diagnostics-expiry')`)
 ]).catch(e=>{ready=null;throw e;});return ready;
}
export const diagnosticStore={
 async put(id,payload){const sql=database();await initialize(sql);await sql.transaction([
  sql`SELECT pg_advisory_xact_lock(${id},72915030)`,
  sql`INSERT INTO radar_presence.diagnostics(report_id,user_id,payload) VALUES(${payload.reportId}::uuid,${id},${JSON.stringify(payload)}::jsonb) ON CONFLICT(report_id) DO NOTHING`,
  sql`DELETE FROM radar_presence.diagnostics WHERE user_id=${id} AND report_id IN(SELECT report_id FROM radar_presence.diagnostics WHERE user_id=${id} ORDER BY received_at DESC OFFSET 50)`,
  sql`DELETE FROM radar_presence.diagnostics WHERE expires_at<=clock_timestamp()`
 ]);},
 async list(id,limit){const sql=database();await initialize(sql);return sql`SELECT user_id AS "userId",received_at AS "receivedAt",expires_at AS "expiresAt",payload FROM radar_presence.diagnostics WHERE expires_at>clock_timestamp() AND (${id}::integer IS NULL OR user_id=${id}) ORDER BY received_at DESC LIMIT ${limit}`;}
};
