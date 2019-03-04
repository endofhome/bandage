import Result.Failure
import Result.Success

class UserManagement(users: List<User>? = null) {

    private val users: List<User> = users ?: listOf(
        User("1", System.getenv("BANDAGE_USER_ONE_FULL_NAME"), System.getenv("BANDAGE_USER_ONE_SHORT_NAME")),
        User("2", System.getenv("BANDAGE_USER_TWO_FULL_NAME"), System.getenv("BANDAGE_USER_TWO_SHORT_NAME")),
        User("3", System.getenv("BANDAGE_USER_THREE_FULL_NAME"), System.getenv("BANDAGE_USER_THREE_SHORT_NAME"))
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

data class User(val userId: String, val fullName: String, val shortName: String)
