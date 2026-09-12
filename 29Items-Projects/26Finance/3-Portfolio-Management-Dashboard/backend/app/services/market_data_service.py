"""Market-data access + ingestion.

Two responsibilities, cleanly separated:

* **Read** stored bars into analytics-ready frames (`get_price_history`).
* **Ingest** new bars from a pluggable provider (`ingest_prices`). The provider
  is selected by ``settings.market_data_provider`` — a deterministic ``stub``
  for local/demo use, or a real HTTP provider (Alpha Vantage) for live data.
"""

from __future__ import annotations

import hashlib
from abc import ABC, abstractmethod
from collections.abc import Sequence

import numpy as np
import pandas as pd
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import Settings
from app.core.exceptions import DomainError
from app.models.asset import Asset, PriceBar


# --------------------------------------------------------------------------- #
# Read path
# --------------------------------------------------------------------------- #
def get_price_history(
    db: Session, asset_ids: Sequence[int], lookback_days: int = 252
) -> pd.DataFrame:
    """Return a wide (dates x symbols) close-price frame for the given assets.

    Rows with any missing asset are dropped so every column is aligned — a
    prerequisite for a well-formed covariance matrix.
    """
    stmt = (
        select(PriceBar.ts, Asset.symbol, PriceBar.close)
        .join(Asset, Asset.id == PriceBar.asset_id)
        .where(PriceBar.asset_id.in_(list(asset_ids)))
        .order_by(PriceBar.ts)
    )
    rows = db.execute(stmt).all()
    if not rows:
        raise DomainError("No price history available for the requested assets")

    frame = pd.DataFrame(rows, columns=["ts", "symbol", "close"])
    wide = frame.pivot(index="ts", columns="symbol", values="close").astype(float)
    wide = wide.dropna(how="any").tail(lookback_days)
    if len(wide) < 2:
        raise DomainError("Insufficient overlapping price history for these assets")
    return wide


def symbol_seed(symbol: str) -> int:
    """Stable per-symbol RNG seed so different assets get distinct price paths."""
    return int(hashlib.sha256(symbol.encode()).hexdigest(), 16) % (2**31)


def generate_synthetic_prices(
    symbols: Sequence[str], days: int = 504, seed: int = 42
) -> pd.DataFrame:
    """Deterministic synthetic OHLC closes — for local seeding, demos, and tests.

    Each symbol follows a GBM with slightly different drift/vol so the optimizer
    has something non-degenerate to chew on.
    """
    rng = np.random.default_rng(seed)
    dates = pd.bdate_range(end=pd.Timestamp.today().normalize(), periods=days)
    data: dict[str, np.ndarray] = {}
    for i, symbol in enumerate(symbols):
        drift = 0.0003 + 0.00008 * i
        vol = 0.010 + 0.0025 * i
        shocks = rng.normal(drift, vol, days)
        data[symbol] = 100.0 * np.exp(np.cumsum(shocks))
    return pd.DataFrame(data, index=dates)


# --------------------------------------------------------------------------- #
# Ingestion (provider pattern)
# --------------------------------------------------------------------------- #
class MarketDataProvider(ABC):
    """Provider interface: fetch a daily OHLCV frame for one symbol."""

    @abstractmethod
    def fetch_daily(self, symbol: str, days: int) -> pd.DataFrame: ...


class StubProvider(MarketDataProvider):
    """Deterministic synthetic provider (no network)."""

    def fetch_daily(self, symbol: str, days: int) -> pd.DataFrame:
        closes = generate_synthetic_prices([symbol], days=days, seed=symbol_seed(symbol))[symbol]
        return pd.DataFrame(
            {
                "open": closes.values,
                "high": closes.values,
                "low": closes.values,
                "close": closes.values,
                "volume": np.zeros(len(closes)),
            },
            index=closes.index,
        )


class AlphaVantageProvider(MarketDataProvider):
    """Live daily bars from Alpha Vantage (requires an API key)."""

    BASE_URL = "https://www.alphavantage.co/query"

    def __init__(self, api_key: str | None):
        if not api_key:
            raise DomainError("MARKET_DATA_API_KEY is required for the alphavantage provider")
        self.api_key = api_key

    def fetch_daily(self, symbol: str, days: int) -> pd.DataFrame:
        import httpx

        params = {
            "function": "TIME_SERIES_DAILY",
            "symbol": symbol,
            "outputsize": "full" if days > 100 else "compact",
            "apikey": self.api_key,
        }
        try:
            resp = httpx.get(self.BASE_URL, params=params, timeout=15.0)
            resp.raise_for_status()
            payload = resp.json()
        except httpx.HTTPError as exc:
            raise DomainError(f"Market-data request failed: {exc}") from exc

        series = payload.get("Time Series (Daily)")
        if not series:
            raise DomainError(
                f"No daily series returned for {symbol}: {payload.get('Note') or payload}"
            )

        records = {
            pd.Timestamp(date): {
                "open": float(v["1. open"]),
                "high": float(v["2. high"]),
                "low": float(v["3. low"]),
                "close": float(v["4. close"]),
                "volume": float(v["5. volume"]),
            }
            for date, v in series.items()
        }
        frame = pd.DataFrame.from_dict(records, orient="index").sort_index()
        return frame.tail(days)


def get_provider(settings: Settings) -> MarketDataProvider:
    provider = settings.market_data_provider.lower()
    if provider == "stub":
        return StubProvider()
    if provider == "alphavantage":
        return AlphaVantageProvider(settings.market_data_api_key)
    raise DomainError(f"Unsupported market data provider: {provider!r}")


def ingest_prices(db: Session, asset: Asset, settings: Settings, days: int = 504) -> int:
    """Fetch bars for an asset via the configured provider and upsert new rows.

    Returns the number of newly inserted bars (existing dates are skipped).
    """
    provider = get_provider(settings)
    frame = provider.fetch_daily(asset.symbol, days)

    existing = {
        ts for (ts,) in db.execute(select(PriceBar.ts).where(PriceBar.asset_id == asset.id)).all()
    }
    existing_dates = {ts.date() for ts in existing}

    new_bars = [
        PriceBar(
            asset_id=asset.id,
            ts=ts.to_pydatetime(),
            open=float(row["open"]),
            high=float(row["high"]),
            low=float(row["low"]),
            close=float(row["close"]),
            volume=float(row["volume"]),
        )
        for ts, row in frame.iterrows()
        if ts.date() not in existing_dates
    ]
    db.add_all(new_bars)
    db.commit()
    return len(new_bars)
