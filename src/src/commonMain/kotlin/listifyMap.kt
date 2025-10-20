package info.bitcoinunlimited.www.wally

/* This gives a Map a list interface, sorted by the passed comparator */
class ListifyMap<K, E>(origmap: Map<K, E>, filterPredicate: (Map.Entry<K, E>) -> Boolean, var comparator: Comparator<K>): List<E>
{
    protected var map = origmap.filter(filterPredicate)
    protected var order: List<K> = map.keys.sortedWith(comparator)
    override val size: Int
        get() = map.size

    override fun get(index: Int): E
    {
        return map[order[index]]!!
    }

    override fun isEmpty(): Boolean = map.isEmpty()

    fun reprocess(comp: Comparator<K>, filterPredicate: (Map.Entry<K, E>) -> Boolean)
    {
        comparator = comp
        map = map.filter(filterPredicate)
        changed()
    }

    fun refilter(filterPredicate: (Map.Entry<K, E>) -> Boolean)
    {
        map = map.filter(filterPredicate)
        changed()
    }

    fun reorder(comp: Comparator<K>)
    {
        comparator = comp
        changed()
    }

    fun changed()
    {
        order = map.keys.sortedWith(comparator)
    }

    class LmIterator<K, E>(val lm: ListifyMap<K, E>):Iterator<E>
    {
        var pos = 0
        override fun hasNext(): Boolean
        {
            return (pos < lm.size)
        }

        override fun next(): E
        {
            val ret = lm.get(pos)
            pos += 1
            return ret
        }
    }

    override fun iterator(): Iterator<E>
    {
        return LmIterator(this)
    }

    override fun listIterator(): ListIterator<E>
    {
        TODO("Not yet implemented")
    }

    override fun listIterator(index: Int): ListIterator<E>
    {
        TODO("Not yet implemented")
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<E>
    {
        if ((fromIndex<0)||(toIndex>order.size)) throw IndexOutOfBoundsException()
        if (fromIndex>toIndex) throw IllegalArgumentException()
        return List(toIndex-fromIndex, { map[order[it]]!!})
    }

    override fun lastIndexOf(element: E): Int
    {
        for (idx in order.size-1 downTo 0)
        {
            if (map[order[idx]] == element)
            {
                return idx
            }
        }
        return -1
    }

    override fun indexOf(element: E): Int
    {
        var ret = 0
        for (i in order)
        {
            if (map[i] == element)
            {
                return ret
            }
            ret++
        }
        return -1
    }

    override fun containsAll(elements: Collection<E>): Boolean
    {
        TODO("Not yet implemented")
    }

    override fun contains(element: E): Boolean
    {
        for (i in map)
        {
            if (i.value == element) return true
        }
        return false
    }

}