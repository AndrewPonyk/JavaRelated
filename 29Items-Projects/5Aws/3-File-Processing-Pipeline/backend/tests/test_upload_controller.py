import json
import pytest
from moto import mock_s3, mock_dynamodb
import boto3
import os

@pytest.fixture
def dynamodb_and_s3():
    with mock_dynamodb(), mock_s3():
        # Setup DynamoDB
        dynamodb = boto3.client('dynamodb', region_name='us-east-1')
        dynamodb.create_table(
            TableName='test-table',
            KeySchema=[{'AttributeName': 'documentId', 'KeyType': 'HASH'}],
            AttributeDefinitions=[{'AttributeName': 'documentId', 'AttributeType': 'S'}],
            ProvisionedThroughput={'ReadCapacityUnits': 1, 'WriteCapacityUnits': 1}
        )
        
        # Setup S3
        s3 = boto3.client('s3', region_name='us-east-1')
        s3.create_bucket(Bucket='test-bucket')
        
        yield

def test_upload_controller_success(dynamodb_and_s3, monkeypatch):
    monkeypatch.setenv('TABLE_NAME', 'test-table')
    monkeypatch.setenv('RAW_BUCKET_NAME', 'test-bucket')
    
    from api.upload_controller import lambda_handler
    
    event = {
        'body': json.dumps({'filename': 'test_document.pdf', 'size': 1000})
    }
    
    response = lambda_handler(event, {})
    
    assert response['statusCode'] == 200
    body = json.loads(response['body'])
    assert 'uploadUrl' in body
    assert 'documentId' in body
    assert body['objectKey'].endswith('.pdf')

def test_upload_controller_missing_filename(dynamodb_and_s3, monkeypatch):
    monkeypatch.setenv('TABLE_NAME', 'test-table')
    monkeypatch.setenv('RAW_BUCKET_NAME', 'test-bucket')
    
    from api.upload_controller import lambda_handler
    
    event = {
        'body': json.dumps({'size': 1000})
    }
    
    response = lambda_handler(event, {})
    
    assert response['statusCode'] == 400
    assert 'A valid filename is required' in json.loads(response['body'])['error']

def test_upload_controller_invalid_extension(dynamodb_and_s3, monkeypatch):
    monkeypatch.setenv('TABLE_NAME', 'test-table')
    monkeypatch.setenv('RAW_BUCKET_NAME', 'test-bucket')
    
    from api.upload_controller import lambda_handler
    
    event = {
        'body': json.dumps({'filename': 'malware.exe', 'size': 1000})
    }
    
    response = lambda_handler(event, {})
    
    assert response['statusCode'] == 400
    assert 'Unsupported file extension' in json.loads(response['body'])['error']

def test_upload_controller_file_too_large(dynamodb_and_s3, monkeypatch):
    monkeypatch.setenv('TABLE_NAME', 'test-table')
    monkeypatch.setenv('RAW_BUCKET_NAME', 'test-bucket')
    
    from api.upload_controller import lambda_handler
    
    event = {
        'body': json.dumps({'filename': 'huge.pdf', 'size': 40 * 1024 * 1024}) # 40 MB
    }
    
    response = lambda_handler(event, {})
    
    assert response['statusCode'] == 400
    assert 'File exceeds maximum size' in json.loads(response['body'])['error']
