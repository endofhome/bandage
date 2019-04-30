package functional.tests

import Authentication
import Bandage
import RouteMappings.dashboard
import RouteMappings.index
import RouteMappings.login
import User
import UserManagement
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import config.BandageConfigItem.API_KEY
import config.BandageConfigItem.PASSWORD
import config.dummyConfiguration
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.testing.ApprovalTest
import org.http4k.testing.Approver
import org.http4k.testing.assertApproved
import org.http4k.webdriver.Http4kWebDriver
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.By
import org.openqa.selenium.Cookie
import storage.AudioFileMetadata
import storage.DummyMetadataStorage
import storage.Duration
import storage.StubMetadataStorage
import java.util.*

@ExtendWith(ApprovalTest::class)
class BandageTest {

    private val config = dummyConfiguration()
    private val bandage = Bandage(config, DummyMetadataStorage).app
    private val driver = Http4kWebDriver(bandage)

    @Test
    fun `index redirects to login`() {
        driver.navigate().to(index)

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.currentUrl, equalTo(login))
    }

    @Test
    fun `can log in via login page`() {
        val loggedInUser = driver.userLogsIn()

        val loginCookie = driver.manage().getCookieNamed(Authentication.loginCookieName) ?: fail("login cookie not set")

        assertThat(loginCookie, equalTo(validCookieFor(loggedInUser)))
        assertThat(driver.currentUrl, equalTo(dashboard))
        assertThat(driver.title, equalTo("Bandage"))
    }

    @Test
    fun `can log out`() {
        driver.userLogsIn()
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
        val loggedInUser = driver.userLogsIn()
        driver.navigate().to(index)

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.currentUrl, equalTo(dashboard))
        assertThat(driver.manage().cookies, equalTo(setOf(validCookieFor(loggedInUser))))
    }

    @Test
    fun `accessing login page with a logged in cookie redirects to dashboard page`() {
        val loggedInUser = driver.userLogsIn()
        driver.navigate().to(login)

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.currentUrl, equalTo(dashboard))
        assertThat(driver.manage().cookies, equalTo(setOf(validCookieFor(loggedInUser))))
    }

    @Test
    fun `static 404 page is served on 404 response`(approver: Approver) {
        val request = Request(GET, "not-found")
        val response = bandage(request)

        approver.assertApproved(response, NOT_FOUND)
    }

    @Test
    fun `static 500 page is served on 500 response`(approver: Approver) {
        val internalServerError: HttpHandler = { Response(Status.INTERNAL_SERVER_ERROR) }
        val handlerWithFilters = internalServerError.with(Bandage.StaticConfig.filters)
        val request = Request(GET, "/will-blow-up")
        val response = handlerWithFilters(request)

        approver.assertApproved(response, INTERNAL_SERVER_ERROR)
    }

    @Test
    fun `list of audio tracks are available in dashboard`() {
        val metadataWithNullValues = exampleAudioFileMetadata.copy(
            uuid = UUID.fromString("f8ab4da2-7ace-4e62-9db0-430af0ba4876"),
            duration = null,
            title = "track with null duration",
            format = "wav"
        )
        val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioFileMetadata, metadataWithNullValues))
        val bandage = Bandage(config, metadataStorage).app
        val driver = Http4kWebDriver(bandage)

        driver.userLogsIn()
        driver.navigate().to(dashboard)

        val folderh4 = driver.findElement(By.cssSelector("h4[data-test=\"[folder-my_folder]\"]")) ?: fail("FolderViewModel h4 is unavailable")
        assertThat(folderh4.text, equalTo("my_folder"))

        val firstFile = driver.findElement(By.cssSelector("div[data-test=\"[file-68ab4da2-7ace-4e62-9db0-430af0ba487f]\"]")) ?: fail("First file div is unavailable")
        assertThat(firstFile.text, equalTo("some title | 0:21 | mp3"))

        val fileWithNullDuration = driver.findElement(By.cssSelector("div[data-test=\"[file-f8ab4da2-7ace-4e62-9db0-430af0ba4876]\"]")) ?: fail("First file div is unavailable")
        assertThat(fileWithNullDuration.text, equalTo("track with null duration | wav"))
    }

    private fun Http4kWebDriver.userLogsIn(): User {
        this.navigate().to(login)

        assertThat(this.status, equalTo(OK))
        assertThat(this.title, equalTo("Bandage"))

        val usernameField = this.findElement(By.cssSelector("#user")) ?: fail("username field not found")
        val lastUser = UserManagement(config).users.last()
        val option = usernameField.findElement(By.cssSelector("option:contains(${lastUser.initials})"))
            ?: fail("option ${lastUser.initials} is not available")
        option.click()

        val passwordField = this.findElement(By.cssSelector("#password")) ?: fail("password field not found")
        passwordField.sendKeys(config.get(PASSWORD))

        val loginButton = this.findElement(By.cssSelector("button[type=\"submit\"][name=\"login\"]"))
            ?: fail("login button not found")
        loginButton.click()

        return lastUser
    }

    private fun validCookieFor(loggedInUser: User) =
        Cookie(Authentication.loginCookieName, "${config.get(API_KEY)}_${loggedInUser.userId}", "login")

    private val exampleAudioFileMetadata = AudioFileMetadata(
        UUID.fromString("68ab4da2-7ace-4e62-9db0-430af0ba487f"),
        "some artist",
        "some album",
        "some title",
        "mp3",
        "320000",
        Duration("21"),
        12345,
        "10000",
        "https://www.passwordprotectedlink.com",
        "/my_folder/my_file",
        "someamazinghashstring"
    )
}
