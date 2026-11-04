# Signing keys

| File | Used for | Password / alias |
| --- | --- | --- |
| `noor-dev.keystore` | Phone app and watch app, debug and test-release builds | `noor-dev-key` / `noor-dev` |
| `noor-watchface-dev.keystore` | Watch faces installed by the watch app (Watch Face Push requires a key different from the app's) | `noor-dev-key` / `noor-wf-dev` |

These keys are **development keys and public on purpose**. Phone and watch apps must carry the same signature to talk to
each other over the Wear OS Data Layer, and every build must carry the same signature so it can be installed over the
previous one. A runner-generated debug key changes on every CI run, which breaks both.

Do not use them for a Play Store release: that needs a private upload key kept outside the repository (Play App Signing).

The original watch face's own key (`noor-release.jks`, package `com.noorface.prayer`) is not in this repository; it is kept
in the private backups, unchanged.
