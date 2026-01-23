"""
Search API Endpoints
"""
from flask import Blueprint, request, jsonify
from app.services.search_service import SearchService
from app.schemas.customer_schema import CustomerSchema

search_bp = Blueprint('search', __name__)
search_service = SearchService()
customers_schema = CustomerSchema(many=True)


@search_bp.route('/', methods=['GET'])
def search_customers():
    """
    Search customers by query string
    ---
    tags:
      - Search
    parameters:
      - name: q
        in: query
        type: string
        required: true
        description: Search query
      - name: field
        in: query
        type: string
        enum: [all, name, email, company]
        default: all
      - name: page
        in: query
        type: integer
        default: 1
      - name: per_page
        in: query
        type: integer
        default: 20
    responses:
      200:
        description: Search results
    """
    query = request.args.get('q', '')
    field = request.args.get('field', 'all')
    page = request.args.get('page', 1, type=int)
    per_page = request.args.get('per_page', 20, type=int)

    result = search_service.search(
        query=query,
        field=field,
        page=page,
        per_page=per_page
    )

    return jsonify({
        'data': customers_schema.dump(result['items']),
        'query': query,
        'pagination': {
            'page': result['page'],
            'per_page': result['per_page'],
            'total': result['total'],
            'pages': result['pages']
        }
    })


@search_bp.route('/advanced', methods=['POST'])
def advanced_search():
    """
    Advanced search with multiple filters
    ---
    tags:
      - Search
    parameters:
      - name: body
        in: body
        required: true
        schema:
          type: object
          properties:
            filters:
              type: object
              properties:
                name:
                  type: string
                email:
                  type: string
                company:
                  type: string
                status:
                  type: string
                created_after:
                  type: string
                  format: date
                created_before:
                  type: string
                  format: date
            sort_by:
              type: string
            sort_order:
              type: string
              enum: [asc, desc]
    responses:
      200:
        description: Advanced search results
    """
    data = request.get_json() or {}
    filters = data.get('filters', {})
    sort_by = data.get('sort_by', 'created_at')
    sort_order = data.get('sort_order', 'desc')
    page = data.get('page', 1)
    per_page = data.get('per_page', 20)

    result = search_service.advanced_search(
        filters=filters,
        sort_by=sort_by,
        sort_order=sort_order,
        page=page,
        per_page=per_page
    )

    return jsonify({
        'data': customers_schema.dump(result['items']),
        'filters': filters,
        'pagination': {
            'page': result['page'],
            'per_page': result['per_page'],
            'total': result['total'],
            'pages': result['pages']
        }
    })
