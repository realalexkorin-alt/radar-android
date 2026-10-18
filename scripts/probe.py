#!/usr/bin/env python3
"""One explicit, anonymous fare lookup. Never loops or rotates identity."""
import argparse,json,urllib.request,urllib.error,datetime
p=argparse.ArgumentParser();p.add_argument('--lat',type=float,required=True);p.add_argument('--lon',type=float,required=True);p.add_argument('--out',required=True);a=p.parse_args()
if not (-90<=a.lat<=90 and -180<=a.lon<=180):p.error('Invalid coordinates')
body={'route':[[a.lon,a.lat]],'selected_class':'econom','payment':{'type':'cash'},'tariff_requirements':[{'class':'econom'},{'class':'business'},{'class':'comfortplus'}],'supports_explicit_antisurge':True,'supports_paid_options':True,'extended_description':True,'format_currency':True,'with_title':True,'summary_version':2,'is_lightweight':False,'skip_estimated_waiting':True}
url='https://ya-authproxy.taxi.yandex.ru/3.0/routestats'
rq=urllib.request.Request(url,json.dumps(body).encode(),{'User-Agent':'Radar/0.1 research','Content-Type':'application/json','Accept-Language':'ru'})
try:
 with urllib.request.urlopen(rq,timeout=20) as r:status=r.status;data=r.read(2_000_001).decode()
except urllib.error.HTTPError as e:status=e.code;data=e.read(2_000_001).decode()
except Exception as e:status=0;data=str(e)
result={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'url':url,'request':body,'status':status,'body':data}
with open(a.out,'w') as f:json.dump(result,f,ensure_ascii=False,indent=2)
print('HTTP',status)
if status==200:
 for t in json.loads(data).get('service_levels',[]):
  if t.get('class') in ['econom','business','comfortplus']:
   print(t.get('class'), 'passenger fare:',t.get('price'),'demand multiplier:',t.get('paid_options',{}).get('value'))
