package androidx.media3.exoplayer.source;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.upstream.Allocator;
import java.util.IdentityHashMap;

/** Shifts only an external subtitle's samples and seeks; the video clock stays unchanged.
 * Kept beside CloudHlsSourceFactory to use the pinned Media3 time-offset period. */
@UnstableApi
public final class SubtitleOffsetMediaSource extends WrappingMediaSource {
  private final long offsetUs;
  private final IdentityHashMap<MediaPeriod, MediaPeriod> periods = new IdentityHashMap<>();

  public SubtitleOffsetMediaSource(MediaSource source, long progressOffsetMs) {
    super(source);
    // Advancing subtitle progress means displaying each cue earlier on the video timeline.
    offsetUs = -progressOffsetMs * 1000L;
  }

  @Override public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    MediaPeriod child = mediaSource.createPeriod(id, allocator, startPositionUs - offsetUs);
    MediaPeriod shifted = new TimeOffsetMediaPeriod(child, offsetUs);
    periods.put(shifted, child);
    return shifted;
  }

  @Override public void releasePeriod(MediaPeriod period) {
    MediaPeriod child = periods.remove(period);
    if (child == null) throw new IllegalStateException("Unknown subtitle period");
    mediaSource.releasePeriod(child);
  }
}
