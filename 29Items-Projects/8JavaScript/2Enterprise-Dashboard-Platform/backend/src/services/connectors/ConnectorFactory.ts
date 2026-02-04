/**
 * Connector Factory
 * Creates and manages database connector instances with connection pooling
 */

import { BaseConnector, ConnectionConfig, ConnectorType } from './BaseConnector.js';
import { PostgreSQLConnector } from './PostgreSQLConnector.js';
import { MySQLConnector } from './MySQLConnector.js';

interface PooledConnector {
  connector: BaseConnector;
  lastUsed: Date;
  useCount: number;
}

export class ConnectorFactory {
  private static instance: ConnectorFactory;
  private connectorPool: Map<string, PooledConnector> = new Map();
  private maxPoolSize: number = 50;
  private maxIdleTime: number = 30 * 60 * 1000; // 30 minutes

  private constructor() {
    // Start cleanup interval
    setInterval(() => this.cleanupIdleConnections(), 5 * 60 * 1000); // Every 5 minutes
  }

  static getInstance(): ConnectorFactory {
    if (!ConnectorFactory.instance) {
      ConnectorFactory.instance = new ConnectorFactory();
    }
    return ConnectorFactory.instance;
  }

  /**
   * Create or get a connector from the pool
   */
  async getConnector(
    connectionId: string,
    type: ConnectorType,
    config: ConnectionConfig,
    credentials: { username: string; password: string }
  ): Promise<BaseConnector> {
    // Check if connector exists in pool
    const pooled = this.connectorPool.get(connectionId);
    if (pooled && pooled.connector.isConnected()) {
      pooled.lastUsed = new Date();
      pooled.useCount++;
      return pooled.connector;
    }

    // Check pool size limit
    if (this.connectorPool.size >= this.maxPoolSize) {
      await this.evictLeastUsedConnector();
    }

    // Create new connector
    const connector = this.createConnector(connectionId, type, config, credentials);
    await connector.connect();

    // Add to pool
    this.connectorPool.set(connectionId, {
      connector,
      lastUsed: new Date(),
      useCount: 1,
    });

    return connector;
  }

  /**
   * Create a new connector instance (not pooled, for testing)
   */
  createConnector(
    connectionId: string,
    type: ConnectorType,
    config: ConnectionConfig,
    credentials: { username: string; password: string }
  ): BaseConnector {
    switch (type) {
      case 'POSTGRESQL':
        return new PostgreSQLConnector(connectionId, config, credentials);
      case 'MYSQL':
        return new MySQLConnector(connectionId, config, credentials);
      default:
        throw new Error(`Unsupported connector type: ${type}`);
    }
  }

  /**
   * Remove a connector from the pool and disconnect
   */
  async removeConnector(connectionId: string): Promise<void> {
    const pooled = this.connectorPool.get(connectionId);
    if (pooled) {
      await pooled.connector.disconnect();
      this.connectorPool.delete(connectionId);
    }
  }

  /**
   * Check if a connector type is supported
   */
  isTypeSupported(type: string): type is ConnectorType {
    return type === 'POSTGRESQL' || type === 'MYSQL';
  }

  /**
   * Get pool statistics
   */
  getPoolStats(): {
    size: number;
    maxSize: number;
    connectors: Array<{
      id: string;
      lastUsed: Date;
      useCount: number;
      connected: boolean;
    }>;
  } {
    const connectors = Array.from(this.connectorPool.entries()).map(([id, pooled]) => ({
      id,
      lastUsed: pooled.lastUsed,
      useCount: pooled.useCount,
      connected: pooled.connector.isConnected(),
    }));

    return {
      size: this.connectorPool.size,
      maxSize: this.maxPoolSize,
      connectors,
    };
  }

  /**
   * Clean up idle connections
   */
  private async cleanupIdleConnections(): Promise<void> {
    const now = Date.now();
    const toRemove: string[] = [];

    for (const [id, pooled] of this.connectorPool.entries()) {
      if (now - pooled.lastUsed.getTime() > this.maxIdleTime) {
        toRemove.push(id);
      }
    }

    for (const id of toRemove) {
      await this.removeConnector(id);
    }
  }

  /**
   * Evict the least recently used connector
   */
  private async evictLeastUsedConnector(): Promise<void> {
    let oldest: { id: string; lastUsed: Date } | null = null;

    for (const [id, pooled] of this.connectorPool.entries()) {
      if (!oldest || pooled.lastUsed < oldest.lastUsed) {
        oldest = { id, lastUsed: pooled.lastUsed };
      }
    }

    if (oldest) {
      await this.removeConnector(oldest.id);
    }
  }

  /**
   * Disconnect all connectors and clear pool
   */
  async shutdown(): Promise<void> {
    const ids = Array.from(this.connectorPool.keys());
    for (const id of ids) {
      await this.removeConnector(id);
    }
  }
}

// Export singleton instance
export const connectorFactory = ConnectorFactory.getInstance();
