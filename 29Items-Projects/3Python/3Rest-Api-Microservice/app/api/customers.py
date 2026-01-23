"""
Customer CRUD API Endpoints
"""
from flask import Blueprint, request, jsonify
from app.services.customer_service import CustomerService
from app.schemas.customer_schema import CustomerSchema, CustomerCreateSchema
from app.utils.exceptions import ValidationError, NotFoundError

customers_bp = Blueprint('customers', __name__)
customer_service = CustomerService()
customer_schema = CustomerSchema()
customers_schema = CustomerSchema(many=True)
customer_create_schema = CustomerCreateSchema()


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
    page = request.args.get('page', 1, type=int)
    per_page = request.args.get('per_page', 20, type=int)
    status = request.args.get('status')

    result = customer_service.get_all(page=page, per_page=per_page, status=status)
    return jsonify({
        'data': customers_schema.dump(result['items']),
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
    data = request.get_json()
    errors = customer_create_schema.validate(data)
    if errors:
        raise ValidationError('Invalid customer data', details=errors)

    customer = customer_service.create(data)
    return jsonify({'data': customer_schema.dump(customer)}), 201


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
