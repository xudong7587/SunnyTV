package io.github.xudong7587.sunnytv.core.playback;

/** Byte-bounded read-ahead: a 79 Mbps stream consumes nearly 10 MB per second.
 * Leave at least two thirds of the Java heap for the UI, artwork and decoders. */
public final class PlaybackBufferPolicy {
    private static final long MIB = 1024L * 1024L;
    private PlaybackBufferPolicy() {}

    public static int targetBytes(long maxHeapBytes) {
        if (maxHeapBytes <= 0) throw new IllegalArgumentException("Invalid heap size");
        return (int) Math.max(1L, Math.min(256L * MIB, maxHeapBytes / 3L));
    }
}
