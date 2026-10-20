// Run only against the test deployment; temporary synthetic coordinates, always withdrawn.
import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
const base=process.env.RADAR_TEST_ENDPOINT;
if(!base?.startsWith('https://'))throw Error('Set RADAR_TEST_ENDPOINT');
const a=randomBytes(32).toString('hex'),b=randomBytes(32).toString('hex');let revision=Date.now();
async function request(method,token,body,query=''){
 const r=await fetch(base+query,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),'Content-Type':'application/json',...(process.env.RADAR_TEST_BYPASS?{'x-vercel-protection-bypass':process.env.RADAR_TEST_BYPASS}:{})},...(body?{body:JSON.stringify({...body,revision:++revision})}:{}),signal:AbortSignal.timeout(15000)});
 return {status:r.status,body:await r.json()};
}
const query='?west=60&south=54&east=63&north=57';
try{
 const first=await request('PUT',a,{lat:55,lon:61,accuracy:10,observedAt:Date.now()});assert.equal(first.status,200);
 const second=await request('PUT',b,{lat:55.01,lon:61.01,accuracy:20,observedAt:Date.now()});assert.equal(second.status,200);
 const all=await request('GET',null,null,query);assert.equal(all.status,200);
 assert.ok(all.body.points.some(p=>p.id===first.body.id));assert.ok(all.body.points.some(p=>p.id===second.body.id));
 assert.equal((await request('PUT',null,{lat:55,lon:61,accuracy:10,observedAt:Date.now()})).status,401);
 const hidden=await request('GET',null,null,'?west=0&south=0&east=1&north=1');assert.ok(!hidden.body.points.some(p=>p.id===first.body.id));
 assert.equal((await request('DELETE',a,{})).status,200);
 const remaining=await request('GET',null,null,query);assert.ok(!remaining.body.points.some(p=>p.id===first.body.id));assert.ok(remaining.body.points.some(p=>p.id===second.body.id));
 const originalRev=revision;
 const replay=await fetch(base,{method:'PUT',headers:{Authorization:'Bearer '+a,'Content-Type':'application/json',...(process.env.RADAR_TEST_BYPASS?{'x-vercel-protection-bypass':process.env.RADAR_TEST_BYPASS}:{})},body:JSON.stringify({lat:55,lon:61,accuracy:10,observedAt:Date.now(),revision:originalRev-2})});assert.equal(replay.status,409);
 console.log('PASS: two clients, viewport, authorization, deletion and stale-write rejection');
}finally{await request('DELETE',a,{}).catch(()=>{});await request('DELETE',b,{}).catch(()=>{});}
