import config.BandageConfigItem.USER_ONE_FULL_NAME
import config.BandageConfigItem.USER_THREE_FULL_NAME
import config.BandageConfigItem.USER_TWO_FULL_NAME
import config.Configuration
import result.Error
import result.Result
import result.Result.Failure
import result.Result.Success

class UserManagement(config: Configuration, users: List<User>? = null) {

    val users: List<User> = users ?: listOf(
        User("1", config.get(USER_ONE_FULL_NAME)),
        User("2", config.get(USER_TWO_FULL_NAME)),
        User("3", config.get(USER_THREE_FULL_NAME))
    )

    fun findUser(userId: String): Result<Error, User> {
        val user = users.firstOrNull { it.userId == userId }
        return if (user == null) {
            Failure(Error("Unknown user ID $userId"))
        } else {
            Success(user)
        }
    }
}

data class User(val userId: String, val fullName: String) {
    private val parts = fullName.split(" ")
    val initials get() = parts.map { it.first() }.joinToString("")
    val shortName get() = parts.first()
}
