package io.github.xudong7587.sunnytv.source.strm

import io.github.xudong7587.sunnytv.core.network.HttpPolicy
import io.github.xudong7587.sunnytv.core.network.bytes
import okhttp3.OkHttpClient
import okhttp3.Request

/** Only explicit .strm paths are fetched as text; /api/play/{token} goes straight to the player. */
class StrmResolver(private val client: OkHttpClient) {
    suspend fun resolve(entryUrl: String): String {
        var target = entryUrl
        val visited = linkedSetOf<String>()
        while (StrmParser.looksLikeStrm(target)) {
            StrmParser.checkRecursion(target, visited)
            visited += target
            target = StrmParser.parse(client.bytes(
                Request.Builder().url(target).build(), StrmParser.MAX_BYTES.toLong()
            ))
        }
        HttpPolicy.validate(target)
        return target
    }
}
