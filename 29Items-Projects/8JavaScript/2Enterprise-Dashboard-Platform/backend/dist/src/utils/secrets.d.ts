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
export declare class SecretsManager {
    private cache;
    private encryptionKey;
    private readonly defaultTTL;
    constructor();
    private initializeEncryption;
    getSecret(name: string, config?: SecretConfig): Promise<string | null>;
    setSecret(name: string, value: string, config?: SecretConfig): Promise<void>;
    rotateSecret(name: string, config: SecretConfig, generator?: () => string): Promise<string>;
    getSecrets(names: string[], config?: SecretConfig): Promise<Record<string, string | null>>;
    private getEnvironmentSecret;
    private getFileSecret;
    private setFileSecret;
    private getAWSSecret;
    private setAWSSecret;
    private getVaultSecret;
    private setVaultSecret;
    private getAzureSecret;
    private setAzureSecret;
    private cacheSecret;
    private getCachedSecret;
    private encryptData;
    private decryptData;
    private generateSecretValue;
    clearCache(): void;
    getCacheStats(): {
        size: number;
        entries: string[];
    };
}
export declare const secretsManager: SecretsManager;
export declare function getSecret(name: string, fallback?: string): Promise<string | null>;
export declare function getRequiredSecret(name: string): Promise<string>;
export declare function getSecrets(names: string[]): Promise<Record<string, string | null>>;
export declare function validateRequiredSecrets(requiredSecrets: string[]): Promise<void>;
export declare function initializeProductionSecrets(): Promise<void>;
//# sourceMappingURL=secrets.d.ts.map