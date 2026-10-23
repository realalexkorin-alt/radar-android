import {scrypt,randomBytes,randomInt,timingSafeEqual,createHash} from 'node:crypto';
import {promisify} from 'node:util';
import {accountStore} from '../account-store.js';
import {postgres} from '../postgres.js';
import {cleanName} from './members.js';
const derive=promisify(scrypt);export const digest=v=>createHash('sha256').update(v).digest('hex');
export async function hash(value){const salt=randomBytes(16).toString('hex');return salt+':'+Buffer.from(await derive(value,salt,64,{N:16384,r:8,p:1,maxmem:64*1024*1024})).toString('hex');}
export async function verify(value,encoded){try{const [salt,h]=encoded.split(':');const expected=Buffer.from(h,'hex'),actual=Buffer.from(await derive(value,salt,64,{N:16384,r:8,p:1,maxmem:64*1024*1024}));return expected.length===64&&timingSafeEqual(actual,expected);}catch{return false;}}
const password=p=>typeof p==='string'&&p.length>=8&&p.length<=128;
const validId=id=>Number.isSafeInteger(id)&&id>=1&&id<=999;
const recoveryCode=()=>Array.from({length:16},()=>randomInt(10)).join('');
export function handler(store=accountStore,rate=postgres){return async(req,res)=>{
 res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');const reply=(s,b)=>res.status(s).json(b);
 try{
  if(req.method!=='POST')return reply(405,{error:'method'});
  let b=req.body;if(typeof b==='string'){if(Buffer.byteLength(b)>4096)return reply(413,{error:'body'});try{b=JSON.parse(b);}catch{return reply(400,{error:'json'});}}
  if(!b||typeof b.action!=='string')return reply(400,{error:'action'});
  const address=String(req.headers['x-vercel-forwarded-for']||req.socket?.remoteAddress||'unknown').split(',')[0];
  const authAction=['register','login','recover'].includes(b.action);
  if(Number(await rate(['EVAL','rate',1,'radar:account:ip:'+digest(address)]))>120)return reply(429,{error:'Подождите перед повтором'});
  if(authAction&&Number(await rate(['EVAL','rate',1,'radar:account:auth:'+digest(address+':'+(validId(b.id)?b.id:'new'))]))>10)return reply(429,{error:'Слишком много попыток. Подождите минуту'});
  if(authAction&&validId(b.id)&&Number(await rate(['EVAL','rate',1,'radar:account:attempt:'+b.id]))>30)return reply(429,{error:'Слишком много попыток для этого ID'});
  let u;let raw=randomBytes(32).toString('hex');
  if(b.action==='register'){
   if((b.id!=null&&!validId(b.id))||!password(b.password))return reply(400,{error:'ID: 1–999; пароль: 8–128 символов'});
   const code=recoveryCode(),legacy=/^[0-9a-f]{64}$/.test(b.legacyToken||'')?digest(b.legacyToken):null;
   u=await store.register(b.id??null,cleanName(b.name),await hash(b.password),await hash(code),legacy,digest(raw));
   if(!u)return reply(409,{error:'ID уже занят или профиль уже перенесён. Выберите другой ID либо войдите'});
   return reply(200,{user:u,session:raw,recoveryCode:code});
  }
  if(b.action==='login'||b.action==='recover'){
   if(!validId(b.id)||!(b.action==='login'?password(b.password):/^\d{16}$/.test(b.code||'')&&password(b.password)))return reply(400,{error:'Проверьте ID, пароль и код'});
   const account=await store.credentials(b.id),credential=account?.[b.action==='login'?'password':'recovery'];
   if(!credential||!await verify(b.action==='login'?b.password:b.code,credential))return reply(401,{error:'Неверные данные входа'});
   if(b.action==='login')await store.login(b.id,digest(raw));
   else {const code=recoveryCode();if(!await store.reset(b.id,credential,await hash(b.password),await hash(code),digest(raw),'recover'))return reply(409,{error:'Код уже использован'});return reply(200,{user:{id:account.id,name:account.name},session:raw,recoveryCode:code});}
   return reply(200,{user:{id:account.id,name:account.name},session:raw});
  }
  const match=/^Bearer ([0-9a-f]{64})$/.exec(req.headers.authorization||'');if(!match)return reply(401,{error:'Войдите в аккаунт'});
  u=await store.session(digest(match[1]));if(!u)return reply(401,{error:'Сессия истекла. Войдите снова'});
  if(b.action==='delete-account'){const account=await store.credentials(u.id);if(typeof b.password!=='string'||!await verify(b.password,account.password))return reply(401,{error:'Неверный пароль'});await store.erase(u.id);return reply(200,{ok:true});}
  if(b.action==='password'){
   if(!password(b.password)||typeof b.oldPassword!=='string')return reply(400,{error:'Новый пароль: 8–128 символов'});
   const account=await store.credentials(u.id);if(!await verify(b.oldPassword,account.password))return reply(401,{error:'Неверный старый пароль'});
   if(!await store.reset(u.id,account.password,await hash(b.password),null,digest(raw),'password'))return reply(409,{error:'Пароль уже изменён'});
   return reply(200,{user:u,session:raw});
  }
  if(b.action==='name'){const name=cleanName(b.name);if(!name)return reply(400,{error:'Введите имя'});await store.name(u.id,name);u.name=name;}
  else if(b.action==='logout'){await store.logout(digest(match[1]));return reply(200,{ok:true});}
  else if(['request','accept','reject','cancel','remove'].includes(b.action)){
   if(!validId(b.target)||b.target===u.id)return reply(400,{error:'Укажите ID другого пользователя'});
   if(!await store.friend(u.id,b.target,b.action))return reply(409,{error:'Пользователь не найден либо запрос уже обработан'});
  }else if(['poll','offline','clear-position','position'].includes(b.action)){
   let point=null;if(b.action==='position'){
    if(!Number.isFinite(b.lat)||!Number.isFinite(b.lon)||Math.abs(b.lat)>90||Math.abs(b.lon)>180||!Number.isFinite(b.accuracy)||b.accuracy<0||b.accuracy>500||!Number.isSafeInteger(b.observedAt)||Date.now()-b.observedAt>120000||b.observedAt>Date.now()+5000)return reply(400,{error:'Недостоверное местоположение'});
    point={lat:b.lat,lon:b.lon,accuracy:b.accuracy,observedAt:b.observedAt};
   }await store.sync(u.id,point,b.action);
  }else return reply(400,{error:'action'});
  return reply(200,{user:u,...await store.state(u.id),serverTime:Date.now()});
 }catch{return reply(503,{error:'Сервер временно недоступен. Повторите позже'});}
};}export default handler();
