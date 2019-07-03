package functional.tests

import Authentication
import Authentication.Companion.Cookies.LOGIN
import Bandage
import RouteMappings.dashboard
import RouteMappings.index
import RouteMappings.login
import User
import UserManagement
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import com.natpryce.hamkrest.startsWith
import config.BandageConfigItem.API_KEY
import config.BandageConfigItem.PASSWORD
import config.dummyConfiguration
import exampleAudioTrackMetadata
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.By
import org.openqa.selenium.Cookie
import org.openqa.selenium.WebElement
import result.expectSuccess
import storage.Collection
import storage.DummyFileStorage
import storage.DummyMetadataStorage
import storage.StubMetadataStorage
import java.time.Instant.EPOCH
import java.time.temporal.ChronoUnit.HOURS
import java.util.UUID

@ExtendWith(ApprovalTest::class)
class BandageTest {

    private val config = dummyConfiguration()
    private val bandage = Bandage(config, DummyMetadataStorage(), DummyFileStorage()).app
    private val driver = Http4kWebDriver(bandage)

    @BeforeEach
    fun resetCookies() {
        Authentication.Companion.Cookies.values().forEach {
            driver.manage().deleteCookieNamed(it.cookieName)
        }
    }

    @Test
    fun `index redirects to login`() {
        driver.navigate().to(index)

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.currentUrl, equalTo(login))
    }

    @Test
    fun `can log in via login page`() {
        driver.navigate().to(login)
        val loggedInUser = driver.userLogsIn()

        val loginCookie = driver.manage().getCookieNamed(LOGIN.cookieName) ?: fail("login cookie not set")
        val username = driver.findElement(By.cssSelector("span[data-test=\"user-short-name\"]"))

        assertThat(loginCookie, equalTo(validCookieFor(loggedInUser)))
        assertThat(driver.currentUrl, equalTo(dashboard))
        assertThat(driver.title, equalTo("Bandage"))
        assertThat(username?.text, equalTo(loggedInUser.shortName))
    }

    @Test
    fun `can log out`() {
        driver.navigate().to(login)
        driver.userLogsIn()
        val logoutLink = driver.findElement(By.cssSelector("a[data-test=\"logout\"]")) ?: fail("Logout link is unavailable")
        logoutLink.click()

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.manage().cookies, allInvalid)
        assertThat(driver.currentUrl, equalTo(login))
    }
    
    @Test
    fun `cannot access dashboard page if not logged in`() {
        driver.navigate().to(dashboard)

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.currentUrl, equalTo(login))
        assertThat(driver.manage().cookies, allInvalid)
    }

    @Test
    fun `logging in after being redirected from index results in redirection to dashboard without fragment identifier`() {
        val requestedResource = index
        driver.navigate().to(requestedResource)
        assertThat(driver.currentUrl, equalTo(login))

        driver.userLogsIn()
        assertThat(driver.currentUrl, equalTo(dashboard))
    }

    @Test
    fun `logging in after being redirected from dashboard results in redirection to the originally requested resource with fragment identifier`() {
        val requestedResource = "$dashboard?id=dd505fee-93f7-4fb3-a7fc-f48d6efb9c7e"

        driver.navigate().to(requestedResource)
        assertThat(driver.currentUrl, equalTo(login))

        driver.userLogsIn()
        assertThat(driver.currentUrl, equalTo("$requestedResource#dd505fee-93f7-4fb3-a7fc-f48d6efb9c7e"))
    }

    @Test
    fun `accessing index page with a logged in cookie redirects to dashboard page`() {
        driver.navigate().to(login)
        val loggedInUser = driver.userLogsIn()
        driver.navigate().to(index)

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.currentUrl, equalTo(dashboard))
        assertThat(driver.manage().cookies, equalTo(setOf(validCookieFor(loggedInUser))))
    }

    @Test
    fun `accessing login page with a logged in cookie redirects to dashboard page`() {
        driver.navigate().to(login)
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
        val metadataWithNullValues = exampleAudioTrackMetadata.copy(
            uuid = UUID.nameUUIDFromBytes("metadataWithNullValues".toByteArray()),
            duration = null,
            title = "track with null duration",
            format = "wav"
        )
        val metadataWithSameTitle = exampleAudioTrackMetadata.copy(
            uuid = UUID.nameUUIDFromBytes("metadataWithSameTitle".toByteArray()),
            recordedTimestamp = exampleAudioTrackMetadata.recordedTimestamp.minusHours(1)
        )
        val metadataUntitled = exampleAudioTrackMetadata.copy(
            uuid = UUID.nameUUIDFromBytes("metadataUntitled".toByteArray()),
            title = "untitled"
        )
        val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioTrackMetadata, metadataWithSameTitle, metadataWithNullValues, metadataUntitled))
        val bandage = Bandage(config, metadataStorage, DummyFileStorage()).app
        val driver = Http4kWebDriver(bandage)

        driver.navigate().to(login)
        driver.userLogsIn()
        driver.navigate().to(dashboard)

        val folderh4 = driver.findElement(By.cssSelector("h4[data-test=\"[date-1970-01-01]\"]")) ?: fail("h4 is unavailable")
        assertThat(folderh4.text, equalTo("1 January 1970"))

        val firstFile = driver.findElement(By.cssSelector("div[data-test=\"[track-${exampleAudioTrackMetadata.uuid}]\"]")) ?: fail("Div for ${exampleAudioTrackMetadata.uuid} is unavailable")
        assertThat(firstFile.text, equalTo("${exampleAudioTrackMetadata.title} (take 2) | 0:21 | ${exampleAudioTrackMetadata.format} | play"))

        val fileWithNullDuration = driver.findElement(By.cssSelector("div[data-test=\"[track-${metadataWithNullValues.uuid}]\"]")) ?: fail("Div for ${metadataWithNullValues.uuid} is unavailable")
        assertThat(fileWithNullDuration.text, equalTo("${metadataWithNullValues.title} | ${metadataWithNullValues.format} | play"))

        val fileWithSameTitle = driver.findElement(By.cssSelector("div[data-test=\"[track-${metadataWithSameTitle.uuid}]\"]")) ?: fail("Div for ${metadataWithSameTitle.uuid} is unavailable")
        assertThat(fileWithSameTitle.text, equalTo("${metadataWithSameTitle.title} (take 1) | 0:21 | ${metadataWithSameTitle.format} | play"))

        val fileUntitled = driver.findElement(By.cssSelector("div[data-test=\"[track-${metadataUntitled.uuid}]\"]")) ?: fail("Div for ${metadataUntitled.uuid} is unavailable")
        assertThat(fileUntitled.text, equalTo("${metadataUntitled.title} | 0:21 | ${metadataUntitled.format} | play"))
    }

    @Test
    fun `audio tracks are sorted and grouped by recorded timestamp`() {
        val earliestRecordedTrack = exampleAudioTrackMetadata.copy(title = "earliest recorded track")
        val middleTrack = exampleAudioTrackMetadata.copy(
            title = "middle track",
            recordedTimestamp = exampleAudioTrackMetadata.recordedTimestamp.plusDays(1)
        )
        val latestRecordedTrack = exampleAudioTrackMetadata.copy(
            title = "latest recorded track",
            recordedTimestamp = exampleAudioTrackMetadata.recordedTimestamp.plusDays(1).plusHours(1)
        )
        val metadataStorage = StubMetadataStorage(mutableListOf(middleTrack, earliestRecordedTrack, latestRecordedTrack))
        val bandage = Bandage(config, metadataStorage, DummyFileStorage()).app
        val driver = Http4kWebDriver(bandage)

        driver.navigate().to(login)
        driver.userLogsIn()
        driver.navigate().to(dashboard)

        val h4Elements = driver.findElements(By.cssSelector("h4")) ?: fail("h4 elements are unavailable")
        assertThat(h4Elements[0].text, equalTo("2 January 1970"))
        assertThat(h4Elements[1].text, equalTo("1 January 1970"))

        val dataTrackElements = driver.findElements(By.cssSelector("[data-track]")) ?: fail("data-track elements are unavailable")
        assertThat(dataTrackElements[0].text, startsWith(latestRecordedTrack.title))
        assertThat(dataTrackElements[1].text, startsWith(middleTrack.title))
        assertThat(dataTrackElements[2].text, startsWith(earliestRecordedTrack.title))
    }

    @Test
    fun `audio tracks can be played via dashboard`() {
        val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioTrackMetadata))
        val bandage = Bandage(config, metadataStorage, DummyFileStorage()).app
        val driver = Http4kWebDriver(bandage)

        driver.userLogsInAndPlaysATrack()

        driver.assertAudioPlayerPresent(autoplayAttributeState = present())
    }

    @Test
    fun `full audio track metadata can be viewed`() {
        val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioTrackMetadata.copy(
            collections = listOf(Collection.ExistingCollection(UUID.randomUUID(), "some_collection", setOf(exampleAudioTrackMetadata.uuid)))))
        )
        val bandage = Bandage(config, metadataStorage, DummyFileStorage()).app
        val driver = Http4kWebDriver(bandage)

        driver.navigate().to(login)
        driver.userLogsIn()

        val trackToView = driver.findElement(By.cssSelector("div[data-test=\"[track-${exampleAudioTrackMetadata.uuid}]\"]")) ?: fail("Track ${exampleAudioTrackMetadata.uuid} is unavailable")
        val viewMetadataLink = trackToView.findElement(By.cssSelector("a[data-test=\"[metadata-link]\"]")) ?: fail("Metadata link for ${exampleAudioTrackMetadata.uuid} is unavailable")
        viewMetadataLink.click()

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.currentUrl, equalTo("/tracks/${exampleAudioTrackMetadata.uuid}"))

        val heading = driver.findElement(By.cssSelector("h4[data-test=\"heading\"]")) ?: fail("Heading for ${exampleAudioTrackMetadata.uuid} is unavailable")
        assertThat(heading.text, equalTo(exampleAudioTrackMetadata.title))
        val artist = driver.findElement(By.cssSelector("input[data-test=\"artist\"]")) ?: fail("Artist for ${exampleAudioTrackMetadata.uuid} is unavailable")
        assertThat(artist.getAttribute("value"), equalTo(exampleAudioTrackMetadata.artist))
        val title = driver.findElement(By.cssSelector("input[data-test=\"title\"]")) ?: fail("Title for ${exampleAudioTrackMetadata.uuid} is unavailable")
        assertThat(title.getAttribute("value"), equalTo(exampleAudioTrackMetadata.title))
        val duration = driver.findElement(By.cssSelector("input[data-test=\"duration\"]")) ?: fail("Duration for ${exampleAudioTrackMetadata.uuid} is unavailable")
        assertThat(duration.getAttribute("value"), equalTo("0:21"))
        val format = driver.findElement(By.cssSelector("input[data-test=\"format\"]")) ?: fail("Format for ${exampleAudioTrackMetadata.uuid} is unavailable")
        assertThat(format.getAttribute("value"), equalTo("mp3"))
        val bitrate = driver.findElement(By.cssSelector("input[data-test=\"bitrate\"]")) ?: fail("Bitrate for ${exampleAudioTrackMetadata.uuid} is unavailable")
        assertThat(bitrate.getAttribute("value"), equalTo("320 kbps"))
        val recordedOn = driver.findElement(By.cssSelector("input[data-test=\"recordedOn\"]")) ?: fail("Recorded on for ${exampleAudioTrackMetadata.uuid} is unavailable")
        assertThat(recordedOn.getAttribute("value"), equalTo("01/01/1970   12:00"))
        val uploadedOn = driver.findElement(By.cssSelector("input[data-test=\"uploadedOn\"]")) ?: fail("Uploaded on for ${exampleAudioTrackMetadata.uuid} is unavailable")
        assertThat(uploadedOn.getAttribute("value"), equalTo("01/01/1970   12:00"))
        val aCollection = driver.findElement(By.cssSelector("[data-test=\"collection-some_collection\"]")) ?: fail("Collections for ${exampleAudioTrackMetadata.uuid} is unavailable")
        assertThat(aCollection.text, equalTo("some_collection"))
    }

    @Test
    fun `title of audio track can be updated`() {
        val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioTrackMetadata))
        val bandage = Bandage(config, metadataStorage, DummyFileStorage()).app
        val driver = Http4kWebDriver(bandage)

        val trackToView = driver.loginAndVisitMetadataPage()

        val title = driver.findElement(By.cssSelector("input[data-test=\"title\"]")) ?: fail("Title for ${exampleAudioTrackMetadata.uuid} is unavailable")
        title.clear()
        title.sendKeys("a new title")

        val editButton = driver.findElement(By.cssSelector("button[data-test=\"edit-metadata\"]")) ?: fail("Title for ${exampleAudioTrackMetadata.uuid} is unavailable")
        editButton.click()

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.currentUrl, equalTo("/dashboard?highlighted=${exampleAudioTrackMetadata.uuid}#${exampleAudioTrackMetadata.uuid}"))
        assertThat(metadataStorage.findTrack(exampleAudioTrackMetadata.uuid).expectSuccess()?.title, equalTo("a new title"))

        val highlightedElements = driver.findElements(By.cssSelector(".highlighted")) ?: fail("No highlighted elements")
        assertThat(highlightedElements.single().getAttribute("data-test"), equalTo(trackToView.getAttribute("data-test")))
    }

    private fun Http4kWebDriver.loginAndVisitMetadataPage(): WebElement {
        navigate().to(login)
        userLogsIn()

        val trackToView =
            findElement(By.cssSelector("div[data-test=\"[track-${exampleAudioTrackMetadata.uuid}]\"]"))
                ?: fail("Track ${exampleAudioTrackMetadata.uuid} is unavailable")
        val viewMetadataLink = trackToView.findElement(By.cssSelector("a[data-test=\"[metadata-link]\"]"))
            ?: fail("Metadata link for ${exampleAudioTrackMetadata.uuid} is unavailable")
        viewMetadataLink.click()
        return trackToView
    }

    @Test
    fun `audio track can be played from metadata view`() {
        val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioTrackMetadata))
        val bandage = Bandage(config, metadataStorage, DummyFileStorage()).app
        val driver = Http4kWebDriver(bandage)

        driver.loginAndVisitMetadataPage()

        driver.assertAudioPlayerPresent(autoplayAttributeState = absent())
    }

    @Test
    fun `currently playing track is highlighted`() {
        val unplayedTrackOne = exampleAudioTrackMetadata.copy(uuid = UUID.randomUUID())
        val unplayedTrackTwo = exampleAudioTrackMetadata.copy(uuid = UUID.randomUUID())
        val metadataStorage = StubMetadataStorage(mutableListOf(unplayedTrackOne,
            exampleAudioTrackMetadata, unplayedTrackTwo))
        val bandage = Bandage(config, metadataStorage, DummyFileStorage()).app
        val driver = Http4kWebDriver(bandage)

        val trackToPlay = driver.userLogsInAndPlaysATrack()

        val highlightedElements = driver.findElements(By.cssSelector(".highlighted")) ?: fail("No highlighted elements")
        assertThat(highlightedElements.single().getAttribute("data-test"), equalTo(trackToPlay.getAttribute("data-test")))
    }

    private fun Http4kWebDriver.userLogsInAndPlaysATrack(): WebElement {
        this.navigate().to(login)
        this.userLogsIn()
        this.navigate().to(dashboard)

        val trackToPlay = findElement(By.cssSelector("div[data-test=\"[track-${exampleAudioTrackMetadata.uuid}]\"]"))
            ?: fail("Div for track to play is unavailable")
        val playLink = trackToPlay.findElement(By.cssSelector("a[data-test=\"[play-audio-link]\"]"))
            ?: fail("Play link is unavailable")
        playLink.click()

        return trackToPlay
    }

    private fun Http4kWebDriver.userLogsIn(): User {
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

    private fun Http4kWebDriver.assertAudioPlayerPresent(autoplayAttributeState: Matcher<String?>) {
        val audioPlayer =
            findElement(By.cssSelector("audio[data-test=\"[play_file-${exampleAudioTrackMetadata.uuid}]\"]"))
                ?: fail("Audio player footer is unavailable")
        val playerMetadata = findElement(By.cssSelector("div[data-test=\"[audio-player-metadata]\"]"))
            ?: fail("Audio player metadata is unavailable")
        assertThat(
            playerMetadata.text,
            equalTo("${exampleAudioTrackMetadata.title} | 0:21 | ${exampleAudioTrackMetadata.format} (320 kbps)")
        )
        assertThat(audioPlayer.getAttribute("autoplay"), autoplayAttributeState)
    }

    private fun validCookieFor(loggedInUser: User) =
        Cookie(LOGIN.cookieName, "${config.get(API_KEY)}_${loggedInUser.userId}", "login")
}

val allInvalid = Matcher(Set<Cookie>::allInvalid)
fun Set<Cookie>.allInvalid() = this.all { it.value.isEmpty() && it.expiry.toInstant() in EPOCH.minus(1, HOURS)..EPOCH.plus(12, HOURS) }
