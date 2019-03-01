import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.Status.Companion.OK
import org.http4k.webdriver.Http4kWebDriver
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.openqa.selenium.By

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