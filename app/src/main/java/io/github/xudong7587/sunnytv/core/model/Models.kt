package io.github.xudong7587.sunnytv.core.model

import java.io.Serializable

enum class SourceKind { EMBY, CLOUDDRIVE }
data class SourceConfig(
    val id: String, val kind: SourceKind, val name: String, val baseUrl: String,
    val userId: String = "", val username: String = "", val secret: String = ""
) : Serializable

data class Artwork(val itemId: String, val type: String, val tag: String, val index: Int? = null) : Serializable

data class MediaEntry(
    val id: String, val sourceId: String, val title: String, val type: String,
    val overview: String = "", val year: Int = 0, val rating: Double = 0.0,
    val durationMs: Long = 0, val positionMs: Long = 0, val played: Boolean = false,
    val favorite: Boolean = false, val genres: List<String> = emptyList(),
    val primary: Artwork? = null, val thumb: Artwork? = null, val backdrop: Artwork? = null,
    val logo: Artwork? = null, val seriesId: String = "", val season: Int = 0,
    val episode: Int = 0, val path: String = "", val isFolder: Boolean = false,
    val banner: Artwork? = null, val collectionType: String = "",
    val people: List<MediaPerson> = emptyList(), val versions: List<MediaVersion> = emptyList(),
    val officialRating: String = "", val externalLinks: List<MediaLink> = emptyList(),
    val tracks:List<MediaTrack> = emptyList(), val lastPlayedAtMs:Long = 0,
    val chapters:List<MediaChapter> = emptyList()
) : Serializable {
    val key: String get() = "$sourceId:$id"
    val isPlayable: Boolean get() = !isFolder && type in setOf("Movie", "Episode", "Video", "File")
    val subtitle: String get() = listOfNotNull(
        year.takeIf { it > 0 }?.toString(),
        if (type == "Episode") "S${season} · E${episode}" else null,
        genres.firstOrNull()
    ).joinToString("  ·  ")
}
data class MediaPage(val items: List<MediaEntry>, val total: Int)
data class MediaPerson(val id:String, val name:String, val role:String, val primary:Artwork?) : Serializable
data class MediaTrack(val index:Int, val type:String, val language:String, val title:String, val codec:String, val external:Boolean=false, val isDefault:Boolean=false) : Serializable
data class MediaVersion(val id:String, val name:String, val width:Int, val height:Int, val range:String,
    val size:Long, val bitrate:Long, val container:String, val tracks:List<MediaTrack>) : Serializable
data class MediaLink(val name:String, val url:String) : Serializable
data class MediaChapter(val name:String,val startMs:Long,val markerType:String="Chapter") : Serializable
data class SkipSegment(val id:String,val type:String,val startMs:Long,val endMs:Long) : Serializable
data class PlayerMediaContext(val item:MediaEntry,val previous:MediaEntry?=null,val next:MediaEntry?=null,
    val skipSegments:List<SkipSegment> = emptyList())
data class HomeFeed(
    val libraries: List<MediaEntry> = emptyList(), val resume: List<MediaEntry> = emptyList(),
    val latest: List<MediaEntry> = emptyList(), val nextUp: List<MediaEntry> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class HeaderScope(val baseUrl: String, val headers: Map<String, String>) : Serializable

data class ExternalSubtitle(val url: String, val mime: String, val language: String, val title: String, val id:String="", val isDefault:Boolean=false) : Serializable

data class PlaybackRequest(
    val sourceId: String, val stableUrl: String, val title: String,
    val startMs: Long = 0, val mimeHint: String? = null,
    val scope: HeaderScope? = null, val subtitles: List<ExternalSubtitle> = emptyList(),
    val embyItemId: String = "", val mediaSourceId: String = "", val playSessionId: String = "",
    val playMethod: String = "DirectStream", val localKey: String = "",
    // elapsedRealtime() marks. -1 means unavailable; never use wall-clock timestamps.
    val requestedAtMs: Long = -1, val sourceReadyAtMs: Long = -1,
    val audioLanguage:String = "", val audioTitle:String = "", val subtitlePreference:String = "default",
    val subtitleTitle:String = "", val mediaLogo:Artwork? = null,
    val subtitleTrackId:String = "", val subtitleOrdinal:Int = -1, val explicitSubtitle:Boolean=false
) : Serializable

data class AppSettings(
    val reduceMotion: Boolean = false, val highQualityArtwork: Boolean = false,
    val backdropEnabled: Boolean = true, val showResume: Boolean = true,
    val showNextUp: Boolean = true, val diagnostics: Boolean = false,
    val seekStepSeconds: Int = 10,
    val darkTheme:Boolean = true, val accentIndex:Int = 2,
    val artworkMode:String = "Poster", val subtitlePreference:String = "default",
    val heroMode:String = "random", val heroLibraryKeys:Set<String> = emptySet(),
    val heroAllLibraries:Boolean = true, val heroIntervalSeconds:Int = 8,
    val uiScaleLevel:Int = 2, val libraryArtworkModes:Map<String,String> = emptyMap(),
    val episodeLayouts:Map<String,String> = emptyMap(),
    val librarySubtitlePreferences:Map<String,String> = emptyMap(),
    val animationSpeed:Float = 1f, val fontScaleLevel:Int = 2,
    val customFontFile:String = "", val customFontName:String = "", val shadowsEnabled:Boolean = true
)
