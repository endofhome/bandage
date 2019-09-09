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
import http.HttpConfig
import io.github.bonigarcia.wdm.WebDriverManager
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.testing.ApprovalTest
import org.http4k.testing.Approver
import org.http4k.testing.assertApproved
import org.http4k.webdriver.Http4kWebDriver
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.openqa.selenium.By
import org.openqa.selenium.Cookie
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.Select
import result.expectSuccess
import storage.Collection
import storage.DummyFileStorage
import storage.DummyMetadataStorage
import storage.StubFileStorage
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
        val internalServerError: HttpHandler = { Response(INTERNAL_SERVER_ERROR) }
        val handlerWithFilters = internalServerError.with(Bandage.StaticConfig.filters)
        val request = Request(GET, "/will-blow-up")
        val response = handlerWithFilters(request)

        approver.assertApproved(response, INTERNAL_SERVER_ERROR)
    }

    @Test
    fun `static 400 page is served on 400 response`(approver: Approver) {
        val internalServerError: HttpHandler = { Response(BAD_REQUEST) }
        val handlerWithFilters = internalServerError.with(Bandage.StaticConfig.filters)
        val request = Request(GET, "/bad-request")
        val response = handlerWithFilters(request)

        approver.assertApproved(response, BAD_REQUEST)
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
        val metadataCompletelyUntitled = exampleAudioTrackMetadata.copy(
            uuid = UUID.nameUUIDFromBytes("metadataUntitled".toByteArray()),
            title = "untitled",
            workingTitles = emptyList()
        )

        val metadataUntitledWithWorkingTitle = exampleAudioTrackMetadata.copy(
            uuid = UUID.nameUUIDFromBytes("metadataWithOnlyWorkingTitle".toByteArray()),
            title = "untitled",
            workingTitles = listOf("first working title", "second working title")
        )
        val metadataStorage = StubMetadataStorage(mutableListOf(
            exampleAudioTrackMetadata,
            metadataWithSameTitle,
            metadataWithNullValues,
            metadataCompletelyUntitled,
            metadataUntitledWithWorkingTitle
        ))
        val bandage = Bandage(config, metadataStorage, DummyFileStorage()).app
        val driver = Http4kWebDriver(bandage)

        driver.navigate().to(login)
        driver.userLogsIn()
        driver.navigate().to(dashboard)

        val folderh4 = driver.findElement(By.cssSelector("h4[data-test=\"[date-1970-01-01]\"]")) ?: fail("h4 is unavailable")
        assertThat(folderh4.text, equalTo("1 January 1970"))

        val track = driver.findElement(By.cssSelector("div[data-test=\"[track-${exampleAudioTrackMetadata.uuid}]\"]")) ?: fail("Div for ${exampleAudioTrackMetadata.uuid} is unavailable")
        assertThat(track.text, equalTo("${exampleAudioTrackMetadata.title} (take 2) | 0:21 | ${exampleAudioTrackMetadata.format} | play"))
        assertThat(track.findElement(By.cssSelector("a")).getAttribute("class"), equalTo("title-link"))

        val trackWithNullDuration = driver.findElement(By.cssSelector("div[data-test=\"[track-${metadataWithNullValues.uuid}]\"]")) ?: fail("Div for ${metadataWithNullValues.uuid} is unavailable")
        assertThat(trackWithNullDuration.text, equalTo("${metadataWithNullValues.title} | ${metadataWithNullValues.format} | play"))

        val trackWithSameTitle = driver.findElement(By.cssSelector("div[data-test=\"[track-${metadataWithSameTitle.uuid}]\"]")) ?: fail("Div for ${metadataWithSameTitle.uuid} is unavailable")
        assertThat(trackWithSameTitle.text, equalTo("${metadataWithSameTitle.title} (take 1) | 0:21 | ${metadataWithSameTitle.format} | play"))

        val trackCompletelyUntitled = driver.findElement(By.cssSelector("div[data-test=\"[track-${metadataCompletelyUntitled.uuid}]\"]")) ?: fail("Div for ${metadataCompletelyUntitled.uuid} is unavailable")
        assertThat(trackCompletelyUntitled.text, equalTo("${metadataCompletelyUntitled.title} | 0:21 | ${metadataCompletelyUntitled.format} | play"))

        val trackUntitledWithWorkingTitle = driver.findElement(By.cssSelector("div[data-test=\"[track-${metadataUntitledWithWorkingTitle.uuid}]\"]")) ?: fail("Div for ${metadataUntitledWithWorkingTitle.uuid} is unavailable")
        assertThat(trackUntitledWithWorkingTitle.text, equalTo("${metadataUntitledWithWorkingTitle.workingTitles.first()} | 0:21 | ${metadataUntitledWithWorkingTitle.format} | play"))
        assertThat(trackUntitledWithWorkingTitle.findElement(By.cssSelector("a")).getAttribute("class"), equalTo("working-title-link"))
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
        assertThat(heading.text, equalTo(exampleAudioTrackMetadata.preferredTitle().first))
        val artist = driver.findElement(By.cssSelector("input[data-test=\"artist\"]")) ?: fail("Artist for ${exampleAudioTrackMetadata.uuid} is unavailable")
        assertThat(artist.getAttribute("value"), equalTo(exampleAudioTrackMetadata.artist))
        val title = driver.findElement(By.cssSelector("input[data-test=\"title\"]")) ?: fail("Title for ${exampleAudioTrackMetadata.uuid} is unavailable")
        assertThat(title.getAttribute("value"), equalTo(exampleAudioTrackMetadata.title))
        val workingTitle1 = driver.findElement(By.cssSelector("input[data-test=\"working-title\"]")) ?: fail("First working title for ${exampleAudioTrackMetadata.uuid} is unavailable")
        assertThat(workingTitle1.getAttribute("value"), equalTo(exampleAudioTrackMetadata.workingTitles[0]))
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
    fun `title and working title of audio track can be updated`() {
        val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioTrackMetadata))
        val bandage = Bandage(config, metadataStorage, DummyFileStorage()).app
        val driver = Http4kWebDriver(bandage)

        val trackToView = driver.loginAndVisitMetadataPage()

        val title = driver.findElement(By.cssSelector("input[data-test=\"title\"]")) ?: fail("Title for ${exampleAudioTrackMetadata.uuid} is unavailable")
        title.clear()
        title.sendKeys("a new title")

        val workingTitle = driver.findElement(By.cssSelector("input[data-test=\"working-title\"]")) ?: fail("Working title for ${exampleAudioTrackMetadata.uuid} is unavailable")
        workingTitle.clear()
        workingTitle.sendKeys("a new working title")

        val editButton = driver.findElement(By.cssSelector("button[data-test=\"edit-metadata\"]")) ?: fail("Title for ${exampleAudioTrackMetadata.uuid} is unavailable")
        editButton.click()

        assertThat(driver.status, equalTo(OK))
        assertThat(driver.currentUrl, equalTo("/dashboard?highlighted=${exampleAudioTrackMetadata.uuid}#${exampleAudioTrackMetadata.uuid}"))
        val updatedMetadata = metadataStorage.findTrack(exampleAudioTrackMetadata.uuid).expectSuccess()
        assertThat(updatedMetadata?.title, equalTo("a new title"))
        assertThat(updatedMetadata?.workingTitles?.first(), equalTo("a new working title"))

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

    @Nested
    @DisplayName("Uploading tracks")
    inner class UploadingTracks {

        @BeforeEach
        fun setup() {
            WebDriverManager.chromedriver().setup()
        }

        @Test
        fun `a track with no date or time in the filename can be uploaded`() {
            assertFileUploadedCorrectly(
                filePath = "/src/test/resources/files/440Hz-5sec.mp3",
                dateh4DataTestAttr = "date-1970-01-01",
                dateh4Text = "1970",
                recordedOnMetadata = "1970"
            )
        }

        @Test
        fun `a track with only a date in the filename can be uploaded`() {
            assertFileUploadedCorrectly(
                filePath = "/src/test/resources/files/2019-09-07 - 440Hz-5sec.mp3",
                dateh4DataTestAttr = "date-2019-09-07",
                dateh4Text = "7 September 2019",
                recordedOnMetadata = "07/09/2019"
            )
        }

        @Test
        fun `a track with a date and time in the filename can be uploaded`() {
            assertFileUploadedCorrectly(
                filePath = "/src/test/resources/files/2019-09-07_19-32-13 - 440Hz-5sec.mp3",
                dateh4DataTestAttr = "date-2019-09-07",
                dateh4Text = "7 September 2019",
                recordedOnMetadata = "07/09/2019   19:32"
            )
        }

        private fun assertFileUploadedCorrectly(
            filePath: String,
            dateh4DataTestAttr: String,
            dateh4Text: String,
            recordedOnMetadata: String
        ) {
            val bandage = Bandage(config, StubMetadataStorage(mutableListOf()), StubFileStorage(mutableMapOf())).app
            val driver = ChromeDriver()
            val baseUrl = "http://localhost:7000"

            val server = bandage.asServer(Jetty(HttpConfig.port))
            server.start()
            driver.navigate().to(baseUrl)
            driver.userLogsIn()

            val uploadLink = driver.findElement(By.cssSelector("a[data-test=\"upload-link\"]")) ?: fail("Upload link is unavailable")
            uploadLink.click()

            assertThat(driver.currentUrl, equalTo("$baseUrl/upload"))
            val uploadTrackForm = driver.findElement(By.cssSelector("form[data-test=\"upload-track-form\"]")) ?: fail("Upload track form is unavailable")
            val filePicker = uploadTrackForm.findElement(By.cssSelector("input[data-test=\"file-picker\"]")) ?: fail("File picker is unavailable")
            filePicker.sendKeys(System.getProperty("user.dir") + filePath)
            uploadTrackForm.submit()

            assertThat(driver.currentUrl, equalTo("$baseUrl/upload-preview"))

            val previewForm = driver.findElement(By.cssSelector("form[data-test=\"preview-metadata-form\"]"))
                ?: fail("Preview metadata form is unavailable")
            previewForm.submit()

            assertThat(driver.currentUrl, startsWith("$baseUrl/dashboard?highlighted="))

            val uuidForTrack = driver.currentUrl.substringAfter("$baseUrl/dashboard?highlighted=")
            val dateh4 =
                driver.findElement(By.cssSelector("h4[data-test=\"[$dateh4DataTestAttr]\"]")) ?: fail("h4 is unavailable")
            assertThat(dateh4.text, equalTo(dateh4Text))

            val track = driver.findElement(By.cssSelector("div[data-track]")) ?: fail("Div for track is unavailable")
            assertThat(track.text, equalTo("440Hz Sine Wave | 0:05 | mp3 | play"))
            assertThat(track.findElement(By.cssSelector("a")).getAttribute("class"), equalTo("working-title-link"))

            track.findElement(By.cssSelector("a")).click()
            assertThat(driver.currentUrl, equalTo("$baseUrl/tracks/$uuidForTrack"))

            val recordedTime = driver.findElement(By.cssSelector("input[data-test=\"recordedOn\"]"))
            assertThat(recordedTime.getAttribute("value"), equalTo(recordedOnMetadata))

            server.stop()
            driver.close()
        }
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

    private fun WebDriver.userLogsIn(): User {
        if (this is Http4kWebDriver) { assertThat(this.status, equalTo(OK)) }
        assertThat(this.title, equalTo("Bandage"))

        val lastUser = UserManagement(config).users.last()
        val userOptions = this.findElement(By.id("user")) ?: fail("user select element is not available")
        val option = Select(userOptions).options.find { it.text == lastUser.initials } ?: fail("option ${lastUser.initials} is not available")
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
