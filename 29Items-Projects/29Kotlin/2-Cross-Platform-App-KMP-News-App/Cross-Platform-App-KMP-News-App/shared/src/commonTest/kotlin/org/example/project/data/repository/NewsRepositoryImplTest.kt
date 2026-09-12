package org.example.project.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.example.project.data.local.ArticleDao
import org.example.project.data.remote.KtorNewsApi
import org.example.project.domain.model.Article
import org.example.project.domain.model.NewsSource
import org.example.project.domain.model.SourceProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NewsRepositoryImplTest {

    @Test
    fun `syncAllNewsSources aggregates from multiple sources and inserts to cache`() = runTest {
        val newsApi = KtorNewsApi()
        val articleDao = ArticleDao()
        val repository = NewsRepositoryImpl(newsApi, articleDao)

        // Sync from all sources
        val syncResult = repository.syncAllNewsSources()
        assertTrue(syncResult.isSuccess)

        // Verify articles in cache
        val articles = repository.getArticlesStream(forceRefresh = false).first()
        assertTrue(articles.isNotEmpty())
        
        // Check that articles from different providers exist
        val hasNewsApi = articles.any { it.source.provider == SourceProvider.NEWS_API }
        val hasGuardian = articles.any { it.source.provider == SourceProvider.GUARDIAN }
        val hasRss = articles.any { it.source.provider == SourceProvider.OPEN_RSS }

        assertTrue(hasNewsApi, "Should contain NewsAPI articles")
        assertTrue(hasGuardian, "Should contain Guardian articles")
        assertTrue(hasRss, "Should contain RSS feed articles")
    }

    @Test
    fun `searchArticles returns matching articles by keyword`() = runTest {
        val articleDao = ArticleDao()
        val repository = NewsRepositoryImpl(KtorNewsApi(), articleDao)

        articleDao.insertArticles(
            listOf(
                Article(
                    id = "art-1",
                    title = "Kyiv tech summit opens today",
                    description = "Major IT conference in Ukraine.",
                    content = null,
                    source = NewsSource("src-1", "Source 1", SourceProvider.CUSTOM),
                    author = "Author 1",
                    url = "https://example.com/1",
                    imageUrl = null,
                    publishedAtEpochMillis = 1000L
                ),
                Article(
                    id = "art-2",
                    title = "Tokyo robotics exhibition highlights",
                    description = "Industrial automation showcase.",
                    content = null,
                    source = NewsSource("src-2", "Source 2", SourceProvider.CUSTOM),
                    author = "Author 2",
                    url = "https://example.com/2",
                    imageUrl = null,
                    publishedAtEpochMillis = 2000L
                )
            )
        )

        val searchResults = repository.searchArticles("Kyiv").first()
        assertEquals(1, searchResults.size)
        assertEquals("art-1", searchResults.first().id)
    }

    @Test
    fun `bookmarking and deletion operations work correctly`() = runTest {
        val articleDao = ArticleDao()
        val repository = NewsRepositoryImpl(KtorNewsApi(), articleDao)

        val article = Article(
            id = "art-3",
            title = "Renewable energy investments rise in 2026",
            description = "Clean power summary.",
            content = null,
            source = NewsSource("src-3", "Source 3", SourceProvider.CUSTOM),
            author = "Eco Analyst",
            url = "https://example.com/3",
            imageUrl = null,
            publishedAtEpochMillis = 3000L
        )

        articleDao.insertArticles(listOf(article))

        // Toggle bookmark ON
        val bookmarkOnResult = repository.toggleBookmark("art-3")
        assertTrue(bookmarkOnResult.getOrNull() == true)

        val bookmarkedArticles = repository.getBookmarkedArticles().first()
        assertEquals(1, bookmarkedArticles.size)
        assertEquals("art-3", bookmarkedArticles.first().id)

        // Delete article
        val deleteResult = repository.deleteArticle("art-3")
        assertTrue(deleteResult.isSuccess)

        val deletedArticle = repository.getArticleById("art-3")
        assertEquals(null, deletedArticle)

        val cachedAfterDelete = articleDao.getAllArticles().first()
        assertTrue(cachedAfterDelete.isEmpty())
    }
}
