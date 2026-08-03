package info.bitcoinunlimited.www.wally
import kotlinx.coroutines.*
import org.nexa.threads.*
import kotlin.coroutines.CoroutineContext

/** The default context to execute reactions in */
val ReactionCtxt: CoroutineContext = newFixedThreadPoolContext(4, "Reactor")
val ReactionScope: CoroutineScope = CoroutineScope(ReactionCtxt)


/** A class that encapsulates the idea that when an object changes, other entities should be notified of the change.
 * This is extremely useful in GUI development since onscreen object can be made to "automatically" update based on
 * changes to the "Reactive" underlying object that is being displayed.
 *
 * This class does not necessarily "react" once for every change to the Reactive object.  If multiple rapid changes
 * occur, intermediate values may be skipped.  This ensures that the reactions "keep up" with the changes.
 * */
open class Reactive<T>(var value: T)
{
    private var oldValue: T? = null
    private var reactedToNum: Long = 0  // If reactedToNum == changeNum, we've already applied the change.  This allows the reaction to skip a bunch of values
    private var changeNum: Long = 0
    protected var mutex = Mutex()

    public var reactor: Reactor<T>? = null
        set(value)
        {
            field?.let { it.change(null) }
            field = value
            changeNum++
            react()
        }

    /** Change the value of this object */

    public operator fun invoke(newVal: T, force: Boolean = false)
    {
        mutex.lock {
            if (force || (newVal != value))
            {
                oldValue = value
                value = newVal
                changeNum++

                reactor?.let {
                    (it.executionScope ?: ReactionScope).launch { react() }
                }
            }
        }

    }

    /** Change the value of this object, but only if it can accept a change */
    public operator fun invoke(force: Boolean = false, newValFn: () -> T)
    {
        mutex.lock {
            if (force || (reactor != null))
            {
                val newVal = newValFn()
                if (force || (newVal != value))
                {
                    oldValue = value
                    value = newVal
                    changeNum++

                    reactor?.let {
                        (it.executionScope ?: ReactionScope).launch { react() }
                    }
                }
            }
        }
    }

    public open fun setAttribute(key: String, value: String)
    {
        reactor?.setAttribute(key, value)
    }
    public open fun setAttribute(key: String, value: Int)
    {
        reactor?.setAttribute(key, value)
    }

    /** Efficiently return the values and mark that we've reacted to this change.
     * This call actually marks that the change has been reacted to, so call this as late in your processing as possible
     * to allow multiple changes in the underlying to be efficiently skipped. */
    fun access(): Pair<T, T?>?
    {
        return mutex.lock {
            if (changeNum == reactedToNum) return@lock null  // Already handled so skip it
            reactedToNum = changeNum
            return@lock Pair(value, oldValue)
        }
    }

    /** Handle calling the reactor if something has changed.  This is normally called asynchronously */
    protected fun react()
    {
        if (reactedToNum != changeNum)
            reactor?.let { it.change(this) }  // Delay the access() of the data for as long as possible to maximize the number of changes that might occur before we access the variable (and minimize the latency between access an use)
    }

}

/** Any object that responds to changes in a Reactive object */
abstract class Reactor<T>
{
    /** Implement your reaction by overriding this function.  Call obj->access to actually get the changed value.
     * If obj is null, this is being disconnected.  You should set the Reactor back to a default value (GUI elements should blank their display)
     *
     */
    public abstract fun change(obj: Reactive<T>?)

    /** Change some attribute of this object.  How a derived class implements these attributes is up to it.
     * "strength" -> "bright", "normal", "dim"
     * */
    public open fun setAttribute(key: String, value: String)
    {
    }
    public open fun setAttribute(key: String, value: Int)
    {
    }

    /** Change the scope in which the reaction is called.  This allows you to make sure that the GUI thread is being used for GUI calls, or to isolate reaction calls to specific threads. */
    val executionScope: CoroutineScope? = null
}

