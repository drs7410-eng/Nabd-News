"""Static release guardrails; no credentials required and no secret values printed."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main'
manifest = (source / 'AndroidManifest.xml').read_text()
checks = {
    'automatic backup disabled': 'android:allowBackup="false"' in manifest,
    'cleartext networking disabled': 'android:usesCleartextTraffic="false"' in manifest,
    'no logging of credentials or requests': True,
    'no embedded API credentials': True,
    'no custom TLS bypass': True,
    'secure windows enabled': 'FLAG_SECURE' in (source / 'java/com/nabd/news/MainActivity.kt').read_text(),
    'no destructive Room migration': 'fallbackToDestructiveMigration' not in (source / 'java/com/nabd/news/Database.kt').read_text(),
}
reader = (source / 'java/com/nabd/news/OriginalArticleReader.kt').read_text()
checks.update({
    'publisher reader has no native JavaScript bridge': 'addJavascriptInterface(' not in reader and 'evaluateJavascript(' not in reader,
    'reader local file and content access disabled': 'allowFileAccess = false' in reader and 'allowContentAccess = false' in reader,
    'reader mixed content blocked': 'MIXED_CONTENT_NEVER_ALLOW' in reader,
    'reader uses default TLS error rejection': 'onReceivedSslError' not in reader,
})
for path in source.rglob('*.kt'):
    text = path.read_text()
    if re.search(r'\b(?:Log\.[vdiew]|println|HttpLoggingInterceptor)\s*\(', text):
        checks['no logging of credentials or requests'] = False
    if re.search(r'(?:sk-[a-zA-Z0-9_-]{20,}|AIza[a-zA-Z0-9_-]{30,})', text):
        checks['no embedded API credentials'] = False
    if re.search(r'(?:X509TrustManager|hostnameVerifier|sslSocketFactory)', text):
        checks['no custom TLS bypass'] = False
for name, passed in checks.items():
    print(('PASS ' if passed else 'FAIL ') + name)
if not all(checks.values()):
    raise SystemExit(1)
