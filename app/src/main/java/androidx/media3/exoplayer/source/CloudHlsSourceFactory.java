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
    ArrayList<MediaSource> sources = new ArrayList<>();
    sources.add(new HlsMediaSource.Factory(dataSource).setExtractorFactory(extractors)
        .setLoadErrorHandlingPolicy(policy).createMediaSource(item));
    DefaultSubtitleParserFactory parsers = new DefaultSubtitleParserFactory();
    for (MediaItem.SubtitleConfiguration subtitle : item.localConfiguration.subtitleConfigurations) {
      Format format = new Format.Builder().setSampleMimeType(subtitle.mimeType)
          .setLanguage(subtitle.language).setSelectionFlags(subtitle.selectionFlags)
          .setRoleFlags(subtitle.roleFlags).setLabel(subtitle.label).setId(subtitle.id).build();
      boolean supported = parsers.supportsFormat(format);
      sources.add(new ProgressiveMediaSource.Factory(dataSource,
            () -> new Extractor[] {supported ? new SubtitleExtractor(parsers.create(format), null)
                : new UnknownSubtitlesExtractor(format)})
            .enableLazyLoadingWithSingleTrack(SubtitleExtractor.TRACK_ID, supported
                ? format.buildUpon().setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
                    .setCodecs(format.sampleMimeType)
                    .setCueReplacementBehavior(parsers.getCueReplacementBehavior(format)).build() : format)
            .setLoadErrorHandlingPolicy(policy).createMediaSource(MediaItem.fromUri(subtitle.uri)));
    }
    return sources.size() == 1 ? sources.get(0) : new MergingMediaSource(sources.toArray(new MediaSource[0]));
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
