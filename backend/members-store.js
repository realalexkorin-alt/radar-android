import {database} from './postgres.js';
let ready;
// Additive schema: legacy 0.1.20 clients and their expiry contract stay intact.
export async function initialize(sql=database()) {
 if(!ready)ready=(async()=>{
  await sql.transaction([
   sql`SELECT pg_advisory_xact_lock(72915022)`,
   sql.query(`CREATE TABLE IF NOT EXISTS radar_presence.profiles_v2(id text PRIMARY KEY,name text NOT NULL,revision bigint NOT NULL)`),
   sql.query(`CREATE TABLE IF NOT EXISTS radar_presence.members_v2(id text PRIMARY KEY REFERENCES radar_presence.profiles_v2(id),point jsonb NOT NULL,expires_at timestamptz NOT NULL)`),
   sql.query(`CREATE INDEX IF NOT EXISTS radar_members_v2_expiry ON radar_presence.members_v2(expires_at)`),
   sql.query(`CREATE OR REPLACE FUNCTION radar_presence.cleanup_members_v2() RETURNS void LANGUAGE plpgsql AS $$ BEGIN
    PERFORM pg_advisory_xact_lock(72915021);
    DELETE FROM radar_presence.members_v2 WHERE expires_at<=clock_timestamp();
    IF NOT EXISTS(SELECT 1 FROM radar_presence.members_v2) THEN PERFORM cron.unschedule(jobid) FROM cron.job WHERE jobname='radar-members-v2-expiry'; END IF;
   END $$`)
  ]);
 })().catch(e=>{ready=null;throw e;});
 return ready;
}
export const memberStore={
 async profile(id,revision,name){
  const sql=database();await initialize(sql);
  const r=await sql`INSERT INTO radar_presence.profiles_v2 AS p(id,name,revision) VALUES(${id},${name},${revision}) ON CONFLICT(id) DO UPDATE SET name=EXCLUDED.name,revision=EXCLUDED.revision WHERE p.revision<EXCLUDED.revision RETURNING id`;
  return r.length>0;
 },
 async change(id,revision,name,point,mode){
  const sql=database();await initialize(sql);
  // All commands for an installation serialize with deletion and increasing revisions.
  const r=await sql.transaction([
   sql`SELECT pg_advisory_xact_lock(72915021)`,
   sql`SELECT pg_advisory_xact_lock(hashtextextended(${id},72915021))`,
   sql`WITH accepted AS (INSERT INTO radar_presence.profiles_v2 AS p(id,name,revision) VALUES(${id},${name},${revision}) ON CONFLICT(id) DO UPDATE SET name=CASE WHEN EXCLUDED.name='' THEN p.name ELSE EXCLUDED.name END,revision=EXCLUDED.revision WHERE p.revision<EXCLUDED.revision RETURNING id),
   published AS (INSERT INTO radar_presence.members_v2(id,point,expires_at)
    SELECT id,${JSON.stringify(point)}::jsonb,clock_timestamp()+interval '7 days' FROM accepted WHERE ${mode}='put'
    ON CONFLICT(id) DO UPDATE SET point=EXCLUDED.point,expires_at=EXCLUDED.expires_at),
   offlined AS (UPDATE radar_presence.members_v2 SET point=jsonb_set(jsonb_set(point,'{offline}','true'::jsonb),'{lastSeen}',to_jsonb((extract(epoch FROM clock_timestamp())*1000)::bigint)) WHERE id IN(SELECT id FROM accepted) AND ${mode}='offline'),
   withdrawn AS (DELETE FROM radar_presence.members_v2 WHERE id IN(SELECT id FROM accepted) AND ${mode}='delete')
   SELECT count(*)::int AS accepted FROM accepted`,
   sql`SELECT pg_advisory_xact_lock(72915021)`,
   sql`SELECT cron.schedule('radar-members-v2-expiry','*/10 * * * *','SELECT radar_presence.cleanup_members_v2()') WHERE ${mode}='put' AND NOT EXISTS(SELECT 1 FROM cron.job WHERE jobname='radar-members-v2-expiry')`
  ]);
  return r[2][0].accepted>0;
 },
 async list(bounds,directory){
  const sql=database();await initialize(sql);
  const {west,east,south,north}=bounds;
  // Bounded queries for the map. Directory is an explicit app operation for shared users.
  const r=await sql`SELECT m.id,p.name,m.point FROM radar_presence.members_v2 m JOIN radar_presence.profiles_v2 p USING(id) WHERE expires_at>clock_timestamp()
    AND (${directory} OR ((point->>'lat')::float8 BETWEEN ${south} AND ${north} AND (CASE WHEN ${west}<=${east} THEN (point->>'lon')::float8 BETWEEN ${west} AND ${east} ELSE (point->>'lon')::float8>=${west} OR (point->>'lon')::float8<=${east} END))) ORDER BY (point->>'lastSeen')::bigint DESC LIMIT 200`;
  const legacy=await sql`SELECT id,point FROM radar_presence.points WHERE point IS NOT NULL AND expires_at>clock_timestamp() AND (${directory} OR ((point->>'lat')::float8 BETWEEN ${south} AND ${north} AND (CASE WHEN ${west}<=${east} THEN (point->>'lon')::float8 BETWEEN ${west} AND ${east} ELSE (point->>'lon')::float8>=${west} OR (point->>'lon')::float8<=${east} END))) ORDER BY (point->>'observedAt')::bigint DESC LIMIT 200`;
  const ids=new Set(r.map(p=>p.id));return [...r.map(p=>({...p.point,id:p.id,name:p.name})),...legacy.filter(p=>!ids.has(p.id)).map(p=>({...p.point,name:'Пользователь '+p.id.slice(0,6),lastSeen:p.point.observedAt}))].slice(0,200);
 }
};
