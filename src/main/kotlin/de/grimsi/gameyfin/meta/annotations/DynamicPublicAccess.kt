package de.grimsi.gameyfin.meta.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION

/**
 * This annotation is used on endpoint methods which can be switched between publicly accessible and
 * only accessible for registered users.
 * One example would be the main library view.
 */

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class DynamicPublicAccess