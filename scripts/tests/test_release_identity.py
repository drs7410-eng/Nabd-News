import importlib.util,json,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('candidate',Path(__file__).parents[1]/'verify_candidate.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class ReleaseIdentityTest(unittest.TestCase):
    def setUp(self):self.policy=json.loads((Path(__file__).parents[2]/'quality/release-policy.json').read_text())
    def test_original_signer_and_new_version_pass(self):m.validate('com.nabd.news',13,self.policy['signer_sha256'],self.policy)
    def test_new_debug_key_fails(self):
        with self.assertRaisesRegex(ValueError,'Signer'):m.validate('com.nabd.news',13,'0'*64,self.policy)
    def test_qa_is_never_an_update(self):
        with self.assertRaisesRegex(ValueError,'application'):m.validate('com.nabd.news.qa',13,self.policy['signer_sha256'],self.policy)
    def test_same_version_is_not_a_new_release(self):
        with self.assertRaisesRegex(ValueError,'Version'):m.validate('com.nabd.news',12,self.policy['signer_sha256'],self.policy)
