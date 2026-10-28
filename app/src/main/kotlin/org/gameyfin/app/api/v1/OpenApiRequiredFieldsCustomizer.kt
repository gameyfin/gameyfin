package org.gameyfin.app.api.v1

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.models.OpenAPI
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.RegexPatternTypeFilter
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField

/**
 * Derives the `required` list of every DTO schema from its Kotlin primary constructor:
 * a parameter without a default value is required (nullable or not, as the key is always present).
 * Opt out per field with `@field:Schema(requiredMode = NOT_REQUIRED)`; opt in a defaulted field with `REQUIRED`
 * (the latter is handled by swagger-core itself).
 */
object OpenApiRequiredFieldsCustomizer : OpenApiCustomizer {

    private const val DTO_PACKAGE = "org.gameyfin.app.api.v1.dto"

    private val dtoClasses: List<KClass<*>> = ClassPathScanningCandidateComponentProvider(false)
        .apply { addIncludeFilter(RegexPatternTypeFilter(Pattern.compile(".*"))) }
        .findCandidateComponents(DTO_PACKAGE)
        .mapNotNull { it.beanClassName }
        .map { Class.forName(it).kotlin }

    override fun customise(openApi: OpenAPI) {
        openApi.components.schemas.forEach { (name, schema) ->
            val kClass = dtoClasses.firstOrNull { it.simpleName == name }
                ?: dtoClasses.firstOrNull { it.typeParameters.isNotEmpty() && name.startsWith(it.simpleName!!) }
                ?: return@forEach
            val derived = requiredProperties(kClass).filter { schema.properties?.containsKey(it) == true }
            val required = ((schema.required ?: emptyList()) + derived).distinct().sorted()
            schema.required = required.ifEmpty { null }
        }
    }

    private fun requiredProperties(kClass: KClass<*>): List<String> {
        val properties = kClass.memberProperties.associateBy { it.name }
        return kClass.primaryConstructor?.parameters.orEmpty()
            .filter { !it.isOptional }
            .mapNotNull { it.name }
            .filter { name ->
                properties[name]?.javaField?.getAnnotation(Schema::class.java)?.requiredMode !=
                        Schema.RequiredMode.NOT_REQUIRED
            }
    }
}
