package org.gameyfin.app.api.v1

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

/** Unknown inputs must not be silently accepted when a client targets a newer contract. */
abstract class StrictInputV1 {
    @JsonAnySetter
    fun unknown(name: String) {
        throw IllegalArgumentException("Unknown input field: $name")
    }
}

data class PageV1<T>(val items: List<T>, val page: Int, val size: Int, val totalElements: Long)
data class OptionV1(val code: String, val name: String)
data class ImageV1(val id: String, val url: String, val contentType: String?, val size: Long?)
data class ExternalIdV1(val provider: String, val id: String)
data class GameV1(
    val id: String, val libraryId: String, val title: String?, val createdAt: Instant?, val updatedAt: Instant?,
    val platforms: List<String>, val release: LocalDate?, val summary: String?, val comment: String?,
    val userRating: Int?, val criticRating: Int?, val publishers: List<String>, val developers: List<String>,
    val genres: List<String>, val themes: List<String>, val features: List<String>, val perspectives: List<String>,
    val keywords: List<String>, val cover: ImageV1?, val header: ImageV1?, val images: List<ImageV1>,
    val videoUrls: List<String>, val mediaOrder: List<String>, val externalIds: List<ExternalIdV1>, val fileSize: Long?
)

@Schema(description = "JSON Merge Patch: omitted fields stay unchanged, null clears summary, comment or release; arrays replace their contents. Other null values are rejected.")
data class GamePatchV1(
    val title: String? = null, val platforms: Set<String>? = null, val release: LocalDate? = null,
    val summary: String? = null, val comment: String? = null, val publishers: List<String>? = null,
    val developers: List<String>? = null, val genres: List<String>? = null, val themes: List<String>? = null,
    val features: List<String>? = null, val perspectives: List<String>? = null, val keywords: List<String>? = null
)

data class ImageSelectionV1(val id: String? = null, val url: String? = null, val uploadKey: String? = null) :
    StrictInputV1()

data class GameMediaV1(
    val cover: ImageSelectionV1? = null, val header: ImageSelectionV1? = null,
    val images: List<ImageSelectionV1> = emptyList(), val videoUrls: List<String> = emptyList(),
    val mediaOrder: List<String> = emptyList()
) : StrictInputV1()

data class LibraryV1(
    val id: String,
    val name: String,
    val platforms: List<String>,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class DirectoryV1(val internalPath: String, val externalPath: String? = null) : StrictInputV1()
data class LibraryConfigurationV1(val directories: List<DirectoryV1>, val platforms: List<String>)
data class CreateLibraryV1(
    @field:NotBlank @field:Size(max = 255) val name: String,
    @field:Size(min = 1, max = 100) val directories: List<DirectoryV1>,
    val platforms: List<String> = emptyList(), val scanAfterCreation: Boolean = false
) : StrictInputV1()

data class LibraryPatchV1(
    val name: String? = null,
    val directories: List<DirectoryV1>? = null,
    val platforms: List<String>? = null
)

data class ScanV1(@field:Schema(allowableValues = ["QUICK", "FULL"]) val type: String = "QUICK") : StrictInputV1()
data class AcceptedScanV1(val libraryId: String, val type: String, val status: String = "QUEUED")
data class CollectionV1(
    val id: String,
    val name: String,
    val description: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

data class CreateCollectionV1(
    @field:NotBlank @field:Size(max = 255) val name: String,
    val description: String? = null
) : StrictInputV1()

data class CollectionPatchV1(val name: String? = null, val description: String? = null)
data class RequestV1(
    val id: String, val title: String, val platform: String, val release: LocalDate?, val status: String,
    val requesterId: String?, val voteCount: Int, val voted: Boolean?, val linkedGameId: String?,
    val createdAt: Instant?, val updatedAt: Instant?
)

data class CreateRequestV1(
    @field:NotBlank @field:Size(max = 255) val title: String,
    val platform: String,
    val release: LocalDate? = null
) : StrictInputV1()

data class RequestStatusV1(@field:Schema(allowableValues = ["PENDING", "APPROVED", "REJECTED", "FULFILLED"]) val status: String) :
    StrictInputV1()

data class TaskV1(val id: String, val name: String, val description: String, val running: Boolean)
data class TaskRunV1(
    val id: String,
    val taskId: String,
    val status: String,
    val startedAt: Instant,
    val finishedAt: Instant?
)

data class DownloadProviderV1(val id: String, val name: String, val description: String, val priority: Int)
data class DownloadLinkV1(val kind: String = "link", val url: String)
data class MeV1(val id: String, val username: String, val permissions: Set<String>)

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Public user profile. Email, account status, SSO status and roles are included only for administrator personal tokens.")
data class UserV1(
    val id: String, val username: String, val hasAvatar: Boolean,
    val avatarId: String?, val email: String? = null, val emailConfirmed: Boolean? = null,
    val enabled: Boolean? = null, val managedBySso: Boolean? = null, val roles: List<String>? = null
)

data class InstanceV1(
    val version: String,
    val buildTime: Instant?,
    val gitCommit: String?,
    val apiVersions: List<String> = listOf("v1"),
    val capabilities: List<String> = listOf(
        "catalog",
        "downloads",
        "metadata-write",
        "media-write",
        "library-scans",
        "requests"
    )
)

data class HealthV1(val status: String)
