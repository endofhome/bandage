package unit.tests

import RouteMappings.login
import User
import UserManagement
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import config.dummyConfiguration
import handlers.Login
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.OK
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import org.junit.jupiter.api.Test

class LoginTest {
    private val config = dummyConfiguration()
    private val someUsers = listOf(
        User("17", "Some full name"),
        User("An ID", "Another full name")
    )

    @Test
    fun `all known users are present in login options`() {
        val options = renderPageAndSelectOptions()

        someUsers.forEachIndexed { index, user ->
            assertThat(options[index].text(), equalTo(user.initials))
            assertThat(options[index].attr("value"), equalTo(user.userId))
        }
    }

    @Test
    fun `login does not present user options who are not known users`() {
        val options = renderPageAndSelectOptions()
        val userIds = someUsers.map { it.userId }
        val initials = someUsers.map { it.initials }

        options.forEachIndexed { index, option ->
            assertThat(userIds[index], equalTo(option.attr("value")))
            assertThat(initials[index], equalTo(option.text()))
        }
    }

    private fun renderPageAndSelectOptions(): Elements {
        val request = Request(GET, login)
        val userManagement = UserManagement(config, someUsers)

        val response = Login(request, userManagement)
        assertThat(response.status, equalTo(OK))

        val document = Jsoup.parse(response.bodyString())
        val selectUser = document.select("select#user").single()
        return selectUser.select("option")
    }
}
