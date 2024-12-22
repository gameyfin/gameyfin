package de.grimsi.gameyfin.core.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION

/**
 * This annotation is used on endpoint methods which can be switched between publicly accessible and
 * only accessible for registered users.
 * One example would be the main library view.
 */

@Target(FUNCTION, CLASS)
@Retention(RUNTIME)
annotation class DynamicPublicAccess