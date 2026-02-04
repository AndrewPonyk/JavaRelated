import crypto from 'crypto';
import { readFileSync, existsSync } from 'fs';
import { logger } from '@/utils/logger.js';
export class SecretsManager {
    cache = new Map();
    encryptionKey = null;
    defaultTTL = 5 * 60 * 1000;
    constructor() {
        this.initializeEncryption();
    }
    initializeEncryption() {
        try {
            const keyEnv = process.env.SECRETS_ENCRYPTION_KEY;
            if (keyEnv) {
                this.encryptionKey = Buffer.from(keyEnv, 'hex');
                if (this.encryptionKey.length !== 32) {
                    throw new Error('SECRETS_ENCRYPTION_KEY must be 32 bytes (64 hex characters)');
                }
            }
            else if (process.env.NODE_ENV === 'production') {
                logger.warn('SECRETS_ENCRYPTION_KEY not set - local secret encryption disabled in production');
            }
        }
        catch (error) {
            logger.error('Failed to initialize secrets encryption', { error });
            throw error;
        }
    }
    async getSecret(name, config = { provider: 'env' }) {
        try {
            const cached = this.getCachedSecret(name);
            if (cached && cached.expiresAt > Date.now()) {
                logger.debug('Secret retrieved from cache', { name });
                return cached.value;
            }
            let secretValue = null;
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
                this.cacheSecret(name, secretValue, config, false);
                logger.debug('Secret retrieved and cached', { name, provider: config.provider });
            }
            else {
                logger.warn('Secret not found', { name, provider: config.provider });
            }
            return secretValue;
        }
        catch (error) {
            logger.error('Failed to retrieve secret', { name, provider: config.provider, error });
            throw error;
        }
    }
    async setSecret(name, value, config = { provider: 'env' }) {
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
            this.cacheSecret(name, value, config, config.encrypted || false);
            logger.info('Secret set successfully', { name, provider: config.provider });
        }
        catch (error) {
            logger.error('Failed to set secret', { name, provider: config.provider, error });
            throw error;
        }
    }
    async rotateSecret(name, config, generator) {
        try {
            let newValue;
            if (generator) {
                newValue = generator();
            }
            else {
                newValue = this.generateSecretValue();
            }
            await this.setSecret(name, newValue, config);
            logger.info('Secret rotated successfully', { name, provider: config.provider });
            return newValue;
        }
        catch (error) {
            logger.error('Failed to rotate secret', { name, error });
            throw error;
        }
    }
    async getSecrets(names, config = { provider: 'env' }) {
        const results = {};
        await Promise.all(names.map(async (name) => {
            try {
                results[name] = await this.getSecret(name, config);
            }
            catch (error) {
                logger.error('Failed to retrieve secret in batch', { name, error });
                results[name] = null;
            }
        }));
        return results;
    }
    getEnvironmentSecret(name) {
        return process.env[name] || null;
    }
    async getFileSecret(name, encrypted = false) {
        const secretsPath = process.env.SECRETS_FILE_PATH || './secrets.json';
        if (!existsSync(secretsPath)) {
            return null;
        }
        try {
            const fileContent = readFileSync(secretsPath, 'utf-8');
            let secretsData;
            if (encrypted && this.encryptionKey) {
                secretsData = JSON.parse(this.decryptData(fileContent));
            }
            else {
                secretsData = JSON.parse(fileContent);
            }
            return secretsData[name] || null;
        }
        catch (error) {
            logger.error('Failed to read secrets file', { path: secretsPath, error });
            return null;
        }
    }
    async setFileSecret(name, value, encrypted = false) {
        const secretsPath = process.env.SECRETS_FILE_PATH || './secrets.json';
        let secretsData = {};
        if (existsSync(secretsPath)) {
            try {
                const fileContent = readFileSync(secretsPath, 'utf-8');
                if (encrypted && this.encryptionKey) {
                    secretsData = JSON.parse(this.decryptData(fileContent));
                }
                else {
                    secretsData = JSON.parse(fileContent);
                }
            }
            catch (error) {
                logger.warn('Could not read existing secrets file, creating new one', { error });
            }
        }
        secretsData[name] = value;
        const { writeFileSync } = await import('fs');
        let contentToWrite;
        if (encrypted && this.encryptionKey) {
            contentToWrite = this.encryptData(JSON.stringify(secretsData, null, 2));
        }
        else {
            contentToWrite = JSON.stringify(secretsData, null, 2);
        }
        writeFileSync(secretsPath, contentToWrite, 'utf-8');
    }
    async getAWSSecret(name) {
        try {
            const { SecretsManagerClient, GetSecretValueCommand } = await import('@aws-sdk/client-secrets-manager');
            const client = new SecretsManagerClient({
                region: process.env.AWS_REGION || 'us-east-1',
                credentials: {
                    accessKeyId: process.env.AWS_ACCESS_KEY_ID,
                    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY
                }
            });
            const command = new GetSecretValueCommand({ SecretId: name });
            const response = await client.send(command);
            return response.SecretString || null;
        }
        catch (error) {
            if (error.name === 'ResourceNotFoundException') {
                return null;
            }
            throw error;
        }
    }
    async setAWSSecret(name, value) {
        const { SecretsManagerClient, UpdateSecretCommand } = await import('@aws-sdk/client-secrets-manager');
        const client = new SecretsManagerClient({
            region: process.env.AWS_REGION || 'us-east-1',
            credentials: {
                accessKeyId: process.env.AWS_ACCESS_KEY_ID,
                secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY
            }
        });
        const command = new UpdateSecretCommand({
            SecretId: name,
            SecretString: value
        });
        await client.send(command);
    }
    async getVaultSecret(name) {
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
            const data = await response.json();
            return data.data?.data?.value || null;
        }
        catch (error) {
            logger.error('Failed to retrieve secret from Vault', { name, error });
            throw error;
        }
    }
    async setVaultSecret(name, value) {
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
    async getAzureSecret(name) {
        try {
            const { SecretClient } = await import('@azure/keyvault-secrets');
            const { DefaultAzureCredential } = await import('@azure/identity');
            const vaultUrl = process.env.AZURE_KEY_VAULT_URL;
            if (!vaultUrl) {
                throw new Error('AZURE_KEY_VAULT_URL must be set for Azure provider');
            }
            const credential = new DefaultAzureCredential();
            const client = new SecretClient(vaultUrl, credential);
            const secret = await client.getSecret(name);
            return secret.value || null;
        }
        catch (error) {
            if (error.code === 'SecretNotFound') {
                return null;
            }
            throw error;
        }
    }
    async setAzureSecret(name, value) {
        const { SecretClient } = await import('@azure/keyvault-secrets');
        const { DefaultAzureCredential } = await import('@azure/identity');
        const vaultUrl = process.env.AZURE_KEY_VAULT_URL;
        if (!vaultUrl) {
            throw new Error('AZURE_KEY_VAULT_URL must be set for Azure provider');
        }
        const credential = new DefaultAzureCredential();
        const client = new SecretClient(vaultUrl, credential);
        await client.setSecret(name, value);
    }
    cacheSecret(name, value, config, encrypted) {
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
    getCachedSecret(name) {
        return this.cache.get(name) || null;
    }
    encryptData(data) {
        if (!this.encryptionKey) {
            throw new Error('Encryption key not available');
        }
        const iv = crypto.randomBytes(16);
        const cipher = crypto.createCipher('aes-256-gcm', this.encryptionKey);
        let encrypted = cipher.update(data, 'utf8', 'hex');
        encrypted += cipher.final('hex');
        const authTag = cipher.getAuthTag();
        return `${iv.toString('hex')}:${authTag.toString('hex')}:${encrypted}`;
    }
    decryptData(encryptedData) {
        if (!this.encryptionKey) {
            throw new Error('Encryption key not available');
        }
        const [ivHex, authTagHex, encrypted] = encryptedData.split(':');
        const iv = Buffer.from(ivHex, 'hex');
        const authTag = Buffer.from(authTagHex, 'hex');
        const decipher = crypto.createDecipher('aes-256-gcm', this.encryptionKey);
        decipher.setAuthTag(authTag);
        let decrypted = decipher.update(encrypted, 'hex', 'utf8');
        decrypted += decipher.final('utf8');
        return decrypted;
    }
    generateSecretValue(length = 64) {
        return crypto.randomBytes(length).toString('hex');
    }
    clearCache() {
        this.cache.clear();
        logger.debug('Secrets cache cleared');
    }
    getCacheStats() {
        return {
            size: this.cache.size,
            entries: Array.from(this.cache.keys())
        };
    }
}
export const secretsManager = new SecretsManager();
export async function getSecret(name, fallback) {
    try {
        const value = await secretsManager.getSecret(name);
        return value || fallback || null;
    }
    catch (error) {
        logger.error('Error getting secret', { name, error });
        return fallback || null;
    }
}
export async function getRequiredSecret(name) {
    const value = await getSecret(name);
    if (!value) {
        throw new Error(`Required secret '${name}' not found`);
    }
    return value;
}
export async function getSecrets(names) {
    return await secretsManager.getSecrets(names);
}
export async function validateRequiredSecrets(requiredSecrets) {
    const missing = [];
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
export async function initializeProductionSecrets() {
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
        await validateRequiredSecrets(requiredSecrets);
        const optionalResults = await getSecrets(optionalSecrets);
        const availableOptional = Object.entries(optionalResults)
            .filter(([, value]) => value !== null)
            .map(([name]) => name);
        logger.info('Production secrets initialization completed', {
            requiredSecrets: requiredSecrets.length,
            optionalSecretsAvailable: availableOptional.length,
            optionalSecrets: availableOptional
        });
    }
    catch (error) {
        logger.error('Failed to initialize production secrets', { error });
        throw error;
    }
}
//# sourceMappingURL=secrets.js.map