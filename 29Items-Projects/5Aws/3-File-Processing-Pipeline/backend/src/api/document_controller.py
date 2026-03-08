import os
import json
import boto3
import logging
from decimal import Decimal

logger = logging.getLogger()
logger.setLevel(logging.INFO)

dynamodb = boto3.resource('dynamodb')
table_name = os.environ.get('TABLE_NAME', 'DocumentsTable')
table = dynamodb.Table(table_name)

class DecimalEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, Decimal):
            return float(obj) if obj % 1 else int(obj)
        return super(DecimalEncoder, self).default(obj)

def lambda_handler(event, context):
    logger.info("Fetching documents list")
    try:
        # In a real production system with user auth, this should be a query based on secondary index
        # For this scoped demo, we use scan and handle pagination lightly
        
        limit = 50
        query_params = event.get('queryStringParameters') or {}
        if 'limit' in query_params and query_params['limit'].isdigit():
            limit = min(int(query_params['limit']), 100) # Max 100
            
        scan_kwargs = {'Limit': limit}
        
        response = table.scan(**scan_kwargs)
        items = response.get('Items', [])
        
        return {
            'statusCode': 200,
            'headers': {
                'Access-Control-Allow-Origin': '*',
                'Content-Type': 'application/json'
            },
            'body': json.dumps(items, cls=DecimalEncoder)
        }
    except Exception as e:
        logger.error(f"Error fetching documents: {str(e)}", exc_info=True)
        return {
            'statusCode': 500,
            'headers': {
                'Access-Control-Allow-Origin': '*'
            },
            'body': json.dumps({'error': 'Internal server error'})
        }

