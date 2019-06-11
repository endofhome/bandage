package api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Response
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.template.ViewModel
import org.http4k.template.viewModel
import result.map
import result.orElse
import storage.AudioTrackMetadata
import storage.AudioTrackMetadata.Companion.presentationFormat
import storage.MetadataStorage

object Tracks {
    private val jsonRenderer: (ViewModel) -> String = { with(ApiJson) { (it as TracksViewModel).toJson() } }
    private val jsonView = Body.viewModel(jsonRenderer, ContentType.APPLICATION_JSON).toLens()

    operator fun invoke(metadataStorage: MetadataStorage): Response =
        metadataStorage.all().map { tracks ->
            Response(OK).with(jsonView of TracksViewModel(tracks.map { it.viewModel() }))
        }.orElse {
            Response(INTERNAL_SERVER_ERROR)
        }

    data class TracksViewModel(val tracks: List<ViewModels.Track>) : ViewModel

    private fun AudioTrackMetadata.viewModel(): ViewModels.Track =
        ViewModels.Track(
            "$uuid",
            artist,
            title,
            "$recordedTimestamp",
            recordedTimestampPrecision.name,
            "$uploadedTimestamp",
            format,
            bitRate?.presentationFormat(),
            duration?.presentationFormat(),
            fileSize,
            "$playUrl",
            collections.map { "$it" }
        )

    object ViewModels {
        data class Track(
            val uuid: String,
            val artist: String,
            val title: String,
            val recordedTimestamp: String,
            val recordedTimestampPrecision: String,
            val uploadedTimestamp: String,
            val format: String,
            val bitRate: String?,
            val duration: String?,
            val size: Int,
            val playUrl: String,
            val collections: List<String>
        )
    }
}

object ApiJson {
    fun Tracks.TracksViewModel.toJson(): String {
        val rootNode = jacksonObjectMapper().createArrayNode()

        this.tracks.forEach { track ->
            val obj = jacksonObjectMapper().createObjectNode()

            obj.put("id", track.uuid)
            obj.put("artist", track.artist)
            obj.put("title", track.title)
            obj.put("recordedTimestamp", track.recordedTimestamp)
            obj.put("recordedTimestampPrecision", track.recordedTimestampPrecision)
            obj.put("uploadedTimestamp", track.uploadedTimestamp)
            obj.put("format", track.format)
            track.bitRate?.let { obj.put("bitRate", track.bitRate) }
            track.duration?.let { obj.put("duration", track.duration) }
            obj.put("fileSize", track.size)
            obj.put("playUrl", track.playUrl)
            track.collections.let { if (it.isNotEmpty()) { obj.putPOJO("collections", it) } }

            rootNode.addPOJO(obj)
        }

        return jacksonObjectMapper().writeValueAsString(rootNode)
    }
}
