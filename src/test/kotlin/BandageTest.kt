import RouteMappings.dashboard
import RouteMappings.index
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.Status.Companion.OK
import org.http4k.webdriver.Http4kWebDriver
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.openqa.selenium.By
import org.openqa.selenium.Cookie

class BandageTest {

    @Test
    fun `fully functional login page is returned`() {
        val driver = Http4kWebDriver(Bandage.routes)

        driver.navigate().to(index)

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.title, equalTo("Bandage: Please log in"))

        val usernameField = driver.findElement(By.cssSelector("#user")) ?: fail("username field not found")
        val option = usernameField.findElement(By.cssSelector("option:contains(TB)"))
        option.click()

        val passwordField = driver.findElement(By.cssSelector("#password")) ?: fail("password field not found")
        passwordField.sendKeys(System.getenv("BANDAGE_PASSWORD"))

        val loginButton = driver.findElement(By.cssSelector("button[type=\"submit\"][name=\"login\"]")) ?: fail("login button not found")
        loginButton.click()

        val loginCookie = driver.manage().getCookieNamed("bandage_login") ?: fail("login cookie not set")
        val expectedCookie = Cookie("bandage_login", "${System.getenv("BANDAGE_API_KEY")}_3", "login")

        assertThat(loginCookie, equalTo(expectedCookie))
        assertThat(driver.currentUrl, equalTo(dashboard))
    }
}
