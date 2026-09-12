import os
import json
import boto3
import logging
from decimal import Decimal
from botocore.exceptions import ClientError, EndpointConnectionError

logger = logging.getLogger()
logger.setLevel(logging.INFO)

dynamodb = boto3.resource('dynamodb')
s3 = boto3.client('s3')
textract = boto3.client('textract')
comprehend = boto3.client('comprehend')

table_name = os.environ.get('TABLE_NAME', 'DocumentsTable')
table = dynamodb.Table(table_name)

IMAGE_EXTENSIONS = {'.png', '.jpg', '.jpeg', '.tiff', '.tif', '.bmp'}


def extract_text_with_textract(bucket_name, object_key):
    """Extract text using AWS Textract."""
    logger.info(f"Calling Textract for {object_key}")
    response = textract.detect_document_text(
        Document={'S3Object': {'Bucket': bucket_name, 'Name': object_key}}
    )
    text_blocks = [
        block.get('Text', '')
        for block in response.get('Blocks', [])
        if block.get('BlockType') == 'LINE'
    ]
    return " ".join(text_blocks)


def extract_text_with_pdfplumber(bucket_name, object_key):
    """Fallback: extract text using pdfplumber (text-based PDFs only)."""
    import pdfplumber

    ext = os.path.splitext(object_key)[1].lower()
    if ext in IMAGE_EXTENSIONS:
        logger.warning(
            f"Cannot OCR image file {object_key} without Textract. "
            "pdfplumber only supports text-based PDFs."
        )
        return ""

    local_path = f"/tmp/{os.path.basename(object_key)}"
    try:
        logger.info(f"Downloading {object_key} from S3 for pdfplumber fallback")
        s3.download_file(bucket_name, object_key, local_path)

        text_parts = []
        with pdfplumber.open(local_path) as pdf:
            for page in pdf.pages:
                page_text = page.extract_text()
                if page_text:
                    text_parts.append(page_text)

        return " ".join(text_parts)
    finally:
        if os.path.exists(local_path):
            os.remove(local_path)


def extract_text(bucket_name, object_key):
    """Try Textract first, fall back to pdfplumber if Textract is unavailable."""
    try:
        return extract_text_with_textract(bucket_name, object_key)
    except (ClientError, EndpointConnectionError) as e:
        error_code = getattr(e, 'response', {}).get('Error', {}).get('Code', '')
        if error_code in ('AccessDeniedException', 'UnrecognizedClientException') \
                or isinstance(e, EndpointConnectionError):
            logger.warning(
                f"Textract unavailable ({type(e).__name__}: {e}). "
                "Falling back to pdfplumber."
            )
            return extract_text_with_pdfplumber(bucket_name, object_key)
        raise

def update_status(document_id, status, error=None, extra_attrs=None):
    update_expr = "SET #st = :status"
    expr_attr_names = {"#st": "status"}
    expr_attr_vals = {":status": status}
    
    if error:
        update_expr += ", #err = :error"
        expr_attr_names["#err"] = "errorMsg"
        expr_attr_vals[":error"] = str(error)
        
    if extra_attrs:
        for k, v in extra_attrs.items():
            attr_name = f"#{k}"
            attr_val = f":{k}"
            update_expr += f", {attr_name} = {attr_val}"
            expr_attr_names[attr_name] = k
            expr_attr_vals[attr_val] = v

    try:
        table.update_item(
            Key={'documentId': document_id},
            UpdateExpression=update_expr,
            ExpressionAttributeNames=expr_attr_names,
            ExpressionAttributeValues=expr_attr_vals
        )
    except Exception as e:
        logger.error(f"Failed to update DynamoDB for {document_id}: {str(e)}", exc_info=True)
        raise

def lambda_handler(event, context):
    logger.info("Processing SQS queue batch")
    for record in event.get('Records', []):
        object_key = None
        document_id = None
        try:
            body = json.loads(record['body'])
            bucket_name = body['bucketName']
            object_key = body['objectKey']
            
            # The object key is structured as {document_id}.{ext}
            if not object_key or '.' not in object_key:
                logger.error(f"Invalid object key format: {object_key}")
                continue
                
            document_id = object_key.split('.')[0]
            
            # Update status to processing
            update_status(document_id, "PROCESSING")
            
            # Extract text (Textract with pdfplumber fallback)
            full_text = extract_text(bucket_name, object_key)
            logger.info(f"Extracted {len(full_text)} characters.")
            
            # Call Comprehend
            entities = []
            if full_text.strip():
                # Limit to 4900 bytes as Comprehend has a 5000 byte limit per request
                text_to_analyze = full_text.encode('utf-8')[:4900].decode('utf-8', 'ignore')
                comp_response = comprehend.detect_entities(
                    Text=text_to_analyze,
                    LanguageCode='en'
                )
                
                # Convert float to Decimal for DynamoDB serialization
                entities = [
                    {
                        'Text': e.get('Text'), 
                        'Type': e.get('Type'), 
                        'Score': Decimal(str(e.get('Score', 0.0)))
                    } 
                    for e in comp_response.get('Entities', [])
                ]
            
            # Save Results
            update_status(document_id, "COMPLETED", extra_attrs={
                'textContent': full_text,
                'entities': entities
            })
            logger.info(f"Successfully processed {document_id}")
            
        except Exception as e:
            logger.error(f"Failed to process record: {str(e)}", exc_info=True)
            if document_id:
                try:
                    update_status(document_id, "FAILED", error=str(e))
                except Exception as update_err:
                    logger.error(f"Also failed to save FAILED status to DynamoDB: {str(update_err)}")
            raise e # Throw back to SQS for DLQ

