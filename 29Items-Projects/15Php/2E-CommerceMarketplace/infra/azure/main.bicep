// =============================================================================
//  Main infrastructure composition (resource-group scope).
//  Deploy:  az deployment group create -g <rg> -f main.bicep -p env=dev
//  Provisions: Log Analytics, Container Apps Environment, ACR pull identity,
//  and the four apps (api, worker, frontend, ml) via the container-apps module.
//
//  NOTE: PostgreSQL, RabbitMQ and Elasticsearch are provisioned separately
//  (Azure DB for PostgreSQL Flexible Server, RabbitMQ add-on, Elastic Cloud);
//  their connection strings are stored in Key Vault and referenced as secrets.
// =============================================================================

@description('Environment name: dev | staging | prod')
param env string = 'dev'

@description('Azure region')
param location string = resourceGroup().location

@description('Container image tag (commit SHA) to deploy')
param imageTag string

@description('ACR login server, e.g. marketplaceacr.azurecr.io')
param acrLoginServer string

var namePrefix = 'marketplace-${env}'

resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: '${namePrefix}-logs'
  location: location
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: env == 'prod' ? 90 : 30
  }
}

resource caEnv 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${namePrefix}-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}

module apps 'container-apps.bicep' = {
  name: 'apps'
  params: {
    env: env
    location: location
    managedEnvironmentId: caEnv.id
    acrLoginServer: acrLoginServer
    imageTag: imageTag
  }
}

output environmentId string = caEnv.id
output apiFqdn string = apps.outputs.apiFqdn
