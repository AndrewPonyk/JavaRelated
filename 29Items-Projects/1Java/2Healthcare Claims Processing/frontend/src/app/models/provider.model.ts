/**
 * Healthcare Claims Processing - Provider Models
 */

export interface Provider {
  id: string;
  npi: string;
  name: string;
  specialty?: string;
  taxId?: string;
  inNetwork: boolean;
  providerType?: string;
  isActive: boolean;
}

export function getNetworkStatus(provider: Provider): string {
  return provider.inNetwork ? 'In-Network' : 'Out-of-Network';
}
