<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Payment & FraudDetection schema.
 *
 *  - payment_transactions : one row per capture attempt; unique idempotency key
 *    makes retries safe; only a PSP reference is stored (no card data).
 *  - payment_holds        : orders blocked by a fraud decision (capture gate).
 *  - fraud_assessments    : auditable risk decision per order + manual-review state.
 *
 * Additive (expand) migration — backward-compatible for zero-downtime rollouts.
 */
final class Version20260622010000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Payment (transactions, holds) and FraudDetection (assessments) schema.';
    }

    public function up(Schema $schema): void
    {
        // ---- Payment ------------------------------------------------------
        $this->addSql(<<<'SQL'
            CREATE TABLE payment_transactions (
                id              VARCHAR(36)  NOT NULL,
                order_id        VARCHAR(36)  NOT NULL,
                customer_id     VARCHAR(36)  NOT NULL,
                amount_minor    INT          NOT NULL,
                currency        VARCHAR(3)   NOT NULL,
                idempotency_key VARCHAR(64)  NOT NULL,
                status          VARCHAR(20)  NOT NULL,
                psp_reference   VARCHAR(100) DEFAULT NULL,
                created_at      TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL,
                PRIMARY KEY (id)
            )
        SQL);
        $this->addSql('CREATE UNIQUE INDEX uniq_payment_idempotency ON payment_transactions (idempotency_key)');
        $this->addSql('CREATE INDEX idx_payment_order ON payment_transactions (order_id)');

        $this->addSql(<<<'SQL'
            CREATE TABLE payment_holds (
                order_id   VARCHAR(36)  NOT NULL,
                reason     VARCHAR(255) NOT NULL,
                created_at TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL,
                PRIMARY KEY (order_id)
            )
        SQL);

        // ---- FraudDetection ----------------------------------------------
        $this->addSql(<<<'SQL'
            CREATE TABLE fraud_assessments (
                id          VARCHAR(36) NOT NULL,
                order_id    VARCHAR(36) NOT NULL,
                risk_score  DOUBLE PRECISION NOT NULL,
                decision    VARCHAR(20) NOT NULL,
                status      VARCHAR(20) NOT NULL,
                resolution  VARCHAR(20) DEFAULT NULL,
                created_at  TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL,
                resolved_at TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT NULL,
                PRIMARY KEY (id)
            )
        SQL);
        $this->addSql('CREATE UNIQUE INDEX uniq_fraud_order ON fraud_assessments (order_id)');
        $this->addSql('CREATE INDEX idx_fraud_status ON fraud_assessments (status)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP TABLE IF EXISTS fraud_assessments');
        $this->addSql('DROP TABLE IF EXISTS payment_holds');
        $this->addSql('DROP TABLE IF EXISTS payment_transactions');
    }
}
