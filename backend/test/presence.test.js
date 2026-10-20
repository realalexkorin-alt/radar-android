import test from 'node:test';
import assert from 'node:assert/strict';
import {handler,identity,visible,fresh,boundsOf,TTL,WRITE} from '../api/presence.js';
const now=1800000000000;
function fakeStore(){
 const points=new Map(),revisions=new Map(),limits=new Map();
 return {points, async command(c){
  if(c[0]==='EVAL'&&c[1]!==WRITE){const k=c[3];limits.set(k,(limits.get(k)||0)+1);return limits.get(k);}
  if(c[0]==='EVAL'){
   const [revisionKey,pointKey,index,rev,action,id,json,score,ttl]=c.slice(3);
   if((revisions.get(revisionKey)||0)>=rev)return 0;
   revisions.set(revisionKey,rev);if(action==='delete')points.delete(pointKey);else points.set(pointKey,json);return 1;
  }
  if(c[0]==='ZREMRANGEBYSCORE')return 0;
  if(c[0]==='ZREVRANGEBYSCORE')return [...points.values()].map(s=>JSON.parse(s).id);
  if(c[0]==='MGET')return c.slice(1).map(k=>points.get(k)||null);
  throw Error('Unexpected command');
 }};
}
async function request(h,method,body,token='a'.repeat(64),query={west:'-180',east:'180',south:'-90',north:'90'}){
 let status,payload;const headers={};await h({method,body,headers:{authorization:'Bearer '+token},query,socket:{remoteAddress:'127.0.0.1'}},{setHeader(k,v){headers[k]=v;},status(n){status=n;return this;},json(b){payload=b;}});return {status,payload,headers};
}
const point=(revision=1)=>({lat:55,lon:61,accuracy:10,observedAt:now,revision});
test('two installations see each other, writer cannot delete another identity',async()=>{
 const s=fakeStore(),h=handler(s.command,()=>now),a='a'.repeat(64),b='b'.repeat(64);
 assert.equal((await request(h,'PUT',point(),a)).status,200);
 assert.equal((await request(h,'PUT',{...point(),lon:62},b)).status,200);
 assert.equal((await request(h,'GET')).payload.points.length,2);
 await request(h,'DELETE',{revision:2},a);
 const remaining=(await request(h,'GET')).payload.points;assert.equal(remaining.length,1);assert.equal(remaining[0].id,identity(b));
 assert.equal((await request(h,'PUT',point(),a)).status,409);
 assert.equal((await request(h,'GET')).payload.points.length,1);
});
test('invalid or stale locations and missing authorization are rejected',async()=>{
 const s=fakeStore(),h=handler(s.command,()=>now);
 for(const change of [{lat:91},{lon:Infinity},{accuracy:501},{accuracy:-1},{observedAt:now-TTL},{observedAt:now+6000}])assert.equal((await request(h,'PUT',{...point(),...change})).status,400);
 assert.equal((await request(h,'PUT',point(),'bad')).status,401);
 assert.equal(s.points.size,0);
});
test('view bounds and expiry do not leak old or off-screen points',async()=>{
 const s=fakeStore();let t=now;const h=handler(s.command,()=>t);
 await request(h,'PUT',point());
 assert.equal((await request(h,'GET',null,undefined,{west:'0',east:'10',south:'0',north:'10'})).payload.points.length,0);
 t+=TTL;assert.equal((await request(h,'GET')).payload.points.length,0);
 assert.ok(visible({lat:0,lon:179},{west:170,east:-170,south:-1,north:1}));
 assert.throws(()=>boundsOf({west:'',east:0,south:0,north:1}));assert.ok(!fresh(null,now));
});
test('storage failure is explicit and all responses prohibit caching',async()=>{
 const h=handler(async()=>{throw Error('secret connection URL');},()=>now);
 const r=await request(h,'GET');assert.equal(r.status,503);assert.equal(r.headers['Cache-Control'],'no-store, max-age=0');assert.ok(!JSON.stringify(r).includes('secret connection'));
});
