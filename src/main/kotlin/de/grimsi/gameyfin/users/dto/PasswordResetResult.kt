package de.grimsi.gameyfin.users.dto

enum class PasswordResetResult() {
    SUCCESS, INVALID_TOKEN, EXPIRED_TOKEN
}