import {createHash} from 'node:crypto';
import {accountStore} from '../account-store.js';
import {database} from '../postgres.js';
import {initialize} from '../statistics-store.js';
import {period,aggregate} from '../chart.js';
export default async function(req,res){
 res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
 if(req.method!=='GET')return res.status(405).json({error:'method'});
 try{
 const token=/^Bearer ([0-9a-f]{64})$/.exec(req.headers.authorization||'')?.[1];
 if(!token||!await accountStore.session(createHash('sha256').update(token).digest('hex')))return res.status(401).json({error:'authorization'});
 const {mode='day',date,region='all',timezone='Asia/Yekaterinburg'}=req.query||{};
 let p;try{p=period(mode,date);new Intl.DateTimeFormat('en',{timeZone:timezone});if(region!=='all'&&!/^\d{1,12}$/.test(region))throw Error('region');}catch{return res.status(400).json({error:'period'});}
 await initialize();const sql=database();
 // 5-minute region cells prevent multiple users/sources from overweighting a zone.
 const rows=await sql`WITH cells AS (
 SELECT region_id,bucket,avg(rub) AS rub,count(*) AS samples,
 to_timestamp(min(measured_at)/1000.0) AT TIME ZONE ${timezone} AS local_time
 FROM radar_presence.observations
 WHERE measured_at>=extract(epoch FROM (${p.history}::date::timestamp AT TIME ZONE ${timezone}))*1000
 AND measured_at<extract(epoch FROM (${p.end}::date::timestamp AT TIME ZONE ${timezone}))*1000
 GROUP BY region_id,bucket
 ) SELECT region_id::text AS region,to_char(local_time,'YYYY-MM-DD') AS date,
 extract(hour FROM local_time)::integer AS hour,avg(rub)::float AS rub,sum(samples)::integer AS samples
 FROM cells GROUP BY region_id,to_char(local_time,'YYYY-MM-DD'),extract(hour FROM local_time)`;
 return res.status(200).json({...aggregate(rows,mode,date,region),timezone,generatedAt:Date.now()});
 }catch{return res.status(503).json({error:'statistics unavailable'});}
}
