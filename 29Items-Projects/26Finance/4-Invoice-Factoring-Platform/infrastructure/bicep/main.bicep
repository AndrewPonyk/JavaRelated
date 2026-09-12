// =============================================================================
//  Invoice Factoring Platform — core Azure infrastructure (Bicep).
//  Provisions: Log Analytics + App Insights, ACR, AKS, Azure SQL, Redis,
//  Key Vault, and a Service Bus namespace with queues.
//
//  This is a scaffold. TODO before production: private endpoints / VNet
//  integration, no-public-network on data stores, RBAC role assignments,
//  diagnostic settings, and parameterized SKUs per environment.
//
//  Deploy:
//    az deployment group create -g <rg> -f main.bicep -p environment=dev
// =============================================================================

@description('Environment name (dev | staging | prod).')
@allowed([ 'dev', 'staging', 'prod' ])
param environment string = 'dev'

@description('Primary location for all resources.')
param location string = resourceGroup().location

@description('SQL administrator login.')
param sqlAdminLogin string

@description('SQL administrator password.')
@secure()
param sqlAdminPassword string

var namePrefix = 'invfact-${environment}'
var tags = {
  app: 'invoice-factoring'
  environment: environment
}

// ---- Observability -----------------------------------------------------------
resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: '${namePrefix}-logs'
  location: location
  tags: tags
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
  }
}

resource appInsights 'Microsoft.Insights/components@2020-02-02' = {
  name: '${namePrefix}-ai'
  location: location
  kind: 'web'
  tags: tags
  properties: {
    Application_Type: 'web'
    WorkspaceResourceId: logAnalytics.id
  }
}

// ---- Container registry -------------------------------------------------------
resource acr 'Microsoft.ContainerRegistry/registries@2023-11-01-preview' = {
  name: replace('${namePrefix}acr', '-', '')
  location: location
  tags: tags
  sku: { name: 'Standard' }
  properties: { adminUserEnabled: false }
}

// ---- AKS cluster --------------------------------------------------------------
resource aks 'Microsoft.ContainerService/managedClusters@2024-02-01' = {
  name: '${namePrefix}-aks'
  location: location
  tags: tags
  identity: { type: 'SystemAssigned' }
  properties: {
    dnsPrefix: '${namePrefix}-aks'
    enableRBAC: true
    agentPoolProfiles: [
      {
        name: 'system'
        mode: 'System'
        count: environment == 'prod' ? 3 : 2
        vmSize: 'Standard_D2s_v5'
        osType: 'Linux'
        enableAutoScaling: true
        minCount: environment == 'prod' ? 3 : 1
        maxCount: environment == 'prod' ? 6 : 3
      }
      // TODO: add a spot node pool for ML training Jobs.
    ]
    addonProfiles: {
      omsagent: {
        enabled: true
        config: { logAnalyticsWorkspaceResourceID: logAnalytics.id }
      }
      azureKeyvaultSecretsProvider: { enabled: true } // Secrets Store CSI driver
    }
  }
}

// Let AKS pull images from ACR (AcrPull role).
resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, aks.id, 'AcrPull')
  scope: acr
  properties: {
    principalId: aks.properties.identityProfile.kubeletidentity.objectId
    principalType: 'ServicePrincipal'
    roleDefinitionId: subscriptionResourceId(
      'Microsoft.Authorization/roleDefinitions',
      '7f951dda-4ed3-4680-a7ca-43fe172d538d') // AcrPull
  }
}

// ---- Azure SQL ----------------------------------------------------------------
resource sqlServer 'Microsoft.Sql/servers@2023-08-01-preview' = {
  name: '${namePrefix}-sql'
  location: location
  tags: tags
  properties: {
    administratorLogin: sqlAdminLogin
    administratorLoginPassword: sqlAdminPassword
    minimalTlsVersion: '1.2'
    publicNetworkAccess: 'Enabled' // TODO: 'Disabled' + private endpoint for prod
  }
}

resource sqlDb 'Microsoft.Sql/servers/databases@2023-08-01-preview' = {
  parent: sqlServer
  name: 'InvoiceFactoring'
  location: location
  tags: tags
  sku: environment == 'prod' ? { name: 'HS_Gen5', tier: 'Hyperscale', capacity: 2 } : { name: 'GP_S_Gen5', tier: 'GeneralPurpose', capacity: 1 }
}

// ---- Redis cache --------------------------------------------------------------
resource redis 'Microsoft.Cache/redis@2024-03-01' = {
  name: '${namePrefix}-redis'
  location: location
  tags: tags
  properties: {
    sku: { name: 'Basic', family: 'C', capacity: 0 }
    minimumTlsVersion: '1.2'
    enableNonSslPort: false
  }
}

// ---- Key Vault ----------------------------------------------------------------
resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: '${namePrefix}-kv'
  location: location
  tags: tags
  properties: {
    sku: { family: 'A', name: 'standard' }
    tenantId: subscription().tenantId
    enableRbacAuthorization: true
    enableSoftDelete: true
    enablePurgeProtection: true
  }
}

// ---- Service Bus --------------------------------------------------------------
resource serviceBus 'Microsoft.ServiceBus/namespaces@2022-10-01-preview' = {
  name: '${namePrefix}-sb'
  location: location
  tags: tags
  sku: { name: 'Standard', tier: 'Standard' }
}

resource underwritingQueue 'Microsoft.ServiceBus/namespaces/queues@2022-10-01-preview' = {
  parent: serviceBus
  name: 'invoice-underwriting'
  properties: {
    maxDeliveryCount: 5
    deadLetteringOnMessageExpiration: true
    lockDuration: 'PT1M'
  }
}

resource paymentsQueue 'Microsoft.ServiceBus/namespaces/queues@2022-10-01-preview' = {
  parent: serviceBus
  name: 'payment-events'
  properties: {
    maxDeliveryCount: 5
    deadLetteringOnMessageExpiration: true
  }
}

// ---- Outputs ------------------------------------------------------------------
output acrLoginServer string = acr.properties.loginServer
output aksName string = aks.name
output keyVaultName string = keyVault.name
output appInsightsConnectionString string = appInsights.properties.ConnectionString
output sqlServerFqdn string = sqlServer.properties.fullyQualifiedDomainName
