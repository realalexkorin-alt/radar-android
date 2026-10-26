// Aggregate regular time buckets before averaging zones and historical days.
const dateKey=d=>d.toISOString().slice(0,10);
export function period(mode,date){
 if(!['day','week','month'].includes(mode)||!/^\d{4}-\d{2}-\d{2}$/.test(date))throw Error('period');
 let start=new Date(date+'T00:00:00Z');if(!Number.isFinite(+start)||dateKey(start)!==date)throw Error('date');
 if(mode==='week')start.setUTCDate(start.getUTCDate()-((start.getUTCDay()+6)%7));
 if(mode==='month')start.setUTCDate(1);
 let end=new Date(+start);if(mode==='month')end.setUTCMonth(end.getUTCMonth()+1);else end.setUTCDate(end.getUTCDate()+(mode==='week'?7:1));
 let history=new Date(+start);if(mode==='month')history.setUTCMonth(history.getUTCMonth()-6);else history.setUTCDate(history.getUTCDate()-56);
 return {start:dateKey(start),end:dateKey(end),history:dateKey(history),count:mode==='day'?24:Math.round((end-start)/86400000)};
}
export function aggregate(rows,mode,date,region='all'){
 const p=period(mode,date),slots=Array.from({length:p.count},(_,i)=>({index:i,actual:null,historical:null,samples:0,historyDays:0}));
 const areas=[...new Set(rows.map(r=>String(r.region)))].sort((a,b)=>Number(a)-Number(b));
 const regionDays=new Map();const byDay=new Map();
 for(const r of rows){if(region!=='all'&&String(r.region)!==region)continue;
  const day=r.date,hour=Number(r.hour),key=(mode==='day'?day+':'+hour:day)+':'+r.region;
  if(!regionDays.has(key))regionDays.set(key,{day,hour,values:[],samples:0});let g=regionDays.get(key);g.values.push(Number(r.rub));g.samples+=Number(r.samples);
 }
 for(const r of regionDays.values()){let key=mode==='day'?r.day+':'+r.hour:r.day;if(!byDay.has(key))byDay.set(key,{day:r.day,hour:r.hour,values:[],samples:0});let g=byDay.get(key);g.values.push(r.values.reduce((a,b)=>a+b,0)/r.values.length);g.samples+=r.samples;}
 const actual=new Map(),history=new Map();let base=new Date(p.start+'T00:00:00Z');
 for(const g of byDay.values()){
  const d=new Date(g.day+'T00:00:00Z'),current=g.day>=p.start&&g.day<p.end;
  if(!current&&g.day>=p.start)continue;
  if(!current&&mode==='day'&&d.getUTCDay()!==base.getUTCDay())continue;
  let i=mode==='day'?g.hour:mode==='week'?(d.getUTCDay()+6)%7:d.getUTCDate()-1;if(i>=p.count)continue;
  let map=current?actual:history;if(!map.has(i))map.set(i,[]);map.get(i).push({value:g.values.reduce((a,b)=>a+b,0)/g.values.length,samples:g.samples,day:g.day});
 }
 for(const s of slots){let a=actual.get(s.index)||[],h=history.get(s.index)||[];
  if(a.length)s.actual=Math.round(a.reduce((v,r)=>v+r.value,0)/a.length);
  if(h.length)s.historical=Math.round(h.reduce((v,r)=>v+r.value,0)/h.length);
  s.samples=a.reduce((v,r)=>v+r.samples,0);s.historyDays=new Set(h.map(r=>r.day)).size;
 }
 return {...p,mode,region,areas,slots,unit:'RUB',historyLabel:mode==='day'?'Предыдущие такие же дни недели (до 8 недель)':mode==='week'?'Среднее по дням недели (до 8 недель)':'Среднее по числам месяца (до 6 месяцев)'};
}
