import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.Method.POST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Request
import org.http4k.core.Status.Companion.FOUND
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.body.Form
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookies
import org.http4k.core.with
import org.http4k.lens.Header
import org.http4k.lens.webForm
import org.http4k.webdriver.Http4kWebDriver
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.openqa.selenium.By
import java.util.*

class BandageTest {

    @Test
    fun `empty login page is returned`() {
        val driver = Http4kWebDriver(Bandage.routes)

        driver.navigate().to("/")

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.title, equalTo("Bandage: Please log in"))

        val usernameField = driver.findElement(By.cssSelector("#user"))
        assertNotNull(usernameField)

        val passwordField = driver.findElement(By.cssSelector("#password"))
        assertNotNull(passwordField)

        val submitButton = driver.findElement(By.cssSelector("button[type=\"submit\"][name=\"login\"]"))
        assertNotNull(submitButton)
    }
}
