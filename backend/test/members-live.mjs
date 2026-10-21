import {randomBytes} from 'node:crypto';import assert from 'node:assert/strict';
const url=process.env.RADAR_TEST_ENDPOINT;if(!url||!url.endsWith('/api/members'))throw Error('Explicit members endpoint required');
const tokens=[randomBytes(32).toString('hex'),randomBytes(32).toString('hex')];let revision=Date.now();
const query='?west=-123.45679&south=-47.12346&east=-123.45677&north=-47.12344';
async function req(method,token,body,queryString=''){
 const r=await fetch(url+queryString,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify({...body,revision:body.revision??++revision}):undefined,signal:AbortSignal.timeout(30000)});const j=await r.json();return {status:r.status,body:j};
}
try{
 for(let i=0;i<2;i++){const r=await req('PUT',tokens[i],{name:'Synthetic '+i,lat:-47.12345,lon:-123.45678,accuracy:10,observedAt:Date.now()});assert.equal(r.status,200,JSON.stringify(r));}
 let all=await req('GET',null,null,query);assert.equal(all.status,200);assert.equal(all.body.points.length,2);assert.ok(all.body.points.every(p=>p.online));
 const id=(await req('POST',tokens[0],{name:'Renamed synthetic'})).body.id;
 all=await req('GET',null,null,query);assert.equal(all.body.points.find(p=>p.id===id).name,'Renamed synthetic');
 assert.equal((await req('POST',tokens[0],{offline:true})).status,200);all=await req('GET',null,null,query);assert.equal(all.body.points.find(p=>p.id===id).online,false);assert.equal(all.body.points.length,2);
 const rev=++revision;assert.equal((await req('DELETE',tokens[0],{revision:rev})).status,200);assert.equal((await req('PUT',tokens[0],{revision:rev,name:'Replay',lat:-47.12345,lon:-123.45678,accuracy:10,observedAt:Date.now()})).status,409);
 all=await req('GET',null,null,query);assert.equal(all.body.points.length,1);assert.notEqual(all.body.points[0].id,id);
 console.log('PASS live Neon/HTTPS: 2 synthetic clients; rename; stable identity; offline point retained; isolated withdrawal; same-revision replay rejected');
}finally{for(const token of tokens)await req('DELETE',token,{}).catch(()=>{});}
