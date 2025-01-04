package de.grimsi.gameyfin.plugins.steam.dto

import de.grimsi.gameyfin.plugins.steam.util.SteamDateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class SteamDetailsResultWrapper(
    val success: Boolean,
    val data: SteamGameDetails
)

@Serializable
data class SteamGameDetails(
    val type: String,
    val name: String,
    @SerialName("steam_appid") val steamAppId: Int,
    @SerialName("required_age") val requiredAge: Int,
    @SerialName("is_free") val isFree: Boolean,
    @SerialName("controller_support") val controllerSupport: String?,
    val dlc: List<Int>?,
    @SerialName("detailed_description") val detailedDescription: String,
    @SerialName("about_the_game") val aboutTheGame: String,
    @SerialName("short_description") val shortDescription: String,
    @SerialName("supported_languages") val supportedLanguages: String,
    val reviews: String,
    @SerialName("header_image") val headerImage: String,
    @SerialName("capsule_image") val capsuleImage: String,
    @SerialName("capsule_imagev5") val capsuleImageV5: String,
    val website: String?,
    @SerialName("pc_requirements") val pcRequirements: SystemRequirements,
    @SerialName("mac_requirements") val macRequirements: SystemRequirements,
    @SerialName("linux_requirements") val linuxRequirements: SystemRequirements,
    @SerialName("legal_notice") val legalNotice: String?,
    @SerialName("drm_notice") val drmNotice: String?,
    @SerialName("ext_user_account_notice") val extUserAccountNotice: String?,
    val developers: List<String>,
    val publishers: List<String>,
    @SerialName("price_overview") val priceOverview: PriceOverview?,
    val packages: List<Int>?,
    @SerialName("package_groups") val packageGroups: List<PackageGroup>,
    val platforms: Platforms,
    val categories: List<Category>,
    val genres: List<SteamGenre>,
    val screenshots: List<Screenshot>,
    val movies: List<Movie>,
    val recommendations: Recommendations?,
    val achievements: Achievements?,
    @SerialName("release_date") val releaseDate: ReleaseDate?,
    @SerialName("support_info") val supportInfo: SupportInfo?,
    val background: String?,
    @SerialName("background_raw") val backgroundRaw: String?,
    @SerialName("content_descriptors") val contentDescriptors: ContentDescriptors?,
    @SerialName("ratings") val ratings: Map<String, Rating>?
)

@Serializable
data class SystemRequirements(
    val minimum: String?,
    val recommended: String?
)

@Serializable
data class PriceOverview(
    val currency: String?,
    val initial: Int?,
    val final: Int?,
    @SerialName("discount_percent") val discountPercent: Int?,
    @SerialName("initial_formatted") val initialFormatted: String?,
    @SerialName("final_formatted") val finalFormatted: String?
)

@Serializable
data class PackageGroup(
    val name: String?,
    val title: String?,
    val description: String?,
    @SerialName("selection_text") val selectionText: String?,
    @SerialName("save_text") val saveText: String?,
    @SerialName("display_type") val displayType: Int?,
    @SerialName("is_recurring_subscription") val isRecurringSubscription: Boolean?,
    val subs: List<Sub>?
)

@Serializable
data class Sub(
    @SerialName("packageid") val packageId: Int,
    @SerialName("percent_savings_text") val percentSavingsText: String?,
    @SerialName("percent_savings") val percentSavings: Int?,
    @SerialName("option_text") val optionText: String?,
    @SerialName("option_description") val optionDescription: String?,
    @SerialName("can_get_free_license") val canGetFreeLicense: String?,
    @SerialName("is_free_license") val isFreeLicense: Boolean,
    @SerialName("price_in_cents_with_discount") val priceInCentsWithDiscount: Int?
)

@Serializable
data class Category(
    val id: Int,
    val description: String?
)

@Serializable
data class SteamGenre(
    val id: Int,
    val description: String?
)

@Serializable
data class Screenshot(
    val id: Int,
    @SerialName("path_thumbnail") val pathThumbnail: String?,
    @SerialName("path_full") val pathFull: String?
)

@Serializable
data class Movie(
    val id: Int,
    val name: String?,
    val thumbnail: String?,
    val webm: Webm?,
    val mp4: Mp4?,
    val highlight: Boolean
)

@Serializable
data class Webm(
    val `480`: String,
    val max: String
)

@Serializable
data class Mp4(
    val `480`: String,
    val max: String
)

@Serializable
data class Recommendations(
    val total: Int?
)

@Serializable
data class Achievements(
    val total: Int,
    val highlighted: List<Achievement>?
)

@Serializable
data class Achievement(
    val name: String?,
    val path: String?
)

@Serializable
data class ReleaseDate(
    @SerialName("coming_soon") val comingSoon: Boolean,
    @Serializable(with = SteamDateSerializer::class) val date: Instant?
)

@Serializable
data class SupportInfo(
    val url: String?,
    val email: String?
)

@Serializable
data class ContentDescriptors(
    val ids: List<Int>,
    val notes: String?
)

@Serializable
data class Rating(
    val rating: String,
    val descriptors: String? = null,
    @SerialName("display_online_notice") val displayOnlineNotice: Boolean? = null,
    @SerialName("use_age_gate") val useAgeGate: Boolean? = null,
    @SerialName("required_age") val requiredAge: Int? = null,
    @SerialName("interactive_elements") val interactiveElements: String? = null
)