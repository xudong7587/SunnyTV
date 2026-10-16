package io.github.xudong7587.sunnytv.core.playback;

/** Read-ahead remains bounded by the granted heap and current system memory headroom. */
public final class PlaybackBufferPolicy {
    private static final long MIB = 1024L * 1024L;
    private PlaybackBufferPolicy() {}

    public static int targetBytes(long maxHeapBytes) {
        return targetBytes(maxHeapBytes, Long.MAX_VALUE, Long.MAX_VALUE, false);
    }

    public static int targetBytes(long maxHeapBytes, long totalBytes, long availableBytes, boolean lowMemory) {
        if (maxHeapBytes <= 0 || totalBytes <= 0 || availableBytes <= 0)
            throw new IllegalArgumentException("Invalid memory size");
        long heapBudget = maxHeapBytes / (lowMemory ? 3L : 2L);
        long systemBudget = Math.min(totalBytes / 8L, availableBytes / 4L);
        return (int) Math.max(1L, Math.min(512L * MIB, Math.min(heapBudget, systemBudget)));
    }
}
