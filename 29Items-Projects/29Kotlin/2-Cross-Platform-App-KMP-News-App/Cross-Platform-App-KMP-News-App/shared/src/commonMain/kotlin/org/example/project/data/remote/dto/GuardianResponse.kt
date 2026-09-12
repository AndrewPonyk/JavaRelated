package org.example.project.data.remote.dto

/**
 * Data Transfer Object for The Guardian Open Platform API.
 */
data class GuardianResponseDto(
    val response: GuardianContentDto
)

data class GuardianContentDto(
    val status: String,
    val total: Int? = null,
    val results: List<GuardianArticleDto>? = null
)

data class GuardianArticleDto(
    val id: String,
    val type: String? = null,
    val sectionName: String? = null,
    val webPublicationDate: String? = null,
    val webTitle: String,
    val webUrl: String,
    val apiUrl: String? = null,
    val fields: GuardianFieldsDto? = null
)

data class GuardianFieldsDto(
    val headline: String? = null,
    val trailText: String? = null,
    val thumbnail: String? = null,
    val bodyText: String? = null,
    val byline: String? = null
)
