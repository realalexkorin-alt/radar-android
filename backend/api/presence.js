import { createHash } from 'node:crypto';

const PREFIX = 'radar:presence:v1:';
export const TTL = 120000;
export const WRITE = `-- radar-presence-write-v1
local previous = tonumber(redis.call('GET', KEYS[1]) or '0')
local revision = tonumber(ARGV[1])
if revision <= previous then return 0 end
redis.call('SET', KEYS[1], ARGV[1], 'EX', 86400)
if ARGV[2] == 'delete' then
  redis.call('DEL', KEYS[2])
  redis.call('ZREM', KEYS[3], ARGV[3])
else
  redis.call('SET', KEYS[2], ARGV[4], 'EX', ARGV[6])
  redis.call('ZADD', KEYS[3], ARGV[5], ARGV[3])
  redis.call('EXPIRE', KEYS[3], 120)
end
redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', ARGV[7])
return 1`;
const RATE = `local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n`;
const hash = value => createHash('sha256').update(value).digest('hex');
export const identity = token => hash(token);
const finite = value => typeof value === 'number' && Number.isFinite(value);
export const fresh = (point, now) => point && finite(point.observedAt) && now >= point.observedAt - 5000 && now - point.observedAt < TTL;
export function boundsOf(query) {
  const b = Object.fromEntries(['west','south','east','north'].map(k=>[k,Number(query[k])]));
  if (!['west','south','east','north'].every(k=>query[k] !== undefined && query[k] !== '' && Number.isFinite(b[k])) ||
      b.west < -180 || b.west > 180 || b.east < -180 || b.east > 180 || b.south < -90 || b.north > 90 || b.south > b.north) throw new Error('bounds');
  return b;
}
export function visible(p,b) {
  return p.lat >= b.south && p.lat <= b.north &&
    (b.west <= b.east ? p.lon >= b.west && p.lon <= b.east : p.lon >= b.west || p.lon <= b.east);
}
async function redis(command) {
  const url=process.env.UPSTASH_REDIS_REST_URL || process.env.KV_REST_API_URL;
  const token=process.env.UPSTASH_REDIS_REST_TOKEN || process.env.KV_REST_API_TOKEN;
  if(!url || !token) throw new Error('storage unavailable');
  const response=await fetch(url,{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:JSON.stringify(command),signal:AbortSignal.timeout(8000)});
  if(!response.ok) throw new Error('storage unavailable');
  const result=await response.json(); if(result.error) throw new Error('storage unavailable');
  return result.result;
}
export function handler(command=redis, clock=Date.now) {
  return async (req,res) => {
    res.setHeader('Cache-Control','no-store, max-age=0');
    res.setHeader('X-Content-Type-Options','nosniff');
    const reply=(status,body)=>res.status(status).json(body);
    if(!['GET','PUT','DELETE'].includes(req.method)){res.setHeader('Allow','GET, PUT, DELETE');return reply(405,{error:'method'});}
    const now=clock();
    try {
      // Provider-supplied client address; never store raw addresses or coordinates in logs.
      const address=String(req.headers['x-vercel-forwarded-for'] || req.socket?.remoteAddress || 'unknown').split(',')[0].trim();
      const ipKey=PREFIX+'limit:ip:'+hash(address).slice(0,24);
      if(Number(await command(['EVAL',RATE,1,ipKey]))>240) return reply(429,{error:'rate limit'});
      if(req.method==='GET'){
        let b;try{b=boundsOf(req.query || {});}catch{return reply(400,{error:'bounds'});}
        await command(['ZREMRANGEBYSCORE',PREFIX+'active','-inf',now-TTL]);
        const ids=await command(['ZREVRANGEBYSCORE',PREFIX+'active','+inf',now-TTL,'LIMIT',0,1000]);
        const raw=ids.length ? await command(['MGET',...ids.map(id=>PREFIX+'point:'+id)]) : [];
        const points=raw.flatMap(value=>{try{const p=JSON.parse(value);return fresh(p,now)&&visible(p,b)?[p]:[];}catch{return [];}}).slice(0,200);
        return reply(200,{points,serverTime:now,ttlSeconds:120});
      }
      const match=/^Bearer ([0-9a-f]{64})$/.exec(req.headers.authorization || '');
      if(!match) return reply(401,{error:'authorization'});
      const id=identity(match[1]);
      let body=req.body;
      if(typeof body==='string'){if(Buffer.byteLength(body)>1024)return reply(413,{error:'body'});try{body=JSON.parse(body);}catch{return reply(400,{error:'json'});}}
      if(!body || !Number.isSafeInteger(body.revision) || body.revision<1)return reply(400,{error:'revision'});
      let point=null,ttl=120;
      if(req.method==='PUT'){
        const {lat,lon,accuracy,observedAt}=body;
        if(!finite(lat)||!finite(lon)||!finite(accuracy)||lat < -90||lat > 90||lon < -180||lon > 180||accuracy < 0||accuracy > 500||!fresh({observedAt},now))return reply(400,{error:'location'});
        if(Number(await command(['EVAL',RATE,1,PREFIX+'limit:writer:'+id]))>6)return reply(429,{error:'rate limit'});
        point={id,lat,lon,accuracy,observedAt};
        ttl=Math.max(1,Math.ceil((TTL-Math.max(0,now-observedAt))/1000));
      }
      const accepted=await command(['EVAL',WRITE,3,PREFIX+'revision:'+id,PREFIX+'point:'+id,PREFIX+'active',body.revision,point?'put':'delete',id,JSON.stringify(point),point?.observedAt || now,ttl,now-TTL]);
      return reply(accepted ? 200 : 409,{ok:!!accepted,id});
    } catch {return reply(503,{error:'temporarily unavailable'});}
  };
}
export default handler();
