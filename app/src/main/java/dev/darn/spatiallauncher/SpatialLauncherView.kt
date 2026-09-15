package dev.darn.spatiallauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.*
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.math.*

class SpatialLauncherView(
    context: Context,
    private val repository: AppRepository,
    private val preferences: LauncherPreferences,
    private val settings: () -> Unit
) : FrameLayout(context) {
    private val scene = Scene(context)
    private val search = EditText(context).apply {
        hint = "Search installed apps"; textSize = 18f; setTextColor(Color.WHITE); setHintTextColor(0xff9cabc4.toInt())
        setSingleLine(); setBackgroundColor(0xaa111a2a.toInt()); setPadding(36, 0, 24, 0); visibility = GONE
        addTextChangedListener(SimpleTextWatcher { scene.filter = it; scene.invalidate() })
    }
    init {
        setWillNotDraw(false); addView(scene, LayoutParams(-1, -1)); addView(search, LayoutParams(-1, dp(58)))
        setOnApplyWindowInsetsListener { _, inset ->
            val bars = inset.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            search.layoutParams = (search.layoutParams as LayoutParams).apply { setMargins(dp(18), bars.top + dp(12), dp(18), 0) }
            scene.safeTop = bars.top; scene.safeBottom = bars.bottom; inset
        }
        reloadApps()
    }
    fun reloadApps() { scene.apps = repository.load(); scene.resolveSlots(); scene.invalidate() }
    fun goHome() { scene.drawer = false; scene.page = 0; search.visibility = GONE; scene.invalidate() }
    fun closeOverlay(): Boolean = if (scene.drawer) { goHome(); true } else false
    private fun openDrawer() { scene.drawer = true; search.visibility = VISIBLE; search.requestFocus(); (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT); scene.invalidate() }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private inner class Scene(context: Context) : View(context) {
        var apps: List<AppEntry> = emptyList(); var filter = ""; var drawer = false; var page = 0
        var safeTop = 0; var safeBottom = 0
        private var offset = 0f; private var drawerScroll = 0f; private var downX = 0f; private var downY = 0f; private var lastX = 0f; private var lastY = 0f; private var downTime = 0L
        private val slots = preferences.homeSlots(); private var resolved = listOf<AppEntry?>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG); private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
        private val velocity = VelocityTracker.obtain()
        fun resolveSlots() { resolved = slots.map { c -> apps.firstOrNull { it.component == c } } }

        override fun onDraw(c: Canvas) {
            super.onDraw(c); c.drawColor(0x27000812)
            paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), 0x35156f9d, 0x66100620, Shader.TileMode.CLAMP); c.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint); paint.shader = null
            if (drawer) drawDrawer(c) else drawHome(c)
        }
        private fun drawHome(c: Canvas) {
            val progress = offset / width.coerceAtLeast(1); val current = page
            for (p in max(0,current-1)..min(2,current+1)) {
                val relative = p - current + progress
                drawPage(c, p, relative)
            }
            drawDock(c); drawPageDots(c)
        }
        private fun drawPage(c: Canvas, p: Int, relative: Float) {
            c.save(); val depth = preferences.depth
            val x = relative * width * .86f
            val scale = 1f - min(.18f, abs(relative) * .16f * depth)
            val lean = relative * 12f * depth
            // Camera supplies a real perspective projection instead of only scaling a flat panel.
            val projection = Matrix(); Camera().apply {
                save(); rotateY(-relative * 24f * depth); getMatrix(projection); restore()
            }
            projection.preTranslate(-width/2f, -height/2f); projection.postTranslate(width/2f + x, height/2f)
            c.concat(projection); c.rotate(lean); c.scale(scale, scale); c.translate(-width/2f, -height/2f)
            paint.color = 0x9a142238.toInt(); paint.setShadowLayer(42f, -relative*24, 22f, 0xaa000000.toInt()); setLayerType(LAYER_TYPE_HARDWARE, paint)
            val card = RectF(dp(16f), safeTop+dp(82f), width-dp(16f), height-safeBottom-dp(150f)); c.drawRoundRect(card, dp(34f), dp(34f), paint); paint.clearShadowLayer()
            paint.style=Paint.Style.STROKE; paint.strokeWidth=2f; paint.color=0x5574d9ff; c.drawRoundRect(card,dp(34f),dp(34f),paint); paint.style=Paint.Style.FILL
            text.textSize=dp(25f); text.typeface=Typeface.create("sans",Typeface.BOLD); text.textAlign=Paint.Align.LEFT
            c.drawText(listOf("ORBIT", "FOCUS", "HORIZON")[p], dp(40f), safeTop+dp(130f), text); text.textAlign=Paint.Align.CENTER
            for (i in 0 until 6) resolved.getOrNull(p*6+i)?.let { drawApp(c,it,i%3,i/3,card.left+dp(20f),card.top+dp(90f), (card.width()-dp(40f))/3,dp(150f)) }
            if (resolved.drop(p*6).take(6).all { it == null }) { text.textSize=dp(15f); text.color=0xff91a1b9.toInt(); c.drawText("Long-press an app in the drawer to place it here", width/2f,card.centerY(),text);text.color=Color.WHITE }
            c.restore()
        }
        private fun drawDock(c: Canvas) {
            val top=height-safeBottom-dp(126f); paint.color=0xcc111a2a.toInt(); paint.setShadowLayer(30f,0f,12f,Color.BLACK); val r=RectF(dp(18f),top,width-dp(18f),height-safeBottom-dp(18f));c.drawRoundRect(r,dp(32f),dp(32f),paint);paint.clearShadowLayer()
            apps.take(4).forEachIndexed { i,a -> drawApp(c,a,i,0,r.left,r.top,(r.width()/4),r.height()) }
            paint.color=0xff71dcff.toInt(); c.drawCircle(width/2f, r.top, dp(4f),paint)
        }
        private fun drawPageDots(c:Canvas){ paint.color=0x88ffffff.toInt(); for(i in 0..2){if(i==page)paint.color=0xff76ddff.toInt() else paint.color=0x66ffffff;c.drawCircle(width/2f+(i-1)*dp(18f),height-safeBottom-dp(142f),if(i==page)dp(4f) else dp(3f),paint)} }
        private fun drawDrawer(c: Canvas) {
            paint.color=0xf0121a29.toInt();c.drawRoundRect(RectF(0f,safeTop+dp(86f),width.toFloat(),height+dp(40f)),dp(42f),dp(42f),paint)
            text.textSize=dp(13f); val shown=apps.filter { it.label.contains(filter,true) }; val cell=width/4f; val row=dp(116f); val top=safeTop+dp(112f)-drawerScroll
            c.save(); c.clipRect(0f,safeTop+dp(86f),width.toFloat(),height.toFloat()); shown.forEachIndexed { i,a -> drawApp(c,a,i%4,i/4,0f,top,cell,row) }; c.restore()
            if(shown.isEmpty()){text.textSize=dp(18f);c.drawText("No apps found",width/2f,top+dp(80f),text)}
        }
        private fun drawApp(c:Canvas,a:AppEntry,col:Int,row:Int,left:Float,top:Float,cw:Float,ch:Float){
            val cx=left+cw*(col+.5f);val y=top+ch*row+dp(12f);paint.color=0x3319a9df;c.drawCircle(cx,y+dp(31f),dp(35f),paint);drawIcon(c,a.icon,cx-dp(27f),y+dp(4f),dp(54f));text.textSize=dp(12f);text.typeface=Typeface.DEFAULT;text.color=Color.WHITE;c.drawText(ellipsize(a.label,13),cx,y+dp(79f),text)
        }
        private fun drawIcon(c:Canvas,d:Drawable,x:Float,y:Float,s:Float){d.setBounds(x.toInt(),y.toInt(),(x+s).toInt(),(y+s).toInt());d.draw(c)}
        private fun ellipsize(s:String,n:Int)=if(s.length>n)s.take(n-1)+"…" else s
        override fun onTouchEvent(e: MotionEvent): Boolean {
            velocity.addMovement(e)
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{downX=e.x;lastX=e.x;downY=e.y;lastY=e.y;downTime=System.currentTimeMillis();performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)}
                MotionEvent.ACTION_MOVE->{if(drawer){val rows=ceil(apps.filter{it.label.contains(filter,true)}.size/4f).toFloat();val maxScroll=max(0f,rows*dp(116f)-(height-safeTop-dp(100f)));drawerScroll=(drawerScroll+lastY-e.y).coerceIn(0f,maxScroll);invalidate()}else if(abs(e.x-downX)>abs(e.y-downY)){offset+=(lastX-e.x);offset=offset.coerceIn(-width*.95f,width*.95f);invalidate()};lastX=e.x;lastY=e.y}
                MotionEvent.ACTION_UP->{
                    if(drawer && abs(e.y-downY)<dp(16f)) drawerTap(e.x,e.y,System.currentTimeMillis()-downTime>520)
                    else if(abs(e.x-downX)>dp(48f)) settle(if(e.x<downX) 1 else -1)
                    else if (downY-e.y>dp(70f)) openDrawer()
                    else {offset=0f;homeTap(e.x,e.y)}
                    velocity.clear()
                }
            };return true
        }
        private fun settle(direction:Int){val old=offset;val can=(page+direction).coerceIn(0,2);if(can!=page){page=can;performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)};ValueAnimator.ofFloat(old,0f).apply{duration=(260*preferences.animation.coerceAtLeast(.25f)).toLong();interpolator=OvershootInterpolator(.6f);addUpdateListener{offset=it.animatedValue as Float;invalidate()};start()}}
        private fun homeTap(x:Float,y:Float){
            if(y>height-safeBottom-dp(145f)){val i=(x/(width/4f)).toInt().coerceIn(0,3);apps.getOrNull(i)?.let(repository::launch);return}
            if(y<safeTop+dp(82f)){settings();return}
            val cardTop=safeTop+dp(82f);val row=((y-cardTop-dp(90f))/dp(150f)).toInt();val col=(x/(width/3f)).toInt();if(row in 0..1&&col in 0..2)resolved.getOrNull(page*6+row*3+col)?.let(repository::launch)
            else if(y>height*.70f)openDrawer()
        }
        private fun drawerTap(x:Float,y:Float,long:Boolean){val top=safeTop+dp(112f)-drawerScroll;val row=((y-top)/dp(116f)).toInt();val col=(x/(width/4f)).toInt();val shown=apps.filter{it.label.contains(filter,true)};val app=shown.getOrNull(row*4+col)?:return;if(long){val empty=slots.indexOfFirst{it==null};if(empty>=0){slots[empty]=app.component;preferences.saveSlots(slots);resolveSlots();performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);Toast.makeText(context,"${app.label} added to page ${empty/6+1}",Toast.LENGTH_SHORT).show()}else Toast.makeText(context,"Home pages are full",Toast.LENGTH_SHORT).show()}else repository.launch(app)}
        private fun dp(v:Float)=v*resources.displayMetrics.density
    }
}

private class SimpleTextWatcher(private val changed:(String)->Unit):android.text.TextWatcher{
    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int)=changed(s?.toString().orEmpty())
    override fun afterTextChanged(s:android.text.Editable?)=Unit
}
