import {identity,boundsOf,fresh,visible} from './presence.js';
import {memberStore} from '../members-store.js';
import {postgres} from '../postgres.js';
export const RETENTION=7*86400000;
export function cleanName(value){return typeof value==='string'?value.normalize('NFC').replace(/[\p{Cc}\p{Cf}]/gu,'').trim().replace(/\s+/gu,' ').slice(0,32):'';}
export function handler(store=memberStore,clock=Date.now,rate=postgres){return async(req,res)=>{
 res.setHeader('Cache-Control','no-store, max-age=0');res.setHeader('X-Content-Type-Options','nosniff');
 const reply=(s,b)=>res.status(s).json(b),now=clock();
 try{
  if(!['GET','PUT','PATCH','POST','DELETE'].includes(req.method))return reply(405,{error:'method'});
  const address=String(req.headers['x-vercel-forwarded-for']||req.socket?.remoteAddress||'unknown').split(',')[0];
  if(Number(await rate(['EVAL','rate',1,'radar:v2:ip:'+identity(address)]))>240)return reply(429,{error:'rate limit'});
  if(req.method==='GET'){
   const directory=req.query?.directory==='1';let bounds;
   try{bounds=directory?{west:0,east:0,south:0,north:0}:boundsOf(req.query||{});}catch{return reply(400,{error:'bounds'});}
   const points=(await store.list(bounds,directory)).filter(p=>now-p.lastSeen<RETENTION&&(directory||visible(p,bounds))).map(p=>({...p,online:!p.offline&&now-p.lastSeen<120000&&now>=p.lastSeen-5000}));
   return reply(200,{points,serverTime:now,retentionDays:7});
  }
  const match=/^Bearer ([0-9a-f]{64})$/.exec(req.headers.authorization||'');if(!match)return reply(401,{error:'authorization'});
  const id=identity(match[1]);let body=req.body;
  if(typeof body==='string'){if(Buffer.byteLength(body)>2048)return reply(413,{error:'body'});try{body=JSON.parse(body);}catch{return reply(400,{error:'json'});}}
  if(!body||!Number.isSafeInteger(body.revision)||body.revision<1)return reply(400,{error:'revision'});
  if(Number(await rate(['EVAL','rate',1,'radar:v2:writer:'+id]))>12)return reply(429,{error:'rate limit'});
  const name=cleanName(body.name);let point=null,mode='profile';
  if(req.method==='PUT'){
   if(!name||!Number.isFinite(body.lat)||!Number.isFinite(body.lon)||Math.abs(body.lat)>90||Math.abs(body.lon)>180||!Number.isFinite(body.accuracy)||body.accuracy<0||body.accuracy>500||!fresh(body,now))return reply(400,{error:'location or name'});
   point={id,lat:body.lat,lon:body.lon,accuracy:body.accuracy,observedAt:body.observedAt,lastSeen:now,offline:false};mode='put';
  }else if(req.method==='DELETE')mode='delete';
  else if(body.offline===true)mode='offline';else if(!name)return reply(400,{error:'name'});
  const accepted=await store.change(id,body.revision,name,point,mode);
  return reply(accepted?200:409,{ok:accepted,id});
 }catch{return reply(503,{error:'temporarily unavailable'});}
};}
export default handler();
