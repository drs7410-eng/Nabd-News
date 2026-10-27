"""Fail-closed JUnit evidence gate. A unit-only run never means device or Galaxy acceptance."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

UNIT_REQUIRED = {"NewsRetentionTest", "NewsTimeTest", "SharedTextIntentTest", "BbcArticleExtractorTest", "BbcRepositoryRoomTest", "FeedParserTest", "WorldRoomTest", "WorldNetworkTest", "WorldEditorialTest", "PlaybackRegressionTest", "ArticleMigrationSqlTest"}
DEVICE_REQUIRED = {"PersistenceTest", "ArticleUiTest", "SceneNavigationTest", "BbcReaderUiTest", "MlKitTranslationSmokeTest", "NativePlaybackTest", "WorldReadingFlowTest", "ArticleMigrationTest", "WorldNavigationTest", "MediaNavigationTest", "UiSmokeTest"}
LIVE_UNIT = {"BbcReferenceLiveTest", "LiveFeedAuditTest"}

def inspect(folder, required, allowed_skips=()):
    folder = Path(folder)
    files = sorted(folder.rglob("TEST*.xml")) if folder.exists() else []
    passed = []; skipped = []; failures = []; errors = []
    for path in files:
        try:
            root = ET.parse(path).getroot()
            cases = list(root.iter("testcase"))
            if not cases: errors.append(f"Empty report: {path.name}")
            for case in cases:
                cls = case.get("classname", "").split(".")[-1]
                name = cls + "." + case.get("name", "")
                if case.find("failure") is not None or case.find("error") is not None: failures.append(name)
                elif case.find("skipped") is not None:
                    skipped.append(name)
                    if cls not in allowed_skips: errors.append("Unexpected skipped test: " + name)
                else: passed.append((cls, name))
            if any(int(s.get("failures", "0")) > 0 or int(s.get("errors", "0")) > 0 for s in root.iter("testsuite")) and not any(c.find("failure") is not None or c.find("error") is not None for c in cases):
                errors.append("Suite failure without testcase evidence: " + path.name)
        except (ET.ParseError, ValueError, OSError) as exc: errors.append(f"Unreadable report {path.name}: {type(exc).__name__}")
    missing = sorted(required - {cls for cls, _ in passed})
    if not files: errors.append("No JUnit reports")
    return {"passed": len(passed), "skipped": skipped, "failed": failures, "missing_required_classes": missing, "errors": errors,
            "valid": bool(passed) and not (failures or missing or errors)}

def assess(unit_dir, device_dirs=(), require_device=False):
    units = inspect(unit_dir, UNIT_REQUIRED, LIVE_UNIT)
    devices = [inspect(path, DEVICE_REQUIRED) for path in device_dirs]
    ok = units["valid"] and all(d["valid"] for d in devices) and (not require_device or bool(devices))
    return {"status": "passed" if ok and devices else "unit_only" if ok else "blocked", "unit": units, "device": devices,
            "emulator_verified": bool(devices) and all(d["valid"] for d in devices), "physical_galaxy": "NOT_VERIFIED",
            "live_services": "NOT_VERIFIED_BY_THIS_GATE", "release_approved": False}, ok

if __name__ == "__main__":
    p = argparse.ArgumentParser(); p.add_argument("--unit-dir", required=True); p.add_argument("--device-dir", action="append", default=[])
    p.add_argument("--require-device", action="store_true"); p.add_argument("--output", required=True); a = p.parse_args()
    report, ok = assess(a.unit_dir, a.device_dir, a.require_device)
    out = Path(a.output); out.parent.mkdir(parents=True, exist_ok=True); out.write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps(report, ensure_ascii=False, indent=2)); raise SystemExit(0 if ok else 1)
