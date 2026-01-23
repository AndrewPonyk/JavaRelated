"""
Analytics and Aggregation API Endpoints
"""
from flask import Blueprint, request, jsonify
from app.services.analytics_service import AnalyticsService

analytics_bp = Blueprint('analytics', __name__)
analytics_service = AnalyticsService()


@analytics_bp.route('/summary', methods=['GET'])
def get_summary():
    """
    Get customer summary statistics
    ---
    tags:
      - Analytics
    responses:
      200:
        description: Summary statistics
        schema:
          type: object
          properties:
            total_customers:
              type: integer
            by_status:
              type: object
            avg_sentiment:
              type: number
    """
    summary = analytics_service.get_summary()
    return jsonify({'data': summary})


@analytics_bp.route('/by-status', methods=['GET'])
def get_by_status():
    """
    Get customer count by status
    ---
    tags:
      - Analytics
    responses:
      200:
        description: Customer counts grouped by status
    """
    result = analytics_service.count_by_status()
    return jsonify({'data': result})


@analytics_bp.route('/by-company', methods=['GET'])
def get_by_company():
    """
    Get customer count by company
    ---
    tags:
      - Analytics
    parameters:
      - name: limit
        in: query
        type: integer
        default: 10
        description: Number of top companies to return
    responses:
      200:
        description: Customer counts grouped by company
    """
    limit = request.args.get('limit', 10, type=int)
    result = analytics_service.count_by_company(limit=limit)
    return jsonify({'data': result})


@analytics_bp.route('/sentiment', methods=['GET'])
def get_sentiment_stats():
    """
    Get sentiment analysis statistics
    ---
    tags:
      - Analytics
    responses:
      200:
        description: Sentiment statistics
        schema:
          type: object
          properties:
            avg_score:
              type: number
            min_score:
              type: number
            max_score:
              type: number
            distribution:
              type: object
    """
    result = analytics_service.get_sentiment_stats()
    return jsonify({'data': result})


@analytics_bp.route('/trends', methods=['GET'])
def get_trends():
    """
    Get customer trends over time
    ---
    tags:
      - Analytics
    parameters:
      - name: period
        in: query
        type: string
        enum: [day, week, month]
        default: month
      - name: range
        in: query
        type: integer
        default: 12
        description: Number of periods to include
    responses:
      200:
        description: Customer trends data
    """
    period = request.args.get('period', 'month')
    range_count = request.args.get('range', 12, type=int)

    result = analytics_service.get_trends(period=period, range_count=range_count)
    return jsonify({'data': result})
