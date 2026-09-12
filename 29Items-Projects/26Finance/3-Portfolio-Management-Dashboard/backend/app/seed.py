"""Seed demo data: a user, assets with synthetic price history, and a portfolio.

Run inside the API container:  ``docker compose exec api python -m app.seed``
Or locally:                    ``python -m app.seed`` (with PYTHONPATH=backend)

Idempotent: re-running skips entities that already exist.
"""

from __future__ import annotations

from sqlalchemy import select

from app.core.security import hash_password
from app.db.session import SessionLocal
from app.models.asset import Asset, PriceBar
from app.models.holding import Holding
from app.models.portfolio import Portfolio
from app.models.user import User
from app.services import market_data_service

DEMO_EMAIL = "demo@example.com"
DEMO_PASSWORD = "password123"
DEMO_ASSETS = [
    ("AAPL", "Apple Inc."),
    ("MSFT", "Microsoft Corp."),
    ("GOOGL", "Alphabet Inc."),
    ("AMZN", "Amazon.com Inc."),
    ("JPM", "JPMorgan Chase & Co."),
]


def run() -> None:
    db = SessionLocal()
    try:
        user = db.execute(select(User).where(User.email == DEMO_EMAIL)).scalar_one_or_none()
        if user is None:
            user = User(
                email=DEMO_EMAIL,
                full_name="Demo User",
                hashed_password=hash_password(DEMO_PASSWORD),
            )
            db.add(user)
            db.commit()
            db.refresh(user)
            print(f"Created user: {DEMO_EMAIL} / {DEMO_PASSWORD}")

        assets: list[Asset] = []
        for symbol, name in DEMO_ASSETS:
            asset = db.execute(select(Asset).where(Asset.symbol == symbol)).scalar_one_or_none()
            if asset is None:
                asset = Asset(symbol=symbol, name=name)
                db.add(asset)
                db.commit()
                db.refresh(asset)
                series = market_data_service.generate_synthetic_prices(
                    [symbol], days=504, seed=market_data_service.symbol_seed(symbol)
                )[symbol]
                db.add_all(
                    PriceBar(
                        asset_id=asset.id,
                        ts=ts.to_pydatetime(),
                        open=float(close),
                        high=float(close),
                        low=float(close),
                        close=float(close),
                        volume=0.0,
                    )
                    for ts, close in series.items()
                )
                db.commit()
                print(f"Created + seeded asset: {symbol} ({len(series)} bars)")
            assets.append(asset)

        existing = db.execute(
            select(Portfolio).where(Portfolio.owner_id == user.id)
        ).scalar_one_or_none()
        if existing is None:
            portfolio = Portfolio(
                owner_id=user.id,
                name="Demo Growth Portfolio",
                description="A seeded sample portfolio across 5 large-caps.",
                holdings=[Holding(asset_id=a.id, quantity=10, cost_basis=100) for a in assets],
            )
            db.add(portfolio)
            db.commit()
            print("Created demo portfolio with 5 holdings.")

        print("Seed complete.")
    finally:
        db.close()


if __name__ == "__main__":
    run()
