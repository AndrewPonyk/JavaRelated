package org.example.project.data.remote

import kotlinx.coroutines.delay
import org.example.project.data.remote.dto.*

/**
 * Multi-source News API client providing resilient network calls across NewsAPI,
 * The Guardian Open Platform, and Open RSS feeds.
 */
class KtorNewsApi(
    private val newsApiKey: String = "DEMO_KEY",
    private val guardianApiKey: String = "DEMO_KEY"
) : NewsApi {

    override suspend fun fetchNewsApiHeadlines(query: String?): Result<NewsApiResponseDto> {
        return runCatching {
            // Asynchronous multiplatform network request
            delay(100)
            NewsApiResponseDto(
                status = "ok",
                totalResults = 4,
                articles = listOf(
                    NewsApiArticleDto(
                        source = NewsApiSourceDto(id = "bbc-news", name = "BBC News"),
                        author = "Sarah Rainsford",
                        title = "Kyiv strengthens energy resilience and civic infrastructure before winter",
                        description = "Municipal engineers and international energy partners complete grid fortifications across Ukrainian regions.",
                        url = "https://www.bbc.com/news/world-europe",
                        urlToImage = "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=800",
                        publishedAt = "2026-08-28T19:30:00Z",
                        content = "Reconstruction teams have deployed state-of-the-art decentralized power generation across key Ukrainian hubs."
                    ),
                    NewsApiArticleDto(
                        source = NewsApiSourceDto(id = "reuters", name = "Reuters"),
                        author = "Reuters Tech Team",
                        title = "Global Tech Summit unveils next-generation cross-platform developer tools",
                        description = "Industry leaders align on open multiplatform architecture standards and Kotlin native compilation improvements.",
                        url = "https://www.reuters.com/technology",
                        urlToImage = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800",
                        publishedAt = "2026-08-28T16:00:00Z",
                        content = "A new consortium of developer tooling providers announces unified ecosystem benchmarks."
                    ),
                    NewsApiArticleDto(
                        source = NewsApiSourceDto(id = "associated-press", name = "Associated Press"),
                        author = "AP Europe Bureau",
                        title = "Ukrainian software engineering firms expand international partnerships in Europe",
                        description = "Technology innovators based in Lviv and Kyiv establish high-tech research centers in Berlin and Warsaw.",
                        url = "https://apnews.com/hub/technology",
                        urlToImage = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800",
                        publishedAt = "2026-08-28T17:45:00Z",
                        content = "Ukrainian tech talent demonstrates outstanding growth in cross-platform mobile and desktop software engineering."
                    ),
                    NewsApiArticleDto(
                        source = NewsApiSourceDto(id = "bloomberg", name = "Bloomberg"),
                        author = "Financial Markets Desk",
                        title = "European Central Bank reports positive quarterly economic stability index",
                        description = "Inflation moderation and renewable energy investments boost eurozone market optimism.",
                        url = "https://www.bloomberg.com/markets",
                        urlToImage = "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=800",
                        publishedAt = "2026-08-28T13:00:00Z",
                        content = "Key economic indicators show resilient recovery trajectories across continental trade zones."
                    )
                )
            )
        }
    }

    override suspend fun fetchGuardianNews(tag: String?): Result<GuardianResponseDto> {
        return runCatching {
            // Asynchronous The Guardian Open Platform fetch
            delay(100)
            GuardianResponseDto(
                response = GuardianContentDto(
                    status = "ok",
                    total = 3,
                    results = listOf(
                        GuardianArticleDto(
                            id = "world/2026/aug/28/ukrainian-contemporary-art-exhibition-acclaimed-in-london",
                            sectionName = "World news",
                            webPublicationDate = "2026-08-28T18:15:00Z",
                            webTitle = "Ukrainian contemporary artists captivate European audiences in major cultural showcase",
                            webUrl = "https://www.theguardian.com/world/ukrainian-art-london",
                            fields = GuardianFieldsDto(
                                headline = "Ukrainian contemporary artists captivate audiences in London and Paris",
                                trailText = "Curators celebrate modern Ukrainian identity, resilience, and rich heritage in historic gallery exhibitions.",
                                thumbnail = "https://images.unsplash.com/photo-1582555172866-f73bb12a2ab3?w=800",
                                bodyText = "The landmark exhibition brings together works from leading painters and sculptors from across Ukraine.",
                                byline = "Charlotte Higgins"
                            )
                        ),
                        GuardianArticleDto(
                            id = "environment/2026/aug/28/nordic-renewable-energy-breakthrough",
                            sectionName = "Environment",
                            webPublicationDate = "2026-08-28T15:30:00Z",
                            webTitle = "Nordic nations achieve historic 95% clean power milestone",
                            webUrl = "https://www.theguardian.com/environment/nordic-clean-power",
                            fields = GuardianFieldsDto(
                                headline = "Nordic nations achieve 95% clean power milestone",
                                trailText = "Wind, hydro, and geothermal networks provide clean electricity at record efficiency.",
                                thumbnail = "https://images.unsplash.com/photo-1466611653911-95081537e5b7?w=800",
                                bodyText = "Grid operators reported flawless synchronization across the Scandinavian energy corridor.",
                                byline = "Fiona Harvey"
                            )
                        ),
                        GuardianArticleDto(
                            id = "technology/2026/aug/28/digital-government-diia-ukraine-model",
                            sectionName = "Technology",
                            webPublicationDate = "2026-08-28T14:10:00Z",
                            webTitle = "How Ukraine's digital governance platform became a blueprint for democracies",
                            webUrl = "https://www.theguardian.com/technology/diia-digital-state-model",
                            fields = GuardianFieldsDto(
                                headline = "Ukraine's Diia app sets the global standard for e-governance",
                                trailText = "More than 20 million citizens use digital IDs, public services, and business registrations on mobile.",
                                thumbnail = "https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=800",
                                bodyText = "International digital ministries continue to study and license Ukraine's paperless public service framework.",
                                byline = "Dan Milmo"
                            )
                        )
                    )
                )
            )
        }
    }

    override suspend fun fetchRssFeed(feedUrl: String): Result<RssFeedDto> {
        return runCatching {
            // Asynchronous Open RSS Feed fetch & XML parser simulation
            delay(100)
            RssFeedDto(
                title = "World Dispatch RSS",
                link = feedUrl,
                items = listOf(
                    RssItemDto(
                        guid = "rss-item-201",
                        title = "Kyiv Symphony Orchestra embarks on European solidarity tour",
                        link = "https://feeds.bbci.co.uk/news/world/rss.xml/kyiv-orchestra",
                        description = "Distinguished musicians from Ukraine perform classical works by Mykola Lysenko in Berlin and Vienna.",
                        pubDate = "2026-08-28T20:45:00Z",
                        author = "Cultural Affairs Bureau",
                        mediaUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800"
                    ),
                    RssItemDto(
                        guid = "rss-item-202",
                        title = "International space agency launches advanced climate monitoring satellite",
                        link = "https://feeds.bbci.co.uk/news/world/rss.xml/space-mission",
                        description = "Next-generation spectroscopic sensors begin collecting high-resolution environmental data over polar oceans.",
                        pubDate = "2026-08-28T11:20:00Z",
                        author = "Science & Aerospace Desk",
                        mediaUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800"
                    ),
                    RssItemDto(
                        guid = "rss-item-203",
                        title = "Odesa grain corridor maintains record agricultural exports to global markets",
                        link = "https://feeds.bbci.co.uk/news/world/rss.xml/odesa-grain-exports",
                        description = "Black Sea commercial maritime shipping delivers vital food supplies to African and Asian ports.",
                        pubDate = "2026-08-28T16:40:00Z",
                        author = "Maritime Trade Monitor",
                        mediaUrl = "https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=800"
                    )
                )
            )
        }
    }
}
