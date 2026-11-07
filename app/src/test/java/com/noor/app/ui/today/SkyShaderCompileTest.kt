package com.noor.app.ui.today

import android.graphics.RuntimeShader
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The GPU shaders compile with Android's own shader compiler (Skia's SkSL, which Robolectric's native graphics carry):
 * no syntax error, and within SkSL's program-size limit (it unrolls every loop and inlines every call, and refuses
 * programs beyond about 100 000 operations: 0.9.6's first cloud code was refused on the emulator for that).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SkyShaderCompileTest {
    @Test fun bothShadersCompileOnAndroid() {
        val res = RuntimeEnvironment.getApplication().resources
        val (sky, land) = SkyShaders.sources(res)
        val errors = listOf("sky" to sky, "land" to land).mapNotNull { (name, src) ->
            runCatching { RuntimeShader(dialect(src)) }.exceptionOrNull()?.let { "$name: ${it.message}" }
        }
        assertTrue(errors.joinToString("\n"), errors.isEmpty())
    }

    /** Robolectric's SkSL predates AGSL's `child.eval(p)`; its spelling is `sample(child, p)`. Nothing else differs. */
    private fun dialect(src: String) = Regex("(\\w+)\\.eval\\(").replace(src) { "sample(${it.groupValues[1]}, " }
}
