"""
Analytics Service - Aggregation and Statistics
"""
from typing import Dict, Any, List
from datetime import datetime, timedelta
from sqlalchemy import func, case
from app.models.customer import Customer
from app.extensions import db


class AnalyticsService:
    """
    Service class for analytics and aggregation operations.
    """

    def get_summary(self) -> Dict[str, Any]:
        """
        Get overall customer summary statistics.

        Returns:
            Summary statistics dictionary
        """
        total = Customer.query.count()

        status_counts = self.count_by_status()

        sentiment_stats = db.session.query(
            func.avg(Customer.sentiment_score).label('avg'),
            func.min(Customer.sentiment_score).label('min'),
            func.max(Customer.sentiment_score).label('max')
        ).filter(Customer.sentiment_score.isnot(None)).first()

        return {
            'total_customers': total,
            'by_status': status_counts,
            'sentiment': {
                'average': round(sentiment_stats.avg, 2) if sentiment_stats.avg else None,
                'min': round(sentiment_stats.min, 2) if sentiment_stats.min else None,
                'max': round(sentiment_stats.max, 2) if sentiment_stats.max else None
            }
        }

    def count_by_status(self) -> Dict[str, int]:
        """
        Count customers grouped by status.

        Returns:
            Dictionary of status -> count
        """
        results = db.session.query(
            Customer.status,
            func.count(Customer.id)
        ).group_by(Customer.status).all()

        return {status: count for status, count in results}

    def count_by_company(self, limit: int = 10) -> List[Dict[str, Any]]:
        """
        Get top companies by customer count.

        Args:
            limit: Number of companies to return

        Returns:
            List of company stats
        """
        results = db.session.query(
            Customer.company,
            func.count(Customer.id).label('count')
        ).filter(
            Customer.company.isnot(None),
            Customer.company != ''
        ).group_by(
            Customer.company
        ).order_by(
            func.count(Customer.id).desc()
        ).limit(limit).all()

        return [
            {'company': company, 'customer_count': count}
            for company, count in results
        ]

    def get_sentiment_stats(self) -> Dict[str, Any]:
        """
        Get detailed sentiment statistics.

        Returns:
            Sentiment statistics
        """
        stats = db.session.query(
            func.avg(Customer.sentiment_score).label('avg'),
            func.min(Customer.sentiment_score).label('min'),
            func.max(Customer.sentiment_score).label('max'),
            func.count(Customer.sentiment_score).label('analyzed_count')
        ).filter(Customer.sentiment_score.isnot(None)).first()

        # Calculate distribution
        distribution = db.session.query(
            case(
                (Customer.sentiment_score >= 0.8, 'very_positive'),
                (Customer.sentiment_score >= 0.6, 'positive'),
                (Customer.sentiment_score >= 0.4, 'neutral'),
                (Customer.sentiment_score >= 0.2, 'negative'),
                else_='very_negative'
            ).label('category'),
            func.count(Customer.id)
        ).filter(
            Customer.sentiment_score.isnot(None)
        ).group_by('category').all()

        return {
            'avg_score': round(stats.avg, 3) if stats.avg else None,
            'min_score': round(stats.min, 3) if stats.min else None,
            'max_score': round(stats.max, 3) if stats.max else None,
            'analyzed_count': stats.analyzed_count,
            'distribution': {cat: count for cat, count in distribution}
        }

    def get_trends(
        self,
        period: str = 'month',
        range_count: int = 12
    ) -> List[Dict[str, Any]]:
        """
        Get customer creation trends over time.

        Args:
            period: 'day', 'week', or 'month'
            range_count: Number of periods to include

        Returns:
            List of trend data points
        """
        now = datetime.utcnow()

        if period == 'day':
            start_date = now - timedelta(days=range_count)
        elif period == 'week':
            start_date = now - timedelta(weeks=range_count)
        else:  # month
            start_date = now - timedelta(days=range_count * 30)

        # Query all customers in the date range and group in Python
        # This approach is database-agnostic (works with SQLite, MySQL, PostgreSQL)
        customers = Customer.query.filter(
            Customer.created_at >= start_date
        ).order_by(Customer.created_at).all()

        # Group by period in Python
        from collections import defaultdict
        period_counts = defaultdict(int)

        for customer in customers:
            if period == 'day':
                key = customer.created_at.strftime('%Y-%m-%d')
            elif period == 'week':
                key = customer.created_at.strftime('%Y-W%W')
            else:  # month
                key = customer.created_at.strftime('%Y-%m')
            period_counts[key] += 1

        return [
            {'period': p, 'count': count}
            for p, count in sorted(period_counts.items())
        ]
