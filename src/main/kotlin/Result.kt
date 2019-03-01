import Result.Failure
import Result.Success

sealed class Result<out F, out S> {
    data class Success<out S>(val value: S) : Result<Nothing, S>()
    data class Failure<out F>(val reason: F) : Result<F, Nothing>()
}

fun <F, S, T> Result<F, S>.map(transform: (S) -> T): Result<F, T> =
    when (this) {
        is Success -> Success(transform(this.value))
        is Failure -> this
    }

fun <F, S> Result<F, S>.orElse(transform: (F) -> S): S =
    when (this) {
        is Success -> this.value
        is Failure -> transform(this.reason)
    }

data class Error(val message: String)
