// Frontend types — mirror services/api-gateway schemas and trading_common models.
// Keep in sync with the backend contract (a generator from OpenAPI is a Phase-3 nicety).

export type StrategyState =
  | 'DRAFT'
  | 'BACKTESTING'
  | 'PAPER'
  | 'LIVE'
  | 'HALTED';

export type Side = 'BUY' | 'SELL';

export interface Strategy {
  id: string;
  name: string;
  class: string;
  symbols: string[];
  params: Record<string, number | string>;
  state: StrategyState;
  max_position_qty: number;
  max_order_notional: number;
  created_at: string; // ISO-8601 UTC
  updated_at: string;
}

export interface CreateStrategyRequest {
  name: string;
  class: string;
  symbols: string[];
  params?: Record<string, number | string>;
  max_position_qty: number;
  max_order_notional: number;
}

export interface Position {
  symbol: string;
  quantity: number;
  avg_price: string; // Decimal serialized as string to preserve precision
  realized_pnl: string;
  unrealized_pnl: string;
  updated_at: string;
}

export interface PnLSummary {
  realized_pnl: string;
  unrealized_pnl: string;
  total_pnl: string;
  open_positions: number;
}

export interface Order {
  id: string;
  client_order_id: string;
  strategy_id: string;
  symbol: string;
  side: Side;
  order_type: string;
  quantity: number;
  limit_price: string | null;
  status: string;
  created_at: string;
}
