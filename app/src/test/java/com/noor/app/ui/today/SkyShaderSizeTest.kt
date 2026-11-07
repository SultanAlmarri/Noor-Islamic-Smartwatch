package com.noor.app.ui.today

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android's shader compiler (SkSL) unrolls every loop and inlines every call when it sizes a program, and refuses
 * programs beyond about 100 000 operations ("program is too large"). Robolectric's older compiler does not apply that
 * limit, so this estimates the count the same way (each expression node 1, a call the callee's whole size, a loop its
 * body times its iterations) and keeps a wide margin. Calibration: 0.9.6's first sky shader, refused on Android 14
 * and 15, estimates at about 150 000 here.
 */
class SkyShaderSizeTest {
    private val raw = listOf(File("src/main/res/raw"), File("app/src/main/res/raw")).first { it.isDirectory }

    @Test fun shadersStayWellWithinSkslSizeLimit() {
        val common = File(raw, "sky_common.agsl").readText()
        for (layer in listOf("sky_layer", "land_layer")) {
            val size = ProgramSize.estimate(common + File(raw, "$layer.agsl").readText())
            println("$layer ≈ $size nodes")
            assertTrue("$layer ≈ $size nodes: keep under 65 000 (SkSL refuses ~100 000)", size < 65_000)
        }
    }

    private object ProgramSize {
        private val token = Regex("""[A-Za-z_]\w*|\d+\.\d*|\.\d+|\d+|==|<=|>=|!=|&&|\|\||\+\+|--|\+=|-=|\*=|/=|[-+*/<>=!?:(){}\[\];,.]""")
        private val skip = setOf("{", "}", ";", ",", "(", ")", "float", "float2", "float3", "float4", "int", "const", "return", "if", "else")

        fun estimate(source: String): Int {
            val src = source.replace(Regex("//[^\n]*"), "").replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            val funcs = HashMap<String, List<String>>()
            Regex("""\b(?:float|float2|float3|float4|half4|int|void)\s+([A-Za-z_]\w*)\s*\([^)]*\)\s*\{""").findAll(src).forEach { m ->
                var i = m.range.last + 1; var depth = 1
                while (depth > 0) { if (src[i] == '{') depth++ else if (src[i] == '}') depth--; i++ }
                funcs[m.groupValues[1]] = token.findAll(src.substring(m.range.last + 1, i - 1)).map { it.value }.toList()
            }
            val memo = HashMap<String, Int>()
            fun size(name: String): Int = memo[name] ?: run { memo[name] = 0; count(funcs.getValue(name), 0, funcs.getValue(name).size, funcs, memo, ::size).also { memo[name] = it } }
            return size("render")
        }

        private fun count(t: List<String>, a: Int, b: Int, funcs: Map<String, List<String>>, memo: Map<String, Int>, size: (String) -> Int): Int {
            var n = 0; var i = a
            while (i < b) {
                val tok = t[i]
                if (tok == "for") {
                    var j = i + 2; var depth = 1; val header = ArrayList<String>()
                    while (depth > 0) { if (t[j] == "(") depth++ else if (t[j] == ")") depth--; if (depth > 0) header += t[j]; j++ }
                    val m = Regex("""int (\w+) = (\d+) ; \1 (<=|<|>=|>) (\d+) ; \1 (\+\+|--)""").find(header.joinToString(" "))
                        ?: error("loop bounds must be literals: ${header.joinToString(" ")}")
                    val lo = m.groupValues[2].toInt(); val hi = m.groupValues[4].toInt()
                    val iters = when (m.groupValues[3]) { "<" -> hi - lo; "<=" -> hi - lo + 1; ">=" -> lo - hi + 1; else -> lo - hi }
                    var k = j + 1; depth = 1
                    while (depth > 0) { if (t[k] == "{") depth++ else if (t[k] == "}") depth--; k++ }
                    n += count(t, j + 1, k - 1, funcs, memo, size) * iters + 3
                    i = k; continue
                }
                n += when {
                    tok in funcs && i + 1 < b && t[i + 1] == "(" -> size(tok)
                    tok in skip -> 0
                    else -> 1
                }
                i++
            }
            return n
        }
    }
}
