package com.noor.app.sky

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.os.SystemClock
import android.util.Log
import com.noor.app.ui.today.NightSky
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The real sky (1.0): a small 3D renderer of its own (OpenGL ES 3.0) for the home screen.
 *
 * The camera never moves and the world changes slowly, which is what makes film-like clouds affordable on a phone:
 *
 * - the land is found in three dimensions **once** per scenery (gbuffer.frag: what every pixel sees, how it faces, how
 *   far it is, where its shadows fall) and only relit afterwards;
 * - the clouds are marched through as volumes, but only one pixel in sixteen is redone at each update (cloud.frag);
 *   what is stored is how much sunlight and skylight each pixel's clouds send to the eye, so the colours of the
 *   moment follow the Sun at once;
 * - the clear sky is computed for the camera's own directions at a small size (sky.frag) only when the Sun moves;
 * - the picture is then put together (scene.frag), softened for glow and for the frosted glass (blur.frag) and sent to
 *   the screen through a filmic curve (final.frag), a few times a second when nothing but the clouds moves, every
 *   frame while the Sun is dragged.
 *
 * The shader files live in assets/sky and are the very files the design bench (tools/sky3d) runs in a browser, pass
 * for pass in the same order. One engine serves the whole app; it keeps its GL context while the home tab is away, so
 * coming back costs nothing. Anything that goes wrong (no ES 3.0, no float render targets, a shader the driver
 * rejects) is reported through [RealSky.fail] and the 0.9 renderer takes over.
 */
internal class RealSkyEngine(private val app: Context) : Runnable {

    /** Everything one picture needs; made on the main thread ([RealSkyFrame]), read by the render thread. */
    private val pending = AtomicReference<RealSkyFrame?>(null)
    private val lock = Object()
    @Volatile private var surface: SurfaceTexture? = null
    @Volatile private var surfaceW = 0
    @Volatile private var surfaceH = 0
    @Volatile private var surfaceChanged = false
    @Volatile private var running = true
    @Volatile private var visible = false
    /** Called on the render thread when the first picture is on screen. */
    @Volatile var onFirstFrame: (() -> Unit)? = null
    private val thread = Thread(this, "NoorRealSky").apply { priority = Thread.NORM_PRIORITY - 1 }

    init { thread.start() }

    fun submit(frame: RealSkyFrame) { pending.set(frame); synchronized(lock) { lock.notifyAll() } }

    fun attach(st: SurfaceTexture, w: Int, h: Int) {
        synchronized(lock) { surface = st; surfaceW = w; surfaceH = h; surfaceChanged = true; visible = true; lock.notifyAll() }
    }

    fun resize(w: Int, h: Int) { synchronized(lock) { surfaceW = w; surfaceH = h; lock.notifyAll() } }

    /** The view's surface is going away: the render thread lets go of it before this returns. */
    fun detach(st: SurfaceTexture) {
        synchronized(lock) {
            if (surface !== st) return
            surface = null; surfaceChanged = true; visible = false
            // Leaving before the first picture was shown is not a failed start.
            if (attempting && !firstShown) { attempting = false; RealSky.cancelAttempt(app) }
            lock.notifyAll()
            val until = SystemClock.uptimeMillis() + 800
            while (eglSurface != EGL14.EGL_NO_SURFACE && SystemClock.uptimeMillis() < until) runCatching { lock.wait(50) }
        }
    }

    fun setVisible(v: Boolean) { synchronized(lock) { visible = v; lock.notifyAll() } }

    // ---- EGL ---------------------------------------------------------------------------------------------------

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    @Volatile private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE

    private fun initEgl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "no EGL display" }
        val v = IntArray(2)
        check(EGL14.eglInitialize(display, v, 0, v, 1)) { "eglInitialize" }
        val attrs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, 0x40 /* EGL_OPENGL_ES3_BIT */, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0, EGL14.EGL_STENCIL_SIZE, 0, EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val n = IntArray(1)
        check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, n, 0) && n[0] > 0) { "no ES 3 config" }
        config = configs[0]
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "no ES 3 context" }
        // A one-pixel surface keeps the context usable while no view is attached.
        pbuffer = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        check(EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)) { "eglMakeCurrent" }
    }

    private fun bindSurface() {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
            EGL14.eglDestroySurface(display, eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
        }
        val st = surface
        if (st != null) {
            eglSurface = EGL14.eglCreateWindowSurface(display, config, st, intArrayOf(EGL14.EGL_NONE), 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) { Log.w(TAG, "no window surface: ${EGL14.eglGetError()}"); return }
            EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)
            EGL14.eglSwapInterval(display, 1)
        }
    }

    // ---- GL helpers --------------------------------------------------------------------------------------------

    private class Uniform(val name: String, val type: Int, val loc: Int)
    private class Program(val id: Int, val uniforms: List<Uniform>)
    private class Tex(val id: Int, val w: Int, val h: Int, val target: Int = GLES30.GL_TEXTURE_2D)
    private class Target(val fbo: Int, val w: Int, val h: Int)

    private val sources = HashMap<String, String>()
    private fun src(name: String) = sources.getOrPut(name) { app.assets.open("sky/$name").bufferedReader().use { it.readText() } }

    private fun compile(type: Int, text: String, label: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, text)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(s)
            GLES30.glDeleteShader(s)
            error("$label: $log")
        }
        return s
    }

    private var binaryDir: File? = null
    private var rendererId = ""

    /** Builds a program from parts of assets/sky; its compiled form is kept on disk, so later starts are quick. */
    private fun program(label: String, parts: List<String>, vertex: String = "quad.vert"): Program {
        val vs = HEAD + src(vertex)
        val fs = HEAD + parts.joinToString("\n") { src(it) }
        val key = MessageDigest.getInstance("SHA-1").digest((rendererId + vs + fs).toByteArray()).joinToString("") { "%02x".format(it) }
        val file = binaryDir?.let { File(it, "$label-$key.bin") }
        var id = 0
        if (file != null && file.exists()) runCatching {
            val bytes = file.readBytes()
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val format = bb.int
            val p = GLES30.glCreateProgram()
            GLES30.glProgramBinary(p, format, ByteBuffer.allocateDirect(bytes.size - 4).put(bytes, 4, bytes.size - 4).position(0), bytes.size - 4)
            val ok = IntArray(1)
            GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
            if (ok[0] != 0) id = p else { GLES30.glDeleteProgram(p); file.delete() }
        }
        while (GLES30.glGetError() != GLES30.GL_NO_ERROR) { /* a stale binary may leave an error behind */ }
        if (id == 0) {
            val v = compile(GLES30.GL_VERTEX_SHADER, vs, "$label.vert")
            val f = compile(GLES30.GL_FRAGMENT_SHADER, fs, label)
            id = GLES30.glCreateProgram()
            GLES30.glAttachShader(id, v); GLES30.glAttachShader(id, f)
            if (vertex != "quad.vert") { GLES30.glBindAttribLocation(id, 0, "aStar"); GLES30.glBindAttribLocation(id, 1, "aLook") }
            GLES30.glLinkProgram(id)
            val ok = IntArray(1)
            GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) error("$label link: ${GLES30.glGetProgramInfoLog(id)}")
            GLES30.glDeleteShader(v); GLES30.glDeleteShader(f)
            if (file != null) runCatching {
                val len = IntArray(1)
                GLES30.glGetProgramiv(id, GLES30.GL_PROGRAM_BINARY_LENGTH, len, 0)
                if (len[0] > 0) {
                    val buf = ByteBuffer.allocateDirect(len[0])
                    val got = IntArray(1); val format = IntArray(1)
                    GLES30.glGetProgramBinary(id, len[0], got, 0, format, 0, buf)
                    if (got[0] > 0 && GLES30.glGetError() == GLES30.GL_NO_ERROR) {
                        val out = ByteArray(got[0]); buf.position(0); buf.get(out)
                        file.outputStream().use { o -> o.write(ByteBuffer.allocate(4).putInt(format[0]).array()); o.write(out) }
                    }
                }
            }
            while (GLES30.glGetError() != GLES30.GL_NO_ERROR) { }
        }
        val count = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_ACTIVE_UNIFORMS, count, 0)
        val list = ArrayList<Uniform>()
        val size = IntArray(1); val type = IntArray(1)
        for (i in 0 until count[0]) {
            val name = GLES30.glGetActiveUniform(id, i, size, 0, type, 0).removeSuffix("[0]")
            list += Uniform(name, type[0], GLES30.glGetUniformLocation(id, name))
        }
        return Program(id, list)
    }

    private fun tex2D(w: Int, h: Int, internal: Int, format: Int, type: Int, filter: Int, wrap: Int): Tex {
        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internal, w, h, 0, format, type, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
        return Tex(t[0], w, h)
    }
    private fun hdr(w: Int, h: Int, filter: Int = GLES30.GL_LINEAR) = tex2D(w, h, GLES30.GL_RGBA16F, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, filter, GLES30.GL_CLAMP_TO_EDGE)
    private fun ldr(w: Int, h: Int, filter: Int = GLES30.GL_LINEAR) = tex2D(w, h, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, filter, GLES30.GL_CLAMP_TO_EDGE)

    private fun target(vararg texs: Tex): Target {
        val f = IntArray(1)
        GLES30.glGenFramebuffers(1, f, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, f[0])
        texs.forEachIndexed { i, t -> GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0 + i, GLES30.GL_TEXTURE_2D, t.id, 0) }
        GLES30.glDrawBuffers(texs.size, IntArray(texs.size) { GLES30.GL_COLOR_ATTACHMENT0 + it }, 0)
        val st = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        check(st == GLES30.GL_FRAMEBUFFER_COMPLETE) { "framebuffer incomplete 0x${st.toString(16)}" }
        return Target(f[0], texs[0].w, texs[0].h)
    }

    private fun delete(vararg t: Tex?) { t.filterNotNull().forEach { GLES30.glDeleteTextures(1, intArrayOf(it.id), 0) } }
    private fun delete(vararg t: Target?) { t.filterNotNull().forEach { GLES30.glDeleteFramebuffers(1, intArrayOf(it.fbo), 0) } }

    // ---- State -------------------------------------------------------------------------------------------------

    private val u = HashMap<String, FloatArray>()       // uniform values by name
    private val ui = HashMap<String, Int>()             // integer uniforms
    private val tex = HashMap<String, Tex>()            // textures by sampler name
    private val progs = HashMap<String, Program>()
    private var glReady = false
    private var tier = RealSky.Tier.HIGH
    private var noiseN = 0

    private var tTrans: Target? = null; private var tMeter: Target? = null; private var tSky: Target? = null
    private var tCloud: Target? = null; private var tScene: Target? = null; private var tG: Target? = null
    private var tB4: Target? = null; private var tB8a: Target? = null; private var tB8b: Target? = null
    private var b4: Tex? = null; private var b8a: Tex? = null; private var b8b: Tex? = null
    private var viewW = 0; private var viewH = 0
    private var sizedFor = ""
    private var starVao = 0; private var starCount = 0

    private var lastHaze = -1f
    private var lightKey = ""
    private var landKey = ""
    private var landRow = 0              // the land is drawn a band of rows at a time; -1 = done
    private var cloudFrame = 0
    private var cloudFill = 0            // cloud updates still owed before every pixel is fresh
    private var cloudKey = ""
    private var shown = false
    private var lastScene = 0L
    private var lastCloud = 0L
    private var lastSwap = 0L
    private var slowFrames = 0
    private var timedFrames = 0
    private var fade = 0f

    private fun initGl() {
        val began = SystemClock.uptimeMillis()
        val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        rendererId = (GLES30.glGetString(GLES30.GL_RENDERER) ?: "") + "|" + (GLES30.glGetString(GLES30.GL_VERSION) ?: "")
        Log.i(TAG, "GL: $rendererId")
        check(ext.contains("EXT_color_buffer_half_float") || ext.contains("EXT_color_buffer_float")) { "no float render targets" }
        binaryDir = File(app.cacheDir, "sky-programs").apply { mkdirs() }
        tier = RealSky.tierFor(app, rendererId)
        val common = listOf("common.glsl")
        val air = listOf("common.glsl", "atmosphere.glsl")
        progs["noise"] = program("noise", listOf("noise.frag"))
        progs["trans"] = program("trans", air + "trans.frag")
        progs["meter"] = program("meter", air + "meter.frag")
        progs["sky"] = program("sky", air + "sky.frag")
        progs["cloud"] = program("cloud", air + "clouds.glsl" + "cloud.frag")
        progs["scene"] = program("scene", air + "clouds.glsl" + "scene.frag")
        progs["blur"] = program("blur", common + "blur.frag")
        progs["final"] = program("final", common + "final.frag")
        progs["stars"] = program("stars", listOf("stars.frag"), vertex = "stars.vert")
        buildNoise(if (tier.ordinal >= RealSky.Tier.MEDIUM.ordinal) 96 else 128)
        tex["tTrans"] = hdr(256, 64).also { tTrans = target(it) }
        tex["tMeter"] = hdr(8, 1, GLES30.GL_NEAREST).also { tMeter = target(it) }
        buildStars()
        lastHaze = -1f; lightKey = ""; landKey = ""; sizedFor = ""
        glReady = true
        Log.i(TAG, "Real sky built: tier ${tier.name}, ${SystemClock.uptimeMillis() - began} ms")
    }

    /** The cloud volume and the flat noise tile, built once on the GPU. */
    private fun buildNoise(n: Int) {
        noiseN = n
        val p = progs.getValue("noise")
        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, t[0])
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGBA8, n, n, n, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        val f = IntArray(1)
        GLES30.glGenFramebuffers(1, f, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, f[0])
        GLES30.glUseProgram(p.id)
        GLES30.glViewport(0, 0, n, n)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(p.id, "uMode"), 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(p.id, "uSize"), n.toFloat())
        val slice = GLES30.glGetUniformLocation(p.id, "uSlice")
        for (z in 0 until n) {
            GLES30.glFramebufferTextureLayer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, t[0], 0, z)
            GLES30.glUniform1f(slice, (z + 0.5f) / n)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
            if (z % 16 == 15) GLES30.glFlush()
        }
        GLES30.glDeleteFramebuffers(1, f, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, t[0])
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_3D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        for (a in intArrayOf(GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_TEXTURE_WRAP_R)) GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, a, GLES30.GL_REPEAT)
        tex["tNoise3D"] = Tex(t[0], n, n, GLES30.GL_TEXTURE_3D)

        val n2 = tex2D(256, 256, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, GLES30.GL_LINEAR, GLES30.GL_REPEAT)
        val f2 = target(n2)
        GLES30.glViewport(0, 0, 256, 256)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(p.id, "uMode"), 1)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(p.id, "uSize"), 256f)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        delete(f2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, n2.id)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        tex["tNoise2D"] = n2
    }

    /** The catalogue's stars as points fixed to the celestial sphere; the shader turns them with the night. */
    private fun buildStars() {
        val stars = runCatching { NightSky.stars(app.resources) }.getOrNull().orEmpty()
        starCount = stars.size
        if (starCount == 0) return
        val fb: FloatBuffer = ByteBuffer.allocateDirect(starCount * 7 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val deg = Math.PI / 180
        for (s in stars) {
            val ra = s.ra * deg; val dec = s.dec * deg
            fb.put((cos(dec) * cos(ra)).toFloat()); fb.put((cos(dec) * sin(ra)).toFloat()); fb.put(sin(dec).toFloat())
            val c = NightSky.colour(s.kelvin)
            fb.put(s.mag); fb.put(((c shr 16) and 255) / 255f); fb.put(((c shr 8) and 255) / 255f); fb.put((c and 255) / 255f)
        }
        fb.position(0)
        val vao = IntArray(1); val vbo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0); GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, starCount * 28, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 28, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, 28, 12)
        GLES30.glBindVertexArray(0)
        starVao = vao[0]
    }

    private fun sizeBuffers(w: Int, h: Int) {
        val key = "$w x $h ${tier.name}"
        if (key == sizedFor) return
        sizedFor = key
        viewW = w; viewH = h
        delete(tSky, tCloud, tScene, tG, tB4, tB8a, tB8b)
        delete(tex["tSky"], tex["tCloudA"], tex["tCloudB"], tex["tScene"], tex["tG0"], tex["tG1"], tex["tG2"], b4, b8a, b8b)
        tex["tSky"] = hdr(ceil(w / 8f).toInt(), ceil(h / 8f).toInt()).also { tSky = target(it) }
        val cw = ceil(w * tier.cloudScale).toInt(); val ch = ceil(h * tier.cloudScale).toInt()
        val ca = hdr(cw, ch); val cb = hdr(cw, ch)
        tex["tCloudA"] = ca; tex["tCloudB"] = cb
        tCloud = target(ca, cb)
        GLES30.glClearColor(0f, 0f, 0f, 1f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        val sw = ceil(w * tier.sceneScale).toInt(); val sh = ceil(h * tier.sceneScale).toInt()
        tex["tScene"] = hdr(sw, sh).also { tScene = target(it) }
        val g0 = ldr(sw, sh, GLES30.GL_NEAREST); val g1 = ldr(sw, sh, GLES30.GL_NEAREST); val g2 = ldr(sw, sh, GLES30.GL_NEAREST)
        tex["tG0"] = g0; tex["tG1"] = g1; tex["tG2"] = g2
        tG = target(g0, g1, g2)
        GLES30.glClearColor(0f, 0f, 0f, 0f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        b4 = hdr(ceil(sw / 4f).toInt(), ceil(sh / 4f).toInt()).also { tB4 = target(it) }
        b8a = hdr(ceil(sw / 8f).toInt(), ceil(sh / 8f).toInt()).also { tB8a = target(it) }
        b8b = hdr(ceil(sw / 8f).toInt(), ceil(sh / 8f).toInt()).also { tB8b = target(it) }
        tex["tBlur"] = b8a!!
        lightKey = ""; landKey = ""; cloudFill = 16; cloudKey = ""
    }

    private fun use(p: Program, t: Target?, extra: Map<String, Any> = emptyMap()) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t?.fbo ?: 0)
        val w = t?.w ?: surfaceW; val h = t?.h ?: surfaceH
        GLES30.glViewport(0, 0, w, h)
        GLES30.glUseProgram(p.id)
        var unit = 0
        for (un in p.uniforms) {
            if (un.type == GLES30.GL_SAMPLER_2D || un.type == GLES30.GL_SAMPLER_3D) {
                val tx = (extra[un.name] as? Tex) ?: tex[un.name] ?: continue
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
                GLES30.glBindTexture(tx.target, tx.id)
                GLES30.glUniform1i(un.loc, unit)
                unit++
                continue
            }
            if (un.name == "uRes") { GLES30.glUniform2f(un.loc, w.toFloat(), h.toFloat()); continue }
            if (un.type == GLES30.GL_INT) { ((extra[un.name] as? Int) ?: ui[un.name])?.let { GLES30.glUniform1i(un.loc, it) }; continue }
            val v = (extra[un.name] as? FloatArray) ?: u[un.name] ?: continue
            when (un.type) {
                GLES30.GL_FLOAT -> GLES30.glUniform1f(un.loc, v[0])
                GLES30.GL_FLOAT_VEC2 -> GLES30.glUniform2f(un.loc, v[0], v[1])
                GLES30.GL_FLOAT_VEC3 -> GLES30.glUniform3f(un.loc, v[0], v[1], v[2])
                GLES30.GL_FLOAT_VEC4 -> GLES30.glUniform4f(un.loc, v[0], v[1], v[2], v[3])
            }
        }
    }
    private fun draw() = GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)

    // ---- A frame -----------------------------------------------------------------------------------------------

    /** Draws what is due; returns true if a new picture went to the screen. */
    private fun frame(f: RealSkyFrame, now: Long): Boolean {
        sizeBuffers(surfaceW, surfaceH)
        f.fill(u, ui, viewW.toFloat(), viewH.toFloat(), (now - epoch) / 1000f)
        u["uCloudRes"] = floatArrayOf(tex.getValue("tCloudA").w.toFloat(), tex.getValue("tCloudA").h.toFloat())
        u["uSteps"] = floatArrayOf(tier.steps)
        ui["uFrame"] = cloudFrame

        if (f.haze != lastHaze) { lastHaze = f.haze; use(progs.getValue("trans"), tTrans); draw(); lightKey = "" }
        val lk = f.lightKey()
        if (lk != lightKey) {
            lightKey = lk
            use(progs.getValue("meter"), tMeter); draw()
            use(progs.getValue("sky"), tSky); draw()
        }

        // The land: once per scenery, season and size, a band of rows per frame so the phone never stalls.
        val lkey = f.landKey() + sizedFor
        if (lkey != landKey) { landKey = lkey; landRow = 0; shown = false; fade = 0f; landBegan = now }
        if (landRow >= 0) {
            val g = tG!!
            val name = "g_" + f.biome.id
            val p = progs.getOrPut(name) { program(name, listOf("common.glsl", "land.glsl", "land_${f.biome.id}.glsl", "gbuffer.frag")) }
            if (landRow == 0) { GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, g.fbo); GLES30.glClearColor(0f, 0f, 0f, 0f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT) }
            val band = max(24, (g.h * tier.landBand).toInt())
            // Rows are counted from the bottom in GL: the foreground first.
            use(p, g, mapOf("uTile" to floatArrayOf(landRow.toFloat(), (landRow + band).toFloat(), 0f, 0f)))
            draw()
            GLES30.glFinish()
            landRow += band
            if (landRow >= g.h) { landRow = -1; Log.i(TAG, "Land drawn: ${f.biome.id}, ${SystemClock.uptimeMillis() - landBegan} ms") }
        }

        // The clouds: one pixel in sixteen per update; all sixteen updates at once when the weather has jumped.
        val ck = f.cloudKey()
        if (ck != cloudKey) { cloudKey = ck; cloudFill = 16 }
        val scrubbing = f.busy
        val cloudDue = now - lastCloud >= (if (scrubbing || cloudFill > 0) 0 else tier.cloudEveryMs)
        if (cloudDue) {
            val n = if (!shown) min(cloudFill, 4) else 1
            for (i in 0 until max(1, n)) {
                ui["uFrame"] = cloudFrame
                use(progs.getValue("cloud"), tCloud, mapOf("uAmort" to 16)); draw()
                cloudFrame++
                if (cloudFill > 0) cloudFill--
            }
            lastCloud = now
        }

        val ready = landRow < 0 && cloudFill <= 0
        if (!ready && !shown) return false
        val sceneDue = scrubbing || !shown || fade < 1f || now - lastScene >= f.biome.sceneEveryMs(tier)
        if (!sceneDue) return false
        lastScene = now
        if (!shown) { shown = true }
        fade = if (f.fade < 1f) f.fade else min(1f, fade + 0.12f)
        u["uFade"] = floatArrayOf(fade)

        use(progs.getValue("scene"), tScene); draw()
        if (f.night > 0.05f && starCount > 0) {
            use(progs.getValue("stars"), tScene)
            GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
            GLES30.glBindVertexArray(starVao)
            GLES30.glDrawArrays(GLES30.GL_POINTS, 0, starCount)
            GLES30.glBindVertexArray(0)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
        val scene = tex.getValue("tScene")
        val blur = progs.getValue("blur")
        use(blur, tB4, mapOf("tSrc" to scene, "uMode" to 0, "uDir" to floatArrayOf(1f / scene.w, 1f / scene.h))); draw()
        use(blur, tB8a, mapOf("tSrc" to b4!!, "uMode" to 0, "uDir" to floatArrayOf(1f / b4!!.w, 1f / b4!!.h))); draw()
        use(blur, tB8b, mapOf("tSrc" to b8a!!, "uMode" to 1, "uDir" to floatArrayOf(1.6f / b8a!!.w, 0f))); draw()
        use(blur, tB8a, mapOf("tSrc" to b8b!!, "uMode" to 1, "uDir" to floatArrayOf(0f, 1.6f / b8a!!.h))); draw()
        use(progs.getValue("final"), null); draw()
        return true
    }

    override fun run() {
        var failed = false
        try {
            initEgl()
        } catch (e: Throwable) {
            Log.w(TAG, "EGL unavailable", e)
            RealSky.fail(app, "egl: ${e.message}")
            return
        }
        while (running) {
            val f: RealSkyFrame?
            synchronized(lock) {
                if (surfaceChanged) { surfaceChanged = false; runCatching { bindSurface() }; lock.notifyAll() }
                while (running && !surfaceChanged && (surface == null || !visible || pending.get() == null || eglSurface == EGL14.EGL_NO_SURFACE)) runCatching { lock.wait(500) }
                f = pending.get()
            }
            if (!running) break
            if (surfaceChanged || f == null || failed) continue
            try {
                if (!glReady || (!firstShown && !attempting)) { attempting = true; RealSky.beginAttempt(app) }
                if (!glReady) initGl()
                val t0 = SystemClock.uptimeMillis()
                if (frame(f, t0)) {
                    // Now and then, how long a picture really takes (the GPU finished): a phone that cannot keep up
                    // steps down a tier by itself.
                    val measure = timedFrames++ % 20 == 5
                    if (measure) GLES30.glFinish()
                    EGL14.eglSwapBuffers(display, eglSurface)
                    if (measure && shown && fade >= 1f) {
                        val ms = SystemClock.uptimeMillis() - t0
                        slowFrames = if (ms > 40) slowFrames + 1 else max(0, slowFrames - 1)
                        if (slowFrames >= 4 && RealSky.stepDown(app, tier)) { slowFrames = 0; tier = RealSky.tierFor(app, rendererId); sizedFor = "" }
                    }
                    if (!firstShown && fade >= 1f) { firstShown = true; attempting = false; RealSky.succeeded(app); onFirstFrame?.invoke() }
                    if (fade >= 1f && shownLogged != landKey) { shownLogged = landKey; Log.i(TAG, "Real sky shown: ${f.biome.id}") }
                    lastSwap = SystemClock.uptimeMillis()
                }
                // Pace: as fast as the screen while something is being dragged or built, otherwise at the tier's rate.
                val wait = if (f.busy || !shown || fade < 1f) 4L else tier.frameMs - (SystemClock.uptimeMillis() - t0)
                if (wait > 0) synchronized(lock) { runCatching { lock.wait(wait) } }
            } catch (e: Throwable) {
                Log.w(TAG, "Real sky failed; the 0.9 sky takes over", e)
                failed = true
                running = false
                RealSky.fail(app, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    @Volatile private var firstShown = false
    @Volatile private var attempting = false
    private var shownLogged = ""
    private var landBegan = 0L
    private val epoch = SystemClock.uptimeMillis()

    companion object {
        private const val TAG = "NoorRealSky"
        private const val HEAD = "#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler3D;\n"
    }
}
