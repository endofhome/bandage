package api

import RouteMappings.play
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import http.httpConfig
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.template.ViewModel
import org.http4k.template.viewModel
import storage.AudioFileMetadata
import storage.BitRate
import storage.Duration
import storage.MetadataStorage
import java.math.BigDecimal
import java.time.temporal.ChronoUnit

object Tracks {
    private val jsonRenderer: (ViewModel) -> String = { with(ApiJson) { (it as TracksViewModel).toJson() } }
    private val jsonView = Body.viewModel(jsonRenderer, ContentType.APPLICATION_JSON).toLens()

    operator fun invoke(metadataStorage: MetadataStorage): Response {
        // TODO return a result and handle failure.
        val tracks = metadataStorage.all().map { it.viewModel() }

        return Response(OK).with(jsonView of TracksViewModel(tracks))
    }

    data class TracksViewModel(val tracks: List<ViewModels.Track>) : ViewModel

    private fun AudioFileMetadata.viewModel(): ViewModels.Track =
        ViewModels.Track(
            "$uuid",
            artist,
            title,
            recordedDate,
            format,
            bitRate?.presentationFormat(),
            duration?.presentationFormat(),
            size,
            httpConfig().config.let { "${it.protocol}://${it.host}$play?id=$uuid" },
            collections.map { "$it" }
        )

    // TODO remove duplication
    private fun BitRate.presentationFormat(): String = (this.value.toBigDecimal() / BigDecimal(1000)).toString()

    private fun String.emptyIfZero(): String =
        if (this != "0") "$this:"
        else ""

    private fun Duration.presentationFormat(): String {
        val (rawSeconds, _) = this.value.split(".")
        val duration = java.time.Duration.ofSeconds(rawSeconds.toLong())
        val hours = duration.toHoursPart().toString().emptyIfZero()
        val minutes = duration.toMinutesPart()
        val seconds = duration.toSecondsPart().toString().padStart(2, '0')
        return "$hours$minutes:$seconds"
    }

    object ViewModels {
        data class Track(
            val uuid: String,
            val artist: String,
            val title: String,
            val recordedDate: String,
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
            obj.put("recordedTimestamp", track.recordedDate)
            obj.put("recordedTimestampPrecision", ChronoUnit.MINUTES.name)
            obj.put("uploadedTimestamp", track.recordedDate)
            obj.put("format", track.format)
            track.bitRate?.let { obj.put("bitRate", track.bitRate) }
            track.duration?.let { obj.put("duration", track.duration) }
            obj.put("size", track.size)
            obj.put("playUrl", track.playUrl)
            track.collections.let { if (it.isNotEmpty()) { obj.putPOJO("collections", it) } }

            rootNode.addPOJO(obj)
        }

        return jacksonObjectMapper().writeValueAsString(rootNode)
    }
}
