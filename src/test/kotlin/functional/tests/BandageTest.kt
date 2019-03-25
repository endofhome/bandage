package functional.tests

import Authentication
import Bandage
import OkeyDokeExtension
import RouteMappings.dashboard
import RouteMappings.index
import RouteMappings.login
import User
import UserManagement
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.oneeyedmen.okeydoke.Approver
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.webdriver.Http4kWebDriver
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.By
import org.openqa.selenium.Cookie

@ExtendWith(OkeyDokeExtension::class)
class BandageTest {

    private val driver = Http4kWebDriver(Bandage.app)

    @Test
    fun `index redirects to login`() {
        driver.navigate().to(index)

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.currentUrl, equalTo(login))
    }

    @Test
    fun `can log in via login page`() {
        val loggedInUser = userLogsIn()

        val loginCookie = driver.manage().getCookieNamed(Authentication.loginCookieName) ?: fail("login cookie not set")
        val expectedCookie = Cookie(Authentication.loginCookieName, "${System.getenv("BANDAGE_API_KEY")}_${loggedInUser.userId}", "login")

        assertThat(loginCookie, equalTo(expectedCookie))
        assertThat(driver.currentUrl, equalTo(dashboard))
        assertThat(driver.title, equalTo("Bandage"))
    }

    @Test
    fun `can log out`() {
        userLogsIn()
        val logoutLink = driver.findElement(By.cssSelector("a[data-test=\"logout\"]")) ?: fail("Logout link is unavailable")
        logoutLink.click()

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.manage().cookies, equalTo(emptySet()))
        assertThat(driver.currentUrl, equalTo(login))
    }
    
    @Test
    fun `cannot access dashboard page if not logged in`() {
        driver.navigate().to(dashboard)

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.manage().cookies, equalTo(emptySet()))
        assertThat(driver.currentUrl, equalTo(login))
    }

    @Test
    fun `accessing index page with a logged in cookie redirects to dashboard page`() {
        val loggedInUser = userLogsIn()
        driver.navigate().to(index)
        val expectedCookie = Cookie(Authentication.loginCookieName, "${System.getenv("BANDAGE_API_KEY")}_${loggedInUser.userId}", "login")

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.currentUrl, equalTo(dashboard))
        assertThat(driver.manage().cookies, equalTo(setOf(expectedCookie)))
    }

    @Test
    fun `accessing login page with a logged in cookie redirects to dashboard page`() {
        val loggedInUser = userLogsIn()
        driver.navigate().to(login)
        val expectedCookie = Cookie(Authentication.loginCookieName, "${System.getenv("BANDAGE_API_KEY")}_${loggedInUser.userId}", "login")

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.currentUrl, equalTo(dashboard))
        assertThat(driver.manage().cookies, equalTo(setOf(expectedCookie)))
    }

    @Test
    fun `static 404 page is served on 404 response`(approver: Approver) {
        driver.navigate().to("/not-found")

        assertThat(driver.status, equalTo(NOT_FOUND))
        assertThat(driver.currentUrl, equalTo("/not-found"))
        approver.assertApproved(driver.pageSource)
    }

    private fun userLogsIn(): User {
        driver.navigate().to(login)

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.title, equalTo("Bandage"))

        val usernameField = driver.findElement(By.cssSelector("#user")) ?: fail("username field not found")
        val lastUser = UserManagement().users.last()
        val option = usernameField.findElement(By.cssSelector("option:contains(${lastUser.initials})"))
            ?: fail("option ${lastUser.initials} is not available")
        option.click()

        val passwordField = driver.findElement(By.cssSelector("#password")) ?: fail("password field not found")
        passwordField.sendKeys(System.getenv("BANDAGE_PASSWORD"))

        val loginButton = driver.findElement(By.cssSelector("button[type=\"submit\"][name=\"login\"]"))
            ?: fail("login button not found")
        loginButton.click()

        return lastUser
    }
}
