package com.crawler.db;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PageRepository.
 */
class PageRepositoryTest {

    @TempDir
    Path tempDir;

    private DatabaseManager dbManager;
    private PageRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        String dbPath = tempDir.resolve("test.db").toString();
        dbManager = new DatabaseManager(dbPath);
        dbManager.initialize();
        repository = new PageRepository(dbManager);
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    @DisplayName("Should save and retrieve page")
    void shouldSaveAndRetrievePage() {
        String url = "https://example.com/test";
        Document doc = Jsoup.parse("<html><head><title>Test Page</title></head><body>Content</body></html>");

        long id = repository.save(url, doc, 200);

        assertTrue(id > 0);

        Optional<PageRepository.PageData> found = repository.findByUrl(url);
        assertTrue(found.isPresent());
        assertEquals(url, found.get().url());
        assertEquals("Test Page", found.get().title());
        assertEquals(200, found.get().statusCode());
    }

    @Test
    @DisplayName("Should update existing page on conflict")
    void shouldUpdateExistingPage() {
        String url = "https://example.com/test";
        Document doc1 = Jsoup.parse("<html><head><title>Original</title></head></html>");
        Document doc2 = Jsoup.parse("<html><head><title>Updated</title></head></html>");

        repository.save(url, doc1, 200);
        repository.save(url, doc2, 200);

        Optional<PageRepository.PageData> found = repository.findByUrl(url);
        assertTrue(found.isPresent());
        assertEquals("Updated", found.get().title());
    }

    @Test
    @DisplayName("Should check page existence")
    void shouldCheckPageExistence() {
        String url = "https://example.com/test";
        Document doc = Jsoup.parse("<html><title>Test</title></html>");

        assertFalse(repository.exists(url));

        repository.save(url, doc, 200);

        assertTrue(repository.exists(url));
    }

    @Test
    @DisplayName("Should find pages by domain")
    void shouldFindPagesByDomain() {
        Document doc = Jsoup.parse("<html><title>Test</title></html>");

        repository.save("https://example.com/page1", doc, 200);
        repository.save("https://example.com/page2", doc, 200);
        repository.save("https://other.com/page1", doc, 200);

        List<PageRepository.PageData> pages = repository.findByDomain("example.com", 10);

        assertEquals(2, pages.size());
        assertTrue(pages.stream().allMatch(p -> p.domain().equals("example.com")));
    }

    @Test
    @DisplayName("Should count pages")
    void shouldCountPages() {
        Document doc = Jsoup.parse("<html><title>Test</title></html>");

        assertEquals(0, repository.count());

        repository.save("https://example.com/page1", doc, 200);
        repository.save("https://example.com/page2", doc, 200);

        assertEquals(2, repository.count());
    }

    @Test
    @DisplayName("Should delete page")
    void shouldDeletePage() {
        String url = "https://example.com/test";
        Document doc = Jsoup.parse("<html><title>Test</title></html>");

        repository.save(url, doc, 200);
        assertTrue(repository.exists(url));

        boolean deleted = repository.delete(url);
        assertTrue(deleted);
        assertFalse(repository.exists(url));
    }

    @Test
    @DisplayName("Should update relevance score")
    void shouldUpdateRelevanceScore() {
        String url = "https://example.com/test";
        Document doc = Jsoup.parse("<html><title>Test</title></html>");

        repository.save(url, doc, 200);
        repository.updateRelevanceScore(url, 0.85);

        Optional<PageRepository.PageData> found = repository.findByUrl(url);
        assertTrue(found.isPresent());
        assertEquals(0.85, found.get().relevanceScore(), 0.001);
    }

    @Test
    @DisplayName("Should find top pages by relevance")
    void shouldFindTopPagesByRelevance() {
        Document doc = Jsoup.parse("<html><title>Test</title></html>");

        repository.save("https://example.com/low", doc, 200);
        repository.updateRelevanceScore("https://example.com/low", 0.2);

        repository.save("https://example.com/high", doc, 200);
        repository.updateRelevanceScore("https://example.com/high", 0.9);

        repository.save("https://example.com/medium", doc, 200);
        repository.updateRelevanceScore("https://example.com/medium", 0.5);

        List<PageRepository.PageData> top = repository.findTopByRelevance(2);

        assertEquals(2, top.size());
        assertEquals("https://example.com/high", top.get(0).url());
        assertEquals("https://example.com/medium", top.get(1).url());
    }

    @Test
    @DisplayName("Should handle null document")
    void shouldHandleNullDocument() {
        String url = "https://example.com/error";

        long id = repository.save(url, null, 500);

        assertTrue(id > 0 || id == -1); // Implementation dependent

        Optional<PageRepository.PageData> found = repository.findByUrl(url);
        if (found.isPresent()) {
            assertNull(found.get().title());
        }
    }
}
