package info.bitcoinunlimited.www.wally

import androidx.annotation.Keep
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import android.net.ConnectivityManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.*
import java.io.File

import java.security.SecureRandom
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

class TextViewReactor<T>(
  public val gui: TextView,
  public val flags:Long = 0,
  public val fontAdjust: ((s:String, p: Paint) -> Unit)? = null,
  ) : Reactor<T>()
{
    companion object
    {
        const val GONE_IF_EMPTY = 1L
    }

    var origColor: Int = gui.currentTextColor
    var color: Int = gui.currentTextColor
    override fun change(obj: Reactive<T>?)
    {
        if (obj == null)
        {
            gui.text = ""
            return
        }
        val v = obj.access()
        if (v != null)
        {
            val vstr = v.first.toString()
            laterUI {
                if ((flags and GONE_IF_EMPTY) > 0L)
                {
                    if (vstr == "") gui.visibility = View.GONE
                    else gui.visibility = View.VISIBLE
                }
                if (vstr != gui.text)
                {
                    gui.setTextColor(color)
                    val paint = gui.paint
                    paint.color = color
                    fontAdjust?.invoke(vstr, paint)
                    gui.text = v.first.toString()
                }
            }
        }
        else
        {
            // We already saw this change
            // LogIt.info(sourceLoc() +": Rewrite GUI change skipped")
        }
    }

    /** Change some attribute of this object.  How a derived class implements these attributes is up to it.
     * "strength" -> "bright", "normal", "dim"
     * */
    @Synchronized
    override public fun setAttribute(key: String, value: String)
    {
        when(key)
        {
            "strength" -> {
               when(value)
               {
                   /*
                   "bright" -> color = gui.highlightColor
                   "normal" -> color = origColor
                   "dim" -> color = gui.shadowColor

                    */
               }
            }

        }
    }
    @Synchronized
    override public fun setAttribute(key: String, value: Int)
    {
        when(key)
        {
            "color" -> {
                color = value
            }
        }
    }
}