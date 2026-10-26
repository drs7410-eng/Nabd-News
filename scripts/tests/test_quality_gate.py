import importlib.util
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location("gate",Path(__file__).parents[1]/"assess_quality.py")
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
class QualityGateTest(unittest.TestCase):
    def setUp(self): self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name)
    def tearDown(self): self.tmp.cleanup()
    def report(self,name,classes,extra=""):
        folder=self.root/name;folder.mkdir(exist_ok=True)
        (folder/'TEST-suite.xml').write_text('<testsuite>'+''.join(f'<testcase classname="com.nabd.news.{c}" name="check" />' for c in classes)+extra+'</testsuite>')
        return folder
    def test_missing_reports_fail(self): self.assertFalse(gate.assess(self.root)[1])
    def test_unit_success_is_not_device_or_release(self):
        report,ok=gate.assess(self.report('unit',gate.UNIT_REQUIRED));self.assertTrue(ok);self.assertEqual('unit_only',report['status']);self.assertFalse(report['release_approved']);self.assertFalse(report['emulator_verified'])
    def test_device_required_cannot_be_omitted(self): self.assertFalse(gate.assess(self.report('unit',gate.UNIT_REQUIRED),require_device=True)[1])
    def test_missing_native_test_blocks(self):
        u=self.report('unit',gate.UNIT_REQUIRED);d=self.report('device',gate.DEVICE_REQUIRED-{'NativePlaybackTest'});self.assertFalse(gate.assess(u,[d],True)[1])
    def test_device_failure_blocks_even_if_every_required_class_has_other_passes(self):
        u=self.report('unit',gate.UNIT_REQUIRED);d=self.report('device',gate.DEVICE_REQUIRED,'<testcase classname="NativePlaybackTest" name="decode"><failure/></testcase>');self.assertFalse(gate.assess(u,[d],True)[1])
    def test_skipping_critical_test_is_not_success(self):
        u=self.report('unit',gate.UNIT_REQUIRED,'<testcase classname="WorldRoomTest" name="skip"><skipped/></testcase>');self.assertFalse(gate.assess(u)[1])
    def test_only_declared_live_unit_skips_are_allowed(self):
        u=self.report('unit',gate.UNIT_REQUIRED,'<testcase classname="LiveFeedAuditTest" name="live"><skipped/></testcase>');self.assertTrue(gate.assess(u)[1])
    def test_all_emulators_must_pass_and_none_implies_physical_acceptance(self):
        u=self.report('unit',gate.UNIT_REQUIRED);d=self.report('api35',gate.DEVICE_REQUIRED);bad=self.report('api36',set())
        self.assertFalse(gate.assess(u,[d,bad],True)[1]);r,ok=gate.assess(u,[d],True);self.assertTrue(ok);self.assertEqual('NOT_VERIFIED',r['physical_galaxy']);self.assertFalse(r['release_approved'])
    def test_malformed_xml_fails(self):
        u=self.report('unit',gate.UNIT_REQUIRED);(u/'TEST-broken.xml').write_text('<oops');self.assertFalse(gate.assess(u)[1])
    def test_suite_error_without_case_error_fails(self):
        u=self.report('unit',gate.UNIT_REQUIRED);p=u/'TEST-suite.xml';p.write_text(p.read_text().replace('<testsuite>','<testsuite errors="1">'));self.assertFalse(gate.assess(u)[1])
if __name__=='__main__':unittest.main()
