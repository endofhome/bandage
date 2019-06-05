package unit.tests

import AuthenticatedRequest
import RouteMappings.dashboard
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import exampleAudioTrackMetadata
import exampleUser
import handlers.Dashboard
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.OK
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import storage.StubMetadataStorage

class DashboardTest {
    @Test
    fun `Folders are ordered crudely by date and folders only containing letters are last`() {
        val authenticatedRequest = AuthenticatedRequest(Request(GET, dashboard), exampleUser)
        val metadataStorage = StubMetadataStorage(mutableListOf(
            exampleAudioTrackMetadata.copy(path = "/1980-10-10/oldest"),
            exampleAudioTrackMetadata.copy(path = "/milford/bäbï"),
            exampleAudioTrackMetadata.copy(path = "/1999-12-31/newest")
        ))

        val response = Dashboard(authenticatedRequest, metadataStorage)
        assertThat(response.status, equalTo(OK))
        val document = Jsoup.parse(response.bodyString())
        val titles = document.select("h4")

        assertThat(titles.map { it.text() }, equalTo(listOf(
            "1999-12-31",
            "1980-10-10",
            "milford"
        )))
    }
}
