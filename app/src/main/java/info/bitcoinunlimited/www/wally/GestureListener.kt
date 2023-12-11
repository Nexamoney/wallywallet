// Copyright (c) 2022 Andrew Stone Consulting (qq9wwnuw4eukyh5g34ckg5vk4aaxnvr04vkspyv850)
// Distributed under the MIT software license, see the accompanying file COPYING or http://www.opensource.org/licenses/mit-license.php.

package info.bitcoinunlimited.www.wally

import android.content.Context
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

private const val MAX_ANGLED_SWIPE = 250
private const val SWIPE_THRESHOLD = 100
private const val SWIPE_VELOCITY_THRESHOLD = 100

open class OnSwipeTouchListener(val view: View, var claimIfUsed: Boolean=true) : View.OnTouchListener, SimpleOnGestureListener()
{
    private val gestureDetector: GestureDetector

    override fun onTouch(v: View, motionEvent: MotionEvent): Boolean
    {
        val ret = gestureDetector.onTouchEvent(motionEvent)
        if (claimIfUsed) return ret
        else return false
    }


        override fun onDown(e: MotionEvent): Boolean
        {
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean
        {
            onClick(Pair(e.getX(), e.getY()))
            return super.onSingleTapUp(e)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean
        {
            onDoubleClick(Pair(e.getX(), e.getY()))
            return super.onDoubleTap(e)
        }

        override fun onLongPress(e: MotionEvent)
        {
            onLongClick(Pair(e.getX(), e.getY()))
            super.onLongPress(e)
        }

        // return the display size in pixels
        fun viewSize():Pair<Int, Int>
        {
            val v = view
            if (v == null) return Pair(1000,1000)  // TODO, but should never be the case...
            return(Pair(v.width, v.height))
        }

        // find which of 9 squares this event comes from (like quadrant but for 9)
        fun nonarant(e: MotionEvent): Pair<Int, Int>
        {
            val sz = viewSize()
            return Pair( (e.getX()/(sz.first/3.0f).toInt()).toInt(), (e.getY()/(sz.second/3.0f).toInt()).toInt())
        }

        // Determines the fling velocity and then fires the appropriate swipe event accordingly
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean
        {
            val result = false
            try
            {
                val diffY = if (e1 != null) e2.y - e1.y else velocityY
                val diffX = if (e1 != null) e2.x - e1.x else velocityX
                if (Math.abs(diffX) > Math.abs(diffY))
                {
                    if (Math.abs(diffY) < MAX_ANGLED_SWIPE)
                    {
                        if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD)
                        {
                            if (diffX > 0)
                            {
                                return onSwipeRight()
                            }
                            else
                            {
                                return onSwipeLeft()
                            }
                        }
                    }
                }
                else
                {
                    if (Math.abs(diffX) < MAX_ANGLED_SWIPE)
                    {
                        if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD)
                        {
                            if (diffY > 0)
                            {
                                return onSwipeDown()
                            }
                            else
                            {
                                return onSwipeUp()
                            }
                        }
                    }
                }

                // Radiate gesture starts from the middle and goes outwards to one of the other quadrants
                // Note that it will only be valid if a prior gesture did not match.  Therefore radiating vertically or horizontally probably won't work
                // Aug 29, 2023 onFling fn def changed so e1 can be a null, but no docs explain what that means.  I need to see the fling direction...
                if (e1 == null) return false
                val st = nonarant(e1)
                val end = nonarant(e2)
                if (st.first == 1 && st.second == 1 && end.first != 1 && end.second != 1)
                {
                    return onRadiate(end)
                }
            } catch (exception: Exception)
            {
                exception.printStackTrace()
            }
            return result
        }

    open fun onRadiate(endNonarant: Pair<Int, Int>): Boolean
    {
        return false
    }

    open fun onSwipeRight(): Boolean
    {
        return false
    }

    open fun onSwipeLeft(): Boolean
    {
        return false
    }

    open fun onSwipeUp(): Boolean
    {
        return false
    }

    open fun onSwipeDown(): Boolean
    {
        return false
    }

    open fun onClick(pos: Pair<Float, Float>): Boolean
    {
        return false
    }

    open fun onDoubleClick(pos: Pair<Float, Float>): Boolean
    {
        return false
    }

    open fun onLongClick(pos: Pair<Float, Float>): Boolean
    {
        return false
    }

    init
    {
        gestureDetector = GestureDetector(view.context, this)
    }
}