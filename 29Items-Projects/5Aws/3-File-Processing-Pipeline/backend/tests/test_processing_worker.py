import json
import pytest
import os
from decimal import Decimal

# Needs pytest-mock to mock boto3 clients locally since Textract/Comprehend aren't fully supported by moto
def test_processing_worker_success(mocker):
    # Mock DynamoDB
    mock_table = mocker.MagicMock()
    
    mock_dynamodb = mocker.MagicMock()
    mock_dynamodb.Table.return_value = mock_table
    
    mocker.patch('boto3.resource', return_value=mock_dynamodb)
    
    # Mock Textract
    mock_textract = mocker.MagicMock()
    mock_textract.detect_document_text.return_value = {
        'Blocks': [
            {'BlockType': 'LINE', 'Text': 'Apple Inc. reported earnings today.'}
        ]
    }
    
    # Mock Comprehend
    mock_comprehend = mocker.MagicMock()
    mock_comprehend.detect_entities.return_value = {
        'Entities': [
            {'Text': 'Apple Inc.', 'Type': 'ORGANIZATION', 'Score': 0.99}
        ]
    }
    
    def mock_boto3_client(service, *args, **kwargs):
        if service == 'textract':
            return mock_textract
        elif service == 'comprehend':
            return mock_comprehend
        return mocker.MagicMock()
        
    mocker.patch('boto3.client', side_effect=mock_boto3_client)
    
    # Import after mocks
    import api.processing_worker
    api.processing_worker.table = mock_table
    api.processing_worker.textract = mock_textract
    api.processing_worker.comprehend = mock_comprehend
    
    event = {
        'Records': [
            {
                'body': json.dumps({
                    'bucketName': 'test-bucket',
                    'objectKey': '123-abc.pdf'
                })
            }
        ]
    }
    
    # Execute
    api.processing_worker.lambda_handler(event, {})
    
    # Assert DynamoDB was called properly
    # First call: set status to PROCESSING
    # Second call: set status to COMPLETED
    assert mock_table.update_item.call_count == 2
    
    # Check the second call arguments (COMPLETED)
    call_args = mock_table.update_item.call_args_list[1][1]
    assert call_args['Key']['documentId'] == '123-abc'
    assert call_args['ExpressionAttributeValues'][':status'] == 'COMPLETED'
    assert 'Apple Inc.' in call_args['ExpressionAttributeValues'][':textContent']
    
    # Check if conversion to decimal worked for the entity score
    entities = call_args['ExpressionAttributeValues'][':entities']
    assert len(entities) == 1
    assert entities[0]['Text'] == 'Apple Inc.'
    assert isinstance(entities[0]['Score'], Decimal)

def test_processing_worker_textract_failure(mocker):
    mock_table = mocker.MagicMock()
    mock_dynamodb = mocker.MagicMock()
    mock_dynamodb.Table.return_value = mock_table
    
    mocker.patch('boto3.resource', return_value=mock_dynamodb)
    
    mock_textract = mocker.MagicMock()
    mock_textract.detect_document_text.side_effect = Exception("Textract Error")
    
    def mock_boto3_client(service, *args, **kwargs):
        return mock_textract if service == 'textract' else mocker.MagicMock()
        
    mocker.patch('boto3.client', side_effect=mock_boto3_client)
    
    import api.processing_worker
    api.processing_worker.table = mock_table
    api.processing_worker.textract = mock_textract
    
    event = {
        'Records': [
            {
                'body': json.dumps({'bucketName': 'test-bucket', 'objectKey': 'bad-doc.pdf'})
            }
        ]
    }
    
    with pytest.raises(Exception) as excinfo:
        api.processing_worker.lambda_handler(event, {})
        
    assert "Textract Error" in str(excinfo.value)
    
    # Check if FAILED status was logged
    assert mock_table.update_item.call_count == 2
    failed_call_args = mock_table.update_item.call_args_list[1][1]
    assert failed_call_args['ExpressionAttributeValues'][':status'] == 'FAILED'
    assert "Textract Error" in failed_call_args['ExpressionAttributeValues'][':error']
