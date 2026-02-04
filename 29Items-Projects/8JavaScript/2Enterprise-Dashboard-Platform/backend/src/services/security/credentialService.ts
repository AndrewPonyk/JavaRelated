/**
 * Credential Service
 * Handles encryption and decryption of sensitive credentials using AES-256-GCM
 */

import crypto from 'crypto';

const ALGORITHM = 'aes-256-gcm';
const IV_LENGTH = 16;
const AUTH_TAG_LENGTH = 16;
const SALT_LENGTH = 32;

interface EncryptedCredentials {
  encrypted: string;
  iv: string;
  authTag: string;
  salt: string;
}

export class CredentialService {
  private static instance: CredentialService;
  private masterKey: Buffer;

  private constructor() {
    const encryptionKey = process.env.CREDENTIAL_ENCRYPTION_KEY;

    if (!encryptionKey) {
      console.warn(
        'WARNING: CREDENTIAL_ENCRYPTION_KEY not set. Using a derived key from NODE_ENV. ' +
        'This is NOT secure for production!'
      );
      // Derive a key for development - NOT FOR PRODUCTION
      this.masterKey = crypto
        .createHash('sha256')
        .update(process.env.NODE_ENV || 'development')
        .digest();
    } else if (encryptionKey.length < 32) {
      throw new Error('CREDENTIAL_ENCRYPTION_KEY must be at least 32 characters');
    } else {
      // Hash the key to ensure it's exactly 32 bytes
      this.masterKey = crypto
        .createHash('sha256')
        .update(encryptionKey)
        .digest();
    }
  }

  static getInstance(): CredentialService {
    if (!CredentialService.instance) {
      CredentialService.instance = new CredentialService();
    }
    return CredentialService.instance;
  }

  /**
   * Derive a unique key for each credential set using salt
   */
  private deriveKey(salt: Buffer): Buffer {
    return crypto.pbkdf2Sync(this.masterKey, salt, 100000, 32, 'sha256');
  }

  /**
   * Encrypt credentials object
   */
  encrypt(credentials: Record<string, unknown>): EncryptedCredentials {
    const plaintext = JSON.stringify(credentials);

    // Generate random salt and IV
    const salt = crypto.randomBytes(SALT_LENGTH);
    const iv = crypto.randomBytes(IV_LENGTH);

    // Derive unique key using salt
    const key = this.deriveKey(salt);

    // Create cipher
    const cipher = crypto.createCipheriv(ALGORITHM, key, iv);

    // Encrypt
    let encrypted = cipher.update(plaintext, 'utf8', 'hex');
    encrypted += cipher.final('hex');

    // Get auth tag
    const authTag = cipher.getAuthTag();

    return {
      encrypted,
      iv: iv.toString('hex'),
      authTag: authTag.toString('hex'),
      salt: salt.toString('hex'),
    };
  }

  /**
   * Decrypt credentials
   */
  decrypt(encryptedData: EncryptedCredentials): Record<string, unknown> {
    const { encrypted, iv, authTag, salt } = encryptedData;

    // Convert from hex
    const ivBuffer = Buffer.from(iv, 'hex');
    const authTagBuffer = Buffer.from(authTag, 'hex');
    const saltBuffer = Buffer.from(salt, 'hex');

    // Derive key using same salt
    const key = this.deriveKey(saltBuffer);

    // Create decipher
    const decipher = crypto.createDecipheriv(ALGORITHM, key, ivBuffer);
    decipher.setAuthTag(authTagBuffer);

    // Decrypt
    let decrypted = decipher.update(encrypted, 'hex', 'utf8');
    decrypted += decipher.final('utf8');

    return JSON.parse(decrypted);
  }

  /**
   * Check if data is encrypted (has the expected structure)
   */
  isEncrypted(data: unknown): data is EncryptedCredentials {
    if (typeof data !== 'object' || data === null) {
      return false;
    }

    const obj = data as Record<string, unknown>;
    return (
      typeof obj.encrypted === 'string' &&
      typeof obj.iv === 'string' &&
      typeof obj.authTag === 'string' &&
      typeof obj.salt === 'string'
    );
  }

  /**
   * Safely get credentials - decrypt if encrypted, return as-is if not
   */
  getCredentials(data: unknown): Record<string, unknown> {
    if (this.isEncrypted(data)) {
      return this.decrypt(data);
    }

    if (typeof data === 'object' && data !== null) {
      return data as Record<string, unknown>;
    }

    return {};
  }

  /**
   * Mask sensitive credential values for display
   */
  maskCredentials(credentials: Record<string, unknown>): Record<string, unknown> {
    const masked: Record<string, unknown> = {};
    const sensitiveKeys = ['password', 'secret', 'token', 'key', 'apikey', 'api_key'];

    for (const [key, value] of Object.entries(credentials)) {
      const isSeansitive = sensitiveKeys.some(
        (sk) => key.toLowerCase().includes(sk)
      );

      if (isSeansitive && typeof value === 'string') {
        masked[key] = value.length > 4
          ? `${'*'.repeat(value.length - 4)}${value.slice(-4)}`
          : '****';
      } else {
        masked[key] = value;
      }
    }

    return masked;
  }
}

// Export singleton instance
export const credentialService = CredentialService.getInstance();
