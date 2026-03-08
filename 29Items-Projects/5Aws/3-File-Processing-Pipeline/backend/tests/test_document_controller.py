import json
import pytest
from moto import mock_dynamodb
import boto3
import os

@pytest.fixture
def aws_credentials():
    os.environ['AWS_ACCESS_KEY_ID'] = 'testing'
    os.environ['AWS_SECRET_ACCESS_KEY'] = 'testing'
    os.environ['AWS_SECURITY_TOKEN'] = 'testing'
    os.environ['AWS_SESSION_TOKEN'] = 'testing'
    os.environ['AWS_DEFAULT_REGION'] = 'us-east-1'
    os.environ['TABLE_NAME'] = 'test-table'

@pytest.fixture
def dynamodb(aws_credentials):
    with mock_dynamodb():
        conn = boto3.client('dynamodb', region_name='us-east-1')
        conn.create_table(
            TableName='test-table',
            KeySchema=[{'AttributeName': 'documentId', 'KeyType': 'HASH'}],
            AttributeDefinitions=[{'AttributeName': 'documentId', 'AttributeType': 'S'}],
            ProvisionedThroughput={'ReadCapacityUnits': 1, 'WriteCapacityUnits': 1}
        )
        yield boto3.resource('dynamodb')

def test_list_documents(dynamodb):
    # Insert test data
    table = dynamodb.Table('test-table')
    table.put_item(Item={'documentId': '123', 'filename': 'test.pdf'})
    
    from api.document_controller import lambda_handler
    
    event = {}
    context = {}
    
    response = lambda_handler(event, context)
    
    assert response['statusCode'] == 200
    body = json.loads(response['body'])
    assert len(body) == 1
    assert body[0]['documentId'] == '123'
