import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.OK
import org.junit.Test

class BandageTest {
    @Test
    fun `returns 200 OK for all requests`() {
        val incomingRequest = Request(GET, "http://www.example.com")
        assertThat(Bandage(incomingRequest).status, equalTo(OK))
    }
}