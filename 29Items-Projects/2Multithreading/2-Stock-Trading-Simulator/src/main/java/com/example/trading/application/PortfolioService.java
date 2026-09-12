package com.example.trading.application;

import com.example.trading.application.exception.EntityNotFoundException;
import com.example.trading.application.port.AuditLog;
import com.example.trading.application.port.PortfolioRepository;
import com.example.trading.domain.AuditType;
import com.example.trading.domain.Portfolio;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PortfolioService {
    private final PortfolioRepository repository;
    private final AuditLog auditLog;
    private final Clock clock;

    public PortfolioService(PortfolioRepository repository, AuditLog auditLog, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Portfolio create(Portfolio portfolio) {
        Portfolio created = repository.create(portfolio);
        record(AuditType.PORTFOLIO_CREATED, created.traderId());
        return created;
    }

    public Portfolio update(Portfolio portfolio) {
        Portfolio updated = repository.update(portfolio);
        record(AuditType.PORTFOLIO_UPDATED, updated.traderId());
        return updated;
    }

    public Optional<Portfolio> find(String traderId) {
        return repository.findByTraderId(traderId);
    }

    public List<Portfolio> list() {
        return repository.findAll();
    }

    public Portfolio delete(String traderId) {
        Portfolio deleted = repository.delete(traderId)
                .orElseThrow(() -> new EntityNotFoundException("portfolio does not exist: " + traderId));
        record(AuditType.PORTFOLIO_DELETED, deleted.traderId());
        return deleted;
    }

    private void record(AuditType type, String traderId) {
        auditLog.record(type, traderId, "traderId=" + traderId, Instant.now(clock));
    }
}
