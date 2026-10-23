import {database} from './postgres.js';
let ready;
export async function initialize(sql=database()){
 if(!ready)ready=sql.transaction([
  sql`SELECT pg_advisory_xact_lock(72915027)`,
  sql.query(`CREATE TABLE IF NOT EXISTS radar_presence.accounts(id integer PRIMARY KEY CHECK(id BETWEEN 1 AND 999),name text NOT NULL DEFAULT '',password text NOT NULL,recovery text NOT NULL,legacy_id text UNIQUE,point jsonb,last_seen bigint NOT NULL DEFAULT 0,offline boolean NOT NULL DEFAULT true)`),
  sql.query(`CREATE TABLE IF NOT EXISTS radar_presence.sessions(token text PRIMARY KEY,user_id integer NOT NULL REFERENCES radar_presence.accounts(id),expires_at timestamptz NOT NULL)`),
  sql.query(`CREATE INDEX IF NOT EXISTS radar_account_sessions ON radar_presence.sessions(user_id)`),
  sql.query(`CREATE TABLE IF NOT EXISTS radar_presence.friendships(low_id integer REFERENCES radar_presence.accounts(id),high_id integer REFERENCES radar_presence.accounts(id),sender integer REFERENCES radar_presence.accounts(id),accepted boolean NOT NULL DEFAULT false,PRIMARY KEY(low_id,high_id),CHECK(low_id<high_id))`)
 ]).catch(e=>{ready=null;throw e;});return ready;
}
const user=p=>p?{id:p.id,name:p.name}:null;
export const accountStore={
 async register(id,name,password,recovery,legacy,token){
  const sql=database();await initialize(sql);
  const r=await sql.transaction([
   sql`SELECT pg_advisory_xact_lock(72915027)`,
   sql`WITH choice AS (SELECT n FROM generate_series(100,999) n WHERE NOT EXISTS(SELECT 1 FROM radar_presence.accounts WHERE id=n) ORDER BY random() LIMIT 1),
   created AS (INSERT INTO radar_presence.accounts(id,name,password,recovery,legacy_id,point,last_seen)
    SELECT COALESCE(${id}::integer,(SELECT n FROM choice)),${name},${password},${recovery},${legacy},
      (SELECT point - 'id' - 'name' - 'lastSeen' - 'offline' FROM radar_presence.members_v2 WHERE id=${legacy}),(extract(epoch FROM clock_timestamp())*1000)::bigint
    WHERE (${id}::integer IS NOT NULL OR EXISTS(SELECT 1 FROM choice))
    ON CONFLICT DO NOTHING RETURNING *),
   session AS (INSERT INTO radar_presence.sessions(token,user_id,expires_at) SELECT ${token},id,clock_timestamp()+interval '90 days' FROM created)
   SELECT id,name FROM created`,
   sql`DELETE FROM radar_presence.members_v2 WHERE id=${legacy} AND EXISTS(SELECT 1 FROM radar_presence.accounts WHERE legacy_id=${legacy})`,
   sql`UPDATE radar_presence.points SET point=NULL,expires_at=NULL WHERE id=${legacy} AND EXISTS(SELECT 1 FROM radar_presence.accounts WHERE legacy_id=${legacy})`
  ]);return user(r[1][0]);
 },
 async credentials(id){const sql=database();await initialize(sql);return (await sql`SELECT id,name,password,recovery FROM radar_presence.accounts WHERE id=${id}`)[0];},
 async session(token){const sql=database();await initialize(sql);return user((await sql`SELECT a.id,a.name FROM radar_presence.accounts a JOIN radar_presence.sessions s ON a.id=s.user_id WHERE s.token=${token} AND s.expires_at>clock_timestamp()`)[0]);},
 async login(id,token){const sql=database();await sql`INSERT INTO radar_presence.sessions(token,user_id,expires_at) VALUES(${token},${id},clock_timestamp()+interval '90 days')`;},
 async reset(id,expected,password,recovery,token,kind){
  const sql=database();const r=await sql.transaction([
   sql`SELECT pg_advisory_xact_lock(${id},72915027)`,
   sql`WITH changed AS (UPDATE radar_presence.accounts SET password=${password},recovery=CASE WHEN ${kind}='recover' THEN ${recovery} ELSE recovery END WHERE id=${id} AND (CASE WHEN ${kind}='recover' THEN recovery ELSE password END)=${expected} RETURNING id),
   removed AS (DELETE FROM radar_presence.sessions WHERE user_id IN(SELECT id FROM changed)),
   added AS (INSERT INTO radar_presence.sessions(token,user_id,expires_at) SELECT ${token},id,clock_timestamp()+interval '90 days' FROM changed)
   SELECT count(*)::int AS n FROM changed`
  ]);return r[1][0].n===1;
 },
 async name(id,name){const sql=database();await sql`UPDATE radar_presence.accounts SET name=${name} WHERE id=${id}`;},
 async erase(id){const sql=database();await sql.transaction([sql`DELETE FROM radar_presence.friendships WHERE low_id=${id} OR high_id=${id}`,sql`DELETE FROM radar_presence.sessions WHERE user_id=${id}`,sql`DELETE FROM radar_presence.accounts WHERE id=${id}`]);},
 async logout(token){const sql=database();await sql`DELETE FROM radar_presence.sessions WHERE token=${token}`;},
 async sync(id,point,mode){const sql=database();await sql`UPDATE radar_presence.accounts SET last_seen=(extract(epoch FROM clock_timestamp())*1000)::bigint,offline=${mode==='offline'},point=CASE WHEN ${mode}='position' AND COALESCE((point->>'observedAt')::bigint,0)<=${point?.observedAt??0} THEN ${JSON.stringify(point)}::jsonb WHEN ${mode}='clear-position' THEN NULL ELSE point END WHERE id=${id}`;},
 async state(id){const sql=database();const r=await sql`SELECT a.id,a.name,a.point,a.last_seen AS "lastSeen",a.offline,f.sender,f.accepted FROM radar_presence.friendships f JOIN radar_presence.accounts a ON a.id=CASE WHEN f.low_id=${id} THEN f.high_id ELSE f.low_id END WHERE f.low_id=${id} OR f.high_id=${id}`;
  return {friends:r.filter(x=>x.accepted).map(({sender,accepted,point,...p})=>({...p,...(point?{lat:point.lat,lon:point.lon,accuracy:point.accuracy,observedAt:point.observedAt}:{}),hasLocation:!!point})),incoming:r.filter(x=>!x.accepted&&x.sender!==id).map(user),outgoing:r.filter(x=>!x.accepted&&x.sender===id).map(user)};
 },
 async friend(id,target,action){const sql=database();const low=Math.min(id,target),high=Math.max(id,target);
  if(action==='request'){const r=await sql`INSERT INTO radar_presence.friendships(low_id,high_id,sender) SELECT ${low},${high},${id} WHERE EXISTS(SELECT 1 FROM radar_presence.accounts WHERE id=${target}) ON CONFLICT DO NOTHING RETURNING sender`;return r.length>0;}
  if(action==='accept'){const r=await sql`UPDATE radar_presence.friendships SET accepted=true WHERE low_id=${low} AND high_id=${high} AND sender=${target} AND accepted=false RETURNING sender`;return r.length>0;}
  const r=await sql`DELETE FROM radar_presence.friendships WHERE low_id=${low} AND high_id=${high} AND ((${action}='remove' AND accepted=true) OR (${action}='cancel' AND sender=${id} AND accepted=false) OR (${action}='reject' AND sender=${target} AND accepted=false)) RETURNING sender`;return r.length>0;
 }
};
