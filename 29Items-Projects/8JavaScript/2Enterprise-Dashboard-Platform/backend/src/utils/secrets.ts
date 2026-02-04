import crypto from 'crypto';
import { readFileSync, existsSync } from 'fs';
import { logger } from '@/utils/logger.js';

/**
 * Enterprise Secrets Management System
 *
 * Provides secure handling of sensitive configuration data including:
 * - Environment variable encryption/decryption
 * - AWS Secrets Manager integration
 * - HashiCorp Vault integration
 * - Azure Key Vault integration
 * - Local secrets file encryption
 * - Runtime secret rotation
 */

export interface SecretConfig {
  provider: 'env' | 'file' | 'aws' | 'vault' | 'azure';
  encrypted?: boolean;
  rotationEnabled?: boolean;
  cacheTTL?: number;
}

export interface SecretMetadata {
  name: string;
  lastRotated?: Date;
  nextRotation?: Date;
  version?: string;
  encrypted: boolean;
}

export interface SecretCache {
  value: string;
  encrypted: boolean;
  expiresAt: number;
  metadata: SecretMetadata;
}

/**
 * Secrets Manager Class
 */
export class SecretsManager {
  private cache = new Map<string, SecretCache>();
  private encryptionKey: Buffer | null = null;
  private readonly defaultTTL = 5 * 60 * 1000; // 5 minutes

  constructor() {
    this.initializeEncryption();
  }

  /**
   * Initialize encryption for local secret storage
   */
  private initializeEncryption(): void {
    try {
      const keyEnv = process.env.SECRETS_ENCRYPTION_KEY;

      if (keyEnv) {
        this.encryptionKey = Buffer.from(keyEnv, 'hex');
        if (this.encryptionKey.length !== 32) {
          throw new Error('SECRETS_ENCRYPTION_KEY must be 32 bytes (64 hex characters)');
        }
      } else if (process.env.NODE_ENV === 'production') {
        logger.warn('SECRETS_ENCRYPTION_KEY not set - local secret encryption disabled in production');
      }

    } catch (error) {
      logger.error('Failed to initialize secrets encryption', { error });
      throw error;
    }
  }

  /**
   * Get secret by name
   */
  async getSecret(
    name: string,
    config: SecretConfig = { provider: 'env' }
  ): Promise<string | null> {
    try {
      // Check cache first
      const cached = this.getCachedSecret(name);
      if (cached && cached.expiresAt > Date.now()) {
        logger.debug('Secret retrieved from cache', { name });
        return cached.value;
      }

      let secretValue: string | null = null;

      // Retrieve secret based on provider
      switch (config.provider) {
        case 'env':
          secretValue = this.getEnvironmentSecret(name);
          break;
        case 'file':
          secretValue = await this.getFileSecret(name, config.encrypted);
          break;
        case 'aws':
          secretValue = await this.getAWSSecret(name);
          break;
        case 'vault':
          secretValue = await this.getVaultSecret(name);
          break;
        case 'azure':
          secretValue = await this.getAzureSecret(name);
          break;
        default:
          throw new Error(`Unsupported secret provider: ${config.provider}`);
      }

      if (secretValue) {
        // Cache the secret
        this.cacheSecret(name, secretValue, config, false);
        logger.debug('Secret retrieved and cached', { name, provider: config.provider });
      } else {
        logger.warn('Secret not found', { name, provider: config.provider });
      }

      return secretValue;

    } catch (error) {
      logger.error('Failed to retrieve secret', { name, provider: config.provider, error });
      throw error;
    }
  }

  /**
   * Set secret (for supported providers)
   */
  async setSecret(
    name: string,
    value: string,
    config: SecretConfig = { provider: 'env' }
  ): Promise<void> {
    try {
      switch (config.provider) {
        case 'file':
          await this.setFileSecret(name, value, config.encrypted);
          break;
        case 'aws':
          await this.setAWSSecret(name, value);
          break;
        case 'vault':
          await this.setVaultSecret(name, value);
          break;
        case 'azure':
          await this.setAzureSecret(name, value);
          break;
        default:
          throw new Error(`Setting secrets not supported for provider: ${config.provider}`);
      }

      // Update cache
      this.cacheSecret(name, value, config, config.encrypted || false);

      logger.info('Secret set successfully', { name, provider: config.provider });

    } catch (error) {
      logger.error('Failed to set secret', { name, provider: config.provider, error });
      throw error;
    }
  }

  /**
   * Rotate secret
   */
  async rotateSecret(
    name: string,
    config: SecretConfig,
    generator?: () => string
  ): Promise<string> {
    try {
      let newValue: string;

      if (generator) {
        newValue = generator();
      } else {
        newValue = this.generateSecretValue();
      }

      await this.setSecret(name, newValue, config);

      logger.info('Secret rotated successfully', { name, provider: config.provider });
      return newValue;

    } catch (error) {
      logger.error('Failed to rotate secret', { name, error });
      throw error;
    }
  }

  /**
   * Get multiple secrets at once
   */
  async getSecrets(
    names: string[],
    config: SecretConfig = { provider: 'env' }
  ): Promise<Record<string, string | null>> {
    const results: Record<string, string | null> = {};

    await Promise.all(
      names.map(async (name) => {
        try {
          results[name] = await this.getSecret(name, config);
        } catch (error) {
          logger.error('Failed to retrieve secret in batch', { name, error });
          results[name] = null;
        }
      })
    );

    return results;
  }

  /**
   * Environment Variables Provider
   */
  private getEnvironmentSecret(name: string): string | null {
    return process.env[name] || null;
  }

  /**
   * File-based Secrets Provider
   */
  private async getFileSecret(name: string, encrypted = false): Promise<string | null> {
    const secretsPath = process.env.SECRETS_FILE_PATH || './secrets.json';

    if (!existsSync(secretsPath)) {
      return null;
    }

    try {
      const fileContent = readFileSync(secretsPath, 'utf-8');
      let secretsData: Record<string, any>;

      if (encrypted && this.encryptionKey) {
        secretsData = JSON.parse(this.decryptData(fileContent));
      } else {
        secretsData = JSON.parse(fileContent);
      }

      return secretsData[name] || null;

    } catch (error) {
      logger.error('Failed to read secrets file', { path: secretsPath, error });
      return null;
    }
  }

  /**
   * Set file-based secret
   */
  private async setFileSecret(name: string, value: string, encrypted = false): Promise<void> {
    const secretsPath = process.env.SECRETS_FILE_PATH || './secrets.json';
    let secretsData: Record<string, any> = {};

    // Read existing secrets
    if (existsSync(secretsPath)) {
      try {
        const fileContent = readFileSync(secretsPath, 'utf-8');
        if (encrypted && this.encryptionKey) {
          secretsData = JSON.parse(this.decryptData(fileContent));
        } else {
          secretsData = JSON.parse(fileContent);
        }
      } catch (error) {
        logger.warn('Could not read existing secrets file, creating new one', { error });
      }
    }

    // Update secret
    secretsData[name] = value;

    // Write back to file
    const { writeFileSync } = await import('fs');
    let contentToWrite: string;

    if (encrypted && this.encryptionKey) {
      contentToWrite = this.encryptData(JSON.stringify(secretsData, null, 2));
    } else {
      contentToWrite = JSON.stringify(secretsData, null, 2);
    }

    writeFileSync(secretsPath, contentToWrite, 'utf-8');
  }

  /**
   * AWS Secrets Manager Provider
   */
  private async getAWSSecret(name: string): Promise<string | null> {
    try {
      // Dynamic import to avoid requiring AWS SDK when not used
      // @ts-ignore - optional dependency
      const { SecretsManagerClient, GetSecretValueCommand } = await import('@aws-sdk/client-secrets-manager');

      const client = new SecretsManagerClient({
        region: process.env.AWS_REGION || 'us-east-1',
        credentials: {
          accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
          secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!
        }
      });

      const command = new GetSecretValueCommand({ SecretId: name });
      const response = await client.send(command);

      return response.SecretString || null;

    } catch (error) {
      if ((error as any).name === 'ResourceNotFoundException') {
        return null;
      }
      throw error;
    }
  }

  /**
   * Set AWS secret
   */
  private async setAWSSecret(name: string, value: string): Promise<void> {
    // @ts-ignore - optional dependency
    const { SecretsManagerClient, UpdateSecretCommand } = await import('@aws-sdk/client-secrets-manager');

    const client = new SecretsManagerClient({
      region: process.env.AWS_REGION || 'us-east-1',
      credentials: {
        accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
        secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!
      }
    });

    const command = new UpdateSecretCommand({
      SecretId: name,
      SecretString: value
    });

    await client.send(command);
  }

  /**
   * HashiCorp Vault Provider
   */
  private async getVaultSecret(name: string): Promise<string | null> {
    try {
      const vaultUrl = process.env.VAULT_URL;
      const vaultToken = process.env.VAULT_TOKEN;

      if (!vaultUrl || !vaultToken) {
        throw new Error('VAULT_URL and VAULT_TOKEN must be set for Vault provider');
      }

      const response = await fetch(`${vaultUrl}/v1/secret/data/${name}`, {
        headers: {
          'X-Vault-Token': vaultToken,
          'Content-Type': 'application/json'
        }
      });

      if (!response.ok) {
        if (response.status === 404) {
          return null;
        }
        throw new Error(`Vault API error: ${response.status} ${response.statusText}`);
      }

      const data: any = await response.json();
      return data.data?.data?.value || null;

    } catch (error) {
      logger.error('Failed to retrieve secret from Vault', { name, error });
      throw error;
    }
  }

  /**
   * Set Vault secret
   */
  private async setVaultSecret(name: string, value: string): Promise<void> {
    const vaultUrl = process.env.VAULT_URL;
    const vaultToken = process.env.VAULT_TOKEN;

    if (!vaultUrl || !vaultToken) {
      throw new Error('VAULT_URL and VAULT_TOKEN must be set for Vault provider');
    }

    const response = await fetch(`${vaultUrl}/v1/secret/data/${name}`, {
      method: 'POST',
      headers: {
        'X-Vault-Token': vaultToken,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        data: { value }
      })
    });

    if (!response.ok) {
      throw new Error(`Vault API error: ${response.status} ${response.statusText}`);
    }
  }

  /**
   * Azure Key Vault Provider
   */
  private async getAzureSecret(name: string): Promise<string | null> {
    try {
      // @ts-ignore - optional dependency
      const { SecretClient } = await import('@azure/keyvault-secrets');
      // @ts-ignore - optional dependency
      const { DefaultAzureCredential } = await import('@azure/identity');

      const vaultUrl = process.env.AZURE_KEY_VAULT_URL;
      if (!vaultUrl) {
        throw new Error('AZURE_KEY_VAULT_URL must be set for Azure provider');
      }

      const credential = new DefaultAzureCredential();
      const client = new SecretClient(vaultUrl, credential);

      const secret = await client.getSecret(name);
      return secret.value || null;

    } catch (error) {
      if ((error as any).code === 'SecretNotFound') {
        return null;
      }
      throw error;
    }
  }

  /**
   * Set Azure secret
   */
  private async setAzureSecret(name: string, value: string): Promise<void> {
    // @ts-ignore - optional dependency
    const { SecretClient } = await import('@azure/keyvault-secrets');
    // @ts-ignore - optional dependency
    const { DefaultAzureCredential } = await import('@azure/identity');

    const vaultUrl = process.env.AZURE_KEY_VAULT_URL;
    if (!vaultUrl) {
      throw new Error('AZURE_KEY_VAULT_URL must be set for Azure provider');
    }

    const credential = new DefaultAzureCredential();
    const client = new SecretClient(vaultUrl, credential);

    await client.setSecret(name, value);
  }

  /**
   * Cache secret with TTL
   */
  private cacheSecret(
    name: string,
    value: string,
    config: SecretConfig,
    encrypted: boolean
  ): void {
    const ttl = config.cacheTTL || this.defaultTTL;
    const expiresAt = Date.now() + ttl;

    this.cache.set(name, {
      value,
      encrypted,
      expiresAt,
      metadata: {
        name,
        encrypted,
        lastRotated: new Date()
      }
    });
  }

  /**
   * Get cached secret
   */
  private getCachedSecret(name: string): SecretCache | null {
    return this.cache.get(name) || null;
  }

  /**
   * Encrypt sensitive data
   */
  private encryptData(data: string): string {
    if (!this.encryptionKey) {
      throw new Error('Encryption key not available');
    }

    const iv = crypto.randomBytes(16);
    const cipher = crypto.createCipheriv('aes-256-gcm', this.encryptionKey!, iv);

    let encrypted = cipher.update(data, 'utf8', 'hex');
    encrypted += cipher.final('hex');

    const authTag = cipher.getAuthTag();

    return `${iv.toString('hex')}:${authTag.toString('hex')}:${encrypted}`;
  }

  /**
   * Decrypt sensitive data
   */
  private decryptData(encryptedData: string): string {
    if (!this.encryptionKey) {
      throw new Error('Encryption key not available');
    }

    const [ivHex, authTagHex, encrypted] = encryptedData.split(':');
    const iv = Buffer.from(ivHex || '', 'hex');
    const authTag = Buffer.from(authTagHex || '', 'hex');

    const decipher = crypto.createDecipheriv('aes-256-gcm', this.encryptionKey!, iv);
    decipher.setAuthTag(authTag);

    let decrypted = decipher.update(encrypted || '', 'hex', 'utf8');
    decrypted += decipher.final('utf8');

    return decrypted;
  }

  /**
   * Generate secure secret value
   */
  private generateSecretValue(length = 64): string {
    return crypto.randomBytes(length).toString('hex');
  }

  /**
   * Clear cache
   */
  clearCache(): void {
    this.cache.clear();
    logger.debug('Secrets cache cleared');
  }

  /**
   * Get cache stats
   */
  getCacheStats(): { size: number; entries: string[] } {
    return {
      size: this.cache.size,
      entries: Array.from(this.cache.keys())
    };
  }
}

// Singleton instance
export const secretsManager = new SecretsManager();

/**
 * Convenience functions for common secret operations
 */

/**
 * Get secret from environment or configured provider
 */
export async function getSecret(name: string, fallback?: string): Promise<string | null> {
  try {
    const value = await secretsManager.getSecret(name);
    return value || fallback || null;
  } catch (error) {
    logger.error('Error getting secret', { name, error });
    return fallback || null;
  }
}

/**
 * Get required secret (throws if not found)
 */
export async function getRequiredSecret(name: string): Promise<string> {
  const value = await getSecret(name);
  if (!value) {
    throw new Error(`Required secret '${name}' not found`);
  }
  return value;
}

/**
 * Get multiple secrets
 */
export async function getSecrets(names: string[]): Promise<Record<string, string | null>> {
  return await secretsManager.getSecrets(names);
}

/**
 * Validate all required secrets are present
 */
export async function validateRequiredSecrets(requiredSecrets: string[]): Promise<void> {
  const missing: string[] = [];

  for (const secretName of requiredSecrets) {
    const value = await getSecret(secretName);
    if (!value) {
      missing.push(secretName);
    }
  }

  if (missing.length > 0) {
    throw new Error(`Missing required secrets: ${missing.join(', ')}`);
  }

  logger.info('All required secrets validated successfully', {
    count: requiredSecrets.length
  });
}

/**
 * Initialize secrets for production environment
 */
export async function initializeProductionSecrets(): Promise<void> {
  const requiredSecrets = [
    'JWT_SECRET',
    'SESSION_SECRET',
    'DATABASE_URL',
    'REDIS_URL'
  ];

  const optionalSecrets = [
    'EMAIL_SERVICE_API_KEY',
    'MONITORING_API_KEY',
    'SENTRY_DSN'
  ];

  try {
    // Validate required secrets
    await validateRequiredSecrets(requiredSecrets);

    // Check optional secrets
    const optionalResults = await getSecrets(optionalSecrets);
    const availableOptional = Object.entries(optionalResults)
      .filter(([, value]) => value !== null)
      .map(([name]) => name);

    logger.info('Production secrets initialization completed', {
      requiredSecrets: requiredSecrets.length,
      optionalSecretsAvailable: availableOptional.length,
      optionalSecrets: availableOptional
    });

  } catch (error) {
    logger.error('Failed to initialize production secrets', { error });
    throw error;
  }
}