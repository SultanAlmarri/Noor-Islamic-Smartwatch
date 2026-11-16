package com.noor.prayer.face

import kotlin.math.roundToInt

/**
 * A prototype (0.18, not wired to anything): writes a [FaceDesign] as a Watch Face Format document, the first step of
 * the route that also works on watches launched with Wear OS 6 and on Google Play: design → WFF XML → an APK built on
 * the device (Google's Pack library) → validated on the device (validator-push-android) → installed with Watch Face
 * Push, as the pushed faces are today. docs/WATCH.md, "Minhad Studio", has the route and its sources.
 *
 * Prayer data reaches a WFF face only through complications, so the Minhad elements are complication slots bound to
 * Minhad's own providers (next prayer, Hijri date, Qibla), as in the pushed faces. What WFF cannot draw from that data
 * (the whole timetable, the Sun's arc, the day's dial) is listed in [Result.unsupported] rather than faked.
 */
object FaceWff {
    data class Result(val xml: String, val unsupported: List<String>)

    private const val SIZE = 450
    private const val PKG = "com.noor.smartwatch"
    private const val NEXT = "$PKG/com.noor.wear.complication.NextPrayerComplicationService"
    private const val HIJRI = "$PKG/com.noor.wear.complication.HijriComplicationService"
    private const val QIBLA = "$PKG/com.noor.wear.complication.QiblaComplicationService"

    fun write(d: FaceDesign): Result {
        val unsupported = mutableListOf<String>()
        var slotId = 0
        val t = d.theme
        val body = StringBuilder()
        fun color(c: Int) = "#%08X".format(c)
        fun px(f: Float) = (SIZE / 2f + f * SIZE / 2f).roundToInt()
        fun ambientAlpha(s: Slot?, e: Element): Int = when (d.ambient) {
            Ambient.DIMMED -> 180
            Ambient.MINIMAL -> if (e == Element.TIME || e == Element.NEXT_PRAYER) 220 else 0
            Ambient.TIME_ONLY -> if (e == Element.TIME) 220 else 0
            Ambient.CUSTOM -> if (e == Element.TIME || s in d.ambientSlots) 200 else 0
        }

        // Background (hidden always-on).
        when (t.background) {
            Background.BLACK -> Unit
            Background.SOLID -> body.append("""<PartDraw x="0" y="0" width="$SIZE" height="$SIZE"><Variant mode="AMBIENT" target="alpha" value="0"/><Ellipse x="0" y="0" width="$SIZE" height="$SIZE"><Fill color="${color(t.bg1)}"/></Ellipse></PartDraw>""")
            Background.GRADIENT, Background.DUNES -> body.append("""<PartDraw x="0" y="0" width="$SIZE" height="$SIZE"><Variant mode="AMBIENT" target="alpha" value="0"/><Ellipse x="0" y="0" width="$SIZE" height="$SIZE"><Fill color="${color(t.bg2)}"><RadialGradient centerX="225" centerY="96" radius="400" colors="${color(t.bg1)} ${color(t.bg2)}" positions="0 1"/></Fill></Ellipse></PartDraw>""")
        }
        if (t.background == Background.DUNES) unsupported += "background:dunes (needs the picture packed as a resource)"

        when (d.edge) {
            Edge.NONE -> Unit
            Edge.PRAYER_RING -> {
                val id = slotId++
                body.append("""<ComplicationSlot slotId="$id" x="0" y="0" width="$SIZE" height="$SIZE" displayName="ring" supportedTypes="RANGED_VALUE" isCustomizable="FALSE">""")
                body.append("""<DefaultProviderPolicy defaultSystemProvider="EMPTY" defaultSystemProviderType="RANGED_VALUE" primaryProvider="$NEXT" primaryProviderType="RANGED_VALUE"/><BoundingBox x="0" y="0" width="$SIZE" height="$SIZE"/>""")
                body.append("""<Complication type="RANGED_VALUE"><PartDraw x="0" y="0" width="$SIZE" height="$SIZE"><Variant mode="AMBIENT" target="alpha" value="0"/>""")
                body.append("""<Arc centerX="225" centerY="225" width="428" height="428" startAngle="0" endAngle="1"><Transform target="endAngle" value="clamp(360 * clamp(([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / clamp([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN], 1, 1000000), 0, 1), 1, 360)"/>""")
                body.append("""<Stroke color="${color(t.accent)}" thickness="6" cap="ROUND"/></Arc></PartDraw></Complication></ComplicationSlot>""")
            }
            Edge.DAY_DIAL -> unsupported += "edge:day"
        }
        if (d.marks != Marks.NONE) unsupported += "marks:${d.marks.id} (to be drawn as WFF parts)"

        for (s in Slot.entries) {
            val p = d.at(s) ?: continue
            val b = FaceGeometry.box(s, d)
            val x = px(b.left); val y = px(b.top); val w = px(b.right) - x; val h = px(b.bottom) - y
            val c = when (p.color) { ColorRole.ACCENT -> t.accent; ColorRole.TEXT -> t.text; ColorRole.SOFT -> t.soft; ColorRole.CUSTOM -> p.custom ?: t.accent }
            val size = (h * 0.62f * p.size.scale).roundToInt()
            val aod = """<Variant mode="AMBIENT" target="alpha" value="${ambientAlpha(s, p.element)}"/>"""
            fun text(template: String) = body.append(
                """<PartText x="$x" y="$y" width="$w" height="$h">$aod<Text align="CENTER" ellipsis="TRUE"><Font family="SYNC_TO_DEVICE" size="$size" color="${color(c)}"><Template>$template</Template></Font></Text></PartText>""")
            fun complication(provider: String, type: String, field: String) {
                val id = slotId++
                body.append("""<ComplicationSlot slotId="$id" x="$x" y="$y" width="$w" height="$h" displayName="${p.element.id}" supportedTypes="$type" isCustomizable="FALSE">""")
                body.append("""<DefaultProviderPolicy defaultSystemProvider="EMPTY" defaultSystemProviderType="$type" primaryProvider="$provider" primaryProviderType="$type"/><BoundingBox x="0" y="0" width="$w" height="$h"/>""")
                body.append("""<Complication type="$type"><PartText x="0" y="0" width="$w" height="$h">$aod<Text align="CENTER" ellipsis="TRUE"><Font family="SYNC_TO_DEVICE" size="$size" color="${color(c)}"><Template>%s<Parameter expression="[COMPLICATION.$field]"/></Template></Font></Text></PartText></Complication></ComplicationSlot>""")
            }
            when (p.element) {
                Element.TIME -> body.append(
                    """<DigitalClock x="$x" y="$y" width="$w" height="$h">$aod<TimeText format="${if (d.seconds) "hh:mm:ss" else "hh:mm"}" hourFormat="${when (d.clock) { FaceClock.H24 -> "24"; FaceClock.H12 -> "12"; FaceClock.FOLLOW -> "SYNC_TO_DEVICE" }}" align="CENTER" x="0" y="0" width="$w" height="$h"><Font family="SYNC_TO_DEVICE" size="${(h * 0.95f * p.size.scale).roundToInt()}" color="${color(c)}"/></TimeText></DigitalClock>""")
                Element.DATE -> text("""%s %s %s<Parameter expression="[DAY_OF_WEEK_S]"/><Parameter expression="[DAY]"/><Parameter expression="[MONTH_S]"/>""")
                Element.WEEKDAY -> text("""%s<Parameter expression="[DAY_OF_WEEK_F]"/>""")
                Element.BATTERY -> text("""%s%%<Parameter expression="[BATTERY_PERCENT]"/>""")
                Element.LABEL -> text(xmlEscape(p.text.ifEmpty { if (d.language == FaceLanguage.AR) "منهاد" else "Minhad" }))
                Element.HIJRI -> complication(HIJRI, "LONG_TEXT", "TEXT")
                Element.NEXT_PRAYER, Element.COUNTDOWN, Element.PRAYER_GAUGE -> complication(NEXT, "SHORT_TEXT", if (p.element == Element.NEXT_PRAYER) "TITLE" else "TEXT")
                Element.QIBLA -> complication(QIBLA, "SHORT_TEXT", "TEXT")
                Element.PRAYER_TIMES, Element.SUN_ARC -> unsupported += "element:${p.element.id}"
                Element.NONE -> Unit
            }
        }

        if (d.hands != Hands.NONE) {
            val hc = color(t.text)
            body.append("""<AnalogClock x="0" y="0" width="$SIZE" height="$SIZE">""")
            body.append("""<HourHand resource="" x="219" y="105" width="12" height="130" pivotX="0.5" pivotY="0.92" tintColor="$hc"/>""")
            body.append("""<MinuteHand resource="" x="221" y="45" width="8" height="190" pivotX="0.5" pivotY="0.947" tintColor="$hc"/>""")
            if (d.seconds) body.append("""<SecondHand resource="" x="223" y="30" width="4" height="210" pivotX="0.5" pivotY="0.93" tintColor="${color(t.accent)}"><Variant mode="AMBIENT" target="alpha" value="0"/></SecondHand>""")
            body.append("</AnalogClock>")
            unsupported += "hands:${d.hands.id} (hand pictures to be packed as resources)"
        }

        val xml = """<?xml version="1.0" encoding="utf-8"?>
<WatchFace width="$SIZE" height="$SIZE" clipShape="CIRCLE"><Metadata key="CLOCK_TYPE" value="${if (d.hands != Hands.NONE) "ANALOG" else "DIGITAL"}"/><Metadata key="PREVIEW_TIME" value="10:08:32"/><Scene backgroundColor="#FF000000">$body</Scene></WatchFace>
"""
        return Result(xml, unsupported)
    }

    private fun xmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("%", "%%")
}
