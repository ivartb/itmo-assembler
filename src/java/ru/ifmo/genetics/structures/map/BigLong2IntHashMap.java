package ru.ifmo.genetics.structures.map;

import it.unimi.dsi.fastutil.HashCommon;
import org.apache.commons.lang.mutable.MutableLong;
import org.apache.log4j.Logger;
import ru.ifmo.genetics.structures.set.BigLongHashSet;
import ru.ifmo.genetics.structures.set.LongHashSet;
import ru.ifmo.genetics.utils.NumUtils;
import ru.ifmo.genetics.utils.tool.Tool;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Memory-efficient map based on many hash tables with open addressing.<br></br>
 * It is resizable (not full support!). Map is synchronized.<br></br>
 * It can contain up to 2^60 (~10^18) elements.<br></br>
 * <br></br>
 */
public class BigLong2IntHashMap implements Long2IntHashMapInterface {
    private static final Logger logger = Logger.getLogger("BigLong2IntHashMap");

    public Long2IntHashMap[] maps;
    protected int mask;


    public BigLong2IntHashMap(long capacity) {
        this(
                NumUtils.getPowerOf2(capacity >> 20 +
                        ((capacity & ((1 << 20) - 1)) == 0 ? 0 : 1)
                ),
                20    // 1 M elements per small map
        );
    }

    public BigLong2IntHashMap(int logSmallMapNumber, int logSmallCapacity) {
        this(logSmallMapNumber, logSmallCapacity, false);
    }

    public BigLong2IntHashMap(int logSmallMapNumber, int logSmallCapacity, boolean debugInfo) {
        if (logSmallMapNumber > 30) {
            throw new IllegalArgumentException("logSmallMapNumber > 30!");
        }

        int smallMapNumber = 1 << logSmallMapNumber;
        mask = smallMapNumber - 1;

        maps = new Long2IntHashMap[smallMapNumber];
        for (int i = 0; i < smallMapNumber; i++) {
            maps[i] = new Long2IntHashMap(logSmallCapacity, LongHashSet.DEFAULT_MAX_LOAD_FACTOR);
        }
        if (debugInfo) {
            Tool.debug(logger, "Created " + NumUtils.groupDigits(smallMapNumber) + " small Long2IntHashMaps");
        }
    }


    @Override
    public int put(long key, int value) {
        int n = HashCommon.murmurHash3((int) key) & mask;
        return maps[n].put(key, value);
    }
    @Override
    public int addAndBound(long key, int incValue) {
        int n = HashCommon.murmurHash3((int) key) & mask;
        return maps[n].addAndBound(key, incValue);
    }

    @Override
    public int get(long key) {
        int n = HashCommon.murmurHash3((int) key) & mask;
        return maps[n].get(key);
    }

    @Override
    public int getWithZero(long key) {
        int n = HashCommon.murmurHash3((int) key) & mask;
        return maps[n].getWithZero(key);
    }

    @Override
    public boolean contains(long key) {
        int n = HashCommon.murmurHash3((int) key) & mask;
        return maps[n].contains(key);
    }

    @Override
    public long size() {
        long size = 0;
        for (Long2IntHashMap map : maps) {
            size += map.size();
        }
        return size;
    }

    @Override
    public long capacity() {
        long capacity = 0;
        for (Long2IntHashMap map : maps) {
            capacity += map.capacity();
        }
        return capacity;
    }



    // --------------  Other methods from interface Long2IntHashMapInterface  ---------------

    @Override
    public void reset() {
        for (Long2IntHashMap map : maps) {
            map.reset();
        }
    }
    @Override
    public void resetValues() {
        for (Long2IntHashMap map : maps) {
            map.resetValues();
        }
    }


    long[] off;

    @Override
    public void prepare() {
        off = new long[maps.length];
        off[0] = 0;
        for (int i = 1; i < maps.length; i++) {
            off[i] = off[i - 1] + maps[i - 1].maxPosition() + 1;
        }
    }

    @Override
    public long maxPosition() {
        return maps.length == 0 ? -1 :
                off[maps.length - 1] + maps[maps.length - 1].maxPosition();
    }

    /**
     * Working ONLY if small sets stay unchanged!
     * Call prepare() before using.
     */
    @Override
    public long getPosition(long key) {
        int n = HashCommon.murmurHash3((int) key) & mask;
        long pos = maps[n].getPosition(key);
        return pos == -1 ? -1 : (off[n] + pos);
    }

    @Override
    public long keyAt(long pos) {
        int n = Arrays.binarySearch(off, pos);
        if (n < 0) {
            n = (-n - 1) - 1;
        }
        return maps[n].keyAt(pos - off[n]);
    }
    @Override
    public int valueAt(long pos) {
        int n = Arrays.binarySearch(off, pos);
        if (n < 0) {
            n = (-n - 1) - 1;
        }
        return maps[n].valueAt(pos - off[n]);
    }
    @Override
    public boolean containsAt(long pos) {
        int n = Arrays.binarySearch(off, pos);
        if (n < 0) {
            n = (-n - 1) - 1;
        }
        return maps[n].containsAt(pos - off[n]);
    }


    @Override
    public void write(DataOutput out) throws IOException {
        out.writeInt(maps.length);

        for (Long2IntHashMap map : maps) {
            map.write(out);
        }
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        int len = in.readInt();
        if (Integer.bitCount(len) != 1) {
            throw new RuntimeException("Length is not a power of two!");
        }
        maps = new Long2IntHashMap[len];
        mask = maps.length - 1;

        for (int i = 0; i < len; i++) {
            maps[i] = new Long2IntHashMap(2);
            maps[i].readFields(in);
        }
    }


    @Override
    public Iterator<MutableLong> iterator() {
        return new BigLongHashSet.MyIterator(maps);
    }

    @Override
    public Iterator<MutableLongIntEntry> entryIterator() {
        return new MyIterator();
    }

    class MyIterator implements Iterator<MutableLongIntEntry> {
        private int index;
        private Iterator<MutableLongIntEntry> it = null;

        public MyIterator() {
            index = 0;
            if (index < maps.length) {
                it = maps[index].entryIterator();
            }
        }

        @Override
        public boolean hasNext() {
            while (index < maps.length) {
                if (it.hasNext()) {
                    return true;
                }
                index++;
                if (index < maps.length) {
                    it = maps[index].entryIterator();
                }
            }
            return false;
        }

        @Override
        public MutableLongIntEntry next() {
            if (hasNext()){
                return it.next();
            }
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
