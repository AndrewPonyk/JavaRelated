// Shared types mirroring the backend Pydantic DTOs (app/schemas/*).
// Keep in sync with the API; a generated client (openapi-typescript) is the
// Phase 3 upgrade path.

export interface User {
  id: number;
  email: string;
  full_name: string | null;
  is_active: boolean;
}

export interface Asset {
  id: number;
  symbol: string;
  name: string;
  asset_class: string;
  currency: string;
}

export interface Holding {
  id: number;
  asset_id: number;
  quantity: string; // Decimal serialized as string
  cost_basis: string;
  asset?: Asset | null;
}

export interface Portfolio {
  id: number;
  owner_id: number;
  name: string;
  description: string | null;
  base_currency: string;
  holdings: Holding[];
}

export interface OptimizedPortfolio {
  objective: string;
  weights: Record<string, number>;
  expected_return: number;
  volatility: number;
  sharpe: number;
}

export interface FrontierPoint {
  expected_return: number;
  volatility: number;
  sharpe: number;
  weights: Record<string, number>;
}

export interface FrontierResponse {
  points: FrontierPoint[];
  max_sharpe: OptimizedPortfolio;
  min_variance: OptimizedPortfolio;
}

export interface RiskMetrics {
  annualized_return: number;
  annualized_volatility: number;
  sharpe_ratio: number;
  sortino_ratio: number;
  var_historical: number;
  var_parametric: number;
  cvar_historical: number;
  max_drawdown: number;
}

export interface AssetAttribution {
  symbol: string;
  weight: number;
  asset_return: number;
  return_contribution: number;
  pct_of_return: number;
  risk_contribution: number;
  pct_of_risk: number;
}

export interface AttributionResponse {
  portfolio_return: number;
  portfolio_volatility: number;
  assets: AssetAttribution[];
}

export interface MonteCarloResult {
  horizon_days: number;
  n_sims: number;
  initial_value: number;
  expected_terminal_value: number;
  median_terminal_value: number;
  prob_loss: number;
  var: number;
  cvar: number;
  var_level: number;
  bands: { t: number[]; p5: number[]; p50: number[]; p95: number[] };
}

export interface JobStatus {
  id: string;
  portfolio_id: number;
  status: "queued" | "running" | "done" | "failed";
  job_type: string;
  result: MonteCarloResult | null;
  error: string | null;
}

export interface BacktestResponse {
  dates: string[];
  strategy_equity: number[];
  benchmark_equity: number[];
  strategy_cagr: number;
  strategy_volatility: number;
  strategy_sharpe: number;
  strategy_max_drawdown: number;
  benchmark_cagr: number;
  n_rebalances: number;
}
