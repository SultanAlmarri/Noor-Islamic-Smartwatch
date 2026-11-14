package com.noor.app.sky

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.os.SystemClock
import android.util.Log
import com.noor.app.ui.today.NightSky
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The real sky (0.11): the home screen's own renderer (OpenGL ES 3.0).
 *
 * The camera never moves. So the land of each scenery, and each kind of cloud, was rendered once by a path tracer on
 * a workstation (tools/scenery), through this camera, under a set of Suns: a few small pictures each. Here they are
 * lit with the Sun of the moment and put together with what must be live:
 *
 * - the light of the moment is metered and the clear sky computed for the camera's own directions, at a small size,
 *   whenever the Sun moves (meter.frag, sky.frag);
 * - the land's and each cloud set's shading under the Suns the moment lies between is mixed (lit.frag), and each
 *   layer of cloud given the colours of the moment (layer.frag);
 * - the picture is put together (scene.frag: sky, Sun, Moon, stars, three layers of cloud sliding with the wind, the
 *   land, live water), softened for glow and for the glass under the day's times (blur.frag) and sent to the screen
 *   through a filmic curve (final.frag).
 *
 * The pictures are read from the app's assets as they are needed, on a thread of their own, and forgotten when they
 * have not been used for a while: only the few that the moment lies between are in memory. The shader files live in
 * assets/sky2 and are the very files the design bench (tools/sky3d) runs in a browser, pass for pass. One engine
 * serves the whole app; it keeps its GL context while the home tab is away. Anything that goes wrong (no ES 3.0, no
 * float render targets, a shader the driver rejects) is reported through [RealSky.fail] and the 0.9 sky takes over.
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

    fun wake() { synchronized(lock) { lock.notifyAll() } }

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

    /** Forgets everything built in a context that is gone; the next frame builds it again. */
    private fun releaseContext() {
        runCatching {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (pbuffer != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, pbuffer)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        }
        eglSurface = EGL14.EGL_NO_SURFACE; pbuffer = EGL14.EGL_NO_SURFACE; context = EGL14.EGL_NO_CONTEXT
        glReady = false
        progs.clear(); tex.clear()
        tTrans = null; tMeter = null; tSky = null; tScene = null; tB4 = null; tB8a = null; tB8b = null
        b4 = null; b8a = null; b8b = null; zero = null
        baked.clear(); loaded.clear(); slots.forEach { it.cur = null; it.prev = null; it.out = null; it.outT = null }
        sizedFor = ""; landKey = ""; lightKey = ""; starVao = 0; starCount = 0; stillDrawn = null
    }

    private fun bindSurface() {
        stillDrawn = null       // a new surface is empty: whatever was on the old one is drawn again
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
    private fun src(name: String) = sources.getOrPut(name) { app.assets.open("sky2/$name").bufferedReader().use { it.readText() } }

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

    /** Builds a program from parts of assets/sky2; its compiled form is kept on disk, so later starts are quick. */
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

    private var tTrans: Target? = null; private var tMeter: Target? = null; private var tSky: Target? = null
    private var tScene: Target? = null
    private var tB4: Target? = null; private var tB8a: Target? = null; private var tB8b: Target? = null
    private var b4: Tex? = null; private var b8a: Tex? = null; private var b8b: Tex? = null
    private var zero: Tex? = null
    private var viewW = 0; private var viewH = 0
    private var sizedFor = ""
    private var starVao = 0; private var starCount = 0

    private var lastHaze = -1f
    private var lightKey = ""
    private var landKey = ""
    private var stagesLogged = 0
    private var stillDrawn: RealSkyFrame? = null   // with animations off: the moment that is on the screen now
    private var shown = false
    private var lastScene = 0L
    private var lastFrame = 0L
    private var lastTidy = 0L
    private var lastSwap = 0L
    private var slowFrames = 0
    private var busyNow = false
    private var timedFrames = 0
    private var fade = 0f
    private var ramp = 0f

    private fun initGl() {
        val began = SystemClock.uptimeMillis()
        val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        rendererId = (GLES30.glGetString(GLES30.GL_RENDERER) ?: "") + "|" + (GLES30.glGetString(GLES30.GL_VERSION) ?: "")
        Log.i(TAG, "GL: $rendererId")
        check(ext.contains("EXT_color_buffer_half_float") || ext.contains("EXT_color_buffer_float")) { "no float render targets" }
        // A GPU that is really the CPU (emulators, a phone without a working graphics driver) is slow at any picture
        // this size: left to choose, Noor draws the 0.9 sky there. (Naming a quality in Settings still asks for this
        // one, which is how the emulator test photographs it.)
        check(RealSky.graphics != com.noor.app.data.SkyGraphics.AUTO || !Regex("swiftshader|llvmpipe|softpipe|software").containsMatchIn(rendererId.lowercase())) { "software GPU" }
        binaryDir = File(app.cacheDir, "sky-programs").apply { mkdirs() }
        tier = RealSky.tierFor(app, rendererId)
        val common = listOf("common.glsl")
        val air = listOf("common.glsl", "atmosphere.glsl")
        progs["noise"] = program("noise", listOf("noise.frag"))
        progs["trans"] = program("trans", air + "trans.frag")
        progs["meter"] = program("meter", air + "meter.frag")
        progs["sky"] = program("sky", air + "sky.frag")
        progs["lit"] = program("lit", common + "lit.frag")
        progs["layer"] = program("layer", air + "layer.frag")
        progs["scene"] = program("scene", air + "scene.frag")
        progs["blur"] = program("blur", common + "blur.frag")
        progs["final"] = program("final", air + "final.frag")
        progs["stars"] = program("stars", listOf("stars.frag"), vertex = "stars.vert")
        buildNoise()
        zero = ldr(2, 2).also { val t = target(it); GLES30.glClearColor(0f, 0f, 0f, 0f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT); delete(t) }
        tex["tTrans"] = hdr(256, 64).also { tTrans = target(it) }
        tex["tMeter"] = hdr(10, 1, GLES30.GL_NEAREST).also { tMeter = target(it) }
        buildStars()
        lastHaze = -1f; lightKey = ""; landKey = ""; sizedFor = ""
        glReady = true
        Log.i(TAG, "Real sky built: tier ${tier.name}, ${SystemClock.uptimeMillis() - began} ms")
    }

    /** The flat noise tile (the night sky's grain, the water's ripples), built once on the GPU. */
    private fun buildNoise() {
        val p = progs.getValue("noise")
        val n2 = tex2D(256, 256, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, GLES30.GL_LINEAR, GLES30.GL_REPEAT)
        val f2 = target(n2)
        GLES30.glUseProgram(p.id)
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
        delete(tSky, tScene, tB4, tB8a, tB8b)
        delete(tex["tSky"], tex["tScene"], b4, b8a, b8b)
        tex["tSky"] = hdr(ceil(w / 8f).toInt(), ceil(h / 8f).toInt()).also { tSky = target(it) }
        // On a very fine screen (QHD and beyond) the scene is drawn as for a 2400-pixel-high one and enlarged: the eye
        // cannot tell, and the GPU draws a third fewer pixels.
        val fine = min(1f, 2400f / max(h, 1))
        val sw = ceil(w * tier.sceneScale * fine).toInt(); val sh = ceil(h * tier.sceneScale * fine).toInt()
        tex["tScene"] = hdr(sw, sh).also { tScene = target(it) }
        b4 = hdr(ceil(sw / 4f).toInt(), ceil(sh / 4f).toInt()).also { tB4 = target(it) }
        b8a = hdr(ceil(sw / 8f).toInt(), ceil(sh / 8f).toInt()).also { tB8a = target(it) }
        b8b = hdr(ceil(sw / 8f).toInt(), ceil(sh / 8f).toInt()).also { tB8b = target(it) }
        tex["tBlur"] = b8a!!
        lightKey = ""
    }

    private fun use(p: Program, t: Target?, extra: Map<String, Any> = emptyMap()) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t?.fbo ?: 0)
        val w = t?.w ?: surfaceW; val h = t?.h ?: surfaceH
        GLES30.glViewport(0, 0, w, h)
        GLES30.glUseProgram(p.id)
        var unit = 0
        for (un in p.uniforms) {
            if (un.type == GLES30.GL_SAMPLER_2D || un.type == GLES30.GL_SAMPLER_3D) {
                val tx = (extra[un.name] as? Tex) ?: tex[un.name] ?: zero ?: continue
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

    // ---- The baked pictures ------------------------------------------------------------------------------------
    //
    // A scenery's land (assets/sky2/land/<id>) and a set of clouds (assets/sky2/clouds/<id>) are each a folder of
    // small pictures and a description. A picture is read and decoded on the loader's thread when it is first asked
    // for, and handed to this thread to become a texture; until then whoever asked goes without it.

    private class Baked(val dir: String, val land: Boolean, val meta: org.json.JSONObject) {
        val tex = HashMap<String, Tex>()
        val asked = HashSet<String>()
        val missing = HashSet<String>()
        val used = HashMap<String, Long>()
        var lit: Tex? = null; var litT: Target? = null; var litKey = ""
        var lastUsed = 0L
        val water: Float get() = if (meta.isNull("water")) 0f else meta.optDouble("water", 0.0).toFloat()
        val mask: Boolean get() = meta.optBoolean("mask", false)
        fun num(name: String, or: Double = 0.0) = meta.optDouble(name, or).toFloat()
    }
    private class Loaded(val set: Baked, val name: String, val w: Int, val h: Int, val grey: Boolean, val nearest: Boolean, val pixels: ByteBuffer?)
    private class Slot { var cur: Baked? = null; var prev: Baked? = null; var fade = 1f; var amount = 0f; var out: Tex? = null; var outT: Target? = null }

    private val baked = HashMap<String, Baked?>()
    private val loaded = ConcurrentLinkedQueue<Loaded>()
    private val slots = Array(3) { Slot() }
    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "NoorSkyPictures").apply { priority = Thread.NORM_PRIORITY - 2; isDaemon = true } }

    private fun bakedSet(dir: String, land: Boolean): Baked? = baked.getOrPut(dir) {
        runCatching {
            val json = app.assets.open("sky2/$dir/" + (if (land) "land.json" else "sky.json")).bufferedReader().use { it.readText() }
            Baked(dir, land, org.json.JSONObject(json))
        }.onFailure { Log.i(TAG, "No pictures for $dir") }.getOrNull()
    }

    /** A picture of a set as a texture, or null while it is being read (or if the set has none of that name). */
    private fun want(b: Baked, name: String, now: Long): Tex? {
        b.tex[name]?.let { b.used[name] = now; return it }
        if (name in b.missing || !b.asked.add(name)) return null
        val colour = b.land && (name == "a" || name == "k" || name == "g" || name == "m")
        val file = when {
            name == "g" && b.land -> "g.png"
            name.length == 1 -> "$name.webp"
            else -> "s_$name.webp"
        }
        // Coarser pictures on phones with little to spare (never the distances, which are small already).
        val sample = if (b.land) (if (name == "g") 1 else tier.landSample) else tier.cloudSample
        loader.execute {
            val out = runCatching {
                val opts = BitmapFactory.Options().apply { inPremultiplied = false; inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val bmp = app.assets.open("sky2/${b.dir}/$file").use { BitmapFactory.decodeStream(it, null, opts) } ?: error("unreadable")
                val w = bmp.width; val h = bmp.height
                val rgba = ByteBuffer.allocateDirect(w * h * 4)
                bmp.copyPixelsToBuffer(rgba)
                bmp.recycle()
                if (colour) { rgba.position(0); Loaded(b, name, w, h, false, name == "g", rgba) }
                else {
                    val all = ByteArray(w * h * 4); rgba.position(0); rgba.get(all)
                    val one = ByteArray(w * h)
                    for (i in one.indices) one[i] = all[i * 4]
                    Loaded(b, name, w, h, true, false, ByteBuffer.allocateDirect(w * h).put(one).also { it.position(0) })
                }
            }.getOrElse { Loaded(b, name, 0, 0, true, false, null) }
            loaded.add(out)
            wake()
        }
        return null
    }

    /** Pictures the loader has finished become textures. */
    private fun drain() {
        while (true) {
            val l = loaded.poll() ?: break
            if (baked[l.set.dir] !== l.set) continue                      // of a context that is gone
            if (l.pixels == null) { l.set.missing += l.name; continue }
            val t = IntArray(1)
            GLES30.glGenTextures(1, t, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
            if (l.grey) GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, l.w, l.h, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, l.pixels)
            else GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, l.w, l.h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, l.pixels)
            val filter = if (l.nearest) GLES30.GL_NEAREST else GLES30.GL_LINEAR
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            l.set.tex[l.name] = Tex(t[0], l.w, l.h)
        }
    }

    private fun known(b: Baked, name: String, now: Long): Boolean = want(b, name, now) != null || name in b.missing

    /**
     * Mixes a set's shading under the light of the moment into its `lit` picture (lit.frag). True when every picture
     * the moment asks for was at hand; until then the mix is made of those that are (so a dragged Sun never waits).
     */
    private fun light(b: Baked, lw: LightWeights, mirror: Boolean, now: Long): Boolean {
        val t = Array(5) { if (lw.w[it] > 0.004f) want(b, lw.names[it], now) else null }
        val g0 = if (lw.glow[0] > 0.004f) want(b, "gl", now) else null
        val g1 = if (lw.glow[1] > 0.004f) want(b, "gr", now) else null
        var all = true; var have = 0f; var total = 0f
        for (i in 0 until 5) {
            total += lw.w[i]
            if (t[i] != null) have += lw.w[i] else if (lw.w[i] > 0.004f && lw.names[i] !in b.missing) all = false
        }
        if (lw.glow[0] > 0.004f && g0 == null && "gl" !in b.missing) all = false
        if (lw.glow[1] > 0.004f && g1 == null && "gr" !in b.missing) all = false
        val first = t.firstOrNull { it != null } ?: g0 ?: g1 ?: return all && b.lit != null
        if (have <= 1e-4f && total > 1e-3f && b.lit != null) return false      // none of the Suns yet: the last mix stays
        val k = if (have > 1e-4f) total / have else 0f
        val w = FloatArray(5) { if (t[it] != null) lw.w[it] * k else 0f }
        val key = lw.names.joinToString() + w.joinToString { (it * 400).toInt().toString() } + " ${(lw.glow[0] * 200).toInt()} ${(lw.glow[1] * 200).toInt()} ${g0 != null} ${g1 != null} $mirror"
        if (key == b.litKey && b.lit != null) return all
        if (b.lit == null || b.lit!!.w != first.w || b.lit!!.h != first.h) {
            delete(b.litT); delete(b.lit)
            b.lit = tex2D(first.w, first.h, GLES30.GL_RG8, GLES30.GL_RG, GLES30.GL_UNSIGNED_BYTE, GLES30.GL_LINEAR, GLES30.GL_CLAMP_TO_EDGE).also { b.litT = target(it) }
        }
        val z = zero!!
        use(progs.getValue("lit"), b.litT, mapOf(
            "tS0" to (t[0] ?: z), "tS1" to (t[1] ?: z), "tS2" to (t[2] ?: z), "tS3" to (t[3] ?: z), "tS4" to (t[4] ?: z), "tG0" to (g0 ?: z), "tG1" to (g1 ?: z),
            "uLitW" to floatArrayOf(w[0], w[1], w[2], w[3]),
            "uLitV" to floatArrayOf(w[4], if (g0 != null) lw.glow[0] else 0f, if (g1 != null) lw.glow[1] else 0f, if (mirror) 1f else 0f),
        ))
        draw()
        b.litKey = key
        return all
    }

    /** A cloud set is ready to be shown when its own four pictures and its shading for this moment are at hand. */
    private fun setReady(b: Baked, lw: LightWeights, mirror: Boolean, now: Long): Boolean {
        var ok = true
        for (n in arrayOf("a", "f", "k", "g")) if (!known(b, n, now)) ok = false
        if (!light(b, lw, mirror, now)) ok = false
        return ok && b.tex["a"] != null
    }

    private fun drawSet(b: Baked, slot: Slot, layer: Int, share: Float, flip: Boolean) {
        val m = b.meta
        use(progs.getValue("layer"), slot.outT, mapOf(
            "tA" to (b.tex["a"] ?: zero!!), "tF" to (b.tex["f"] ?: zero!!), "tK" to (b.tex["k"] ?: zero!!), "tG" to (b.tex["g"] ?: zero!!), "tLit" to (b.lit ?: zero!!),
            "uLay" to floatArrayOf(b.num("rMax", 4.0), layer.toFloat(), share, if (flip) 1f else 0f),
            "uLayFrame" to floatArrayOf(b.num("viewWidth", 1080.0), b.num("horizon", 1056.0), b.num("width", 1350.0), b.num("height", 1080.0)),
        ))
        draw()
    }

    /**
     * One layer of cloud (0 high, 1 middle, 2 low): the set the weather asks for comes in over the one before, each
     * lit for the moment. Returns false only while the first set of a fresh start is still being read.
     */
    private fun layer(i: Int, id: String?, amount: Float, f: RealSkyFrame, now: Long, dt: Float, instant: Boolean): Boolean {
        val slot = slots[i]
        val wanted = id?.let { bakedSet("clouds/$it", false) }
        var waiting = false
        if (wanted != null && wanted !== slot.cur) {
            if (setReady(wanted, f.cloudLight, f.flip, now)) {
                slot.prev = if (slot.amount > 0.01f) slot.cur else null
                slot.cur = wanted
                slot.fade = if (instant || slot.prev == null) 1f else 0f
                if (slot.prev == null && !instant) slot.amount = 0f
            } else waiting = slot.cur == null
        }
        val goal = if (wanted == null) 0f else amount
        slot.amount = if (instant) goal else slot.amount + (goal - slot.amount) * min(1f, dt / 3f)
        slot.fade = min(1f, slot.fade + dt / 7f)
        if (slot.fade >= 1f) slot.prev = null
        val cur = slot.cur
        if (cur == null || slot.amount < 0.004f) {
            tex["tCloud$i"] = zero!!
            u["uCloudF$i"] = floatArrayOf(1080f, 1056f, 1350f, 1080f)
            return !waiting
        }
        cur.lastUsed = now; slot.prev?.lastUsed = now
        light(cur, f.cloudLight, f.flip, now)
        slot.prev?.let { light(it, f.cloudLight, f.flip, now) }
        val a = cur.tex["a"] ?: return !waiting
        if (slot.out == null || slot.out!!.w != a.w || slot.out!!.h != a.h) {
            delete(slot.outT); delete(slot.out)
            slot.out = hdr(a.w, a.h).also { slot.outT = target(it) }
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, slot.outT!!.fbo)
        GLES30.glClearColor(0f, 0f, 0f, 0f); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        slot.prev?.let { if (it.tex["a"] != null) drawSet(it, slot, i, (1f - slot.fade) * slot.amount, f.flip) }
        drawSet(cur, slot, i, slot.fade * slot.amount, f.flip)
        GLES30.glDisable(GLES30.GL_BLEND)
        tex["tCloud$i"] = slot.out!!
        val vw = cur.num("viewWidth", 1080.0); val w = cur.num("width", 1350.0)
        u["uCloudF$i"] = floatArrayOf(vw, cur.num("horizon", 1056.0), w, cur.num("height", 1080.0))
        slides[i] = f.slide(i, (now - epoch) / 1000f, travelNow) * 0.5f * (w - vw)
        coverNow = 1f - (1f - coverNow) * (1f - cur.num("cover", 0.3) * slot.amount)
        return !waiting
    }
    private val slides = FloatArray(3)
    private var coverNow = 0f
    private var travelNow = 0f

    /** Forgets pictures that have not been used for a while: only what the moment needs stays in memory. */
    private fun tidy(now: Long) {
        if (now - lastTidy < 5000) return
        lastTidy = now
        val live = slots.flatMap { listOfNotNull(it.cur, it.prev) }.toSet()
        val it = baked.entries.iterator()
        while (it.hasNext()) {
            val b = it.next().value ?: continue
            if (b.land) {
                if (b.dir != "land/$landKey" && now - b.lastUsed > 20_000) { free(b); it.remove() }
                continue
            }
            if (b !in live && now - b.lastUsed > 30_000) { free(b); it.remove(); continue }
            // Of a set in use, the shading under Suns the day has left behind.
            val old = b.used.filter { (n, at) -> n.length > 1 && now - at > 25_000 }.keys
            if (b.tex.size - old.size >= 6) for (n in old) { delete(b.tex.remove(n)); b.used.remove(n); b.asked.remove(n) }
        }
    }

    private fun free(b: Baked) {
        b.tex.values.forEach { delete(it) }
        b.tex.clear(); b.asked.clear(); b.used.clear()
        delete(b.litT); delete(b.lit); b.lit = null; b.litT = null; b.litKey = ""
    }

    // ---- A frame -----------------------------------------------------------------------------------------------

    /** Draws what is due; returns true if a new picture went to the screen. */
    private fun frame(f: RealSkyFrame, now: Long): Boolean {
        sizeBuffers(surfaceW, surfaceH)
        drain()
        // A touch's breath of wind: strong at once, gone in a few seconds; the clouds keep the ground it gave them.
        val gusts = RealSky.gusts
        val age = (now - RealSky.gustAt) / 1000f
        val gust = if (gusts > 0 && age < 8f && !f.still) kotlin.math.exp(-age / 1.4f) else 0f
        travelNow = if (gusts > 0 && !f.still) 0.06f * (gusts - 1) + 0.06f * (1f - kotlin.math.exp(-age / 1.4f)) else 0f
        busyNow = f.busy || gust > 0.02f
        val dt = if (lastFrame == 0L) 0f else min(1f, (now - lastFrame) / 1000f)
        lastFrame = now
        f.fill(u, ui, viewW.toFloat(), viewH.toFloat(), (now - epoch) / 1000f, gust, travelNow)

        if (f.haze != lastHaze) { lastHaze = f.haze; use(progs.getValue("trans"), tTrans); draw(); lightKey = "" }
        val lk = f.lightKey()
        if (lk != lightKey) {
            lightKey = lk
            use(progs.getValue("meter"), tMeter); draw()
            use(progs.getValue("sky"), tSky); draw()
        }

        // The land of this scenery, in the light of the moment.
        if (f.landKey() != landKey) { landKey = f.landKey(); shown = false; fade = 0f; ramp = 0f; landBegan = now }
        val land = bakedSet("land/${f.biome.id}", true)
        var ready = true
        var water = false
        if (land != null) {
            land.lastUsed = now
            for (n in arrayOf("a", "k", "g")) if (!known(land, n, now)) ready = false
            if (land.mask && !known(land, "m", now)) ready = false
            if (!light(land, f.landLight, false, now)) ready = false
            water = land.water > 0f
            u["uLandFrame"] = floatArrayOf(land.num("width", 1080.0), land.num("horizon", 1056.0), land.num("top"), land.num("height", 1620.0))
            u["uLandInfo"] = floatArrayOf(if (land.tex["a"] != null && land.lit != null) 1f else 0f, land.water, land.num("sMax", 2.0), if (land.tex["m"] != null) 1f else 0f)
            tex["tLandA"] = land.tex["a"] ?: zero!!; tex["tLandK"] = land.tex["k"] ?: zero!!; tex["tLandG"] = land.tex["g"] ?: zero!!
            tex["tLandM"] = land.tex["m"] ?: zero!!; tex["tLandLit"] = land.lit ?: zero!!
            // The camera stands where the land was rendered from.
            land.meta.optJSONArray("eye")?.let { e -> u["uCam"] = floatArrayOf(f.focalPx, f.horizon, e.optDouble(2, 2.0).toFloat(), if (f.flip) 1f else 0f) }
        } else {
            u["uLandFrame"] = floatArrayOf(1080f, 1056f, 0f, 1f); u["uLandInfo"] = floatArrayOf(0f, 0f, 2f, 0f)
            for (n in arrayOf("tLandA", "tLandK", "tLandG", "tLandM", "tLandLit")) tex[n] = zero!!
        }

        // The clouds: three layers, each a set in the light of the moment.
        val instant = !shown || f.still || f.busy
        coverNow = 0f
        val s = f.state
        if (!layer(0, s.highSet, s.highAmount, f, now, dt, instant)) ready = false
        if (!layer(1, s.midSet, s.midAmount, f, now, dt, instant)) ready = false
        if (!layer(2, s.lowSet, s.lowAmount, f, now, dt, instant)) ready = false
        u["uCloudD"] = floatArrayOf(slides[0], slides[1], slides[2], if (slots.any { it.cur != null && it.amount >= 0.004f }) 1f else 0f)
        u["uCover"] = floatArrayOf(coverNow)
        tidy(now)

        if (!ready && !shown) return false
        val sceneDue = busyNow || !shown || fade < 1f || now - lastScene >= (if (water) tier.waterMs else tier.stillMs)
        if (!sceneDue) return false
        lastScene = now
        if (!shown) { shown = true; Log.i(TAG, "Pictures read: ${f.biome.id}, ${now - landBegan} ms") }
        // It comes in out of the page's colour: with the opening when there is one, otherwise over a few frames.
        ramp = if (f.still) 1f else min(1f, ramp + 0.10f)
        fade = min(f.fade, ramp)
        u["uFade"] = floatArrayOf(fade)

        val timed = stagesLogged < 3
        val t1 = SystemClock.uptimeMillis()
        use(progs.getValue("scene"), tScene); draw()
        if (timed) GLES30.glFinish()
        val t2 = SystemClock.uptimeMillis()
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
        if (timed) {
            GLES30.glFinish()
            stagesLogged++
            // Anything the driver objected to on the way to this picture, for whoever reads the log of a phone on
            // which it does not look right.
            var err = GLES30.glGetError(); var n = 0
            while (err != GLES30.GL_NO_ERROR && n++ < 8) { Log.w(TAG, "GL error 0x${err.toString(16)} by picture $stagesLogged"); err = GLES30.glGetError() }
            Log.i(TAG, "Picture: scene ${t2 - t1} ms, finish ${SystemClock.uptimeMillis() - t2} ms (${tex.getValue("tScene").w}x${tex.getValue("tScene").h}, tier ${tier.name})")
        }
        // (With animations off a moment is drawn once; but not before all its pictures have been read.)
        if (f.still && fade >= 1f && ready) stillDrawn = f
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
                if (f.still && f === stillDrawn && sizedFor == "$surfaceW x $surfaceH ${tier.name}") {
                    synchronized(lock) { runCatching { lock.wait(250) } }
                    continue
                }
                val t0 = SystemClock.uptimeMillis()
                if (frame(f, t0)) {
                    // Now and then, how long a picture really takes (the GPU finished): a phone that cannot keep up
                    // steps down a tier by itself.
                    val measure = timedFrames++ % 20 == 5
                    if (measure) GLES30.glFinish()
                    if (!EGL14.eglSwapBuffers(display, eglSurface)) {
                        // The surface went away under us (the view was removed), or the system took the context back
                        // (a phone short of memory): bind again, rebuilding everything if the context is gone.
                        val err = EGL14.eglGetError()
                        Log.w(TAG, "swap failed: 0x${err.toString(16)}")
                        if (err == EGL14.EGL_CONTEXT_LOST) { releaseContext(); initEgl() }
                        synchronized(lock) { surfaceChanged = true }
                        continue
                    }
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
                val wait = if (busyNow || !shown || fade < 1f) 4L else tier.frameMs - (SystemClock.uptimeMillis() - t0)
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
        // High precision everywhere: phone GPUs default 2D textures to low precision, which would crush linear light.
        private const val HEAD = "#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\nprecision highp sampler3D;\n"
    }
}
