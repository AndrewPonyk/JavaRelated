package com.rtap.api.alerts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertsServiceTest {

    private static final String ID = "a1f0c2d4-0000-0000-0000-000000000001";

    @Mock AnomalyAlertRepository repository;

    @Test
    void acknowledgeTransitionsAndReturnsFreshRow() {
        AlertsService service = new AlertsService(repository);
        AnomalyAlertDto acked = dto("acknowledged", "op-1");
        when(repository.find(ID)).thenReturn(Optional.of(acked));

        AnomalyAlertDto result = service.acknowledge(ID, "op-1");

        verify(repository).acknowledge(ID, "op-1");
        assertThat(result.status()).isEqualTo("acknowledged");
        assertThat(result.ackedBy()).isEqualTo("op-1");
    }

    @Test
    void acknowledgeUnknownAlertIs404() {
        AlertsService service = new AlertsService(repository);
        when(repository.find(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acknowledge(ID, "op-1"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void ingestReportsFirstDeliveryOnly() {
        AlertsService service = new AlertsService(repository);
        AlertMessage message = new AlertMessage(ID, "orders.completed", "ewma-zscore", "-",
                6.0, 4.0, 200, 50, "critical", 1L, 2L, 3L, Map.of());
        when(repository.insert(message)).thenReturn(true).thenReturn(false);

        assertThat(service.ingest(message)).isTrue();   // first delivery → fan out
        assertThat(service.ingest(message)).isFalse();  // redelivery → silent
    }

    private static AnomalyAlertDto dto(String status, String ackedBy) {
        return new AnomalyAlertDto(ID, "orders.completed", "critical", 6.0, 200, 50,
                Instant.EPOCH, Instant.EPOCH, status, ackedBy);
    }
}
