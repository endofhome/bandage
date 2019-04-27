package result

import result.Result.Failure
import result.Result.Success

sealed class Result<out F, out S> {
    data class Success<out S>(val value: S) : Result<Nothing, S>()
    data class Failure<out F>(val reason: F) : Result<F, Nothing>()
}

fun <F, S, T> Result<F, S>.map(transform: (S) -> T): Result<F, T> =
    when (this) {
        is Success -> Success(transform(this.value))
        is Failure -> this
    }

fun <F, S, T> Result<F, S>.flatMap(transform: (S) -> Result<F, T>): Result<F, T> =
    when (this) {
        is Success -> transform(this.value)
        is Failure -> this
    }

fun <F, S> Result<F, S>.orElse(transform: (F) -> S): S =
    when (this) {
        is Success -> this.value
        is Failure -> transform(this.reason)
    }

fun <F, S> List<Result<F, S>>.partition(): Pair<List<F>, List<S>> =
        this.fold(emptyList<F>() to emptyList()) { pair, result ->
            when (result) {
                is Success -> pair.copy(second = pair.second + result.value)
                is Failure -> pair.copy(first = pair.first + result.reason)
            }
        }

fun <T> T.asSuccess(): Result<Nothing, T> = Success(this)

fun <F, S> Result<F, S>.expectSuccess(): S =
    when (this) {
        is Success -> this.value
        is Failure -> throw RuntimeException("Expected success but was failure: ${this.reason}")
    }

data class Error(val message: String)
