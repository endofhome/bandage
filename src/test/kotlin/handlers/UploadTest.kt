package handlers

import RouteMappings.dashboard
import com.natpryce.hamkrest.allElements
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.startsWith
import org.http4k.core.Method.GET
import org.http4k.core.Request
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
import java.io.File

internal class UploadTest {

    private val noOpMetadataStorage = object : DummyMetadataStorage() {
        override fun addTracks(newMetadata: List<AudioTrackMetadata>) {}
    }

    private val noOpFileStorage = object : DummyFileStorage() {
        override fun uploadFile(file: File, destinationPath: String): Result<Error, File> = File("dummy-file").asSuccess()
        override fun publicLink(path: String, permission: FileStoragePermission): Result<Error, Uri> = Uri.of("http://some-uri.com").asSuccess()
    }

    @Test
    fun `month values must be within 1 and 12`() {
        val validMonths = 1..12
        val invalidBoundaryMonths = listOf(0, 13)
        val negativeMonth = listOf(-1)
        val randomInvalidMonths = (1..10).map { (Math.random() * 100).toInt() }.filter { ! validMonths.contains(it) }
        val validRequests = validMonths.map { request(month = it)}
        val invalidRequest = (invalidBoundaryMonths + negativeMonth + randomInvalidMonths).map { request(month = it) }

        val validResponses = validRequests.map {
            Upload(it, noOpMetadataStorage, noOpFileStorage, "file-storage-password")
        }
        
        val invalidResponses = invalidRequest.map {
            Upload(it, noOpMetadataStorage, noOpFileStorage, "file-storage-password")
        }

        assertThat(validResponses.map { it.status }, allElements(equalTo(SEE_OTHER)))
        assertThat(validResponses.map { it.header("Location")!! }, allElements(startsWith("$dashboard?highlighted=")))
        assertThat(invalidResponses.map { it.status }, allElements(equalTo(BAD_REQUEST)))
    }

    @Test
    fun `day values must be within 1 and 2`() {
        val validDays = 1..31
        val invalidBoundaryDays = listOf(0, 32)
        val negativeDay = listOf(-1)
        val randomInvalidDays = (1..10).map { (Math.random() * 100).toInt() }.filter { ! validDays.contains(it) }
        val validRequests = validDays.map { request(day = it)}
        val invalidRequest = (invalidBoundaryDays + negativeDay + randomInvalidDays).map { request(day = it) }

        val validResponses = validRequests.map {
            Upload(it, noOpMetadataStorage, noOpFileStorage, "file-storage-password")
        }

        val invalidResponses = invalidRequest.map {
            Upload(it, noOpMetadataStorage, noOpFileStorage, "file-storage-password")
        }

        assertThat(validResponses.map { it.status }, allElements(equalTo(SEE_OTHER)))
        assertThat(validResponses.map { it.header("Location")!! }, allElements(startsWith("$dashboard?highlighted=")))
        assertThat(invalidResponses.map { it.status }, allElements(equalTo(BAD_REQUEST)))
    }

    private fun request(month: Int = 1, day: Int = 1): Request =
        Request(GET, "some-url")
            .form("artist", "some artist")
            .form("title", "")
            .form("working_title", "")
            .form("duration_raw", "")
            .form("format", "")
            .form("bitrate_raw", "")
            // TODO use snake case
            .form("recordedYear", "1968")
            .form("recordedMonth", "$month")
            .form("recordedDay", "$day")
            .form("recordedHour", "")
            .form("recordedMinute", "")
            .form("recordedSecond", "")
            .form("filename", "")
            .form("hash", "")
}