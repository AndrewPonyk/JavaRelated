<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Initial schema for all bounded contexts.
 *
 * Note the two persistence styles living side by side:
 *  - catalog_products / vendor_* / identity_users : conventional state tables.
 *  - order_event_store                            : append-only EVENT STREAM
 *    (event sourcing). The unique (aggregate_id, version) is the optimistic
 *    concurrency control for order streams.
 *
 * Migrations are expand/contract & backward-compatible to allow zero-downtime
 * rollouts on Azure Container Apps (run as a pre-deploy job).
 */
final class Version20260622000000 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Initial schema: identity, catalog, ordering (event store), vendor commission.';
    }

    public function up(Schema $schema): void
    {
        // ---- Identity ------------------------------------------------------
        $this->addSql(<<<'SQL'
            CREATE TABLE identity_users (
                id          VARCHAR(36)  NOT NULL,
                email       VARCHAR(180) NOT NULL,
                roles       JSON         NOT NULL,
                password    VARCHAR(255) NOT NULL,
                PRIMARY KEY (id)
            )
        SQL);
        $this->addSql('CREATE UNIQUE INDEX uniq_user_email ON identity_users (email)');

        // ---- Catalog (write model) ----------------------------------------
        $this->addSql(<<<'SQL'
            CREATE TABLE catalog_products (
                id                 VARCHAR(36)  NOT NULL,
                seller_id          VARCHAR(36)  NOT NULL,
                name               VARCHAR(255) NOT NULL,
                description        TEXT         NOT NULL,
                stock              INT          NOT NULL,
                active             BOOLEAN      NOT NULL,
                price_amount_minor INT          NOT NULL,
                price_currency     VARCHAR(3)   NOT NULL,
                PRIMARY KEY (id)
            )
        SQL);
        $this->addSql('CREATE INDEX idx_catalog_products_seller ON catalog_products (seller_id)');

        // ---- Ordering (event store) ---------------------------------------
        $this->addSql(<<<'SQL'
            CREATE TABLE order_event_store (
                sequence     BIGSERIAL    NOT NULL,
                aggregate_id VARCHAR(36)  NOT NULL,
                version      INT          NOT NULL,
                event_name   VARCHAR(100) NOT NULL,
                payload      JSON         NOT NULL,
                occurred_on  TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL,
                PRIMARY KEY (sequence)
            )
        SQL);
        $this->addSql('CREATE UNIQUE INDEX uniq_stream_version ON order_event_store (aggregate_id, version)');
        $this->addSql('CREATE INDEX idx_event_store_aggregate ON order_event_store (aggregate_id)');

        // ---- Vendor (sellers + commission ledger) -------------------------
        $this->addSql(<<<'SQL'
            CREATE TABLE vendor_sellers (
                id                  VARCHAR(36)  NOT NULL,
                display_name        VARCHAR(255) NOT NULL,
                commission_rate_bps INT          NOT NULL,
                PRIMARY KEY (id)
            )
        SQL);
        $this->addSql(<<<'SQL'
            CREATE TABLE vendor_commission_ledger (
                id               VARCHAR(36) NOT NULL,
                seller_id        VARCHAR(36) NOT NULL,
                order_id         VARCHAR(36) NOT NULL,
                gross_minor      INT         NOT NULL,
                commission_minor INT         NOT NULL,
                currency         VARCHAR(3)  NOT NULL,
                occurred_on      TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL,
                PRIMARY KEY (id)
            )
        SQL);
        $this->addSql('CREATE INDEX idx_commission_seller ON vendor_commission_ledger (seller_id)');

        // ---- Messenger failed transport (doctrine) ------------------------
        $this->addSql(<<<'SQL'
            CREATE TABLE messenger_messages (
                id           BIGSERIAL    NOT NULL,
                body         TEXT         NOT NULL,
                headers      TEXT         NOT NULL,
                queue_name   VARCHAR(190) NOT NULL,
                created_at   TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL,
                available_at TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL,
                delivered_at TIMESTAMP(0) WITHOUT TIME ZONE DEFAULT NULL,
                PRIMARY KEY (id)
            )
        SQL);
        $this->addSql('CREATE INDEX idx_messenger_queue ON messenger_messages (queue_name)');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('DROP TABLE IF EXISTS messenger_messages');
        $this->addSql('DROP TABLE IF EXISTS vendor_commission_ledger');
        $this->addSql('DROP TABLE IF EXISTS vendor_sellers');
        $this->addSql('DROP TABLE IF EXISTS order_event_store');
        $this->addSql('DROP TABLE IF EXISTS catalog_products');
        $this->addSql('DROP TABLE IF EXISTS identity_users');
    }
}
