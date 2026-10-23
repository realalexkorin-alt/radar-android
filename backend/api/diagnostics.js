import {createHash,timingSafeEqual} from 'node:crypto';import {accountStore} from '../account-store.js';import {diagnosticStore} from '../diagnostics-store.js';import {postgres} from '../postgres.js';
const digest=v=>createHash('sha256').update(v).digest('hex');
const word=(s,max=80)=>typeof s==='string'&&s.length<=max&&/^[a-zA-Z0-9_.$:-]+$/.test(s);
const integer=(n,max=Number.MAX_SAFE_INTEGER)=>Number.isSafeInteger(n)&&n>=0&&n<=max;
export function cleanReport(b){
 if(!b||!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(b.reportId||'')||!word(b.trigger)||!integer(b.createdAt)||!b.device||!Array.isArray(b.events)||b.events.length>1200)throw Error('shape');
 const d=b.device;if(!word(d.version,40)||!integer(d.sdk,200)||!integer(d.versionCode,1000000))throw Error('device');
 const device={version:d.version,versionCode:d.versionCode,sdk:d.sdk,manufacturer:String(d.manufacturer||'').replace(/[^a-zA-Z0-9 _.-]/g,'').slice(0,50),model:String(d.model||'').replace(/[^a-zA-Z0-9 _.-]/g,'').slice(0,60)};
 const snapshot={};for(const key of ['running','expected','locationPermission','notifications','gpsEnabled','network','widget','sharing'])if(typeof b.snapshot?.[key]==='boolean')snapshot[key]=b.snapshot[key];
 for(const key of ['gpsAgeSeconds','lastTickAgeSeconds','lastAttemptAgeSeconds','lastSuccessAgeSeconds','pauseRemainingSeconds','queuePending','enabledPoints'])if(integer(b.snapshot?.[key],2147483647))snapshot[key]=b.snapshot[key];
 const events=b.events.map(e=>{if(!integer(e.at)||!word(e.area,40)||!word(e.event)||!['info','error','fatal'].includes(e.level))throw Error('event');const out={at:e.at,area:e.area,event:e.event,level:e.level};if(integer(e.http,599)&&e.http>=100)out.http=e.http;if(integer(e.durationMs,3600000))out.durationMs=e.durationMs;if(integer(e.pauseSeconds,86400))out.pauseSeconds=e.pauseSeconds;if(integer(e.count,1000000))out.count=e.count;if(word(e.error,80))out.error=e.error;if(['own','saved','none'].includes(e.target))out.target=e.target;if(Array.isArray(e.frames))out.frames=e.frames.filter(f=>typeof f==='string'&&/^dev\.raf\.radar\.[a-zA-Z0-9_.$]+:[0-9-]{1,7}$/.test(f)).slice(0,8);return out;});
 return {reportId:b.reportId,createdAt:b.createdAt,trigger:b.trigger,device,snapshot,events};
}
export function handler(store=diagnosticStore,accounts=accountStore,rate=postgres,readerHash=()=>process.env.RADAR_DIAGNOSTICS_READER_HASH){return async(req,res)=>{
 res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');const reply=(s,b)=>res.status(s).json(b);
 try{
  const match=/^Bearer ([0-9a-f]{64})$/.exec(req.headers.authorization||'');if(!match)return reply(401,{error:'authorization'});
  if(req.method==='GET'){
   const expected=readerHash()||'',actual=digest(match[1]);if(!/^[0-9a-f]{64}$/.test(expected)||!timingSafeEqual(Buffer.from(actual,'hex'),Buffer.from(expected,'hex')))return reply(403,{error:'reader authorization'});
   const raw=req.query?.userId,id=raw==null?null:Number(raw);if(id!=null&&(!integer(id,999)||id<1))return reply(400,{error:'userId'});
   const limit=Math.min(20,Math.max(1,Number(req.query?.limit)||5));return reply(200,{reports:await store.list(id,Math.floor(limit)),retentionDays:7});
  }
  if(req.method!=='POST')return reply(405,{error:'method'});
  const user=await accounts.session(digest(match[1]));if(!user)return reply(401,{error:'session'});
  if(Number(await rate(['EVAL','rate',1,'radar:diagnostics:'+user.id]))>4)return reply(429,{error:'rate'});
  let b=req.body;let raw=typeof b==='string'?b:JSON.stringify(b);if(Buffer.byteLength(raw||'')>384000)return reply(413,{error:'size'});try{b=cleanReport(typeof b==='string'?JSON.parse(b):b);}catch{return reply(400,{error:'report'});}
  await store.put(user.id,b);return reply(200,{ok:true,reportId:b.reportId});
 }catch{return reply(503,{error:'storage unavailable'});}
};}export default handler();
