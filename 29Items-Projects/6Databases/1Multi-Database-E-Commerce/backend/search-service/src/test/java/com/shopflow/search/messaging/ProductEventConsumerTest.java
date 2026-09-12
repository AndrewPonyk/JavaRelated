package com.shopflow.search.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.shopflow.common.event.ProductUpdatedEvent;
import com.shopflow.search.domain.ProductDocument;
import com.shopflow.search.repository.ProductSearchRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductEventConsumerTest {

    @Mock
    private ProductSearchRepository repository;

    @Test
    void onProductUpdated_mapsEventToDocumentAndIndexes() {
        ProductEventConsumer consumer = new ProductEventConsumer(repository);
        ProductUpdatedEvent event = new ProductUpdatedEvent(
                UUID.randomUUID(), "corr-1", 1, Instant.now(),
                "p-1", "Widget", "widgets", new BigDecimal("12.34"), true);

        consumer.onProductUpdated(event);

        ArgumentCaptor<ProductDocument> captor = ArgumentCaptor.forClass(ProductDocument.class);
        verify(repository).save(captor.capture());
        ProductDocument doc = captor.getValue();
        assertThat(doc.getId()).isEqualTo("p-1");
        assertThat(doc.getName()).isEqualTo("Widget");
        assertThat(doc.getCategory()).isEqualTo("widgets");
        assertThat(doc.isActive()).isTrue();
    }
}
