import com.noor.prayer.GoldenChecks

/** Runs the engine accuracy checks without Gradle (kotlinc + java only). */
fun main() {
    val t0 = System.currentTimeMillis()
    val d = GoldenChecks.dubaiRaw()
    println("Dubai C1 vs research reference: ${d.compared} times, max |diff| = ${"%.3f".format(d.maxAbsSeconds)} s (${d.worst}); rounded-minute mismatches = ${d.minuteMismatches} (${d.ambiguous} reference values within 0.02 s of a minute boundary skipped)")
    val g = GoldenChecks.genericRaw()
    println("Other places/angles vs reference: ${g.compared} times, max |diff| = ${"%.3f".format(g.maxAbsSeconds)} s (${g.worst})")
    val o = GoldenChecks.iacadOfficial()
    println("Dubai profile vs official IACAD (current era): ${o.exact}/${o.compared} exact (${"%.1f".format(100.0 * o.exact / o.compared)}%), per prayer ${o.perPrayer}, max |diff| = ${o.maxAbsMinutes} min")
    println("elapsed ${System.currentTimeMillis() - t0} ms")
    val ok = d.maxAbsSeconds < 0.5 && d.minuteMismatches == 0 && g.maxAbsSeconds < 0.5 && o.exact == 527 && o.compared == 588 && o.maxAbsMinutes == 1
    println(if (ok) "ALL CHECKS PASSED" else "CHECKS FAILED")
    if (!ok) kotlin.system.exitProcess(1)
}
