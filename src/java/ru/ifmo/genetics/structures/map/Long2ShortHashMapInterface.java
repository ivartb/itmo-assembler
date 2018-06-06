package ru.ifmo.genetics.structures.map;

import org.apache.commons.lang.mutable.MutableLong;
import org.apache.hadoop.io.Writable;

import java.util.Iterator;

public interface Long2ShortHashMapInterface extends Writable, Iterable<MutableLong> {
    /**
     * @return the previous value of this key, or -1, if no value was associated.
     */
    public short put(long key, short value);
    /**
     * @return the previous value of this key, or 0, if no value was associated.
     */
    public short addAndBound(long key, short incValue);


    /**
     * @return -1, if not found (but -1 can also be a value, if you put such one there!)
     */
    public short get(long key);

    /**
     * @return 0, if not found
     */
    public short getWithZero(long key);
    public boolean contains(long key);


    public long size();
    public long capacity();

    public void reset();
    public void resetValues();

    public Iterator<MutableLongShortEntry> entryIterator();


    // Methods, that use information about the internal structure. May be unsupported.
    /**
     * Call this method to prepare to the future requests.
     * Assuming no other thread modifying map!
     */
    public void prepare();
    public long maxPosition();
    /**
     * @return pos or -1, if not found
     */
    public long getPosition(long key);
    /**
     * Returns FREE, if pos is free.
     */
    public long keyAt(long pos);
    /**
     * Returns -1, if pos is free.
     */
    public short valueAt(long pos);
    public boolean containsAt(long pos);

}
