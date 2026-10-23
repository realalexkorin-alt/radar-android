#!/usr/bin/env python3
"""Read private diagnostic reports using the existing protected signing backup.
No credentials are printed or passed on the command line. Both files must stay private.
"""
import argparse,hashlib,hmac,json,urllib.request
from pathlib import Path

def reader_key(directory):
 root=Path(directory)
 material=hashlib.sha256((root/'radar-release.p12').read_bytes()+b'\0'+(root/'password.txt').read_bytes().strip()).digest()
 return hmac.new(material,b'radar-diagnostics-reader-v1',hashlib.sha256).hexdigest()

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--private-directory',required=True);parser.add_argument('--user-id',type=int);parser.add_argument('--limit',type=int,default=5);args=parser.parse_args()
 query='?limit='+str(max(1,min(20,args.limit)))
 if args.user_id is not None:
  if not 1<=args.user_id<=999:parser.error('ID must be 1..999')
  query+='&userId='+str(args.user_id)
 request=urllib.request.Request('https://radar-presence.vercel.app/api/diagnostics'+query,headers={'Authorization':'Bearer '+reader_key(args.private_directory)})
 with urllib.request.urlopen(request,timeout=35) as response:print(json.dumps(json.load(response),ensure_ascii=False,indent=2))
