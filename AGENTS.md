# Nabd engineering rules

- Preserve the native reader, BBC identity/extraction reference, saved IDs, notes and secure API settings.
- Use Room migrations; never destructive migration or deletion to fix an upgrade.
- Production, QA (.qa), original publisher text, translations and AI analyses must remain distinct.
- Do not replace source access or failed extraction with invented article text. Never bypass publisher access controls.
- Run Python gate tests, security checks, fixture integrity, embedded JS tests and relevant Gradle unit/Lint tests.
- Instrumentation must target `-PqaBuild=true`; this is an isolated test app, never the personal database.
- Decoder fixture is synthetic QA media only. Fake translators test orchestration, not ML Kit language quality.
- Report unavailable live services and Galaxy testing as NOT VERIFIED. Compilation is not device execution.
- APK certificate must match quality/release-policy.json before calling it a compatible personal update.
- Signing keys/passwords/API credentials must never enter Git, source archives, logs or test reports.
- The Required quality gate must succeed before publishing a QA artifact. Personal signed candidates additionally require the original signing key and manual Galaxy acceptance before release.
