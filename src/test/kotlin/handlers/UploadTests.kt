package handlers

import RouteMappings.dashboard
import com.natpryce.hamkrest.allElements
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.startsWith
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.Uri
import org.http4k.core.body.form
import org.junit.jupiter.api.Test
import result.Error
import result.Result
import result.asSuccess
import storage.AudioTrackMetadata
import storage.DummyFileStorage
import storage.DummyMetadataStorage
import storage.FileStoragePermission
import storage.Waveform
import storage.Waveform.Companion.Bits
import storage.Waveform.Companion.Channels
import storage.Waveform.Companion.Data
import storage.Waveform.Companion.Length
import storage.Waveform.Companion.SampleRate
import storage.Waveform.Companion.SamplesPerPixel
import storage.Waveform.Companion.Version
import java.io.File

internal class UploadTests {

    private val noOpMetadataStorage = object : DummyMetadataStorage() {
        override fun addTracks(newMetadata: List<AudioTrackMetadata>) {}
    }

    private val noOpFileStorage = object : DummyFileStorage() {
        override fun uploadFile(file: File, destinationPath: String): Result<Error, File> = File("dummy-file").asSuccess()
        override fun publicLink(path: String, permission: FileStoragePermission): Result<Error, Uri> = Uri.of("http://some-uri.com").asSuccess()
    }

    private val stubExtractWaveform : (File) -> Waveform = {
        Waveform(Bits(0), Data(emptyList()), Length(0), Version(0), Channels(0), SampleRate(0), SamplesPerPixel(0))
    }

    @Test
    fun `month values must be within 1 and 12`() {
        val validMonths = 1..12
        val invalidBoundaryMonths = listOf(0, 13)
        val negativeMonth = listOf(-1)
        val randomInvalidMonths = (1..10).map { (Math.random() * 100).toInt() }.filter { ! validMonths.contains(it) }
        val validRequests = validMonths.map { request(month = it)}
        val invalidRequests = (invalidBoundaryMonths + negativeMonth + randomInvalidMonths).map { request(month = it) }

        val (validResponses, invalidResponses) = handleRequests(validRequests, invalidRequests)

        assertExpectedResponses(validResponses, invalidResponses)
    }

    @Test
    fun `day values must be within 1 and 2`() {
        val validDays = 1..31
        val invalidBoundaryDays = listOf(0, 32)
        val negativeDay = listOf(-1)
        val randomInvalidDays = (1..10).map { (Math.random() * 100).toInt() }.filter { ! validDays.contains(it) }
        val validRequests = validDays.map { request(day = it)}
        val invalidRequests = (invalidBoundaryDays + negativeDay + randomInvalidDays).map { request(day = it) }

        val (validResponses, invalidResponses) = handleRequests(validRequests, invalidRequests)

        assertExpectedResponses(validResponses, invalidResponses)
    }

    @Test
    fun `hour values must be within 0 and 23`() {
        val validHours = 0..23
        val invalidBoundaryHours = listOf(-1, 24)
        val randomInvalidHours = (1..10).map { (Math.random() * 100).toInt() }.filter { ! validHours.contains(it) }
        val validRequests = validHours.map { request(hour = it)}
        val invalidRequests = (invalidBoundaryHours + randomInvalidHours).map { request(hour = it) }

        val (validResponses, invalidResponses) = handleRequests(validRequests, invalidRequests)

        assertExpectedResponses(validResponses, invalidResponses)
    }

    @Test
    fun `minute values must be within 0 and 59`() {
        val validMinutes = 0..59
        val invalidBoundaryMinutes = listOf(-1, 60)
        val randomInvalidHours = (1..10).map { (Math.random() * 100).toInt() }.filter { ! validMinutes.contains(it) }
        val validRequests = validMinutes.map { request(minutes = it)}
        val invalidRequests = (invalidBoundaryMinutes + randomInvalidHours).map { request(minutes = it) }

        val (validResponses, invalidResponses) = handleRequests(validRequests, invalidRequests)

        assertExpectedResponses(validResponses, invalidResponses)
    }

    @Test
    fun `second values must be within 0 and 59`() {
        val validSecond = 0..59
        val invalidBoundarySecond = listOf(-1, 60)
        val randomInvalidHours = (1..10).map { (Math.random() * 100).toInt() }.filter { ! validSecond.contains(it) }
        val validRequests = validSecond.map { request(seconds = it)}
        val invalidRequests = (invalidBoundarySecond + randomInvalidHours).map { request(seconds = it) }

        val (validResponses, invalidResponses) = handleRequests(validRequests, invalidRequests)

        assertExpectedResponses(validResponses, invalidResponses)
    }

    private fun request(month: Int = 1, day: Int = 1, hour: Int = 0, minutes: Int = 0, seconds: Int = 0): Request =
        Request(GET, "some-url")
            .form("artist", "some artist")
            .form("title", "")
            .form("working_title", "")
            .form("duration_raw", "")
            .form("format", "")
            .form("bitrate_raw", "")
            .form("recorded_year", "1968")
            .form("recorded_month", "$month")
            .form("recorded_day", "$day")
            .form("recorded_hour", "$hour")
            .form("recorded_minute", "$minutes")
            .form("recorded_second", "$seconds")
            .form("filename", "")
            .form("normalised_file_size", "1")
            .form("hash", "")

    private fun handleRequests(validRequests: List<Request>, invalidRequests: List<Request>): Pair<List<Response>, List<Response>> {
        fun handle(request: Request) =
            Upload(request, noOpMetadataStorage, noOpFileStorage, "file-storage-password", stubExtractWaveform)

        val validResponses = validRequests.map { handle(it) }
        val invalidResponses = invalidRequests.map { handle(it) }

        return Pair(validResponses, invalidResponses)
    }

    private fun assertExpectedResponses(
        validResponses: List<Response>,
        invalidResponses: List<Response>
    ) {
        assertThat(validResponses.map { it.status }, allElements(equalTo(SEE_OTHER)))
        assertThat(validResponses.map { it.header("Location")!! }, allElements(startsWith("$dashboard?highlighted=")))
        assertThat(invalidResponses.map { it.status }, allElements(equalTo(BAD_REQUEST)))
    }
}