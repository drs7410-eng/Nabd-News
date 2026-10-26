"""Fetch only public RSS feeds for an explicitly requested live audit; never scrape articles.
Failures are recorded per source and are not replaced by test or generated news.
"""
import json
from pathlib import Path
import urllib.request
from datetime import datetime, timezone

root = Path(__file__).resolve().parents[1]
out = root / 'app/build/live-feeds'
out.mkdir(parents=True, exist_ok=True)
feeds = [
    ('bbc-ar', 'BBC عربي', 'https://feeds.bbci.co.uk/arabic/rss.xml'),
    ('aljazeera', 'الجزيرة', 'https://www.aljazeera.net/aljazeerarss/arhome'),
    ('guardian', 'The Guardian', 'https://www.theguardian.com/world/rss'),
    ('aawsat-ar', 'الشرق الأوسط', 'https://aawsat.com/feed/news'),
    ('rt-ar', 'RT Arabic', 'https://arabic.rt.com/rss/'),
    ('dw-ar', 'DW عربية', 'https://rss.dw.com/rdf/rss-ar-all'),
    ('cnn-ar', 'CNN بالعربية', 'https://arabic.cnn.com/api/v1/rss/rss.xml'),
]
records = []
for key, name, url in feeds:
    record = dict(id=key, name=name, url=url, testedAt=datetime.now(timezone.utc).isoformat())
    try:
        request = urllib.request.Request(url, headers={'User-Agent': 'NabdPersonal/1.2 RSS audit'})
        with urllib.request.urlopen(request, timeout=30) as response:
            data = response.read(4_000_001)
            if len(data) > 4_000_000:
                raise ValueError('oversized feed')
            if b'<rss' not in data.lower() and b'<feed' not in data.lower() and b'<rdf:' not in data.lower():
                raise ValueError('not an RSS/Atom response')
        (out / (key + '.xml')).write_bytes(data)
        record['fetchState'] = 'downloaded'
    except Exception as exc:
        record['fetchState'] = 'unavailable'
        record['errorType'] = type(exc).__name__
        (out / (key + '.xml')).unlink(missing_ok=True)
    records.append(record)
    print(key, record['fetchState'])
records.extend([
    dict(id='cnbc-ar', name='CNBC عربية', fetchState='no_confirmed_public_feed', testedAt=datetime.now(timezone.utc).isoformat()),
    dict(id='reuters-ar', name='رويترز عربية', fetchState='no_confirmed_public_feed', testedAt=datetime.now(timezone.utc).isoformat()),
])
(out / 'fetch-results.json').write_text(json.dumps(records, ensure_ascii=False, indent=2))
