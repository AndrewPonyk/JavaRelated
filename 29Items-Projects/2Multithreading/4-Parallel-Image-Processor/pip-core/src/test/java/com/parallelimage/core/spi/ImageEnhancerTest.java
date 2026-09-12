package com.parallelimage.core.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parallelimage.core.pipeline.ImageOperation.EnhanceMode;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ImageEnhancer} tests.
 *
 * <p>{@code pip-core}'s test classpath carries a deliberately malformed
 * {@code META-INF/services/com.parallelimage.core.spi.ImageEnhancer} entry (see that resource file's
 * comment) naming a class that does not exist. That is what {@link #discoverFallsBackOnBrokenProvider}
 * exercises: {@link ImageEnhancer#discover()} must swallow the resulting
 * {@link java.util.ServiceConfigurationError} and still return the {@link PassthroughEnhancer}
 * fallback rather than propagating it.
 */
class ImageEnhancerTest {

    @Test
    @DisplayName("discover() falls back to PassthroughEnhancer when the only registered provider descriptor is broken")
    void discoverFallsBackOnBrokenProvider() {
        ImageEnhancer chosen = ImageEnhancer.discover();

        assertEquals("passthrough (pure Java, no OpenCV)", chosen.describe());
    }

    @Test
    @DisplayName("discover() never returns null")
    void discoverNeverReturnsNull() {
        assertTrue(ImageEnhancer.discover() != null);
    }

    /** Minimal implementation exercising the interface's default methods. */
    private static final class MinimalEnhancer implements ImageEnhancer {
        @Override
        public BufferedImage enhance(BufferedImage source, EnhanceMode mode, double strength) {
            return source;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String describe() {
            return "minimal";
        }
    }

    @Test
    @DisplayName("priority() defaults to 0")
    void defaultPriorityIsZero() {
        assertEquals(0, new MinimalEnhancer().priority());
    }

    @Test
    @DisplayName("supports() defaults to true for every mode")
    void defaultSupportsIsTrueForEveryMode() {
        MinimalEnhancer enhancer = new MinimalEnhancer();
        for (EnhanceMode mode : EnhanceMode.values()) {
            assertTrue(enhancer.supports(mode));
        }
    }
}
