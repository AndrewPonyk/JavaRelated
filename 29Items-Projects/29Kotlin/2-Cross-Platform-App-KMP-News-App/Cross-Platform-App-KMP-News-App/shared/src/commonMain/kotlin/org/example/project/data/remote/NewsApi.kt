package org.example.project.data.remote

import org.example.project.data.remote.dto.GuardianResponseDto
import org.example.project.data.remote.dto.NewsApiResponseDto
import org.example.project.data.remote.dto.RssFeedDto

/**
 * Multi-source remote network contract for aggregating news from min 2-3 sources.
 */
interface NewsApi {
    /**
     * Source 1: Fetches top headlines from NewsAPI.
     */
    suspend fun fetchNewsApiHeadlines(query: String? = null): Result<NewsApiResponseDto>

    /**
     * Source 2: Fetches world news from The Guardian API.
     */
    suspend fun fetchGuardianNews(tag: String? = null): Result<GuardianResponseDto>

    /**
     * Source 3: Fetches open RSS news feed.
     */
    suspend fun fetchRssFeed(feedUrl: String): Result<RssFeedDto>
}
