# Choosing a location (0.9.7)

How the phone app lets the user say where prayer times are for, where the data comes from, and why those providers.
The watch is unchanged (it still uses the 34,000-place gazetteer in `shared`; see [WATCH.md](WATCH.md)).

## What the user sees

Settings → **Location mode** opens one screen (`app/.../ui/location/LocationScreen.kt`):

- **Prayer times are for**: what is chosen now, with its coordinates.
- **One search box** for everything: cities, towns, villages, districts, addresses (online), coordinates in any common
  format and map links (`geo:`, `?q=lat,lon`, `/@lat,lon,`). Offline results appear at once; online results a moment
  later under "More places (online)".
- **Use my current location**: automatic mode (asks for the location permission if needed).
- **Choose on the map**: a full-screen map (satellite with place names, or a street map) with a fixed pin in the
  middle: the user moves the map until the pin is on their spot. Pinch, double-tap and fling work; the map has its own
  search (results move the map there), zoom buttons and "my location". The bottom card names the place under the pin
  and shows its coordinates live.
- **Enter coordinates**: latitude and longitude in two fields, or one pasted line.
- **Browse by country**: country (flags, the user's own country first) → (0.9.8, for large countries) its largest
  cities and its states, emirates, provinces or regions → city, town or village (largest first, with a filter; small
  villages folded) → area (the city's districts and suburbs, alphabetical, with a filter; "centre" and "pin your exact spot on
  the map" at the top). A village or town without districts goes straight to the confirmation.

Every way ends on **Confirm the location**: the point on the satellite picture, its name and context, the exact
coordinates, the time zone (its name, its offset now, whether daylight saving applies), and **today's prayer times
there, computed exactly as the app will compute them** (same authority choice, official timetable where one applies).
"Use this location" saves; "Adjust on the map" opens the map at that point.

## What is saved, and how times follow

`KnownLocation` (unchanged format): the exact point (full double precision), the IANA zone id, names in English and
Arabic, context, country and region codes. Prayer times are computed for that point (`PrayerProvider` → engine), in
that zone; java.time applies the zone's daylight-saving rules per date, so times stay right across clock changes.
Alerts, the notification and the watch follow automatically (they already react to any settings change).

- **Time zone**: from the offline time-zone map for every chosen point (list, search, map or coordinates). A zone newer
  than the phone's own time-zone data is replaced by the nearest place's zone (same clock).
- **Country and region** (for the authority, e.g. IACAD for Dubai emirate): from the geocoder when online, else from
  the nearest offline place.
- **Elevation**: the offline place's (from GeoNames' DEM) when chosen from the list; 0 for a pin or coordinates. Only
  profiles that use elevation (none of the defaults) are affected.

## Data and providers

| Need | Chosen | Why | Alternatives considered |
|---|---|---|---|
| Offline places | **GeoNames** (CC BY 4.0), built by `tools/geo/build_world.py` into `app/src/main/assets/world.bin` (9 MB) | Free, worldwide, includes districts (PPLX), Arabic names (tagged `ar`), time zones; works with no network and sends nothing | OSM extracts (much larger, heavy processing); Who's On First (good but larger, fewer Arabic names) |
| Online search and naming | **Android's Geocoder** (the platform service; Google's on phones with Play services) | No account, key or cost; strong coverage of addresses, neighbourhoods and villages; Arabic results; Noor sends only the words typed | Google Places (key, billing, per-request cost); Mapbox (key; its terms forbid keeping results); HERE (key); Nominatim (1 request/s, no type-ahead) |
| Online search without a geocoder | **Photon** (komoot, OpenStreetMap data) | No key; designed for search-as-you-type; used only on phones without a Geocoder | Nominatim (stricter policy) |
| Map imagery | **Esri World Imagery** + **World Boundaries and Places** (labels); **World Street Map** | Sub-metre imagery in most cities, worldwide; free for non-commercial use with attribution (shown under the map); no key | Google Maps SDK (needs a Cloud project with billing, the owner's decision); Mapbox/MapTiler satellite (keys, quotas); Sentinel-2 cloudless (10 m: too coarse to place a pin on a house) |
| Time zones | **timezone-boundary-builder** (OpenStreetMap data, ODbL), built by `tools/geo/build_timezones.py` into `tzmap.bin` (2 MB) | Exact IANA zone boundaries, offline; borders kept to ~100 m; open sea in 15° nautical bands | Google Time Zone API (key, billing); GeoNames web service (account, limits); nearest-city zone (wrong near borders) |

Privacy: nothing is sent while browsing the list or typing coordinates. Online search sends only the typed words to
the phone's geocoder. The map fetches imagery tiles for the area shown (as any map does); tiles are cached in the app's
cache folder (at most ~150 MB, 30 days). Reverse naming of a pinned point uses the phone's geocoder.

Limits and costs: none of the providers needs an account or key. The public Photon instance and Esri's tiles have no
service guarantee; if either stops, the offline list and coordinates still work, and the provider can be swapped in
one place (`WorldStore`, `MapLayer`).

## Accuracy checks (automated)

- `core/prayer` `LocationDataTest`: 43 points worldwide (including Dubai/Sharjah, El Paso, the Indiana–Illinois line,
  Hebron/Jerusalem) get the right zone; open sea gets nautical time; every zone in the map exists in java.time; the
  list's zone and the map's agree for 99.7 %+ of a 1-in-97 sample of all places (any difference keeps the same clock);
  browsing UAE → Dubai → its districts; search in English and Arabic (Alexandria by any common Arabic spelling);
  coordinates in 16 formats accepted and 11 malformed inputs refused.
- `WorldTimesTest`: for 20 places on every continent (zones from the map) and 22 dates around 2026's daylight-saving
  changes, sunrise and sunset match an independent NOAA calculation within 2 minutes (800+ checks), each shown in the
  place's own offset that day; Dhuhr moves smoothly through every day of 2026 (no hour jumps at clock changes).
- `app` `LocationFlowTest`: the real screens, end to end: coordinates typed (Makkah) → saved exactly with Asia/Riyadh;
  a district by name (Dubai Marina) → Asia/Dubai and IACAD's region; UAE → Dubai → Al Karama; the map offline; Arabic
  search and confirmation, with no Latin letters on the Arabic confirmation. Renders: `app/build/screenshots/6x_*`, `7x_*`.

Not yet checked on the owner's phone: map gestures and imagery speed on a real device, and the phone's geocoder results
(the tests run without Play services).
