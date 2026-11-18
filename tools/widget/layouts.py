#!/usr/bin/env python3
"""
Writes the home-screen widgets' layouts (0.23): app/src/main/res/layout/widget_*.xml.

Why generated: every widget is built from the same parts (the background, the countdown with its seconds wheel, a
row of prayer cells), and each layout exists twice: with a soft shadow under the text (over the sky, a wallpaper,
dark glass) and without it (over a light background). A home screen cannot change a TextView's shadow, so the two
are two layouts, alike but for the theme on their root. Change the layouts here, not by hand, then run from the
repository root:

    python3 tools/widget/layouts.py

The ids are the code's (app/src/main/java/com/noor/app/widget/WidgetRender.kt); text sizes and colours are set by
the code for each widget's size and options, the styles only give the font and the defaults.
"""
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res", "layout")
A = 'xmlns:android="http://schemas.android.com/apk/res/android"'


def attrs(**kw):
    out = []
    for k, v in kw.items():
        k = k.replace("__", ":").replace("_", "_")
        out.append(f'{k}="{v}"')
    return " ".join(out)


def tv(id, style, w="wrap_content", h="wrap_content", extra=""):
    return (f'<TextView android:id="@+id/{id}" style="@style/Widget.Noor.Text.{style}" '
            f'android:layout_width="{w}" android:layout_height="{h}" {extra}/>')


def image(id, w="wrap_content", h="wrap_content", extra=""):
    return (f'<ImageView android:id="@+id/{id}" android:layout_width="{w}" android:layout_height="{h}" '
            f'android:contentDescription="@null" {extra}/>')


def row(children, w="match_parent", h="wrap_content", extra=""):
    return (f'<LinearLayout android:layout_width="{w}" android:layout_height="{h}" android:orientation="horizontal" {extra}>'
            + "".join(children) + "</LinearLayout>")


def col(children, w="match_parent", h="wrap_content", extra=""):
    return (f'<LinearLayout android:layout_width="{w}" android:layout_height="{h}" android:orientation="vertical" {extra}>'
            + "".join(children) + "</LinearLayout>")


def space(w="0dp", h="0dp", weight=None):
    wt = f'android:layout_weight="{weight}"' if weight else ""
    return f'<FrameLayout android:layout_width="{w}" android:layout_height="{h}" {wt}/>'


def count(extra=""):
    """The countdown (0.9.13, 0.9.14): Android's Chronometer (before Android 12, where the phone writes Minhad's digits),
    Minhad's text to the minute (before Android 12 otherwise), or (Android 12 and later) the hours and minutes as a
    picture in Minhad's digits and the seconds through a window onto a wheel of Minhad's digits, turned by an
    AnalogClock as its second hand (tools/widget/SecondsWheel.java). Always left to right."""
    return (f'<FrameLayout android:id="@+id/widget_countdown_box" android:layout_width="wrap_content" android:layout_height="wrap_content" {extra}>'
            + tv("widget_countdown", "Countdown", extra='android:visibility="gone"').replace("<TextView", "<Chronometer")
            + tv("widget_countdown_text", "Countdown", extra='android:visibility="gone"')
            + '<LinearLayout android:id="@+id/widget_count_row" android:layout_width="wrap_content" android:layout_height="wrap_content" '
              'android:layoutDirection="ltr" android:orientation="horizontal" android:visibility="gone">'
            + image("widget_count_main")
            + '<FrameLayout android:id="@+id/widget_count_window" android:layout_width="@dimen/noor_count_window" android:layout_height="@dimen/noor_count_height">'
              '<AnalogClock android:id="@+id/widget_count_wheel" android:layout_width="@dimen/noor_count_wheel" android:layout_height="@dimen/noor_count_wheel" '
              'android:layout_gravity="top|center_horizontal" android:dial="@drawable/noor_wheel_blank" android:hand_hour="@drawable/noor_wheel_blank" '
              'android:hand_minute="@drawable/noor_wheel_blank" /></FrameLayout>'
            + "</LinearLayout></FrameLayout>")


def cell(i, icon=False, vertical=True, h="match_parent"):
    """A prayer's cell: its name over its time (or beside it, in a column). The one counted to or from stands on a pill
    that wraps its text (widget_cell_pad above and below, widget_cell_pad_h at the sides, so six columns keep readable text), centred in the cell, so every cell's text
    is at the same height whether marked or not. (Both children match their wrapping parent: a FrameLayout sizes a
    match_parent child to itself only when it has two or more.)"""
    pill = image(f"widget_pill_{i}", "match_parent", "match_parent", 'android:scaleType="fitXY" android:src="@drawable/widget_pill" android:visibility="gone"')
    if vertical:
        inner = col(([image(f"widget_icon_{i}", "@dimen/widget_icon", "@dimen/widget_icon", 'android:layout_marginBottom="4dp"')] if icon else [])
                    + [tv(f"widget_name_{i}", "Small"), tv(f"widget_time_{i}", "Time", extra='android:layout_marginTop="2dp"')],
                    w="match_parent", h="match_parent",
                    extra='android:gravity="center" android:paddingStart="@dimen/widget_cell_pad_h" android:paddingEnd="@dimen/widget_cell_pad_h" android:paddingTop="@dimen/widget_cell_pad" android:paddingBottom="@dimen/widget_cell_pad"')
        return (f'<FrameLayout android:id="@+id/widget_cell_{i}" android:layout_width="0dp" android:layout_height="{h}" android:layout_weight="1">'
                + '<FrameLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_gravity="center">'
                + pill + inner + "</FrameLayout></FrameLayout>")
    inner = row(([image(f"widget_icon_{i}", "@dimen/widget_icon_small", "@dimen/widget_icon_small", 'android:layout_marginEnd="8dp"')] if icon else [])
                + [tv(f"widget_name_{i}", "Body", "0dp", extra='android:layout_weight="1"'), tv(f"widget_time_{i}", "Time")],
                h="match_parent", extra='android:gravity="center_vertical" android:paddingStart="10dp" android:paddingEnd="10dp"')
    return (f'<FrameLayout android:id="@+id/widget_cell_{i}" android:layout_width="match_parent" android:layout_height="{h}">'
            + pill + inner + "</FrameLayout>")


def cells(n=6, icon=False, h="wrap_content", extra="", cell_h="match_parent"):
    return row([cell(i, icon, h=cell_h) for i in range(n)], h=h, extra=extra)


def progress(extra=""):
    return image("widget_progress", "match_parent", "@dimen/widget_bar", f'android:scaleType="fitXY" {extra}')


def root(comment, content, plain):
    theme = "Theme.Noor.Widget.Plain" if plain else "Theme.Noor.Widget"
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Generated by tools/widget/layouts.py (0.23): {comment}{" Without text shadows (light backgrounds)." if plain else ""} -->\n'
            f'<FrameLayout {A} android:id="@+id/widget_root" android:layout_width="match_parent" android:layout_height="match_parent" '
            f'android:theme="@style/{theme}">'
            + image("widget_bg", "match_parent", "match_parent", 'android:scaleType="fitXY"')
            + image("widget_scrim", "match_parent", "match_parent", 'android:scaleType="fitXY" android:visibility="gone"')
            + content + "</FrameLayout>\n")


PAD = 'android:paddingStart="@dimen/widget_pad" android:paddingEnd="@dimen/widget_pad" android:paddingTop="@dimen/widget_pad_v" android:paddingBottom="@dimen/widget_pad_v"'

title_row = row([tv("widget_prayer", "Title"), tv("widget_time", "Body", extra='android:layout_marginStart="8dp"')],
                w="wrap_content", extra='android:baselineAligned="true"')

LAYOUTS = {
    # Next prayer, 2x1 and other short sizes: the prayer and its time, the countdown, the place when there is room.
    "widget_next": ("the next prayer, its time and the countdown (short sizes).",
        col([title_row, count('android:layout_marginTop="2dp"'), tv("widget_place", "Small", extra='android:layout_marginTop="2dp"')],
            h="match_parent", extra=f'android:id="@+id/widget_content" android:gravity="center_vertical|start" {PAD}')),
    # Next prayer, 3x1 and wider short sizes: the prayer on the reading side, the countdown balanced on the other.
    "widget_next_wide": ("the next prayer and its time on one side, the countdown on the other (wide short sizes).",
        row([col([title_row, tv("widget_then", "Small", extra='android:layout_marginTop="4dp"'), tv("widget_place", "Small", extra='android:layout_marginTop="2dp"')],
                 w="0dp", extra='android:layout_weight="1"'),
             count('android:layout_marginStart="12dp"')],
            h="match_parent", extra=f'android:id="@+id/widget_content" android:gravity="center_vertical" {PAD}')),
    # Next prayer, 2x2 and larger: a calm column, the countdown large at the foot.
    "widget_next_tall": ("the next prayer, its time and the countdown (tall sizes).",
        col([row([tv("widget_prayer", "Display"), tv("widget_time", "Body", extra='android:layout_marginStart="8dp"')],
                 w="wrap_content", extra='android:baselineAligned="true"'),
             space(h="0dp", weight=1), count(), space(h="0dp", weight=1),
             tv("widget_then", "Small"), tv("widget_place", "Small", extra='android:layout_marginTop="2dp"')],
            h="match_parent", extra=f'android:gravity="start" {PAD}')),
    # Today's prayers, 4x2: the next prayer, the countdown, the place and date, the day's progress, the six times.
    "widget_today": ("today's prayers: the next one and the countdown, the place and the Hijri date, the progress to the next, the six times.",
        col([row([col([title_row, tv("widget_place", "Small", extra='android:layout_marginTop="2dp"')], w="0dp", extra='android:layout_weight="1"'),
                  count('android:layout_marginStart="10dp"')], extra='android:gravity="top"'),
             space(h="0dp", weight=1), progress('android:layout_marginBottom="4dp"'), cells(cell_h="@dimen/widget_cell")],
            h="match_parent", extra=PAD)),
    # Today's prayers, 4x1: the six times in one row.
    "widget_today_row": ("today's prayers in one row, the next one marked.",
        cells(h="match_parent", extra=PAD.replace("widget_pad_v", "widget_pad_row"))),
    # Today's prayers, 2x3 and 2x4: a column.
    "widget_today_column": ("today's prayers in a column: the next one and the countdown, then the six times.",
        col([title_row, count('android:layout_marginTop="4dp"'), tv("widget_then", "Small", extra='android:layout_marginTop="2dp"'),
             progress('android:layout_marginTop="8dp" android:layout_marginBottom="8dp"'),
             col([cell(i, vertical=False, h="@dimen/widget_row") for i in range(6)]),
             space(h="0dp", weight=1),
             tv("widget_place", "Small", extra='android:layout_marginTop="4dp"')],
            h="match_parent", extra=PAD)),
    # Today's prayers, 4x3 and larger: roomier, a grid of the six with their signs.
    "widget_today_grid": ("today's prayers, large: the next one and the countdown, the place and the date, the progress, a grid of the six with their signs.",
        col([row([col([title_row, tv("widget_place", "Small", extra='android:layout_marginTop="2dp"')], w="0dp", extra='android:layout_weight="1"'),
                  count('android:layout_marginStart="10dp"')], extra='android:gravity="top"'),
             progress('android:layout_marginTop="10dp" android:layout_marginBottom="8dp"'),
             row([cell(i, icon=True) for i in range(3)], h="0dp", extra='android:layout_weight="1"'),
             row([cell(i, icon=True) for i in range(3, 6)], h="0dp", extra='android:layout_weight="1" android:layout_marginTop="6dp"')],
            h="match_parent", extra=PAD)),
    # Minhad sky, 4x2 and 4x3: the home sky in small, the next prayer and the countdown at the top.
    "widget_sky": ("the home sky in small, with the next prayer, its time, the place and the date, and the countdown.",
        col([row([col([title_row, tv("widget_place", "Small", extra='android:layout_marginTop="2dp"')], w="0dp", extra='android:layout_weight="1"'),
                  count('android:layout_marginStart="10dp"')], extra='android:gravity="top"'),
             space(h="0dp", weight=1),
             cells(cell_h="@dimen/widget_cell", extra='android:id="@+id/widget_cells" android:visibility="gone"')],
            h="match_parent", extra=PAD)),
    # Countdown ring, 2x2: the time from the last prayer to the next as a ring, the countdown inside it.
    "widget_ring": ("a ring from the last prayer to the next, the next prayer, the countdown and its time inside.",
        image("widget_ring", "match_parent", "match_parent", 'android:scaleType="fitCenter" android:padding="@dimen/widget_ring_pad"')
        + col([row([tv("widget_prayer", "Title"), tv("widget_time", "Small", extra='android:layout_marginStart="6dp"')],
                   w="wrap_content", extra='android:baselineAligned="true"'),
               count('android:layout_marginTop="2dp"')],
              h="match_parent", extra='android:gravity="center"')),
    # Date, 2x1 and other narrow sizes: the Hijri date, the Gregorian date, the next occasion.
    "widget_date": ("the Hijri date, the Gregorian date and weekday, and the next Islamic occasion (narrow sizes).",
        col([tv("widget_hijri_day", "Display"), tv("widget_hijri_year", "Small", extra='android:layout_marginTop="2dp"'),
             tv("widget_gregorian", "Small", extra='android:layout_marginTop="2dp"'),
             tv("widget_occasion", "Small", extra='android:layout_marginTop="4dp"')],
            h="match_parent", extra=f'android:gravity="center_vertical|start" {PAD}')),
    # Date, 2x2 and larger: the day's number large on top, the month and year, the Gregorian date, the next occasion.
    "widget_date_tall": ("the Hijri date with the day's number large, the Gregorian date and weekday, and the next Islamic occasion (tall sizes).",
        col([tv("widget_hijri_number", "Number"), tv("widget_hijri_day", "Title", extra='android:layout_marginTop="2dp"'),
             tv("widget_gregorian", "Small", extra='android:layout_marginTop="2dp"'), space(h="0dp", weight=1),
             tv("widget_occasion_name", "Small"), tv("widget_occasion", "Body", extra='android:layout_marginTop="2dp"')],
            h="match_parent", extra=PAD)),
    # Date, 4x1 and wider: the day's number large, the month and year, the Gregorian date, the next occasion apart.
    "widget_date_wide": ("the Hijri date with the day's number large, the Gregorian date and weekday, and the next Islamic occasion.",
        row([tv("widget_hijri_number", "Number"),
             col([tv("widget_hijri_day", "Title"), tv("widget_gregorian", "Small", extra='android:layout_marginTop="2dp"')],
                 w="0dp", extra='android:layout_weight="1" android:layout_marginStart="12dp"'),
             col([tv("widget_occasion_name", "Small"), tv("widget_occasion", "Body", extra='android:layout_marginTop="2dp"')],
                 w="wrap_content", extra='android:id="@+id/widget_occasion_box" android:gravity="end" android:layout_marginStart="12dp"')],
            h="match_parent", extra=f'android:gravity="center_vertical" {PAD}')),
    # Any prayer widget before a place is chosen: an invitation to open Minhad.
    "widget_message": ("a message (before a place is chosen).",
        col([tv("widget_message", "Body", "match_parent", extra='android:gravity="center" android:textAlignment="center" android:maxLines="3"')],
            h="match_parent", extra=f'android:gravity="center" {PAD}')),
    # Prayer strip, 4x1 and 5x1: the next prayer and the countdown, then the five prayers (and Shuruq) in a row.
    "widget_strip": ("a slim row of the day's prayers, the next one marked, with the next prayer and the countdown above.",
        col([row([tv("widget_prayer", "Title"), tv("widget_time", "Small", extra='android:layout_marginStart="6dp"'), space(weight=1), count()],
                 extra='android:id="@+id/widget_header" android:gravity="center_vertical" android:baselineAligned="false"'),
             cells(h="0dp", extra='android:layout_weight="1"')],
            h="match_parent", extra='android:id="@+id/widget_content" ' + PAD.replace("widget_pad_v", "widget_pad_row"))),
}


# ---- The widget picker's previews (Android 12 and later: previewLayout): the same parts with fixed text, so the
# picker shows each widget as it looks, in the phone's language, at the size the launcher gives it. ----

def ptv(text, size, color="#FFFFFFFF", font="sans-serif", extra=""):
    return (f'<TextView style="@style/Widget.Noor.Text" android:layout_width="wrap_content" android:layout_height="wrap_content" '
            f'android:text="{text}" android:textSize="{size}sp" android:textColor="{color}" android:fontFamily="{font}" {extra}/>')


GOLD = "#FFF6D696"
COUNT = ptv("@string/widget_preview_count", 28, font="sans-serif-medium", extra='android:textDirection="ltr" android:layoutDirection="ltr"')
SMALL_COUNT = ptv("@string/widget_preview_count", 20, font="sans-serif-medium", extra='android:textDirection="ltr" android:layoutDirection="ltr"')


def pcells(n=6, names=("fajr", "shuruq", "dhuhr", "asr", "maghrib", "isha"), times=("5:58", "7:25", "12:28", "3:37", "5:58", "7:19"),
           marked=3, h="wrap_content", extra=""):
    out = []
    for i in range(n):
        on = i == marked
        bg = 'android:background="@drawable/widget_preview_pill"' if on else ""
        out.append(f'<LinearLayout android:layout_width="0dp" android:layout_height="{h}" android:layout_weight="1" android:gravity="center" '
                   f'android:orientation="vertical" android:paddingTop="4dp" android:paddingBottom="4dp" {bg}>'
                   + ptv(f"@string/widget_preview_{names[i]}", 11, GOLD if on else "#D9FFFFFF")
                   + ptv(times[i], 14, GOLD if on else "#FFFFFFFF", "sans-serif-medium", 'android:layout_marginTop="2dp"')
                   + "</LinearLayout>")
    return row(out, h=h, extra=extra)


def proot(comment, bg, content):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Generated by tools/widget/layouts.py (0.23): the widget picker\'s preview of {comment} -->\n'
            f'<FrameLayout {A} android:layout_width="match_parent" android:layout_height="match_parent" android:theme="@style/Theme.Noor.Widget">'
            f'<ImageView android:layout_width="match_parent" android:layout_height="match_parent" android:contentDescription="@null" '
            f'android:scaleType="centerCrop" android:src="@drawable/{bg}" />'
            + content + "</FrameLayout>\n")


ASR = ptv("@string/widget_preview_asr", 17, font="sans-serif-medium")
AT = ptv("3:37 PM", 13, "#E6FFFFFF", extra='android:layout_marginStart="8dp"')
PLACE = ptv("@string/widget_preview_place", 11, "#B8FFFFFF", extra='android:layout_marginTop="2dp"')

PREVIEWS = {
    "widget_preview_next": ("the Next prayer widget.", "widget_preview_sky_bg",
        col([ptv("@string/next_prayer", 11, GOLD, "sans-serif-medium"), row([ASR, AT], w="wrap_content"), COUNT],
            h="match_parent", extra=f'android:gravity="center_vertical|start" {PAD}')),
    "widget_preview_today": ("Today's prayers.", "widget_preview_sky_bg",
        col([row([col([row([ASR, AT], w="wrap_content"), PLACE], w="0dp", extra='android:layout_weight="1"'), COUNT]),
             space(h="0dp", weight=1), pcells()], h="match_parent", extra=PAD)),
    "widget_preview_sky": ("the Minhad sky widget.", "widget_preview_sky_bg",
        col([row([col([row([ASR, AT], w="wrap_content"), PLACE], w="0dp", extra='android:layout_weight="1"'), COUNT])], extra=PAD)),
    "widget_preview_ring": ("the Countdown ring.", "widget_glass",
        '<ImageView android:layout_width="match_parent" android:layout_height="match_parent" android:contentDescription="@null" '
        'android:padding="8dp" android:scaleType="fitCenter" android:src="@drawable/widget_preview_ring_art" />'
        + col([row([ptv("@string/widget_preview_asr", 14, GOLD, "sans-serif-medium"), ptv("3:37 PM", 12, "#E6FFFFFF", extra='android:layout_marginStart="6dp"')],
                   w="wrap_content"), SMALL_COUNT],
              h="match_parent", extra='android:gravity="center"')),
    "widget_preview_strip": ("the Prayer strip.", "widget_glass",
        col([row([ptv("@string/widget_preview_asr", 14, GOLD, "sans-serif-medium"), ptv("3:37 PM", 12, "#E6FFFFFF", extra='android:layout_marginStart="6dp"'),
                  space(weight=1), SMALL_COUNT], extra='android:gravity="center_vertical"'),
             pcells(5, ("fajr", "dhuhr", "asr", "maghrib", "isha"), ("5:58", "12:28", "3:37", "5:58", "7:19"), 2, h="0dp",
                    extra='android:layout_weight="1"')], h="match_parent", extra=PAD.replace("widget_pad_v", "widget_pad_row"))),
    "widget_preview_date": ("the Hijri date widget.", "widget_glass",
        col([ptv("@string/widget_preview_hijri", 20, font="sans-serif-medium"), ptv("@string/widget_preview_hijri_year", 11, GOLD),
             ptv("@string/widget_preview_gregorian", 11.5, "#E6FFFFFF")], h="match_parent", extra=f'android:gravity="center_vertical|start" {PAD}')),
}


def main():
    for name, (comment, content) in LAYOUTS.items():
        for plain in (False, True):
            path = os.path.join(OUT, name + ("_plain" if plain else "") + ".xml")
            with open(path, "w", encoding="utf-8") as f:
                f.write(root(comment, content, plain))
    for name, (comment, bg, content) in PREVIEWS.items():
        with open(os.path.join(OUT, name + ".xml"), "w", encoding="utf-8") as f:
            f.write(proot(comment, bg, content))
    print(f"wrote {2 * len(LAYOUTS)} layouts and {len(PREVIEWS)} previews")


if __name__ == "__main__":
    main()
