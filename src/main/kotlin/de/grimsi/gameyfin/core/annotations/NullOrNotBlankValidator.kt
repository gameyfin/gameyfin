package de.grimsi.gameyfin.core.annotations

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class NullOrNotBlankValidator : ConstraintValidator<NullOrNotBlank, String?> {

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        return value == null || value.isNotBlank()
    }
}