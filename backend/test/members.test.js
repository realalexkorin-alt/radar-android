import test from 'node:test';import assert from 'node:assert/strict';
import {handler,cleanName,RETENTION} from '../api/members.js';import {identity} from '../api/presence.js';
function fixture(){let now=1800000000000;const profiles=new Map(),points=new Map();const store={async change(id,revision,name,point,mode){let p=profiles.get(id);if(p&&p.revision>=revision)return false;profiles.set(id,{revision,name:name||p?.name||''});if(mode==='put')points.set(id,point);if(mode==='delete')points.delete(id);if(mode==='offline'&&points.has(id))Object.assign(points.get(id),{offline:true,lastSeen:now});return true;},async list(){return [...points].map(([id,p])=>({...p,name:profiles.get(id).name}));}};const h=handler(store,()=>now,async()=>1);
return {points,setTime(t){now=t;},async req(method,body,token='a'.repeat(64),query={west:'60',east:'62',south:'54',north:'56'}){let status,payload;await h({method,body,query,headers:{authorization:'Bearer '+token}}, {setHeader(){},status(s){status=s;return this;},json(b){payload=b;}});return {status,payload};}};}
const point={revision:1,name:'Никита',lat:55,lon:61,accuracy:10,observedAt:1800000000000};
test('profile rename keeps identity, updates map, offline remains and withdrawal is isolated',async()=>{
 const f=fixture();assert.equal((await f.req('PUT',point)).status,200);await f.req('PUT',{...point,name:'Друг'},'b'.repeat(64));
 const id=(await f.req('GET')).payload.points[0].id;assert.equal(id,identity('a'.repeat(64)));
 await f.req('POST',{revision:2,name:'Новое имя'});assert.equal((await f.req('GET')).payload.points[0].name,'Новое имя');
 await f.req('POST',{revision:3,offline:true});let p=(await f.req('GET')).payload.points[0];assert.equal(p.online,false);assert.equal(p.lat,55);
 await f.req('DELETE',{revision:4});assert.equal((await f.req('GET')).payload.points.length,1);assert.equal((await f.req('PUT',{...point,revision:3})).status,409);
});
test('offline after heartbeat timeout, 7 day expiry, viewport, name validation and auth',async()=>{
 const f=fixture();await f.req('PUT',point);f.setTime(point.observedAt+120000);assert.equal((await f.req('GET')).payload.points[0].online,false);
 assert.equal((await f.req('GET',null,undefined,{west:'0',east:'1',south:'0',north:'1'})).payload.points.length,0);
 f.setTime(point.observedAt+RETENTION);assert.equal((await f.req('GET')).payload.points.length,0);
 assert.equal((await f.req('POST',{revision:2,name:'   '})).status,400);assert.equal((await f.req('DELETE',{revision:2},'bad')).status,401);
 assert.equal(cleanName('  Ва\u0000ся\n  Петров  '),'Вася Петров');
});
