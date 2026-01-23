"""
Customer CRUD API Endpoints

|su:36) REST API LAYER - HTTP endpoints that receive requests, validate input, call services,
        and return JSON responses. Keeps HTTP concerns separate from business logic.
"""
from flask import Blueprint, request, jsonify
from app.services.customer_service import CustomerService
from app.schemas.customer_schema import CustomerSchema, CustomerCreateSchema
from app.utils.exceptions import ValidationError, NotFoundError

# |su:37) BLUEPRINT - Modular route grouping. Registered in app factory with url_prefix='/api/customers'
customers_bp = Blueprint('customers', __name__)
# |su:38) DEPENDENCY SETUP - Instantiate service and schemas used by route handlers
customer_service = CustomerService()
customer_schema = CustomerSchema()
customers_schema = CustomerSchema(many=True)  # many=True for lists
customer_create_schema = CustomerCreateSchema()


# |su:39) GET ENDPOINT - List resources with optional filtering and pagination
@customers_bp.route('/', methods=['GET'])
def get_customers():
    """
    Get all customers with pagination
    ---
    tags:
      - Customers
    parameters:
      - name: page
        in: query
        type: integer
        default: 1
      - name: per_page
        in: query
        type: integer
        default: 20
      - name: status
        in: query
        type: string
        enum: [active, inactive, lead]
    responses:
      200:
        description: List of customers
    """
    # |su:40) QUERY PARAMS - request.args.get() with type conversion and defaults
    page = request.args.get('page', 1, type=int)
    per_page = request.args.get('per_page', 20, type=int)
    status = request.args.get('status')

    result = customer_service.get_all(page=page, per_page=per_page, status=status)
    # |su:41) RESPONSE FORMAT - Consistent structure: {data: [...], pagination: {...}}
    return jsonify({
        'data': customers_schema.dump(result['items']),  # Schema serializes models to dicts
        'pagination': {
            'page': result['page'],
            'per_page': result['per_page'],
            'total': result['total'],
            'pages': result['pages']
        }
    })


@customers_bp.route('/<int:customer_id>', methods=['GET'])
def get_customer(customer_id: int):
    """
    Get customer by ID
    ---
    tags:
      - Customers
    parameters:
      - name: customer_id
        in: path
        type: integer
        required: true
    responses:
      200:
        description: Customer details
      404:
        description: Customer not found
    """
    customer = customer_service.get_by_id(customer_id)
    if not customer:
        raise NotFoundError(f'Customer with ID {customer_id} not found')
    return jsonify({'data': customer_schema.dump(customer)})


# |su:42) POST ENDPOINT - Create new resource, returns 201 Created with the new object
@customers_bp.route('/', methods=['POST'])
def create_customer():
    """
    Create a new customer
    ---
    tags:
      - Customers
    parameters:
      - name: body
        in: body
        required: true
        schema:
          type: object
          required:
            - name
            - email
          properties:
            name:
              type: string
            email:
              type: string
            phone:
              type: string
            company:
              type: string
            notes:
              type: string
    responses:
      201:
        description: Customer created
      400:
        description: Validation error
    """
    # |su:43) REQUEST BODY - request.get_json() parses JSON body into Python dict
    data = request.get_json()
    # |su:44) SCHEMA VALIDATION - Validate input data, get dict of field errors if invalid
    errors = customer_create_schema.validate(data)
    if errors:
        raise ValidationError('Invalid customer data', details=errors)

    # |su:45) SERVICE CALL - Delegate business logic to service layer
    customer = customer_service.create(data)
    return jsonify({'data': customer_schema.dump(customer)}), 201  # 201 = Created


@customers_bp.route('/<int:customer_id>', methods=['PUT'])
def update_customer(customer_id: int):
    """
    Update an existing customer
    ---
    tags:
      - Customers
    parameters:
      - name: customer_id
        in: path
        type: integer
        required: true
      - name: body
        in: body
        required: true
        schema:
          type: object
          properties:
            name:
              type: string
            email:
              type: string
            phone:
              type: string
            company:
              type: string
            notes:
              type: string
            status:
              type: string
    responses:
      200:
        description: Customer updated
      404:
        description: Customer not found
    """
    data = request.get_json()
    customer = customer_service.update(customer_id, data)
    if not customer:
        raise NotFoundError(f'Customer with ID {customer_id} not found')
    return jsonify({'data': customer_schema.dump(customer)})


@customers_bp.route('/<int:customer_id>', methods=['DELETE'])
def delete_customer(customer_id: int):
    """
    Delete a customer
    ---
    tags:
      - Customers
    parameters:
      - name: customer_id
        in: path
        type: integer
        required: true
    responses:
      204:
        description: Customer deleted
      404:
        description: Customer not found
    """
    success = customer_service.delete(customer_id)
    if not success:
        raise NotFoundError(f'Customer with ID {customer_id} not found')
    return '', 204
