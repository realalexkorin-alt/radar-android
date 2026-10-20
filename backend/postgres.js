import {neon} from '@neondatabase/serverless';
let client;
export function database() {
 if (!client) {
  if(!process.env.DATABASE_URL) throw Error('storage unavailable');
  client=neon(process.env.DATABASE_URL);
 }
 return client;
}
// Compatibility adapter for the small atomic storage contract of presence.js.
// SQL parameters carry all user values; neither tokens nor coordinates are logged.
export async function postgres(command) {
 const sql=database(),[op,script,count,...args]=command;
 if(op==='EVAL' && count===1) {
  const rows=await sql`INSERT INTO radar_presence.limits AS l (id,count,expires_at)
   VALUES (${args[0]},1,clock_timestamp()+interval '60 seconds')
   ON CONFLICT(id) DO UPDATE SET count=CASE WHEN l.expires_at<=clock_timestamp() THEN 1 ELSE l.count+1 END,
   expires_at=CASE WHEN l.expires_at<=clock_timestamp() THEN clock_timestamp()+interval '60 seconds' ELSE l.expires_at END RETURNING count`;
  return rows[0].count;
 }
 if(op==='EVAL' && count===3) {
  const [, , ,revision,mode,id,json,observedAt]=args;
  const rows=await sql`SELECT radar_presence.write_point(${id},${revision}::bigint,${mode==='delete'?null:json}::jsonb,${observedAt}::bigint) AS accepted`;
  return rows[0].accepted;
 }
 if(op==='ZREMRANGEBYSCORE') {
  // Every read also discards expired data; pg_cron covers periods without traffic.
  await sql`UPDATE radar_presence.points SET point=NULL,expires_at=NULL WHERE expires_at<=clock_timestamp()`;
  return 0;
 }
 if(op==='ZREVRANGEBYSCORE') {
  const rows=await sql`SELECT id FROM radar_presence.points WHERE point IS NOT NULL AND expires_at>clock_timestamp() ORDER BY (point->>'observedAt')::bigint DESC LIMIT 1000`;
  return rows.map(row=>row.id);
 }
 if(op==='MGET') {
  const ids=command.slice(1).map(key=>key.split(':').at(-1));
  const rows=await sql`SELECT id,point FROM radar_presence.points WHERE id=ANY(${ids}::text[]) AND expires_at>clock_timestamp()`;
  const byId=new Map(rows.map(row=>[row.id,JSON.stringify(row.point)]));
  return ids.map(id=>byId.get(id)??null);
 }
 throw Error('unsupported storage command');
}
