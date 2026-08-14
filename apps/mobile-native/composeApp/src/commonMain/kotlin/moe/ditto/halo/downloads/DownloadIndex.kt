package moe.ditto.halo.downloads

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import moe.ditto.halo.api.HaloJson
import moe.ditto.halo.storage.KeyValueStore
import moe.ditto.halo.storage.StorageKeys

/**
 * What was read back from the store, and whether all of it was understood.
 *
 * [complete] exists because of the orphan sweep. Deleting files no entry
 * references is only safe when the entries are known to be all of them: an
 * index that failed to decode looks exactly like an empty one, and sweeping
 * against it would delete every download on the device. So a partial read still
 * yields what it could, and says it was partial.
 */
internal data class DownloadIndexSnapshot(
    val entries: List<DownloadEntry>,
    val complete: Boolean,
)

/**
 * The download list as it survives restarts.
 *
 * A JSON object keyed by video id, which is the one-entry-per-video rule
 * expressed in the shape of the document rather than enforced on top of a list.
 * Entries are decoded one at a time, so a record written by another build
 * cannot discard the rest, following [moe.ditto.halo.storage.SubtitleChoiceStore].
 *
 * Nothing is capped or evicted here. Every entry corresponds to bytes on the
 * device, and dropping the oldest would orphan a file rather than free it.
 */
internal class DownloadIndex(private val store: KeyValueStore) {

    fun read(): DownloadIndexSnapshot {
        val raw = store.read(StorageKeys.Downloads) ?: return DownloadIndexSnapshot(emptyList(), complete = true)
        val root = try {
            HaloJson.parseToJsonElement(raw) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return DownloadIndexSnapshot(emptyList(), complete = false)

        var complete = true
        val entries = root.mapNotNull { (_, element) ->
            try {
                HaloJson.decodeFromJsonElement(DownloadEntry.serializer(), element)
            } catch (_: SerializationException) {
                complete = false
                null
            } catch (_: IllegalArgumentException) {
                complete = false
                null
            }
        }
        return DownloadIndexSnapshot(entries, complete)
    }

    fun write(entries: List<DownloadEntry>) {
        val document = entries.associateBy { it.videoId }
        store.write(StorageKeys.Downloads, HaloJson.encodeToString(Serializer, document))
    }

    private companion object {
        val Serializer = MapSerializer(String.serializer(), DownloadEntry.serializer())
    }
}
