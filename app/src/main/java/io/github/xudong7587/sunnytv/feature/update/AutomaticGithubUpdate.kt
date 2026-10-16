package io.github.xudong7587.sunnytv.feature.update

import java.io.File

/** No network work while disabled; never downloads an equal or older release. */
internal suspend fun prepareAutomaticGithubUpdate(
    enabled:Boolean,currentVersion:Long,
    latest:suspend ()->GithubUpdates.Release,
    download:suspend (GithubUpdates.Release)->File
):File? {
    if(!enabled) return null
    val release=latest()
    return if(release.versionCode>currentVersion) download(release) else null
}
