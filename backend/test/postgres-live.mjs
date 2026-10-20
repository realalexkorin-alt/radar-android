// Explicit integration test against the configured preview database, never CI by default.
// Uses synthetic coordinates and random disposable identities. No API/SQL traffic during expiry wait.
import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {handler,identity} from '../api/presence.js';
import {database,postgres} from '../postgres.js';
if(!process.env.RADAR_ALLOW_DB_TEST)throw Error('Set RADAR_ALLOW_DB_TEST for the dedicated integration run');
const serve=handler(postgres),sql=database(),a=randomBytes(32).toString('hex'),b=randomBytes(32).toString('hex'),expiry=randomBytes(32).toString('hex');
let revision=Date.now();
async function request(method,token,body,query={west:'60',south:'54',east:'63',north:'57'}){
 const result={};const res={setHeader(){},status(n){result.status=n;return this;},json(body){result.body=body;return result;}};
 await serve({method,headers:{'x-vercel-forwarded-for':'192.0.2.125',...(token?{authorization:'Bearer '+token}:{})},body:body?{...body,revision:body.revision??++revision}:undefined,query},res);
 return result;
}
try{
 const point={lat:55,lon:61,accuracy:10,observedAt:Date.now()};
 assert.equal((await request('PUT',a,point)).status,200);
 assert.equal((await request('PUT',b,{...point,lon:61.01,observedAt:Date.now()})).status,200);
 let all=await request('GET');assert.equal(all.status,200);assert.ok(all.body.points.some(p=>p.id===identity(a)));assert.ok(all.body.points.some(p=>p.id===identity(b)));
 assert.equal((await request('GET',null,null,{west:'0',south:'0',east:'1',north:'1'})).body.points.length,0);
 const deleteRev=++revision;assert.equal((await request('DELETE',a,{revision:deleteRev})).status,200);
 assert.equal((await request('PUT',a,{...point,observedAt:Date.now(),revision:deleteRev-1})).status,409);
 all=await request('GET');assert.ok(!all.body.points.some(p=>p.id===identity(a)));assert.ok(all.body.points.some(p=>p.id===identity(b)));
 await request('DELETE',b,{});
 console.log('PASS PostgreSQL: two clients, viewport, isolated deletion, atomic stale-write rejection');
 const before=Date.now();
 assert.equal((await request('PUT',expiry,{lat:55,lon:61,accuracy:10,observedAt:before})).status,200);
 const jobs=await sql`SELECT jobid FROM cron.job WHERE jobname='radar-presence-expiry'`;assert.equal(jobs.length,1);
 console.log('WAIT: fresh expiry fixture seeded; no API or SQL calls for 121 seconds');
 await new Promise(resolve=>setTimeout(resolve,121000));
 const rows=await sql`SELECT point IS NULL AS removed FROM radar_presence.points WHERE id=${identity(expiry)}`;
 assert.equal(rows[0]?.removed,true,'Coordinates retained after idle expiry');
 const stopped=await sql`SELECT count(*)::int AS count FROM cron.job WHERE jobname='radar-presence-expiry'`;assert.equal(stopped[0].count,0,'Empty scheduler must stop');
 console.log('PASS PostgreSQL: idle coordinates cleared automatically and empty scheduler stopped');
}catch(e){
 console.error('PostgreSQL validation failed:',e.message);
 try{console.error('Recent cron status:',await sql`SELECT status,return_message FROM cron.job_run_details WHERE command='SELECT radar_presence.cleanup()' ORDER BY start_time DESC LIMIT 3`);}catch{}
 process.exitCode=1;
}finally{
 for(const token of [a,b,expiry])await request('DELETE',token,{}).catch(()=>{});
}
