import os
import json
import boto3
import logging

logger = logging.getLogger()
logger.setLevel(logging.INFO)

sqs = boto3.client('sqs')
queue_url = os.environ.get('PROCESS_QUEUE_URL')

def lambda_handler(event, context):
    logger.info(f"Received S3 event: {json.dumps(event)}")
    try:
        # Check if queue_url is fully set.
        if not queue_url:
            logger.error("PROCESS_QUEUE_URL is not set.")
            raise Exception("Configuration error: PROCESS_QUEUE_URL missing")
            
        for record in event.get('Records', []):
            bucket_name = record['s3']['bucket']['name']
            object_key = record['s3']['object']['key']
            
            message = {
                'bucketName': bucket_name,
                'objectKey': object_key
            }
            
            # Send to SQS
            sqs.send_message(
                QueueUrl=queue_url,
                MessageBody=json.dumps(message)
            )
            logger.info(f"Queued document {object_key} for processing")
            
        return {'statusCode': 200}
    except Exception as e:
        logger.error(f"Error processing S3 event: {str(e)}", exc_info=True)
        raise

