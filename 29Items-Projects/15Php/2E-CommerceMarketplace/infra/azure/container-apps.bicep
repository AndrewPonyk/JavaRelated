// =============================================================================
//  Container Apps: api (external), worker (scales on queue depth), frontend,
//  ml-service (internal only). Secrets come from Key Vault references (wired via
//  the app's managed identity — omitted here for brevity, marked TODO).
// =============================================================================

param env string
param location string
param managedEnvironmentId string
param acrLoginServer string
param imageTag string

var namePrefix = 'marketplace-${env}'

// ---- API (external ingress, scales on HTTP concurrency) ---------------------
resource api 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'marketplace-api'
  location: location
  identity: { type: 'SystemAssigned' } // used for ACR pull + Key Vault access
  properties: {
    managedEnvironmentId: managedEnvironmentId
    configuration: {
      activeRevisionsMode: 'Multiple' // enables blue/green & canary via traffic split
      ingress: {
        external: true
        targetPort: 8000
        transport: 'auto'
      }
      registries: [
        { server: acrLoginServer, identity: 'system' }
      ]
      // TODO: secrets: [{ name: 'database-url', keyVaultUrl: '...', identity: 'system' }]
    }
    template: {
      containers: [
        {
          name: 'api'
          image: '${acrLoginServer}/marketplace-backend:${imageTag}'
          resources: { cpu: json('0.5'), memory: '1Gi' }
          probes: [
            { type: 'Liveness', httpGet: { path: '/health/live', port: 8000 } }
            { type: 'Readiness', httpGet: { path: '/health/ready', port: 8000 } }
          ]
        }
      ]
      scale: {
        minReplicas: env == 'prod' ? 2 : 1 // avoid cold starts on latency-sensitive api
        maxReplicas: 10
        rules: [
          { name: 'http', http: { metadata: { concurrentRequests: '80' } } }
        ]
      }
    }
  }
}

// ---- Worker (no ingress, scales 0→N on RabbitMQ queue depth via KEDA) --------
resource worker 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'marketplace-worker'
  location: location
  identity: { type: 'SystemAssigned' }
  properties: {
    managedEnvironmentId: managedEnvironmentId
    configuration: {
      registries: [{ server: acrLoginServer, identity: 'system' }]
    }
    template: {
      containers: [
        {
          name: 'worker'
          image: '${acrLoginServer}/marketplace-backend:${imageTag}'
          command: ['php', 'bin/console', 'messenger:consume', 'async', '--time-limit=3600']
          resources: { cpu: json('0.5'), memory: '1Gi' }
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 20
        rules: [
          {
            name: 'rabbitmq-queue'
            custom: {
              type: 'rabbitmq'
              metadata: { queueName: 'messages', mode: 'QueueLength', value: '20' }
              // auth: [{ secretRef: 'rabbitmq-conn', triggerParameter: 'host' }]  // TODO
            }
          }
        ]
      }
    }
  }
}

// ---- ml-service (internal ingress only) -------------------------------------
resource ml 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'marketplace-ml'
  location: location
  identity: { type: 'SystemAssigned' }
  properties: {
    managedEnvironmentId: managedEnvironmentId
    configuration: {
      ingress: { external: false, targetPort: 8001 } // reachable only inside the env
      registries: [{ server: acrLoginServer, identity: 'system' }]
    }
    template: {
      containers: [
        {
          name: 'ml'
          image: '${acrLoginServer}/marketplace-ml-service:${imageTag}'
          resources: { cpu: json('1.0'), memory: '2Gi' }
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 5, rules: [{ name: 'cpu', custom: { type: 'cpu', metadata: { type: 'Utilization', value: '70' } } }] }
    }
  }
}

// ---- Frontend (external static host) ----------------------------------------
resource frontend 'Microsoft.App/containerApps@2024-03-01' = {
  name: 'marketplace-frontend'
  location: location
  identity: { type: 'SystemAssigned' }
  properties: {
    managedEnvironmentId: managedEnvironmentId
    configuration: {
      ingress: { external: true, targetPort: 8080 }
      registries: [{ server: acrLoginServer, identity: 'system' }]
    }
    template: {
      containers: [
        {
          name: 'frontend'
          image: '${acrLoginServer}/marketplace-frontend:${imageTag}'
          resources: { cpu: json('0.25'), memory: '0.5Gi' }
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 5 }
    }
  }
}

output apiFqdn string = api.properties.configuration.ingress.fqdn
