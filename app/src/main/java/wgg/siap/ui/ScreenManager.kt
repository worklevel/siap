package wgg.siap.ui

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

class ScreenManager(
    private val fragmentManager: FragmentManager,
    private val container: FrameLayout
) {

    private val uiHandler = Handler(Looper.getMainLooper())
    private val dismissRunnables = mutableMapOf<String, Runnable>()
    private var currentTag: String? = null

    fun show(tag: String, fragment: Fragment) {
        cancelAllAutoDismiss()
        uiHandler.post {
            try {
                currentTag = tag
                container.visibility = View.VISIBLE
                fragmentManager.beginTransaction()
                    .replace(container.id, fragment, tag)
                    .commitAllowingStateLoss()
                Log.d("SIAP", "[SCREEN:SHOW] $tag")
            } catch (e: Exception) {
                Log.e("SIAP", "[SCREEN:SHOW_ERROR] $tag", e)
            }
        }
    }

    fun dismiss(tag: String) {
        cancelAutoDismiss(tag)
        uiHandler.post {
            try {
                val fragment = fragmentManager.findFragmentByTag(tag)
                if (fragment != null) {
                    fragmentManager.beginTransaction()
                        .remove(fragment)
                        .commitAllowingStateLoss()
                }
                if (currentTag == tag) {
                    container.visibility = View.GONE
                    currentTag = null
                }
                Log.d("SIAP", "[SCREEN:DISMISS] $tag")
            } catch (e: Exception) {
                Log.e("SIAP", "[SCREEN:DISMISS_ERROR] $tag", e)
            }
        }
    }

    fun dismissAll() {
        cancelAllAutoDismiss()
        uiHandler.post {
            try {
                currentTag?.let { tag ->
                    val fragment = fragmentManager.findFragmentByTag(tag)
                    if (fragment != null) {
                        fragmentManager.beginTransaction()
                            .remove(fragment)
                            .commitAllowingStateLoss()
                    }
                }
                container.visibility = View.GONE
                currentTag = null
                Log.d("SIAP", "[SCREEN:DISMISS_ALL]")
            } catch (e: Exception) {
                Log.e("SIAP", "[SCREEN:DISMISS_ALL_ERROR]", e)
            }
        }
    }

    fun scheduleAutoDismiss(tag: String, delayMs: Long) {
        cancelAutoDismiss(tag)
        val runnable = Runnable { dismiss(tag) }
        dismissRunnables[tag] = runnable
        uiHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelAutoDismiss(tag: String) {
        dismissRunnables.remove(tag)?.let { uiHandler.removeCallbacks(it) }
    }

    private fun cancelAllAutoDismiss() {
        dismissRunnables.forEach { (_, runnable) -> uiHandler.removeCallbacks(runnable) }
        dismissRunnables.clear()
    }
}
