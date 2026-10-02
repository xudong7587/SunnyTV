# Provenance

Audio Java classes and JNI copied from androidx/media 1.9.4 at 75ccb55ec085d76cbbf12e2f1af8241d378a753a. Audited format routing, native loading, buffer allocation, decoding, reset and release. No network or filesystem playback access in these classes. Experimental video renderer excluded; its javadoc reference removed. CMake and FFmpeg build script retained. Gradle adapted to published 1.9.4 artifacts.

FFmpeg is built from n6.0.1 at c41ff724ede7da657762d61097e26fac296c53bf with only ac3/eac3/dca/truehd decoders and swresample; no executable, protocol/demuxer, video codec, GPL or nonfree dependency. See scripts/build-audio-decoders.sh. Release includes source and relink material.
