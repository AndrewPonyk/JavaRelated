package org.example.project.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.example.project.domain.model.Article
import org.example.project.domain.model.NewsSource
import org.example.project.domain.model.SourceProvider
import org.example.project.domain.repository.NewsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetUkraineNewsUseCaseTest {

    private class FakeNewsRepository(private var articles: List<Article>) : NewsRepository {
        override fun getArticlesStream(forceRefresh: Boolean): Flow<List<Article>> = flowOf(articles)
        override suspend fun syncAllNewsSources(): Result<Unit> = Result.success(Unit)
        override suspend fun getArticleById(id: String): Article? = articles.firstOrNull { it.id == id }
        override fun searchArticles(query: String): Flow<List<Article>> = flowOf(
            articles.filter { it.title.contains(query, ignoreCase = true) }
        )
        override suspend fun toggleBookmark(articleId: String): Result<Boolean> {
            articles = articles.map { if (it.id == articleId) it.copy(isBookmarked = !it.isBookmarked) else it }
            return Result.success(articles.firstOrNull { it.id == articleId }?.isBookmarked == true)
        }
        override fun getBookmarkedArticles(): Flow<List<Article>> = flowOf(articles.filter { it.isBookmarked })
        override suspend fun deleteArticle(articleId: String): Result<Unit> {
            articles = articles.filter { it.id != articleId }
            return Result.success(Unit)
        }
    }

    @Test
    fun `invoke prioritizes Ukraine-related articles at the beginning of the list`() = runTest {
        val testArticles = listOf(
            Article(
                id = "1",
                title = "Global market indices fluctuate following quarterly reports",
                description = "Stock markets experienced moderate volatility today.",
                content = null,
                source = NewsSource("reuters", "Reuters", SourceProvider.NEWS_API),
                author = "Financial Desk",
                url = "https://example.com/1",
                imageUrl = null,
                publishedAtEpochMillis = 1000L
            ),
            Article(
                id = "2",
                title = "Ukrainian developers win European software architecture award",
                description = "Engineers from Kyiv showcased multiplatform technology.",
                content = null,
                source = NewsSource("tech-crunch", "TechCrunch", SourceProvider.NEWS_API),
                author = "Tech Writer",
                url = "https://example.com/2",
                imageUrl = null,
                publishedAtEpochMillis = 500L
            ),
            Article(
                id = "3",
                title = "New renewable energy initiative launched in South America",
                description = "Solar installations expand across sunny regions.",
                content = null,
                source = NewsSource("guardian", "The Guardian", SourceProvider.GUARDIAN),
                author = "Eco Reporter",
                url = "https://example.com/3",
                imageUrl = null,
                publishedAtEpochMillis = 2000L
            )
        )

        val repository = FakeNewsRepository(testArticles)
        val useCase = GetUkraineNewsUseCase(repository)

        val result = useCase(forceRefresh = false).first()

        assertEquals(3, result.size)
        assertEquals("2", result.first().id)
        assertTrue(result.first().isUkraineRelated)
        assertTrue(result.first().relevanceScore > 0)
    }

    @Test
    fun `calculateUkraineRelevance detects Cyrillic Ukrainian keywords accurately`() {
        val repository = FakeNewsRepository(emptyList())
        val useCase = GetUkraineNewsUseCase(repository)

        val cyrillicArticle = Article(
            id = "cyr-1",
            title = "Україна розширює цифрові сервіси та електронне урядування",
            description = "Команда Мінцифри представила нові послуги.",
            content = "ЗСУ та волонтери впроваджують інноваційні системи зв'язку.",
            source = NewsSource("ukrinform", "Укрінформ", SourceProvider.CUSTOM),
            author = "Інформ Агентство",
            url = "https://example.com/ua",
            imageUrl = null,
            publishedAtEpochMillis = 500L
        )

        val (isRelated, score) = useCase.calculateUkraineRelevance(cyrillicArticle)
        assertTrue(isRelated)
        assertTrue(score >= 15) // Title match gives high score
    }

    @Test
    fun `empty articles feed returns empty list without error`() = runTest {
        val repository = FakeNewsRepository(emptyList())
        val useCase = GetUkraineNewsUseCase(repository)

        val result = useCase(forceRefresh = false).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `articles with zero relevance score are sorted chronologically by published date`() = runTest {
        val testArticles = listOf(
            Article(
                id = "old-world",
                title = "Ancient astronomical observatory discovered in Egypt",
                description = "Archeologists uncover stone calendar.",
                content = null,
                source = NewsSource("science", "Science News", SourceProvider.CUSTOM),
                author = null,
                url = "https://example.com/old",
                imageUrl = null,
                publishedAtEpochMillis = 1000L
            ),
            Article(
                id = "new-world",
                title = "Pacific marine sanctuary expansion announced",
                description = "Ocean conservation zone enlarged.",
                content = null,
                source = NewsSource("nature", "Nature Wire", SourceProvider.CUSTOM),
                author = null,
                url = "https://example.com/new",
                imageUrl = null,
                publishedAtEpochMillis = 5000L
            )
        )

        val repository = FakeNewsRepository(testArticles)
        val useCase = GetUkraineNewsUseCase(repository)

        val result = useCase(forceRefresh = false).first()
        assertEquals(2, result.size)
        assertEquals("new-world", result[0].id)
        assertEquals("old-world", result[1].id)
        assertFalse(result[0].isUkraineRelated)
        assertFalse(result[1].isUkraineRelated)
    }
}
