# From today to "published on Google Play": the checklist

Written 22 November 2026 for the first release of Minhad on Google Play, from the audit in
`docs/RELEASE_AUDIT.md` (every fact below has its file and line there). The other two documents this checklist
uses: `docs/PRIVACY.md` (the privacy policy) and `docs/STORE_LISTING.md` (the store texts and pictures).

**How to read it.** Every step carries one of three marks:

- **OWNER**: only the owner can do it. It needs his Google account, his money, his decision, his rights to
  something, or his phone and watch.
- **CODE — to do**: work in the repository, with the exact place. Not done yet.
- **CODE — done**: already true in the code today; nothing to do, listed so nobody redoes or breaks it.

**An honest warning about Google's rules.** What Google Play requires is written here as far as the author knows
it, from memory, without access to the Play Console. Google changes its rules and dates. The Play Console is the
authority: it shows every form that must be filled, and it refuses an upload that breaks a hard rule. Where this
document says "as known", check it there. Nobody can promise that a review will pass, or how long it takes.

**Where things stand, in one paragraph.** The app is complete as a product and passes its own tests, but it has
only ever been a development build: signed with a key that is public on purpose, carrying a testers' page, linking
to GitHub downloads, and never checked by anyone on a real watch. None of the store paperwork exists yet. There
are a few decisions only the owner can make (Part 1), about a dozen pieces of work in the code (Part 2), the Play
Console steps (Part 3), testing on his own devices (Part 4), and then the submission (Part 5). The step that takes
longest is not in the code: Google makes new personal developer accounts run a test with real people for two weeks
before the first public release (Part 3, P4), so it is worth starting Part 3 at once.

---

## Part 1. Decisions only the owner can make

Each needs a plain answer. Until they are answered, some code work cannot start.

| # | Decision | Why it is his | What follows from each answer |
|---|---|---|---|
| **D1** | **The Dubai Adhan recordings (Abdulrahman Al-Hindi).** Does he hold a licence or written permission from the person or body that owns these recordings, and can he put that document with the project's records? | Only he knows where they came from. The two original files carry the addresses of YouTube videos in their own tags, which is what a download from YouTube looks like, and they are in the public repository now. A copyright complaint can remove the whole app from Play and counts against the account. | **Yes, with a document**: keep them; the app's credit line can then say so; the store text may name Dubai. **No, or not sure**: they come out of the phone app, the watch app and the repository before release (C1 below), and Dubai becomes "add your own recording" like Al-Quds. Makkah, Madinah and Egypt stay: their licences are on record. |
| **D2** | **The app's permanent identity on Play**: keep the id `com.noor.smartwatch`, or change it to one that says Minhad (for example `com.minhad.app`)? | It can never be changed after the first upload, and it is visible in the Play address. It was kept at the rename only so installed builds would keep updating; the Play build cannot update them anyway (see P3), so this is the one free moment to change it. | **Keep**: nothing to do. **Change**: C5. |
| **D3** | **Location in the background**: keep "Update in the background", or leave it out of the first release? | It is the permission Google refuses most often. Keeping it means a form, a short screen video and a stricter explanation text; leaving it out means prayer times update when the app is opened (and widgets and alerts follow from there). | **Keep**: C8 (new text), P6 (form and video). **Leave out**: C8 removes the permission; nothing to declare. The author's suggestion for a first release: leave it out, add it back in an update once the app is established. |
| **D4** | **Minhad Studio on the watch** (the face designed on the phone). | By the project's own research (`docs/WATCH.md`), Google Play has not installed this older kind of watch face since January 2026. Shipping it risks the watch app being refused. | **Leave it out of the store build** (the sixteen ready-made Minhad faces stay; the phone's editor says so): C10. **Or** wait for it to be rebuilt in the newer format before releasing the watch app. |
| **D5** | **The map's pictures** come from Esri's servers under terms the code describes as "non-commercial", without an account. | Whether that is allowed for a Play app has not been established; an account or a different provider may mean accepting terms or paying, which is his to agree to. | **Check Esri's terms / register for a key** → C11 uses it. **Or** accept the risk for now (the map may one day stop loading; the rest of the location page works without it). |
| **D6** | **Free, and where?** Free with no ads and no purchases (what the code is today); which countries. | Money and reach. As known, an app published as free cannot later be made paid. | Entered in the Console (P8). |
| **D7** | **Contact e-mail address** for the listing and the privacy policy, and the **publisher name** shown on Play. | They are public. No address has been written anywhere in these documents. | Fill the three placeholders in PRIVACY.md and the one in STORE_LISTING.md. |
| **D8** | **The version number shown on Play**: stay at 0.25.0, or call the first public release 1.0.0? | Taste. | C4. |
| **D9** | **Phone and watch together, or the phone first?** | The watch app has never run on a real watch under anyone's eyes but its own tests. | **Together**: both go through the same test period. **Phone first**: the phone's Watch page will offer an install that does not exist yet; C6 must hide it until the watch app is out. |

---

## Part 2. Work in the code before the first upload

In a sensible order. "Place" is where the change is made; the audit has the line numbers.

| # | Step | Mark | Place |
|---|---|---|---|
| **C1** | **Remove the tester's options** (the owner's standing instruction), and settle what they were used to tune: one "icon slot" mode and one timer lead time become the shipped defaults. Remove the Samsung experiment's manifest entry if the chosen mode does not need it. | CODE — begun in 0.25 (a store build, `-Pminhad.store=true`, leaves the Testing row out: `BuildConfig.TESTER`; the code itself is still to be removed, and the defaults settled) | `app/src/main/java/com/noor/app/testing/` (three files); `MainActivity.kt` (route, page, banner); `ui/settings/SettingsScreen.kt` (the row); `NoorApplication.kt`; `ui/today/LivingSky.kt`; `sky/RealSkyFrame.kt`; `notify/StatusNotification.kt` (test hook, `slotMode`, `StatusTicker.saveTiming`); `app/src/main/AndroidManifest.xml` 87–90; four strings in each language; four test files. Full list: audit 5.1. |
| **C1b** | If D1 is "no": remove the four Dubai files from `app/src/main/assets/sounds`, the two from `wear/src/main/assets/sounds`, their entries in both `library.json`, the two originals in `tools/sounds/owner/`, and the default that points to one of them: **the default Fajr Adhan for every user is the Dubai Fajr recording** (`DEFAULT_FAJR_ADHAN = "adhan.dubai_fajr"`, `core/prayer/.../alerts/AlertSettings.kt` 245), so Fajr needs a new default (Makkah's, as the other prayers have). Deleting a file does not remove it from the repository's history; whether to rewrite history is the owner's call. If D1 is "yes": fix the dead "Source" link the app shows for them. | CODE — to do | as named; `docs/SOUNDS.md` |
| **C2** | **Release signing.** A release signing configuration that reads a private upload key from outside the repository (a local file or CI secrets); the public development key stays for development builds only. The same for the watch faces' key. Never commit the new keys (rule 3). | CODE — to do | `app/build.gradle.kts` 19–37; `wear/build.gradle.kts` 20–36; `tools/watchface/build_pushed_face.py` 34, 120–121; `signing/README.md` |
| **C3** | **Build bundles.** Play takes `.aab` files, not APKs: add `:app:bundleRelease` and `:wear:bundleRelease` to a release workflow that signs with the upload key and gives the owner the two files (or uploads them to Play). | CODE — to do | `.github/workflows/android.yml` (or a new workflow) |
| **C4** | **Version codes and name.** One numbering scheme in which the phone's and the watch's codes can never collide (they share one Play app), fixed before the first upload; the version name per D8. | CODE — to do | `app/build.gradle.kts` 15–16; `wear/build.gradle.kts` 16–17 |
| **C5** | If D2 is "change": the new application id in both apps, the watch-face package name built from it, and the test hooks' names. Code packages can stay `com.noor.*`. | CODE — to do | `app/build.gradle.kts` 12; `wear/build.gradle.kts` 13; `tools/watchface/build_pushed_face.py` 29–30; `wear/src/main/AndroidManifest.xml` 109, 125, 156 |
| **C6** | **Links a store user should never meet.** The watch's "Minhad for your phone" opens the GitHub development download: point it at the Play listing. The phone's install guide opens `adb` instructions on GitHub: for a Play install the page already offers the Play Store on the watch; drop or replace the guide. Take the repository address out of the User-Agent sent to map and search servers, or keep it knowingly. | CODE — to do | `wear/.../data/PhoneLink.kt` 55; `app/.../ui/watch/WatchScreen.kt` 110, 136; `app/.../data/WorldStore.kt` 200 |
| **C7** | **Target Android 16 (API 36) for the phone app**, if the Console confirms it is required (as known, since 31 August 2026 for new apps; Wear OS one level lower). It changes some behaviour, so it needs the emulator workflows run again, not just the number. | CODE — to do (after checking) | `app/build.gradle.kts` 14; `wear/build.gradle.kts` 15 |
| **C8** | **Say the truth about location, in both languages.** (1) The sentence shown before the location prompt says the location "stays on this phone"; it does not quite (place names, the map, the watch, backup). Reword, for example: "Minhad uses your location to work out prayer times and the Qibla. It is not sent to us." (2) Background location per D3: either remove the permission and the setting, or rewrite the disclosure so it says, as Google asks: that Minhad collects **location data**, **even when the app is closed or not in use**, to keep prayer times and alerts right when you travel. | CODE — to do | `app/src/main/res/values/strings.xml` 83, 86–87 and `values-ar/strings.xml` 82, 85–86; if removing: `app/src/main/AndroidManifest.xml` 12, `data/LocationWorker.kt`, `ui/onboarding/OnboardingScreen.kt` (step 2), `ui/settings/SettingsScreen.kt` 135, 207–210, 479–493 |
| **C9** | **Privacy policy inside the app** and on the web: publish `docs/PRIVACY.md` as a public page (GitHub Pages from this repository is free; any site will do) and add a "Privacy policy" row in Settings → About that opens it. | CODE — done in 0.25 (Settings → About Minhad → Privacy policy opens `docs/PRIVACY.md` on GitHub; the owner fills its placeholders, D7, and the page must be on `main`) | `ui/settings/SettingsScreen.kt` near 300–301; new strings in both languages |
| **C10** | **The watch build for Play.** Per D4, leave Minhad Studio's face service out of the store build (and its page on the phone says the Minhad faces are used instead, as it already does for new watches). Rename or leave out the "original" face, which shows as "Noor Prayer Watch Face". | CODE — to do | `wear/src/main/AndroidManifest.xml` 60–74; `wear/.../studio/`; `tools/watchface/build_pushed_face.py` (`VARIANTS`); `core/prayer/.../WatchFaces.kt` |
| **C11** | **Credits where the licences ask for them.** A "Licences and credits" page on the phone (fonts; GeoNames; OpenStreetMap; Esri; the recordings; Kartverket and Copernicus for the Arctic scenery; NASA; Poly Haven; the open-source libraries). A short credits page on the watch (GeoNames; the recordings it carries). Rename the watch faces' cut-down digits font so it no longer calls itself "IBM Plex". Remove three unused strings that would be untrue if shown. Per D5, the map key or provider. | CODE — the phone's page done in 0.25 (Settings → About Minhad → Acknowledgements, both languages, with the Kartverket and Copernicus notices word for word); the watch's credits, the digits font's name and the three unused strings still to do | new page from `ui/settings/AccuracyScreen.kt` 58–60; `wear/.../ui/SettingsScreens.kt`; `tools/watchface/noor_face.py`; `strings.xml` 294, 305, 420; `ui/location/TileMap.kt` 71–78. The notices to show are in `tools/scenery/sources/arctic.md` and `docs/SOUNDS.md`. |
| **C12** | **Exact alarms on a new phone.** On Android 14 and later a new install may not ring at the exact minute until the user allows "Alarms & reminders"; the welcome turns the Adhan on without leading there. Add that step to the welcome (one tap to the setting, with a sentence why), or apply for the alarm-app permission (`USE_EXACT_ALARM`) if the Console's rules allow it for Minhad. | CODE — to do | `ui/onboarding/OnboardingScreen.kt` 91–98; `alerts/src/main/AndroidManifest.xml` 6–8 |
| **C13** | **Backup.** Decide what Android's backup may copy (today: everything, the saved position included, and nothing at all for a user with large imported sounds) and write the rules. | CODE — to do | `app/src/main/AndroidManifest.xml` 25; `wear/src/main/AndroidManifest.xml` 22; new `res/xml` rules |
| **C14** | **The release candidate through every check**: all unit tests, both release builds, and the emulator workflows (`device-alerts.yml`, `real-sky.yml`, `widgets.yml`, `watch-face.yml`, `smooth.yml`). HANDOVER section 41 says `widgets.yml` should be run before a release. | CODE — to do | `.github/workflows/` |
| **C15** | Documents brought up to date for a store app: README, `docs/INSTALL.md`, `docs/ARCHITECTURE.md`, `docs/HANDOVER.md`, `CLAUDE.md`, `docs/SOUNDS.md` (it still says "generated… except Dubai"; names Noor). | CODE — to do | `docs/` |
| — | No advertising, analytics, accounts or tracking code; nothing is sent to the developer. | CODE — done | audit 2 |
| — | All connections encrypted (HTTPS); three hosts only. | CODE — done | audit 2 |
| — | Shrinking (R8) on; test hooks and the `bench` build are outside the release build. | CODE — done | `app/build.gradle.kts` 31–47; audit 5.1 |
| — | Arabic complete: every string exists in both languages (975 phone, 166 watch, 42 alerts). | CODE — done | audit 6 |
| — | Credits on the phone for the recordings, GeoNames, OpenStreetMap, and the map's attribution. | CODE — done | audit 4.2 |
| — | The alert path never fails silently (fallback Adhan, backup channel, history); alarms survive a restart before the first unlock. | CODE — done (emulator-tested, not phone-tested) | `docs/ALERTS.md` |
| — | The watch app is declared standalone, has tile previews, works without the phone. | CODE — done (software-tested only) | `wear/src/main/AndroidManifest.xml` |
| — | The two small native libraries are aligned for 16 KB memory pages (read from today's built APK). | CODE — done | audit 4.1 |
| — | The original watch face v1.3.2 (`com.noorface.prayer`) is untouched by all of this. | CODE — done | `legacy/` |

---

## Part 3. Google Play Console (the owner's account)

| # | Step | Mark | Notes |
|---|---|---|---|
| **P1** | **Create a Google Play developer account** at play.google.com/console with the Google account that will own the app for good. As known: a one-time fee of 25 US dollars, identity verification with an official document, and for a personal account a check that you have a real Android phone. Verification can take days: start now. Choose personal or organisation carefully; it decides what is shown publicly about you and (P4) the testing rule. | OWNER | Some of your details (developer name, country, contact e-mail) are shown publicly; the Console says which before you confirm. |
| **P2** | **Create the app** in the Console: name (STORE_LISTING.md), default language, "App", "Free". Do not upload anything until C4 and C5 are settled: the application id and every version code are permanent from the first upload. | OWNER | |
| **P3** | **Signing.** Accept **Play App Signing** (Google keeps the app's real key). The **upload key** is made once, kept private (a file plus its password, in two safe places of yours), and given to the build as a secret. If it is ever lost, Google can replace it on request; that is the point of the arrangement. **What this means for the builds you have installed:** the Play version cannot install over a development build. Before installing from Play, **uninstall Minhad from the phone and from the watch**; your settings and imported sounds on those devices are erased, so note your settings first. From then on, the phone and the watch must **both** come from Play (or both from the development link): a Play phone app and a development watch app do not talk to each other. Your original watch face 1.3.2 is a separate app and is not affected. | OWNER (with C2) | Since the watch can no longer be reached with `adb`, installing from Play's test track is also the practical way to get new builds onto your watch. |
| **P4** | **Testing tracks.** (a) **Internal testing**: available at once, up to 100 people you name; use it yourself first, phone and watch. (b) **Closed testing**: as known, a **personal** account created after November 2023 must run a closed test with **at least 12 testers who stay opted in for 14 days in a row** before it may apply for a public release. Find twelve people with Android phones and Google accounts now (family, friends, people at the mosque), collect their Gmail addresses, and send them the opt-in link when the build is ready. An organisation account is, as known, exempt. | OWNER | The two weeks are also the first real test by people other than the owner: ask the testers to turn the Adhan on and report whether Fajr sounded. |
| **P5** | **Add the watch.** In the Console's advanced settings add the **Wear OS** form factor, upload the watch bundle to the Wear OS track, and add at least one Wear OS screenshot. This opens Google's review against its Wear OS quality rules. | OWNER (with C10) | Per D9. |
| **P6** | **"App content" forms.** Fill every form the Console lists. Expect at least: privacy policy address; ads (none); app access (everything works without signing in); content rating questionnaire (answer truthfully: a religious reference and alarm app, no violence, no user-to-user content, no purchases); target audience (choose ages 13 and over, not children: choosing children brings stricter rules); **Data safety** (answers below); **location permissions** (if D3 keeps background location: the form and a video); **foreground services** (texts below; as known a short video for each); full-screen notifications (text below); advertising ID (not used); and any "does your app do X" declarations it asks of every app (government, health, financial: no to each; Minhad is independent of every authority it names). | OWNER, from the texts below | Videos can be recorded with the phone's own screen recorder and uploaded as an unlisted YouTube video or a shared file link, as the form asks. |
| **P7** | **Store listing**: texts from STORE_LISTING.md in English and Arabic, icon, feature graphic, screenshots taken on your phone and watch. | OWNER (pictures); CODE — to do (icon and feature graphic exported from the app's artwork) | |
| **P8** | **Countries and price** per D6. | OWNER | |

---

## Part 4. Testing on the owner's phone and watch (from the Play test track)

Nothing below can be done by anyone else, and none of it has been done: every result so far is from tests on
computers and emulators. Do it on the build installed from Play's internal testing, because that is the build
users will get.

| # | What to check | Mark | How |
|---|---|---|---|
| **T1** | First run: the welcome, location allowed, and again choosing a city by hand; the home screen appears with the right place and times. | OWNER | Install fresh. Compare one day's times with the Dulook DXB app. |
| **T2** | **The Adhan**: the six steps in `docs/ALERTS.md`, "Testing on a phone" (test in 10 seconds with the phone locked; on vibrate; a real prayer; the history afterwards). Then leave it for three days, Fajr included, and read Alert history. | OWNER | If anything did not sound: "Share report". |
| **T3** | An alarm before a prayer with Snooze; the reminder before a prayer. | OWNER | |
| **T4** | Qibla against a known direction; the calibration figure. | OWNER | |
| **T5** | The countdown notification and the status-bar timer after closing the app from recent apps; the widgets, each size, after a night. | OWNER | |
| **T6** | Location: search, the map (does it load, is it smooth), the confirmation page; travel or change the time zone if possible. | OWNER | |
| **T7** | **Watch**: install from Play on the watch; the phone's Watch page turns green step by step; times agree with the phone; a tile and a complication; an alert on the watch; a Minhad face (Wear OS 6 only); "Sync with phone". The list in `docs/WATCH.md`, "Needs a real watch". | OWNER | |
| **T8** | Arabic: switch the app to Arabic and walk every tab once. | OWNER | |
| **T9** | Battery after a normal day with the app installed. | OWNER | Android Settings → Battery. |
| **T10** | Take the store screenshots while doing the above (STORE_LISTING.md, section 6). | OWNER | |

What the testers report in the 14 days (P4) is the second half of this part.

---

## Part 5. Submit, and after

1. **OWNER**: when the closed test has run its time and Parts 2 to 4 are done, apply for production access in the
   Console and answer its questions about the test honestly.
2. **OWNER**: send the release for review. As known, reviews take from a day to more than a week, longer for a new
   account or when a declaration (location, foreground service) is looked at by a person. If Google refuses, its
   message names the policy; fix that one thing and resubmit. A refusal before publication is common for a first submission; as
   known it does not by itself harm the account (repeated or serious violations can).
3. **CODE — to do**: after the first public release, every update goes through the same build (C3) with a higher
   version code (C4). The development link (`dev-latest`) can continue for testing, but its builds and the Play
   builds cannot replace each other on a device.
4. **OWNER**: reply to the first reviews; a missed Adhan report is the one to act on the same day.

---

## Data safety form: answers prepared from the audit

The form is the owner's own legal declaration to Google; these are the answers the code supports, with the
reasoning. Google's own definitions (shown as help text in the form) decide; if a definition there differs from
what is assumed here, follow the form. "Collected" below means, as known, "sent off the device by the app or by
software inside it, to anyone", not only "sent to the developer".

**The plain facts** (audit sections 2 and 3): no account; no ads; no analytics; the developer has no server and
receives nothing. The position is given to the phone's own place-name service to be named; the map squares for the
area on screen are fetched from Esri while a map is open; typed place searches go to the phone's place-name
service (or, on phones without one, to Photon); the position goes to the user's own watch. All connections are
encrypted.

There are two defensible ways to fill the form. **The author recommends the careful one**: understating is what
Google acts against; overstating costs nothing but a line on the listing.

| Question | Careful answer (recommended) | Minimal answer (only if the form's definitions clearly exclude phone-system services and map pictures) |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | No |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | — |
| Do you provide a way for users to request that their data is deleted? | Nothing is held by the developer; users erase everything by clearing the app's storage or uninstalling. Choose the option the form offers for "we do not keep the data" (if it only offers yes/no, answer per its help text). | — |
| **Location → Approximate location** | **Collected: yes. Shared: no.** Processed ephemerally: yes (the developer stores nothing). Required or optional: **optional** (the user can choose a place by hand). Purpose: **App functionality**. | — |
| **Location → Precise location** | Same as approximate (the user chooses precise or approximate in Android's prompt). | — |
| **App activity → In-app search history** | Collected: yes (the words typed in the place search go to the place-name service). Ephemeral: yes. Optional. Purpose: App functionality. Include this line if the form's definition covers search text sent to a look-up service; it is the least certain of the three. | — |
| Personal info, financial info, health, messages, photos and videos, contacts, calendar, web browsing | **No** | No |
| **Audio files** | **No**: imported sounds stay on the device (and on the user's own watch). | No |
| **App info and performance (crash logs, diagnostics)** | **No**: there is no crash reporting. "Share report" is text the user chooses to send themselves. | No |
| **Device or other IDs** | **No**: none is read. | No |

Why "shared: no" in the careful answer: as known, Google's definition of sharing leaves out transfers to a service
that only carries out the app's own function for the user (the place-name service, the map) and transfers the user
starts. If the form's help text reads otherwise, mark location as shared too, with the same purpose.

If D3 removes background location, nothing above changes (the form does not ask about background separately; the
permissions form does). If the map provider changes (D5), nothing above changes either.

---

## Permissions declarations: draft texts

Short, factual, and each true of the code today. Edit to match the decisions (a text for a feature that was
removed must not be submitted).

**Location in the background** (only if D3 keeps it). *Why does your app need background location?*

> Minhad shows Islamic prayer times, which depend on where the user is. If the user turns on "Update in the
> background", the app takes a single low-power location fix about every six hours while it is closed, so that the
> prayer times, the Adhan alarms set for them, the home-screen widgets and the paired watch stay correct when the
> user travels to another city or country without opening the app. Without it the alarms would ring at the
> previous city's times until the app is next opened. The feature is off by default, is explained in the app
> before Android's permission screen, and can be switched off at any time in Settings → Location. The location is
> used on the device; it is not sent to the developer, who operates no server.

The video should show: opening Settings → Location → "Update in the background", the app's explanation, Android's
"Allow all the time" screen, and the setting on.

**Foreground service: media playback.**

> At each prayer time Minhad plays the Adhan (the call to prayer), an audio recording of up to about three
> minutes, started by an alarm the user has set. The foreground service keeps the audio playing while the screen is
> off and shows a notification with a Stop button. It ends when the recording ends or the user taps Stop. The same
> service rings the alarms the user sets before or after a prayer, with Stop and Snooze.

The video: Adhan tab → Alert history → "Test the Adhan in 10 seconds", lock the phone, the Adhan plays, Stop.

**Foreground service: special use** (only if the per-second countdown is kept; see audit risk R7).

> The user can keep a live countdown to the next prayer in the notification shade and, on Android 16, in the
> status bar. Android's built-in notification timer cannot draw it in the app's own digits (for example
> Arabic-Indic digits with a sign) or in a status-bar chip's text, so the app writes the countdown itself, once a
> second, while the screen is on. The service holds only that one notification so that the countdown does not
> freeze when the user swipes the app away. It takes no wake lock, does nothing while the screen is off, and stops
> when the user turns the notification off (Settings → "Notification bar") or chooses "Timer counted by the phone
> itself".

The video: the notification counting, the app swiped away from recent apps, the countdown still counting; then the
setting turned off and the notification gone.

**Full-screen intent.**

> Minhad's core function is an alarm: it calls the Adhan at each prayer time and rings alarms the user sets a
> number of minutes before or after a prayer. The full-screen notification shows the alarm screen, with a large
> Stop button (and Snooze for the user's alarms), over the lock screen while the sound plays, as an alarm clock
> does. It is used only for alarms the user has turned on.

**Exact alarms.** As known, `SCHEDULE_EXACT_ALARM` (what Minhad declares) needs no form; the user grants it in
Android's settings. Only if C12 switches to `USE_EXACT_ALARM`:

> Minhad is an alarm app for prayer times: its core, user-facing function is to sound the Adhan at the exact
> minute of each prayer and to ring the alarms the user sets before or after a prayer. A late alarm defeats the
> app's purpose.

---

## Open risks (short; the full list with evidence is audit section 6)

| # | Risk | Who can close it |
|---|---|---|
| R1 | The Dubai recordings: no proof of rights; the files' tags point to YouTube; the originals are in the public repository; one of them is the default Fajr Adhan for every user. | OWNER (D1) |
| R2 | The watch app contains an older kind of watch face (Minhad Studio) that Play no longer installs. | OWNER (D4), then CODE (C10) |
| R3 | No test on a real watch at all; the phone tested only by the owner's own use. | OWNER (Part 4), testers (P4) |
| R4 | The app says the location "stays on this phone"; that is not exactly true. | CODE (C8) |
| R5 | The map uses Esri's servers on unclear terms. | OWNER (D5), CODE (C11) |
| R6 | On new phones the Adhan may be late until the user finds a setting. | CODE (C12) |
| R7 | Three declarations Google may refuse (background location, the countdown service, the always-on status-bar timer with a transparent icon). | OWNER (D3), CODE (C1, C8) |
| R8 | Authorities are named, and Dubai's times carried, without their permission; the listing must not suggest affiliation. | OWNER (keep the listing's wording) |
| R9 | Accuracy must be described as measured, never as "exact". | OWNER (keep the listing's wording) |
| R10 | Small credit gaps (watch, Arctic scenery data, fonts, licences page). | CODE (C11) |
| R11 | Backup copies the saved position to the user's Google account by default. | CODE (C13) |
| R12 | Leftovers: tester's page, GitHub links, "Noor" face name. | CODE (C1, C6, C10) |
| R13 | Version codes and the application id are permanent once uploaded. | OWNER (D2), CODE (C4, C5) — before P2 |
| R14 | The Palestine display rule is the owner's decision and stays; noted only so he publishes knowing it is visible worldwide. | OWNER |
