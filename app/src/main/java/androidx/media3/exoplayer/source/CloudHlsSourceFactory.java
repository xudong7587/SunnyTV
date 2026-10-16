/*
 * Subtitle merging adapted from Media3 DefaultMediaSourceFactory (Apache-2.0).
 * Copyright 2020 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for details.
 */
package androidx.media3.exoplayer.source;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.hls.HlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.IndexSeekMap;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.common.util.Consumer;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleExtractor;
import java.util.ArrayList;
import java.io.IOException;

/** Keeps the pinned Media3 parsed-cue/lazy subtitle path with a custom HLS extractor. */
@UnstableApi
public final class CloudHlsSourceFactory {
  private CloudHlsSourceFactory() {}

  public static MediaSource create(MediaItem item, DataSource.Factory dataSource,
      HlsExtractorFactory extractors, LoadErrorHandlingPolicy policy) {
    return create(item, dataSource, extractors, policy, 0);
  }

  public static MediaSource create(MediaItem item, DataSource.Factory dataSource,
      HlsExtractorFactory extractors, LoadErrorHandlingPolicy policy, long subtitleProgressMs) {
    MediaItem videoItem = item.buildUpon().setSubtitleConfigurations(java.util.Collections.emptyList()).build();
    MediaSource video = new HlsMediaSource.Factory(dataSource).setExtractorFactory(extractors)
        .setLoadErrorHandlingPolicy(policy).createMediaSource(videoItem);
    return mergeSubtitles(video, item, dataSource, policy, subtitleProgressMs);
  }

  /** Original and cloud playback share the same external subtitle timeline. */
  public static MediaSource mergeSubtitles(MediaSource video, MediaItem item,
      DataSource.Factory dataSource, LoadErrorHandlingPolicy policy, long subtitleProgressMs) {
    ArrayList<MediaSource> sources = new ArrayList<>();
    sources.add(video);
    DefaultSubtitleParserFactory parsers = new DefaultSubtitleParserFactory();
    for (MediaItem.SubtitleConfiguration subtitle : item.localConfiguration.subtitleConfigurations) {
      Format format = new Format.Builder().setSampleMimeType(subtitle.mimeType)
          .setLanguage(subtitle.language).setSelectionFlags(subtitle.selectionFlags)
          .setRoleFlags(subtitle.roleFlags).setLabel(subtitle.label).setId(subtitle.id).build();
      boolean supported = parsers.supportsFormat(format);
      MediaSource text = new ProgressiveMediaSource.Factory(dataSource,
            () -> new Extractor[] {supported ? new TimedSubtitleExtractor(parsers.create(format), subtitleProgressMs * 1000L)
                : new UnknownSubtitlesExtractor(format)})
            .enableLazyLoadingWithSingleTrack(SubtitleExtractor.TRACK_ID, supported
                ? format.buildUpon().setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
                    .setCodecs(format.sampleMimeType)
                    .setCueReplacementBehavior(parsers.getCueReplacementBehavior(format)).build() : format)
            .setLoadErrorHandlingPolicy(policy).createMediaSource(MediaItem.fromUri(subtitle.uri));
      sources.add(text);
    }
    return sources.size() == 1 ? sources.get(0) : new MergingMediaSource(sources.toArray(new MediaSource[0]));
  }

  /** Preserve cue end times: ProgressiveMediaPeriod otherwise infers last-start + 10ms.
   * A single long cue can then be treated as finished after a bootstrap/backward seek. */
  private static final class TimedSubtitleExtractor implements Extractor {
    private final SubtitleExtractor delegate;
    private ExtractorOutput output;
    private long durationUs;
    TimedSubtitleExtractor(SubtitleParser parser, long progressOffsetUs) {
      delegate = new SubtitleExtractor(new SubtitleParser() {
        @Override public int getCueReplacementBehavior() { return parser.getCueReplacementBehavior(); }
        @Override public void reset() { parser.reset(); }
        @Override public void parse(byte[] data, int offset, int length, OutputOptions options,
            Consumer<CuesWithTiming> consumer) {
          // Keep the extractor, its cached samples, seek filtering and published duration
          // in the VIDEO time domain. Shifting only the MediaPeriod leaves lazy subtitle
          // seek/discard decisions in a different time domain after a paused forward seek.
          // SubtitleExtractor performs seek filtering after these converted samples arrive.
          parser.parse(data, offset, length, OutputOptions.allCues(), cues -> {
            long startUs = cues.startTimeUs == C.TIME_UNSET ? C.TIME_UNSET : cues.startTimeUs - progressOffsetUs;
            CuesWithTiming shifted = new CuesWithTiming(cues.cues, startUs, cues.durationUs);
            if (shifted.endTimeUs != C.TIME_UNSET) durationUs = Math.max(durationUs, shifted.endTimeUs);
            consumer.accept(shifted);
          });
          if (durationUs > 0) output.seekMap(new IndexSeekMap(new long[] {0}, new long[] {0}, durationUs));
        }
      }, null);
    }
    @Override public boolean sniff(ExtractorInput input) throws IOException { return delegate.sniff(input); }
    @Override public void init(ExtractorOutput output) { this.output = output; delegate.init(output); }
    @Override public int read(ExtractorInput input, PositionHolder position) throws IOException { return delegate.read(input, position); }
    @Override public void seek(long position, long timeUs) { delegate.seek(position, timeUs); }
    @Override public void release() { delegate.release(); }
  }

  private static final class UnknownSubtitlesExtractor implements Extractor {
    private final Format format;
    UnknownSubtitlesExtractor(Format format) { this.format = format; }
    @Override public boolean sniff(ExtractorInput input) { return true; }
    @Override public void init(ExtractorOutput output) {
      androidx.media3.extractor.TrackOutput track = output.track(SubtitleExtractor.TRACK_ID, C.TRACK_TYPE_TEXT);
      output.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
      output.endTracks();
      track.format(format.buildUpon().setSampleMimeType(MimeTypes.TEXT_UNKNOWN)
          .setCodecs(format.sampleMimeType).build());
    }
    @Override public int read(ExtractorInput input, PositionHolder position) throws IOException {
      return input.skip(Integer.MAX_VALUE) == C.RESULT_END_OF_INPUT ? RESULT_END_OF_INPUT : RESULT_CONTINUE;
    }
    @Override public void seek(long position, long timeUs) {}
    @Override public void release() {}
  }
}

