"""market-data — ingest external feeds, normalize, publish ticks and bars."""

from market_data.feed_handler import (
    BarAggregator,
    FeedAdapter,
    MarketDataPublisher,
    SimulatedFeed,
)

__all__ = ["BarAggregator", "FeedAdapter", "MarketDataPublisher", "SimulatedFeed"]
