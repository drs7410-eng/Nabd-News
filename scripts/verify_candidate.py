"""Verify update identity using Android tools; never treat a new ephemeral debug key as compatible."""
import argparse,hashlib,json,re,subprocess
from pathlib import Path

def validate(package,version,cert,policy):
    if package!=policy['application_id']: raise ValueError('Wrong application ID (QA/diagnostic APK is not a personal update)')
    if version<=policy['previous_version_code']: raise ValueError('Version code must advance')
    if cert.lower().replace(':','')!=policy['signer_sha256']: raise ValueError('Signer differs from the installed personal release; update blocked')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--apk',required=True);p.add_argument('--build-tools',required=True);p.add_argument('--policy',default='quality/release-policy.json');p.add_argument('--output',required=True);a=p.parse_args()
    tools=Path(a.build_tools);apk=Path(a.apk)
    signature=subprocess.check_output([str(tools/'apksigner'),'verify','--print-certs',str(apk)],text=True)
    certs=re.findall(r'certificate SHA-256 digest: ([0-9a-fA-F:]+)',signature)
    if len(certs)!=1:raise SystemExit('Expected exactly one verified signer')
    badging=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(apk)],text=True)
    package=re.search(r"package: name='([^']+)' versionCode='(\d+)'",badging)
    if not package:raise SystemExit('Missing Android package identity')
    validate(package[1],int(package[2]),certs[0],json.loads(Path(a.policy).read_text()))
    subprocess.run([str(tools/'zipalign'),'-c','-P','16','4',str(apk)],check=True)
    Path(a.output).write_text(json.dumps({'applicationId':package[1],'versionCode':int(package[2]),'signerSha256':certs[0],'apkSha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'compatible_update_identity':True,'galaxy_acceptance':'PENDING','release_approved':False},indent=2))
