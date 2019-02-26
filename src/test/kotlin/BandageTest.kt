import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.OK
import org.http4k.webdriver.Http4kWebDriver
import org.junit.jupiter.api.Test

class BandageTest {

    @Test
    fun `empty login page is returned`() {
        val driver = Http4kWebDriver(Bandage)

        driver.navigate().to("Bandage")

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.title, equalTo("Bandage: Please log in"))
    }
}