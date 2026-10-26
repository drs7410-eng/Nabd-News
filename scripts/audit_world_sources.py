"""Actual public network audit; a Cloud proxy denial is never reported as a publisher paywall.
No generated news, authentication, alternate content mirrors, or access-control bypasses.
"""
from pathlib import Path
import concurrent.futures, json, re, urllib.request, urllib.error, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/com/nabd/news/WorldCatalog.kt').read_text()
records=re.findall(r'p\("([^"]+)","([^"]+)","([^"]+)","([^"]+)","([^"]+)"(?:,"([^"]+)")?\)',source)
# Documented candidates only, not claims of working feeds.
feeds={'nyt':'https://rss.nytimes.com/services/xml/rss/nyt/World.xml','guardian-world':'https://www.theguardian.com/world/rss','bbc-world':'https://feeds.bbci.co.uk/news/world/rss.xml','dw-world':'https://rss.dw.com/xml/rss-en-all','lemonde':'https://www.lemonde.fr/rss/une.xml','globalvoices':'https://globalvoices.org/feed/'}
def probe(row):
    ident,name,arabic,site,country,lang=row
    result=dict(id=ident,name=name,arabicName=arabic,website=site,country=country,language=lang or 'en',candidateFeed=feeds.get(ident,''),verified=False)
    url=feeds.get(ident,site)
    try:
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'NabdPersonal/2.1 RSS audit'}),timeout=15) as response:
            data=response.read(4000001)
            result.update(httpCode=response.status,finalUrl=response.url,contentType=response.headers.get('Content-Type',''),bytes=len(data))
            if len(data)>4000000: raise ValueError('Response exceeded 4 MB')
            if ident in feeds:
                xml=ET.fromstring(data)
                if xml.tag.split('}')[-1].lower() not in ('rss','feed','rdf'): raise ValueError('Not RSS or Atom')
                entries=[e for e in xml.iter() if e.tag.split('}')[-1].lower() in ('item','entry')]
                result.update(verified=bool(entries),sampleCount=len(entries),extendedFieldCount=sum(any(c.tag.split('}')[-1] in ('encoded','content') and ''.join(c.itertext()).strip() for c in e) for e in entries))
            else:result['note']='Website received; no RSS candidate verified. Discovery must validate actual feeds.'
    except Exception as exc:
        message=str(exc)
        result.update(error=message,httpCode=getattr(exc,'code',0),failureOrigin='cloud_proxy' if 'Tunnel connection failed' in message else 'network_or_response',verified=False)
    return result
rows=list(concurrent.futures.ThreadPoolExecutor(max_workers=6).map(probe,records))
out=root/'app/build/reports/world-source-audit';out.mkdir(parents=True,exist_ok=True)
(out/'actual-source-results.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2))
print(json.dumps({'candidates':len(rows),'feedsVerified':sum(r['verified'] for r in rows),'proxyDenied':sum(r.get('failureOrigin')=='cloud_proxy' for r in rows),'report':str(out/'actual-source-results.json')},ensure_ascii=False))
