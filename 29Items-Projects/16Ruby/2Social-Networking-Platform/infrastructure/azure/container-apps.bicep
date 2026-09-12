@description('Azure Container Apps environment name')
param environmentName string

@description('Location for resources')
param location string = resourceGroup().location

@description('Backend container image')
param backendImage string

@description('Frontend container image')
param frontendImage string

@description('Application secret key')
@secure()
param secretKeyBase string

@description('JWT signing secret')
@secure()
param jwtSecretKeyBase string

@description('PostgreSQL connection string')
@secure()
param databaseUrl string

@description('Redis connection string')
@secure()
param redisUrl string

@description('Elasticsearch endpoint')
param elasticsearchUrl string

resource containerAppsEnvironment 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: environmentName
  location: location
  properties: {}
}

resource backendApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${environmentName}-api'
  location: location
  properties: {
    managedEnvironmentId: containerAppsEnvironment.id
    configuration: {
      ingress: {
        external: true
        targetPort: 3000
      }
      secrets: [
        { name: 'database-url', value: databaseUrl }
        { name: 'redis-url', value: redisUrl }
        { name: 'secret-key-base', value: secretKeyBase }
        { name: 'jwt-secret-key-base', value: jwtSecretKeyBase }
      ]
    }
    template: {
      containers: [
        {
          name: 'api'
          image: backendImage
          env: [
            { name: 'RAILS_ENV', value: 'production' }
            { name: 'DATABASE_URL', secretRef: 'database-url' }
            { name: 'REDIS_URL', secretRef: 'redis-url' }
            { name: 'SECRET_KEY_BASE', secretRef: 'secret-key-base' }
            { name: 'JWT_SECRET_KEY_BASE', secretRef: 'jwt-secret-key-base' }
            { name: 'ELASTICSEARCH_URL', value: elasticsearchUrl }
            { name: 'FORCE_SSL', value: 'true' }
          ]
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 10
      }
    }
  }
}

resource workerApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${environmentName}-worker'
  location: location
  properties: {
    managedEnvironmentId: containerAppsEnvironment.id
    configuration: {
      secrets: [
        { name: 'database-url', value: databaseUrl }
        { name: 'redis-url', value: redisUrl }
        { name: 'secret-key-base', value: secretKeyBase }
        { name: 'jwt-secret-key-base', value: jwtSecretKeyBase }
      ]
    }
    template: {
      containers: [
        {
          name: 'worker'
          image: backendImage
          command: ['bundle', 'exec', 'sidekiq', '-C', 'config/sidekiq.yml']
          env: [
            { name: 'RAILS_ENV', value: 'production' }
            { name: 'DATABASE_URL', secretRef: 'database-url' }
            { name: 'REDIS_URL', secretRef: 'redis-url' }
            { name: 'SECRET_KEY_BASE', secretRef: 'secret-key-base' }
            { name: 'JWT_SECRET_KEY_BASE', secretRef: 'jwt-secret-key-base' }
            { name: 'ELASTICSEARCH_URL', value: elasticsearchUrl }
            { name: 'FORCE_SSL', value: 'true' }
          ]
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 10
      }
    }
  }
}

resource frontendApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${environmentName}-frontend'
  location: location
  properties: {
    managedEnvironmentId: containerAppsEnvironment.id
    configuration: {
      ingress: {
        external: true
        targetPort: 5173
      }
    }
    template: {
      containers: [
        {
          name: 'frontend'
          image: frontendImage
          env: [
            { name: 'GRAPHQL_ENDPOINT', value: 'https://${backendApp.properties.configuration.ingress.fqdn}/graphql' }
          ]
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 5
      }
    }
  }
}
