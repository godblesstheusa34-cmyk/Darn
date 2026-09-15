package dev.darn.spatiallauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.math.*

/** The launcher scene. All scene geometry is drawn in one hardware accelerated layer. */
class SpatialLauncherView(
    context: Context,
    private val repository: AppRepository,
    private val preferences: LauncherPreferences,
    private val settings: () -> Unit
) : FrameLayout(context) {
    private val scene = Scene(context)
    private val search = EditText(context).apply {
        hint = "Search installed apps"
        textSize = 18f
        setTextColor(Color.WHITE)
        setHintTextColor(0xffa8b6cc.toInt())
        setSingleLine()
        setBackgroundColor(0xe0142032.toInt())
        setPadding(36, 0, 24, 0)
        visibility = GONE
        elevation = dp(12).toFloat()
        addTextChangedListener(SimpleTextWatcher { scene.filter = it; scene.drawerScroll = 0f; scene.invalidate() })
    }

    init {
        setWillNotDraw(false)
        addView(scene, LayoutParams(-1, -1))
        addView(search, LayoutParams(-1, dp(58)))
        setOnApplyWindowInsetsListener { _, inset ->
            val bars = inset.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            search.layoutParams = (search.layoutParams as LayoutParams).apply {
                setMargins(dp(18), bars.top + dp(12), dp(18), 0)
            }
            scene.safeTop = bars.top
            scene.safeBottom = bars.bottom
            inset
        }
        reloadApps()
    }

    fun reloadApps() { scene.apps = repository.load(); scene.resolveSlots(); scene.invalidate() }
    fun refreshVisuals() = scene.invalidate()
    fun goHome() { scene.closeDrawer(); scene.page = 0; scene.invalidate() }
    fun closeOverlay(): Boolean = if (scene.drawer) { scene.closeDrawer(); true } else false
    private fun openDrawer() { search.visibility = VISIBLE; scene.openDrawer() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private inner class Scene(context: Context) : View(context) {
        var apps: List<AppEntry> = emptyList()
        var filter = ""
        var drawer = false
        var drawerScroll = 0f
        var page = 0
        var safeTop = 0
        var safeBottom = 0
        private var dragX = 0f
        private var drawerReveal = 0f
        private var downX = 0f
        private var downY = 0f
        private var lastY = 0f
        private var downTime = 0L
        private var moved = false
        private var rippleX = 0f
        private var rippleY = 0f
        private var ripple = 0f
        private var animator: ValueAnimator? = null
        private val slots = preferences.homeSlots()
        private var resolved = listOf<AppEntry?>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }

        init { setLayerType(LAYER_TYPE_HARDWARE, null); isFocusable = true }
        fun resolveSlots() { resolved = slots.map { component -> apps.firstOrNull { it.component == component } } }

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            c.drawColor(0x35000610)
            paint.shader = RadialGradient(width * .18f, safeTop + height * .12f, width * 1.15f,
                intArrayOf(0x553cc8ff, 0x33102e50, 0x88100620.toInt()), null, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            if (drawer) drawDrawer(c) else drawHome(c)
            drawRipple(c)
        }

        private fun drawHome(c: Canvas) {
            for (p in max(0, page - 1)..min(2, page + 1)) drawPage(c, p, p - page + dragX / width.coerceAtLeast(1))
            drawDock(c)
            drawPageDots(c)
        }

        private fun drawPage(c: Canvas, p: Int, relative: Float) {
            val depth = preferences.depth.coerceIn(.05f, 1f)
            val centerX = width / 2f
            val centerY = (safeTop + height - safeBottom) / 2f
            val travel = relative * width * .93f
            val angle = -relative.coerceIn(-1.2f, 1.2f) * 38f * depth
            val scale = 1f - min(.12f, abs(relative) * .10f * depth)
            val matrix = Matrix()
            Camera().apply {
                save()
                setLocation(0f, 0f, -8f * resources.displayMetrics.density)
                rotateY(angle)
                getMatrix(matrix)
                restore()
            }
            matrix.preTranslate(-centerX, -centerY)
            matrix.postTranslate(centerX + travel, centerY)
            c.save()
            c.concat(matrix)
            c.scale(scale, scale, centerX, centerY)

            val card = RectF(dp(18f), safeTop + dp(64f), width - dp(18f), height - safeBottom - dp(168f))
            // A visible floor behind the icons makes their virtual depth readable even while stationary.
            paint.shader = LinearGradient(card.left, card.top, card.right, card.bottom,
                intArrayOf(0xf0203047.toInt(), 0xe0142032.toInt(), 0xf009101d.toInt()), null, Shader.TileMode.CLAMP)
            paint.setShadowLayer(dp(28f), -relative * dp(20f), dp(18f), 0xcc000000.toInt())
            c.drawRoundRect(card, dp(34f), dp(34f), paint)
            paint.clearShadowLayer(); paint.shader = null
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(1.2f); paint.color = 0x8878dcff.toInt()
            c.drawRoundRect(card, dp(34f), dp(34f), paint); paint.style = Paint.Style.FILL
            drawFloorGrid(c, card)

            text.textSize = dp(24f); text.typeface = Typeface.create("sans", Typeface.BOLD); text.textAlign = Paint.Align.LEFT
            c.drawText(listOf("ORBIT", "FOCUS", "HORIZON")[p], card.left + dp(25f), card.top + dp(47f), text)
            text.textAlign = Paint.Align.CENTER
            for (i in 0 until 6) resolved.getOrNull(p * 6 + i)?.let {
                drawApp(c, it, i % 3, i / 3, card.left + dp(13f), card.top + dp(78f), (card.width() - dp(26f)) / 3, dp(148f), true)
            }
            if (resolved.drop(p * 6).take(6).all { it == null }) {
                text.textSize = dp(15f); text.color = 0xffaab8ca.toInt()
                c.drawText("Long-press an app in the drawer to place it here", card.centerX(), card.centerY(), text)
                text.color = Color.WHITE
            }
            c.restore()
        }

        private fun drawFloorGrid(c: Canvas, card: RectF) {
            val horizon = card.top + dp(70f)
            val bottom = card.bottom - dp(14f)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(.7f); paint.color = 0x294fc9ef
            for (i in 0..3) {
                val x = card.left + card.width() * i / 3f
                c.drawLine(card.centerX() + (x - card.centerX()) * .58f, horizon, x, bottom, paint)
            }
            for (i in 0..7) {
                val t = i / 7f
                val y = horizon + (bottom - horizon) * t.pow(1.65f)
                val inset = (1f - t) * card.width() * .20f
                c.drawLine(card.left + inset, y, card.right - inset, y, paint)
            }
            paint.style = Paint.Style.FILL
        }

        private fun drawDock(c: Canvas) {
            val top = height - safeBottom - dp(135f)
            val r = RectF(dp(18f), top, width - dp(18f), height - safeBottom - dp(18f))
            paint.shader = LinearGradient(0f, top, 0f, r.bottom, 0xee263950.toInt(), 0xf00b1321.toInt(), Shader.TileMode.CLAMP)
            paint.setShadowLayer(dp(24f), 0f, dp(12f), Color.BLACK); c.drawRoundRect(r, dp(32f), dp(32f), paint)
            paint.clearShadowLayer(); paint.shader = null
            apps.take(4).forEachIndexed { i, app -> drawApp(c, app, i, 0, r.left, r.top, r.width() / 4, r.height(), true) }
            paint.color = 0xff71dcff.toInt(); c.drawCircle(width / 2f, r.top, dp(4f), paint)
        }

        private fun drawPageDots(c: Canvas) {
            for (i in 0..2) { paint.color = if (i == page) 0xff76ddff.toInt() else 0x66ffffff; c.drawCircle(width / 2f + (i - 1) * dp(18f), height - safeBottom - dp(151f), if (i == page) dp(4f) else dp(3f), paint) }
        }

        private fun drawDrawer(c: Canvas) {
            val reveal = drawerReveal.coerceIn(0f, 1f)
            c.save(); c.translate(0f, (1f - reveal) * height * .16f); c.scale(.94f + .06f * reveal, .94f + .06f * reveal, width / 2f, height.toFloat())
            val panel = RectF(dp(8f), safeTop + dp(82f), width - dp(8f), height + dp(45f))
            paint.shader = LinearGradient(0f, panel.top, width.toFloat(), panel.bottom,
                intArrayOf(0xf023344a.toInt(), 0xf0141d2c.toInt(), 0xff080e18.toInt()), null, Shader.TileMode.CLAMP)
            paint.setShadowLayer(dp(30f), 0f, -dp(8f), Color.BLACK); c.drawRoundRect(panel, dp(42f), dp(42f), paint)
            paint.clearShadowLayer(); paint.shader = null
            val shown = apps.filter { it.label.contains(filter, true) }
            val cell = width / 4f; val rowHeight = dp(116f); val top = safeTop + dp(108f) - drawerScroll
            c.clipRect(panel.left, panel.top, panel.right, height.toFloat())
            shown.forEachIndexed { i, app ->
                val row = i / 4
                val rowY = top + row * rowHeight
                paint.color = if (row % 2 == 0) 0x183ad1ff else 0x10277ca0
                c.drawRoundRect(dp(20f), rowY + dp(2f), width - dp(20f), rowY + dp(101f), dp(20f), dp(20f), paint)
                drawApp(c, app, i % 4, row, 0f, top, cell, rowHeight, true)
            }
            if (shown.isEmpty()) { text.textSize = dp(18f); c.drawText("No apps found", width / 2f, top + dp(80f), text) }
            c.restore()
        }

        private fun drawApp(c: Canvas, app: AppEntry, col: Int, row: Int, left: Float, top: Float, cw: Float, ch: Float, elevated: Boolean) {
            val cx = left + cw * (col + .5f); val y = top + ch * row + dp(10f)
            if (elevated) { paint.color = 0x78000000; c.drawOval(cx - dp(29f), y + dp(58f), cx + dp(29f), y + dp(72f), paint) }
            paint.shader = RadialGradient(cx - dp(10f), y + dp(15f), dp(44f), 0x663ae3ff, 0x10168aaa, Shader.TileMode.CLAMP)
            c.drawCircle(cx, y + dp(31f), dp(38f), paint); paint.shader = null
            drawIcon(c, app.icon, cx - dp(28f), y + dp(1f), dp(56f))
            text.textSize = dp(12f); text.typeface = Typeface.DEFAULT; text.color = Color.WHITE
            c.drawText(ellipsize(app.label, 13), cx, y + dp(82f), text)
        }

        private fun drawRipple(c: Canvas) {
            if (ripple <= 0f) return
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f) * (1f - ripple)
            paint.color = (min(150, ((1f - ripple) * 190).toInt()) shl 24) or 0x71dcff
            c.drawCircle(rippleX, rippleY, dp(12f) + ripple * dp(72f), paint); paint.style = Paint.Style.FILL
        }

        private fun startRipple(x: Float, y: Float) {
            rippleX = x; rippleY = y; ripple = .01f
            ValueAnimator.ofFloat(.01f, 1f).apply { duration = 420; interpolator = DecelerateInterpolator(); addUpdateListener { ripple = it.animatedValue as Float; invalidate() }; start() }
        }

        fun openDrawer() { drawer = true; drawerReveal = 0f; animateFloat(0f, 1f, 260) { drawerReveal = it } }
        fun closeDrawer() {
            drawer = false; drawerScroll = 0f; drawerReveal = 0f; search.visibility = GONE; search.clearFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
            invalidate()
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { animator?.cancel(); downX = e.x; downY = e.y; lastY = e.y; downTime = System.currentTimeMillis(); moved = false; startRipple(e.x, e.y) }
                MotionEvent.ACTION_MOVE -> {
                    moved = moved || hypot(e.x - downX, e.y - downY) > dp(9f)
                    if (drawer) {
                        val rows = ceil(apps.filter { it.label.contains(filter, true) }.size / 4f)
                        val maxScroll = max(0f, rows * dp(116f) - (height - safeTop - dp(105f)))
                        drawerScroll = (drawerScroll + lastY - e.y).coerceIn(0f, maxScroll); invalidate()
                    } else if (abs(e.x - downX) > abs(e.y - downY)) { dragX = (e.x - downX).coerceIn(-width * .98f, width * .98f); invalidate() }
                    lastY = e.y
                }
                MotionEvent.ACTION_UP -> {
                    if (drawer && !moved) drawerTap(e.x, e.y, System.currentTimeMillis() - downTime > 520)
                    else if (!drawer && abs(dragX) > dp(48f)) settle(if (dragX < 0) 1 else -1)
                    else if (!drawer && downY - e.y > dp(70f)) { dragX = 0f; openDrawer() }
                    else if (!drawer) { animateFloat(dragX, 0f, 180) { dragX = it }; if (!moved) homeTap(e.x, e.y) }
                }
                MotionEvent.ACTION_CANCEL -> if (!drawer) animateFloat(dragX, 0f, 180) { dragX = it }
            }
            return true
        }

        private fun settle(direction: Int) {
            val next = (page + direction).coerceIn(0, 2)
            if (next == page) { animateFloat(dragX, 0f, 220) { dragX = it }; return }
            page = next; performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            val start = dragX - direction * -width // new page continues exactly where it was during the drag
            dragX = start
            animateFloat(start, 0f, (250 * preferences.animation.coerceIn(.35f, 1f)).toLong(), OvershootInterpolator(.45f)) { dragX = it }
        }

        private fun animateFloat(from: Float, to: Float, durationMs: Long, interpolator: android.animation.TimeInterpolator = DecelerateInterpolator(), update: (Float) -> Unit) {
            animator?.cancel(); animator = ValueAnimator.ofFloat(from, to).apply { duration = durationMs; this.interpolator = interpolator; addUpdateListener { update(it.animatedValue as Float); invalidate() }; start() }
        }

        private fun homeTap(x: Float, y: Float) {
            if (y > height - safeBottom - dp(145f)) { apps.getOrNull((x / (width / 4f)).toInt().coerceIn(0, 3))?.let(repository::launch); return }
            if (y < safeTop + dp(64f)) { settings(); return }
            val cardTop = safeTop + dp(64f); val row = ((y - cardTop - dp(78f)) / dp(148f)).toInt(); val col = (x / (width / 3f)).toInt()
            if (row in 0..1 && col in 0..2) resolved.getOrNull(page * 6 + row * 3 + col)?.let(repository::launch)
            else if (y > height * .68f) openDrawer()
        }

        private fun drawerTap(x: Float, y: Float, long: Boolean) {
            val top = safeTop + dp(108f) - drawerScroll; val row = ((y - top) / dp(116f)).toInt(); val col = (x / (width / 4f)).toInt()
            val app = apps.filter { it.label.contains(filter, true) }.getOrNull(row * 4 + col) ?: return
            if (long) {
                val empty = slots.indexOfFirst { it == null }
                if (empty >= 0) { slots[empty] = app.component; preferences.saveSlots(slots); resolveSlots(); performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); Toast.makeText(context, "${app.label} added to page ${empty / 6 + 1}", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(context, "Home pages are full", Toast.LENGTH_SHORT).show()
            } else repository.launch(app)
        }

        private fun drawIcon(c: Canvas, drawable: Drawable, x: Float, y: Float, size: Float) { drawable.setBounds(x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt()); drawable.draw(c) }
        private fun ellipsize(value: String, length: Int) = if (value.length > length) value.take(length - 1) + "…" else value
        private fun dp(value: Float) = value * resources.displayMetrics.density
    }
}

private class SimpleTextWatcher(private val changed: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed(s?.toString().orEmpty())
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}
