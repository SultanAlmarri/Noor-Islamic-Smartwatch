# Installing the development build

Two files from the [latest development build](https://github.com/SultanAlmarri/Noor-Islamic-Smartwatch/releases/tag/dev-latest):

| File | Goes on |
| --- | --- |
| `Noor-phone.apk` | the Android phone |
| `Noor-watch.apk` | the Galaxy Watch (Wear OS 4 or newer; the watch face part needs Wear OS 6) |

Both carry the same app id (`com.noor.smartwatch`) and the same development signature, which the phone and the watch need
in order to talk to each other. The original watch face **Noor Prayer 1.3.2** (`com.noorface.prayer`) is a separate app;
nothing here replaces or changes it.

## Phone

1. Open the link above on the phone and download `Noor-phone.apk`.
2. Open it. Android asks once to allow installs from the browser: allow, then install.
3. Open Noor, choose automatic location or a city.

If an earlier development build is installed and Android refuses the update ("package conflicts"), uninstall Noor first:
builds before 0.2.0 were signed with a temporary key.

## Watch (same method as Noor Prayer 1.3.2)

On the watch: **Settings → Developer options → ADB debugging** on, and **Wireless debugging** on. Under
**Wireless debugging → Pair new device** the watch shows an address with a port and a six-digit code.

On the computer, in the `platform-tools` folder:

```
adb pair <address:pair-port> <code>
adb connect <address:port shown on the Wireless debugging screen>
adb install Noor-watch.apk
```

## Connect them

1. On the phone: Noor → **Watch** tab. Your watch appears with three steps: Noor on the watch, prayer settings,
   Noor watch face. Each turns green only when the watch itself confirms it.
2. On the watch: open Noor. It shows the next prayer, a countdown and today's times for the phone's location and method.
3. Tiles: swipe to the end of the tiles → **+** → **Prayer times** and/or **Noor** (Qibla, tasbih, calendar).
4. Complications: long-press the watch face → **Customise** → a complication slot → **Noor** → Next prayer, Hijri
   date, Qibla or Tasbih.
5. Watch face (Wear OS 6): on the phone, Watch tab → **Use the Noor watch face**. The watch opens Noor's face setup;
   confirm the permission on the watch once. Your other watch faces are not changed.

Once Noor is published on Google Play, step "Noor on the watch" becomes a single button on the phone that opens the
Play Store on the watch, where one tap installs it; no computer needed.

After the first sync the watch calculates on its own; the phone can be off or out of range. Changing location or settings
on the phone updates the watch the next time they are connected.

**Without the phone app:** open Noor on the watch and choose **Use my location** (allow location when asked) or
**Choose a city**. The watch then works fully on its own, alerts included (Settings on the watch → Prayer alerts).
