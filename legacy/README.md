# Noor watch face v1.3.2 — preserved working version

This folder is a frozen copy of the working Noor Prayer Watch Face (Watch Face Format, package `com.noorface.prayer`,
version code 7, version 1.3.2) exactly as it was on 4 November 2026. **Do not edit it.** New watch-face work
happens in new modules; this copy stays as the reference and the fallback.

| Item | Location |
|---|---|
| Installable APK, the same build and signing certificate as on your watch (SHA-256 `95f2fafa…2d06706`) | `NoorPrayerWatchFace-1.3.2.apk` |
| Full source: generator, Gradle project, fonts, icons, docs, tests | `watchface-v1.3.2/` |
| How to rebuild | `watchface-v1.3.2/README.md` and `watchface-v1.3.2/build_apk.sh` |

Deliberately not in this public repository:

- **The signing keystore** (`signing/noor-release.jks`). Anyone holding it could sign updates that replace the
  watch face. It is kept in the complete backup archive `noor-watchface-v1.3.2-full-project.tar.gz`
  (SHA-256 `9721af4e5abf0e1594ffb10dba167e6061618663d39e006dd749c7ebc89edc7c`) and in the 1.3.2 source zip in your
  `Downloads\platform-tools-latest-windows` folder. Without it, the installed watch face cannot be updated in place.
- The 16 MB numpy wheel used by the generator (`pip install numpy` restores it).
