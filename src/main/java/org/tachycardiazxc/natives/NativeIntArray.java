package org.tachycardiazxc.natives;

import sun.misc.Unsafe;

public class NativeIntArray {

    private static final int UNSAFE_COPY_THRESHOLD = 1024;

    private static final Unsafe unsafe;
    private static final NativeIntArray nativeIntArrayInstance;

    static {
        unsafe = UnsafeConfig.getUnsafeInstance();
        nativeIntArrayInstance = new NativeIntArray();
    }

    private NativeIntArray() {
    }

    public static NativeIntArray getNativeIntArrayInstance() {
        return nativeIntArrayInstance;
    }

    public long allocArray() {
        final int bytesToAllocate = 64;
        long allocatedAddress = unsafe.allocateMemory(bytesToAllocate - 1);
        this.putDirectly(allocatedAddress, 0);
        this.putDirectly(allocatedAddress + 4, (bytesToAllocate - 8) / 4);
        this.fillArrayWithMaxInt(allocatedAddress, 0, bytesToAllocate - 8);
        return allocatedAddress;
    }

    public long allocArray(int size) {
        if (size < 14) {
            return this.allocArray();
        }
        int byteSize = size * 4 + 8;

        long allocatedAddress;

        if (this.isPowerOfTwo(byteSize)) {
            allocatedAddress = unsafe.allocateMemory(byteSize - 1);
            this.putDirectly(allocatedAddress, 0);
            this.putDirectly(allocatedAddress + 4, size);
        } else {
            byteSize = nextPowerOfTwo(byteSize);
            allocatedAddress = unsafe.allocateMemory(byteSize - 1);
            this.putDirectly(allocatedAddress, 0);
            this.putDirectly(allocatedAddress + 4, (byteSize - 12) / 4);
        }
        this.fillArrayWithMaxInt(allocatedAddress, 0, byteSize - 8);
        return allocatedAddress;
    }

    private void fillArrayWithMaxInt(long arrayAddress, int startPos, int endPos) {
        for (int i = startPos; i < endPos; i += 4) {
            this.putDirectly(arrayAddress + 8 + i, 1 << 31);
        }
    }

    public long add(long address, int value, long index) throws IllegalArgumentException, IndexOutOfBoundsException {
        if (index >= this.getDirectly(address + 4)) {
            throw new IndexOutOfBoundsException();
        }
        if (value == 1 << 31) {
            throw new IllegalArgumentException();
        }
        if (this.getDirectly((address + 8) + index * 4) == 1 << 31) {
            this.incrementSize(address);
        }
        this.putDirectly((address + 8) + index * 4, value);
        return address;
    }

    public long add(long address, int value) throws IllegalArgumentException {
        if (value == 1 << 31) {
            throw new IllegalArgumentException();
        }
        long index = this.getDirectly(address);
        if (this.getDirectly(address) == this.getDirectly(address + 4)) {
            address = this.grow(address);
        }
        if (this.getDirectly((address + 8) + index * 4) == 1 << 31) {
            this.incrementSize(address);
        }
        this.putDirectly((address + 8) + index * 4, value);
        return address;
    }

    public int get(long address, long index) throws IndexOutOfBoundsException, IllegalArgumentException {
        if (index >= this.getDirectly(address + 4)) {
            throw new IndexOutOfBoundsException();
        }
        int value = this.getDirectly((address + 8) + index * 4);
        if (value == 1 << 31) {
            throw new IllegalArgumentException();
        }
        return value;
    }

    private void incrementSize(long address) {
        int currentSize = this.getDirectly(address);
        this.putDirectly(address, ++currentSize);
    }

    private long grow(long address) {
        int currentCapacityBytes = this.getDirectly(address + 4L) * 4 + 8;
        int newCapacityBytes = this.nextPowerOfTwo(currentCapacityBytes);
        long newMemoryAddress = unsafe.allocateMemory(newCapacityBytes - 1);
        int previousSize = this.getDirectly(address);
        this.putDirectly(address + 4, (newCapacityBytes - 8) / 4);
        if (newCapacityBytes > 32768) {
            this.copyMemoryWithSafePoint(address, newMemoryAddress, currentCapacityBytes);
        } else {
            unsafe.copyMemory(address, newMemoryAddress, currentCapacityBytes);
        }
        this.fillArrayWithMaxInt(newMemoryAddress, previousSize * 4, newCapacityBytes - 8);
        unsafe.freeMemory(address);
        return newMemoryAddress;
    }

    private void putDirectly(long address, int value) {
        unsafe.putInt(address, value);
    }

    private int getDirectly(long address) {
        return unsafe.getInt(address);
    }

    private void copyMemoryWithSafePoint(long srcAddress, long dstAddress, long length) {
        while (length > 0) {
            long size = length < UNSAFE_COPY_THRESHOLD ? length : UNSAFE_COPY_THRESHOLD;
            unsafe.copyMemory(srcAddress, dstAddress, size);
            length -= size;
            srcAddress += size;
            dstAddress += size;
        }
    }

    private int nextPowerOfTwo(int n) {
        n |= (n >> 16);
        n |= (n >> 8);
        n |= (n >> 4);
        n |= (n >> 2);
        n |= (n >> 1);
        return ++n;
    }

    private boolean isPowerOfTwo(long n) {
        return (n != 0) && ((n & (n - 1)) == 0);
    }

}
