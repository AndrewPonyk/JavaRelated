import React from 'react';
import { useAuthStore } from '@/stores/authStore';

// Message types
export interface WSMessage {
  type: string;
  data: any;
  timestamp: number;
  id?: string;
}

// Connection states
export enum WSConnectionState {
  CONNECTING = 'connecting',
  CONNECTED = 'connected',
  DISCONNECTED = 'disconnected',
  RECONNECTING = 'reconnecting',
  RATE_LIMITED = 'rate_limited',
  BANNED = 'banned',
  ERROR = 'error'
}

// Event handlers
export interface WSEventHandlers {
  onConnect?: () => void;
  onDisconnect?: (event: CloseEvent) => void;
  onMessage?: (message: WSMessage) => void;
  onError?: (error: Event) => void;
  onRateLimit?: (reason: string) => void;
  onBanned?: (reason: string) => void;
  onReconnect?: (attempt: number) => void;
  onStateChange?: (state: WSConnectionState) => void;
}

// WebSocket client configuration
export interface WSClientConfig {
  url?: string;
  autoReconnect?: boolean;
  maxReconnectAttempts?: number;
  reconnectInterval?: number;
  maxReconnectInterval?: number;
  reconnectDecay?: number;
  messageQueueSize?: number;
  heartbeatInterval?: number;
  authRequired?: boolean;
  debug?: boolean;
}

// Default configuration
const defaultConfig: Required<WSClientConfig> = {
  url: process.env.VITE_WS_URL || 'ws://localhost:3002',
  autoReconnect: true,
  maxReconnectAttempts: 10,
  reconnectInterval: 1000,
  maxReconnectInterval: 30000,
  reconnectDecay: 1.5,
  messageQueueSize: 100,
  heartbeatInterval: 25000,
  authRequired: true,
  debug: false,
};

export class EnterpriseWebSocketClient {
  private ws: WebSocket | null = null;
  private config: Required<WSClientConfig>;
  private handlers: WSEventHandlers = {};
  private state: WSConnectionState = WSConnectionState.DISCONNECTED;
  private reconnectAttempts = 0;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private heartbeatTimer: NodeJS.Timeout | null = null;
  private messageQueue: WSMessage[] = [];
  private subscriptions: Set<string> = new Set();
  private isAuthenticated = false;
  private lastError: string | null = null;

  constructor(config: Partial<WSClientConfig> = {}) {
    this.config = { ...defaultConfig, ...config };
    this.log('WebSocket client initialized', this.config);
  }

  /**
   * Connect to WebSocket server
   */
  public async connect(handlers: WSEventHandlers = {}): Promise<void> {
    this.handlers = { ...this.handlers, ...handlers };

    if (this.ws?.readyState === WebSocket.OPEN) {
      this.log('Already connected');
      return;
    }

    this.setState(WSConnectionState.CONNECTING);
    this.lastError = null;

    try {
      // Add authentication token if required
      let wsUrl = this.config.url;
      if (this.config.authRequired) {
        const token = this.getAuthToken();
        if (token) {
          const url = new URL(wsUrl);
          url.searchParams.set('token', token);
          wsUrl = url.toString();
        }
      }

      this.log('Connecting to WebSocket server', { url: wsUrl });

      this.ws = new WebSocket(wsUrl);
      this.setupEventHandlers();

    } catch (error) {
      this.log('Connection failed', error);
      this.setState(WSConnectionState.ERROR);
      this.handleConnectionError(error);
      throw error;
    }
  }

  /**
   * Disconnect from WebSocket server
   */
  public disconnect(): void {
    this.log('Disconnecting from WebSocket server');

    this.clearTimers();
    this.config.autoReconnect = false; // Prevent auto-reconnect

    if (this.ws) {
      this.ws.close(1000, 'Client disconnect');
      this.ws = null;
    }

    this.setState(WSConnectionState.DISCONNECTED);
    this.isAuthenticated = false;
    this.messageQueue = [];
  }

  /**
   * Send message to server
   */
  public send(type: string, data: any = {}): boolean {
    const message: WSMessage = {
      type,
      data,
      timestamp: Date.now(),
      id: this.generateMessageId(),
    };

    if (this.ws?.readyState === WebSocket.OPEN && this.isReady()) {
      try {
        this.ws.send(JSON.stringify(message));
        this.log('Message sent', { type, dataSize: JSON.stringify(data).length });
        return true;
      } catch (error) {
        this.log('Failed to send message', { error, type });
        this.queueMessage(message);
        return false;
      }
    } else {
      this.log('Connection not ready, queuing message', { type, state: this.state });
      this.queueMessage(message);
      return false;
    }
  }

  /**
   * Subscribe to topic
   */
  public subscribe(topic: string): void {
    this.subscriptions.add(topic);

    if (this.isReady()) {
      this.send('subscribe', { topic });
    } else {
      this.log('Not ready to subscribe, will subscribe after connection', { topic });
    }
  }

  /**
   * Unsubscribe from topic
   */
  public unsubscribe(topic: string): void {
    this.subscriptions.delete(topic);

    if (this.isReady()) {
      this.send('unsubscribe', { topic });
    }
  }

  /**
   * Authenticate with server
   */
  public authenticate(): void {
    const token = this.getAuthToken();
    if (!token) {
      this.log('No authentication token available');
      return;
    }

    this.send('auth', { token });
  }

  /**
   * Get current connection state
   */
  public getState(): WSConnectionState {
    return this.state;
  }

  /**
   * Get connection statistics
   */
  public getStats() {
    return {
      state: this.state,
      reconnectAttempts: this.reconnectAttempts,
      queuedMessages: this.messageQueue.length,
      subscriptions: Array.from(this.subscriptions),
      isAuthenticated: this.isAuthenticated,
      lastError: this.lastError,
      wsReadyState: this.ws?.readyState,
    };
  }

  // Private methods
  private setupEventHandlers(): void {
    if (!this.ws) return;

    this.ws.onopen = (event) => {
      this.log('WebSocket connection opened');
      this.setState(WSConnectionState.CONNECTED);
      this.reconnectAttempts = 0;

      // Authenticate if required
      if (this.config.authRequired) {
        this.authenticate();
      } else {
        this.onConnectionReady();
      }

      this.handlers.onConnect?.();
    };

    this.ws.onmessage = (event) => {
      try {
        const message: WSMessage = JSON.parse(event.data);
        this.handleMessage(message);
      } catch (error) {
        this.log('Failed to parse message', { error, data: event.data });
      }
    };

    this.ws.onclose = (event) => {
      this.log('WebSocket connection closed', { code: event.code, reason: event.reason });

      this.clearTimers();
      this.isAuthenticated = false;

      // Handle different close codes
      switch (event.code) {
        case 1008: // Policy violation (rate limited)
          this.setState(WSConnectionState.RATE_LIMITED);
          this.lastError = event.reason || 'Rate limited';
          this.handlers.onRateLimit?.(this.lastError);
          break;
        case 1003: // Unsupported data (banned)
          this.setState(WSConnectionState.BANNED);
          this.lastError = event.reason || 'Connection banned';
          this.handlers.onBanned?.(this.lastError);
          break;
        default:
          this.setState(WSConnectionState.DISCONNECTED);
          break;
      }

      // Attempt reconnection if enabled and not explicitly closed
      if (this.config.autoReconnect && event.code !== 1000 && event.code !== 1001) {
        this.scheduleReconnect();
      }

      this.handlers.onDisconnect?.(event);
    };

    this.ws.onerror = (event) => {
      this.log('WebSocket error', event);
      this.setState(WSConnectionState.ERROR);
      this.lastError = 'Connection error';
      this.handlers.onError?.(event);
    };
  }

  private handleMessage(message: WSMessage): void {
    this.log('Message received', { type: message.type });

    switch (message.type) {
      case 'welcome':
        this.log('Welcome message received', message.data);
        break;

      case 'auth_success':
        this.log('Authentication successful');
        this.isAuthenticated = true;
        this.onConnectionReady();
        break;

      case 'error':
        this.handleServerError(message.data);
        break;

      case 'pong':
        // Handle heartbeat response
        break;

      default:
        this.handlers.onMessage?.(message);
        break;
    }
  }

  private handleServerError(errorData: any): void {
    this.log('Server error', errorData);

    const { code, message } = errorData;

    switch (code) {
      case 'RATE_LIMITED':
        this.setState(WSConnectionState.RATE_LIMITED);
        this.handlers.onRateLimit?.(message);
        break;

      case 'AUTH_REQUIRED':
      case 'AUTH_FAILED':
        this.isAuthenticated = false;
        if (this.config.authRequired) {
          this.authenticate(); // Retry authentication
        }
        break;

      default:
        this.lastError = message;
        break;
    }
  }

  private onConnectionReady(): void {
    this.log('Connection ready, processing queued actions');

    // Send queued messages
    this.processMessageQueue();

    // Re-subscribe to topics
    for (const topic of this.subscriptions) {
      this.send('subscribe', { topic });
    }

    // Start heartbeat
    this.startHeartbeat();
  }

  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.config.maxReconnectAttempts) {
      this.log('Max reconnect attempts reached');
      this.setState(WSConnectionState.ERROR);
      this.lastError = 'Max reconnect attempts reached';
      return;
    }

    const delay = Math.min(
      this.config.reconnectInterval * Math.pow(this.config.reconnectDecay, this.reconnectAttempts),
      this.config.maxReconnectInterval
    );

    this.log(`Scheduling reconnect attempt ${this.reconnectAttempts + 1} in ${delay}ms`);

    this.setState(WSConnectionState.RECONNECTING);

    this.reconnectTimer = setTimeout(() => {
      this.reconnectAttempts++;
      this.handlers.onReconnect?.(this.reconnectAttempts);
      this.connect();
    }, delay);
  }

  private startHeartbeat(): void {
    this.heartbeatTimer = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.send('ping');
      }
    }, this.config.heartbeatInterval);

    this.log('Heartbeat started', { interval: this.config.heartbeatInterval });
  }

  private clearTimers(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private queueMessage(message: WSMessage): void {
    if (this.messageQueue.length >= this.config.messageQueueSize) {
      this.messageQueue.shift(); // Remove oldest message
    }
    this.messageQueue.push(message);
  }

  private processMessageQueue(): void {
    const messages = [...this.messageQueue];
    this.messageQueue = [];

    this.log(`Processing ${messages.length} queued messages`);

    for (const message of messages) {
      if (this.ws?.readyState === WebSocket.OPEN) {
        try {
          this.ws.send(JSON.stringify(message));
        } catch (error) {
          this.log('Failed to send queued message', { error, type: message.type });
          // Re-queue failed messages
          this.queueMessage(message);
          break;
        }
      }
    }
  }

  private getAuthToken(): string | null {
    try {
      const authStore = useAuthStore.getState();
      return authStore.token;
    } catch (error) {
      this.log('Failed to get auth token', error);
      return null;
    }
  }

  private isReady(): boolean {
    return (
      this.state === WSConnectionState.CONNECTED &&
      (!this.config.authRequired || this.isAuthenticated)
    );
  }

  private setState(newState: WSConnectionState): void {
    if (this.state !== newState) {
      this.log('State changed', { from: this.state, to: newState });
      this.state = newState;
      this.handlers.onStateChange?.(newState);
    }
  }

  private handleConnectionError(error: any): void {
    this.lastError = error?.message || 'Connection error';

    if (this.config.autoReconnect) {
      this.scheduleReconnect();
    }
  }

  private generateMessageId(): string {
    return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  private log(message: string, ...args: any[]): void {
    if (this.config.debug) {
      console.log(`[WebSocket] ${message}`, ...args);
    }
  }
}

// Create global WebSocket client instance
export const websocketClient = new EnterpriseWebSocketClient({
  debug: process.env.NODE_ENV === 'development',
  url: process.env.VITE_WS_URL || 'ws://localhost:3002',
});

// React hook for WebSocket
export function useWebSocket(handlers: WSEventHandlers = {}) {
  const [state, setState] = React.useState<WSConnectionState>(websocketClient.getState());
  const [stats, setStats] = React.useState(websocketClient.getStats());

  React.useEffect(() => {
    const handleStateChange = (newState: WSConnectionState) => {
      setState(newState);
      setStats(websocketClient.getStats());
    };

    const allHandlers = {
      ...handlers,
      onStateChange: handleStateChange,
    };

    // Connect with handlers
    websocketClient.connect(allHandlers);

    // Update stats periodically
    const statsInterval = setInterval(() => {
      setStats(websocketClient.getStats());
    }, 1000);

    return () => {
      clearInterval(statsInterval);
      websocketClient.disconnect();
    };
  }, []);

  return {
    client: websocketClient,
    state,
    stats,
    connect: () => websocketClient.connect(handlers),
    disconnect: () => websocketClient.disconnect(),
    send: websocketClient.send.bind(websocketClient),
    subscribe: websocketClient.subscribe.bind(websocketClient),
    unsubscribe: websocketClient.unsubscribe.bind(websocketClient),
  };
}

export default EnterpriseWebSocketClient;