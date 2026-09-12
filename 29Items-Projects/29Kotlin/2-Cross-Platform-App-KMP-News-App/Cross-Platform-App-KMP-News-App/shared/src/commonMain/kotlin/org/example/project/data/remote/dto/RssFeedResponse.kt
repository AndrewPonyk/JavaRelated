package org.example.project.data.remote.dto

/**
 * Data Transfer Object for Parsed RSS / Atom XML Feed Items.
 */
data class RssFeedDto(
    val title: String,
    val link: String,
    val items: List<RssItemDto>
)

data class RssItemDto(
    val guid: String,
    val title: String,
    val link: String,
    val description: String?,
    val pubDate: String?,
    val author: String? = null,
    val mediaUrl: String? = null
)
