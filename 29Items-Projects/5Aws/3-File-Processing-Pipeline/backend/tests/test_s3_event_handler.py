import json
import pytest
from moto import mock_sqs
import boto3
import os

@pytest.fixture
def sqs_queue():
    with mock_sqs():
        sqs = boto3.client('sqs', region_name='us-east-1')
        response = sqs.create_queue(QueueName='test-queue')
        queue_url = response['QueueUrl']
        yield queue_url, sqs

def test_s3_event_handler_success(sqs_queue, monkeypatch):
    queue_url, sqs = sqs_queue
    monkeypatch.setenv('PROCESS_QUEUE_URL', queue_url)
    
    # Needs to be imported after monkeypatch if variables are initialized at module level
    import api.s3_event_handler
    api.s3_event_handler.queue_url = queue_url
    
    event = {
        'Records': [
            {
                's3': {
                    'bucket': {'name': 'test-bucket'},
                    'object': {'key': '12345.pdf'}
                }
            }
        ]
    }
    
    response = api.s3_event_handler.lambda_handler(event, {})
    assert response['statusCode'] == 200
    
    messages = sqs.receive_message(QueueUrl=queue_url)
    assert 'Messages' in messages
    assert len(messages['Messages']) == 1
    
    body = json.loads(messages['Messages'][0]['Body'])
    assert body['bucketName'] == 'test-bucket'
    assert body['objectKey'] == '12345.pdf'

def test_s3_event_handler_missing_queue(monkeypatch):
    import api.s3_event_handler
    api.s3_event_handler.queue_url = None
    
    event = {
        'Records': []
    }
    
    with pytest.raises(Exception) as excinfo:
        api.s3_event_handler.lambda_handler(event, {})
        
    assert "Configuration error: PROCESS_QUEUE_URL missing" in str(excinfo.value)
