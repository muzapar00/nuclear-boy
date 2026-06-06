package com.nuclearboy.common

/**
 * Sealed result type for representing success or failure throughout the app.
 * Prevents null-safety issues and forces explicit error handling.
 */
sealed class AppResult<out T> {

    data class Success<T>(val data: T) : AppResult<T>()

    data class Failure(
        val error: AppError,
        val technicalDetail: String? = null
    ) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    fun onSuccess(action: (T) -> Unit): AppResult<T> {
        if (this is Success) action(data)
        return this
    }

    fun onFailure(action: (AppError) -> Unit): AppResult<T> {
        if (this is Failure) action(error)
        return this
    }

    companion object {
        fun <T> success(data: T): AppResult<T> = Success(data)
        fun failure(error: AppError, detail: String? = null): AppResult<Nothing> =
            Failure(error, detail)

        inline fun <T> runCatching(block: () -> T): AppResult<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Failure(
                    error = AppError.Unknown,
                    technicalDetail = e.message ?: "Unknown error occurred"
                )
            }
        }
    }
}
