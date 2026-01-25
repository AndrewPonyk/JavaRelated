package com.crawler.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DomainExtractor.
 */
class DomainExtractorTest {

    @Test
    @DisplayName("Should extract domain from URL")
    void shouldExtractDomain() {
        assertEquals("example.com", DomainExtractor.extractDomain("https://example.com/page"));
        assertEquals("www.example.com", DomainExtractor.extractDomain("https://www.example.com/page"));
        assertEquals("sub.domain.example.com", DomainExtractor.extractDomain("https://sub.domain.example.com/"));
    }

    @Test
    @DisplayName("Should return null for invalid URL")
    void shouldReturnNullForInvalidUrl() {
        assertNull(DomainExtractor.extractDomain("not-a-url"));
        assertNull(DomainExtractor.extractDomain(""));
        assertNull(DomainExtractor.extractDomain(null));
    }

    @ParameterizedTest
    @CsvSource({
        "https://www.example.com/page, example.com",
        "https://sub.example.com/page, example.com",
        "https://deep.sub.example.com/page, example.com",
        "https://example.co.uk/page, example.co.uk",
        "https://www.example.co.uk/page, example.co.uk",
        "https://sub.example.co.uk/page, example.co.uk"
    })
    @DisplayName("Should extract registrable domain")
    void shouldExtractRegistrableDomain(String url, String expected) {
        assertEquals(expected, DomainExtractor.extractRegistrableDomain(url));
    }

    @ParameterizedTest
    @CsvSource({
        "https://example.com/page, example.com, true",
        "https://www.example.com/page, example.com, true",
        "https://sub.example.com/page, example.com, true",
        "https://other.com/page, example.com, false",
        "https://notexample.com/page, example.com, false"
    })
    @DisplayName("Should check domain matching")
    void shouldCheckDomainMatching(String url, String domain, boolean expected) {
        assertEquals(expected, DomainExtractor.matchesDomain(url, domain));
    }

    @ParameterizedTest
    @CsvSource({
        "https://example.com/page1, https://example.com/page2, true",
        "https://example.com/page, https://www.example.com/page, false",
        "https://example.com/page, https://other.com/page, false"
    })
    @DisplayName("Should check if URLs are from same domain")
    void shouldCheckSameDomain(String url1, String url2, boolean expected) {
        assertEquals(expected, DomainExtractor.sameDomain(url1, url2));
    }

    @ParameterizedTest
    @CsvSource({
        "https://example.com/page1, https://www.example.com/page2, true",
        "https://sub.example.com/page, https://other.example.com/page, true",
        "https://example.com/page, https://example.org/page, false"
    })
    @DisplayName("Should check same registrable domain")
    void shouldCheckSameRegistrableDomain(String url1, String url2, boolean expected) {
        assertEquals(expected, DomainExtractor.sameRegistrableDomain(url1, url2));
    }

    @Test
    @DisplayName("Should get protocol from URL")
    void shouldGetProtocol() {
        assertEquals("https", DomainExtractor.getProtocol("https://example.com"));
        assertEquals("http", DomainExtractor.getProtocol("http://example.com"));
    }

    @Test
    @DisplayName("Should check if URL uses HTTPS")
    void shouldCheckHttps() {
        assertTrue(DomainExtractor.isHttps("https://example.com/page"));
        assertFalse(DomainExtractor.isHttps("http://example.com/page"));
    }

    @Test
    @DisplayName("Should handle URLs with ports")
    void shouldHandleUrlsWithPorts() {
        assertEquals("example.com", DomainExtractor.extractDomain("https://example.com:8080/page"));
    }

    @Test
    @DisplayName("Should extract domain from host")
    void shouldExtractDomainFromHost() {
        assertEquals("example.com", DomainExtractor.extractRegistrableDomainFromHost("www.example.com"));
        assertEquals("example.com", DomainExtractor.extractRegistrableDomainFromHost("sub.domain.example.com"));
        assertEquals("example.com", DomainExtractor.extractRegistrableDomainFromHost("example.com"));
    }
}
