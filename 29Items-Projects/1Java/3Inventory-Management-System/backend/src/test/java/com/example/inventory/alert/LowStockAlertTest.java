package com.example.inventory.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.inventory.common.error.NotFoundException;
import com.example.inventory.common.web.RequestContext;
import com.example.inventory.inventory.domain.InventoryItem;
import com.example.inventory.inventory.domain.Warehouse;
import com.example.inventory.inventory.repository.InventoryItemRepository;
import com.example.inventory.messaging.StockChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LowStockAlertTest {

    @Mock private LowStockAlertRepository alertRepository;
    @Mock private InboxEventRepository inboxRepository;
    @Mock private InventoryItemRepository itemRepository;
    @Mock private RequestContext requestContext;
    private InventoryItem item;
    private LowStockAlert alert;

    @BeforeEach
    void setUp() {
        Warehouse warehouse = new Warehouse("ALERT", "Alert warehouse");
        ReflectionTestUtils.setField(warehouse, "id", UUID.randomUUID());
        item = new InventoryItem(warehouse, "LOW-1", "4006381333931", "Low item", 2, 5);
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        alert = new LowStockAlert(UUID.randomUUID(), item, 2, 5);
        ReflectionTestUtils.setField(alert, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(alert, "createdAt", Instant.now());
    }

    @Test
    void entityAcknowledgesAndResolvesOnlyOpenAlerts() {
        alert.acknowledge("operator");
        Instant acknowledgedAt = alert.getAcknowledgedAt();
        alert.acknowledge("different");

        assertThat(alert.getId()).isNotNull();
        assertThat(alert.getEventId()).isNotNull();
        assertThat(alert.getItem()).isSameAs(item);
        assertThat(alert.getQuantity()).isEqualTo(2);
        assertThat(alert.getReorderPoint()).isEqualTo(5);
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(alert.getCreatedAt()).isNotNull();
        assertThat(alert.getAcknowledgedAt()).isEqualTo(acknowledgedAt);
        assertThat(alert.getAcknowledgedBy()).isEqualTo("operator");
        alert.resolve();
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);

        LowStockAlert resolvable = new LowStockAlert(UUID.randomUUID(), item, 1, 5);
        resolvable.resolve();
        assertThat(resolvable.getStatus()).isEqualTo(AlertStatus.RESOLVED);
    }

    @Test
    void serviceListsAcknowledgesAndReportsMissingAlerts() {
        LowStockAlertService service = new LowStockAlertService(alertRepository, requestContext);
        PageRequest page = PageRequest.of(0, 20);
        when(alertRepository.findByStatusOrderByCreatedAtDesc(AlertStatus.OPEN, page))
                .thenReturn(new PageImpl<>(List.of(alert), page, 1));
        when(alertRepository.findById(alert.getId()))
                .thenReturn(Optional.of(alert), Optional.empty());
        when(requestContext.actor()).thenReturn("operator");
        when(alertRepository.saveAndFlush(alert)).thenReturn(alert);

        assertThat(service.list(AlertStatus.OPEN, page).getTotalElements()).isEqualTo(1);
        assertThat(service.acknowledge(alert.getId()).status()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThatThrownBy(() -> service.acknowledge(alert.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Low-stock");
    }

    @Test
    void consumerDeduplicatesAndHandlesMissingItems() throws Exception {
        LowStockAlertConsumer consumer = consumer();
        StockChangedEvent event = event(true);
        when(inboxRepository.existsById(any())).thenReturn(true);
        consumer.consume(json(event));
        verify(itemRepository, never()).findById(any());

        when(inboxRepository.existsById(any())).thenReturn(false);
        when(itemRepository.findById(item.getId())).thenReturn(Optional.empty());
        consumer.consume(json(event));
        verify(inboxRepository).save(any(InboxEvent.class));
    }

    @Test
    void consumerCreatesAndResolvesAlerts() throws Exception {
        LowStockAlertConsumer consumer = consumer();
        when(inboxRepository.existsById(any())).thenReturn(false);
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(alertRepository.findFirstByItemIdAndStatusOrderByCreatedAtDesc(item.getId(), AlertStatus.OPEN))
                .thenReturn(Optional.empty(), Optional.of(alert));

        consumer.consume(json(event(true)));
        verify(alertRepository).save(any(LowStockAlert.class));
        consumer.consume(json(event(false)));
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
    }

    @Test
    void inboxIdentityHasValueSemantics() {
        UUID eventId = UUID.randomUUID();
        InboxEventId first = new InboxEventId(eventId, "consumer");
        InboxEventId same = new InboxEventId(eventId, "consumer");
        InboxEventId different = new InboxEventId(UUID.randomUUID(), "consumer");
        InboxEvent inbox = new InboxEvent(first);
        ReflectionTestUtils.setField(inbox, "processedAt", Instant.now());

        assertThat(first).isEqualTo(first).isEqualTo(same).isNotEqualTo(different).isNotEqualTo("other");
        assertThat(first.hashCode()).isEqualTo(same.hashCode());
        assertThat(first.getEventId()).isEqualTo(eventId);
        assertThat(first.getConsumerName()).isEqualTo("consumer");
        assertThat(inbox.getId()).isEqualTo(first);
        assertThat(inbox.getProcessedAt()).isNotNull();
    }

    private LowStockAlertConsumer consumer() {
        return new LowStockAlertConsumer(inboxRepository, alertRepository, itemRepository,
                new ObjectMapper().findAndRegisterModules());
    }

    private StockChangedEvent event(boolean lowStock) {
        return new StockChangedEvent(UUID.randomUUID(), "inventory.stock-changed", 1, item.getId(),
                item.getWarehouse().getId(), item.getSku(), item.getQuantity(), item.getReservedQuantity(),
                item.getAvailableQuantity(), item.getReorderPoint(), lowStock, Instant.now(), "tester", "corr");
    }

    private String json(StockChangedEvent event) throws Exception {
        return new ObjectMapper().findAndRegisterModules().writeValueAsString(event);
    }
}
