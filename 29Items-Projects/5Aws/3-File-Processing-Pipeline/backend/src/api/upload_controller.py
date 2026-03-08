import os
import json
import boto3
import uuid
import logging
from datetime import datetime

logger = logging.getLogger()
logger.setLevel(logging.INFO)

# Use environmental variables securely (assumes Lambda runtime provided env vars)
table_name = os.environ.get('TABLE_NAME', 'DocumentsTable')
bucket_name = os.environ.get('RAW_BUCKET_NAME', 'RawDocumentsBucket')

# Clients
s3_client = boto3.client('s3')
dynamodb = boto3.resource('dynamodb')
table = dynamodb.Table(table_name)

ALLOWED_EXTENSIONS = {'pdf', 'png', 'jpg', 'jpeg'}

def lambda_handler(event, context):
    logger.info("Generating presigned URL for upload")
    try:
        body_str = event.get('body', '{}')
        if not body_str:
            body_str = '{}'
        body = json.loads(body_str)
        filename = body.get('filename')
        file_size = body.get('size', 0)
        
        MAX_SIZE = 30 * 1024 * 1024
        
        if file_size > MAX_SIZE:
            return {'statusCode': 400, 'headers': {'Access-Control-Allow-Origin': '*'}, 'body': json.dumps({'error': 'File exceeds maximum size of 30MB'})}
        
        if not filename or '.' not in filename:
            return {'statusCode': 400, 'headers': {'Access-Control-Allow-Origin': '*'}, 'body': json.dumps({'error': 'A valid filename is required'})}
            
        extension = filename.rsplit('.', 1)[-1].lower()
        if extension not in ALLOWED_EXTENSIONS:
            return {'statusCode': 400, 'headers': {'Access-Control-Allow-Origin': '*'}, 'body': json.dumps({'error': f'Unsupported file extension. Allowed: {", ".join(ALLOWED_EXTENSIONS)}'})}
            
        document_id = str(uuid.uuid4())
        object_key = f"{document_id}.{extension}"
        
        # Security: Presigned URL with tight expiration and strict content type
        content_type = 'application/pdf' if extension == 'pdf' else f'image/{extension}'
        if extension == 'jpg':
            content_type = 'image/jpeg'
            
        presigned_url = s3_client.generate_presigned_url(
            'put_object',
            Params={'Bucket': bucket_name, 'Key': object_key, 'ContentType': content_type},
            ExpiresIn=300
        )
        
        # Save placeholder record to DynamoDB
        item = {
            'documentId': document_id,
            'filename': filename,
            'objectKey': object_key,
            'status': 'PENDING',
            'uploadedAt': datetime.utcnow().isoformat(),
            'textContent': None,
            'entities': []
        }
        table.put_item(Item=item)
        
        return {
            'statusCode': 200,
            'headers': {
                'Access-Control-Allow-Origin': '*',
                'Content-Type': 'application/json'
            },
            'body': json.dumps({
                'uploadUrl': presigned_url,
                'documentId': document_id,
                'objectKey': object_key
            })
        }
    except json.JSONDecodeError:
        logger.warning("Invalid JSON provided in the request body")
        return {
            'statusCode': 400,
            'headers': {'Access-Control-Allow-Origin': '*'},
            'body': json.dumps({'error': 'Invalid JSON body'})
        }
    except Exception as e:
        logger.error(f"Error handling upload request: {str(e)}", exc_info=True)
        return {
            'statusCode': 500,
            'headers': {'Access-Control-Allow-Origin': '*'},
            'body': json.dumps({'error': 'Failed to generate upload URL'})
        }
