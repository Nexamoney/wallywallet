import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.util.Collections
import java.util.IdentityHashMap

// A hide still animating when the test's activity closes keeps the jank monitor holding it, which LeakCanary reports
actual fun hideKeyboard()
{
    val instr = InstrumentationRegistry.getInstrumentation()
    val running = Collections.newSetFromMap(IdentityHashMap<WindowInsetsAnimationCompat, Boolean>())
    var sawStart = false
    var decor: View? = null
    fun imeVisible(): Boolean
    {
        var v = false
        instr.runOnMainSync { v = decor?.let { ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime()) } == true }
        return v
    }
    instr.runOnMainSync {
        val w = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()?.window ?: return@runOnMainSync
        if (ViewCompat.getRootWindowInsets(w.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true) return@runOnMainSync
        decor = w.decorView
        ViewCompat.setWindowInsetsAnimationCallback(w.decorView, object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE)
        {
            override fun onStart(animation: WindowInsetsAnimationCompat, bounds: WindowInsetsAnimationCompat.BoundsCompat): WindowInsetsAnimationCompat.BoundsCompat
            {
                synchronized(running) { running.add(animation) }
                sawStart = true
                return bounds
            }
            override fun onProgress(insets: WindowInsetsCompat, runningAnimations: MutableList<WindowInsetsAnimationCompat>) = insets
            override fun onEnd(animation: WindowInsetsAnimationCompat) { synchronized(running) { running.remove(animation) } }
        })
        WindowCompat.getInsetsController(w, w.decorView).hide(WindowInsetsCompat.Type.ime())
    }
    val d = decor ?: return
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < 2000)
    {
        Thread.sleep(50)
        if (synchronized(running) { running.isEmpty() } && !imeVisible() && (sawStart || System.currentTimeMillis() - start > 500)) break
    }
    instr.runOnMainSync { ViewCompat.setWindowInsetsAnimationCallback(d, null) }
    instr.waitForIdleSync()
}
