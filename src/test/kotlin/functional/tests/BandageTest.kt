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
import org.openqa.selenium.WebElement
import storage.DummyFileStorage
import storage.DummyMetadataStorage
import storage.StubMetadataStorage
import java.util.UUID

@ExtendWith(ApprovalTest::class)
class BandageTest {

    private val config = dummyConfiguration()
    private val bandage = Bandage(config, DummyMetadataStorage(), DummyFileStorage()).app
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
            uuid = UUID.fromString(exampleAudioFileMetadata.uuid.toString().reversed()),
            duration = null,
            title = "track with null duration",
            format = "wav"
        )
        val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioFileMetadata, metadataWithNullValues))
        val bandage = Bandage(config, metadataStorage, DummyFileStorage()).app
        val driver = Http4kWebDriver(bandage)

        driver.userLogsIn()
        driver.navigate().to(dashboard)

        val folderh4 = driver.findElement(By.cssSelector("h4[data-test=\"[folder-my_folder]\"]")) ?: fail("FolderViewModel h4 is unavailable")
        assertThat(folderh4.text, equalTo("my_folder"))

        val firstFile = driver.findElement(By.cssSelector("div[data-test=\"[file-${exampleAudioFileMetadata.uuid}]\"]")) ?: fail("First file div is unavailable")
        assertThat(firstFile.text, equalTo("${exampleAudioFileMetadata.title} | 0:21 | ${exampleAudioFileMetadata.format} | play"))

        val fileWithNullDuration = driver.findElement(By.cssSelector("div[data-test=\"[file-${metadataWithNullValues.uuid}]\"]")) ?: fail("First file div is unavailable")
        assertThat(fileWithNullDuration.text, equalTo("${metadataWithNullValues.title} | ${metadataWithNullValues.format} | play"))
    }

    @Test
    fun `audio tracks can be played via dashboard`() {
        val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioFileMetadata))
        val bandage = Bandage(config, metadataStorage, DummyFileStorage()).app
        val driver = Http4kWebDriver(bandage)

        driver.userLogsInAndPlaysATrack()

        driver.findElement(By.cssSelector("audio[data-test=\"[play_file-${exampleAudioFileMetadata.uuid}]\"]")) ?: fail("Audio player footer is unavailable")
        val playerMetadata = driver.findElement(By.cssSelector("span[data-test=\"[audio-player-metadata]\"]")) ?: fail("Audio player metadata is unavailable")
        assertThat(playerMetadata.text, equalTo("${exampleAudioFileMetadata.title} | 0:21 | ${exampleAudioFileMetadata.format} (320 kbps)"))
        assertThat(driver.currentUrl, equalTo("/dashboard?id=${exampleAudioFileMetadata.uuid}#${exampleAudioFileMetadata.uuid}"))
    }

    @Test
    fun `currently playing track is highlighted`() {
        val unplayedTrackOne = exampleAudioFileMetadata.copy(uuid = UUID.randomUUID())
        val unplayedTrackTwo = exampleAudioFileMetadata.copy(uuid = UUID.randomUUID())
        val metadataStorage = StubMetadataStorage(mutableListOf(unplayedTrackOne, exampleAudioFileMetadata, unplayedTrackTwo))
        val bandage = Bandage(config, metadataStorage, DummyFileStorage()).app
        val driver = Http4kWebDriver(bandage)

        val trackToPlay = driver.userLogsInAndPlaysATrack()

        val highlightedElements = driver.findElements(By.cssSelector(".highlighted")) ?: fail("No highlighted elements")
        assertThat(highlightedElements.single().getAttribute("data-test"), equalTo(trackToPlay.getAttribute("data-test")))
    }

    private fun Http4kWebDriver.userLogsInAndPlaysATrack(): WebElement {
        this.userLogsIn()
        this.navigate().to(dashboard)

        val trackToPlay = findElement(By.cssSelector("div[data-test=\"[file-${exampleAudioFileMetadata.uuid}]\"]"))
            ?: fail("Div for track to play is unavailable")
        val playLink = trackToPlay.findElement(By.cssSelector("a[data-test=\"[play-audio-link]\"]"))
            ?: fail("Play link is unavailable")
        playLink.click()

        return trackToPlay
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
}
