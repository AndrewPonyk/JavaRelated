import json
import pytest
import os
from decimal import Decimal
from botocore.exceptions import ClientError, EndpointConnectionError

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


def test_processing_worker_textract_denied_falls_back_to_pdfplumber(mocker):
    """When Textract returns AccessDeniedException, fallback to pdfplumber."""
    mock_table = mocker.MagicMock()
    mock_dynamodb = mocker.MagicMock()
    mock_dynamodb.Table.return_value = mock_table

    mocker.patch('boto3.resource', return_value=mock_dynamodb)

    # Mock Textract to raise AccessDeniedException
    mock_textract = mocker.MagicMock()
    mock_textract.detect_document_text.side_effect = ClientError(
        {'Error': {'Code': 'AccessDeniedException', 'Message': 'Not authorized'}},
        'DetectDocumentText'
    )

    # Mock S3 client
    mock_s3 = mocker.MagicMock()

    # Mock Comprehend
    mock_comprehend = mocker.MagicMock()
    mock_comprehend.detect_entities.return_value = {
        'Entities': [
            {'Text': 'Apple Inc.', 'Type': 'ORGANIZATION', 'Score': 0.95}
        ]
    }

    def mock_boto3_client(service, *args, **kwargs):
        if service == 'textract':
            return mock_textract
        elif service == 's3':
            return mock_s3
        elif service == 'comprehend':
            return mock_comprehend
        return mocker.MagicMock()

    mocker.patch('boto3.client', side_effect=mock_boto3_client)

    # Mock pdfplumber
    mock_page = mocker.MagicMock()
    mock_page.extract_text.return_value = 'Apple Inc. reported earnings today.'

    mock_pdf = mocker.MagicMock()
    mock_pdf.pages = [mock_page]
    mock_pdf.__enter__ = mocker.MagicMock(return_value=mock_pdf)
    mock_pdf.__exit__ = mocker.MagicMock(return_value=False)

    mock_pdfplumber = mocker.MagicMock()
    mock_pdfplumber.open.return_value = mock_pdf
    mocker.patch.dict('sys.modules', {'pdfplumber': mock_pdfplumber})

    # Mock os.path.exists and os.remove for /tmp cleanup
    mocker.patch('os.path.exists', return_value=True)
    mocker.patch('os.remove')

    import api.processing_worker
    api.processing_worker.table = mock_table
    api.processing_worker.textract = mock_textract
    api.processing_worker.s3 = mock_s3
    api.processing_worker.comprehend = mock_comprehend

    event = {
        'Records': [
            {
                'body': json.dumps({
                    'bucketName': 'test-bucket',
                    'objectKey': '456-def.pdf'
                })
            }
        ]
    }

    api.processing_worker.lambda_handler(event, {})

    # Textract was attempted
    mock_textract.detect_document_text.assert_called_once()

    # S3 download was called for pdfplumber fallback
    mock_s3.download_file.assert_called_once_with(
        'test-bucket', '456-def.pdf', '/tmp/456-def.pdf'
    )

    # pdfplumber was used
    mock_pdfplumber.open.assert_called_once_with('/tmp/456-def.pdf')

    # Document was marked COMPLETED
    assert mock_table.update_item.call_count == 2
    call_args = mock_table.update_item.call_args_list[1][1]
    assert call_args['ExpressionAttributeValues'][':status'] == 'COMPLETED'
    assert 'Apple Inc.' in call_args['ExpressionAttributeValues'][':textContent']


def test_processing_worker_image_fallback_returns_empty(mocker):
    """When Textract is unavailable and file is an image, fallback returns empty text."""
    mock_table = mocker.MagicMock()
    mock_dynamodb = mocker.MagicMock()
    mock_dynamodb.Table.return_value = mock_table

    mocker.patch('boto3.resource', return_value=mock_dynamodb)

    mock_textract = mocker.MagicMock()
    mock_textract.detect_document_text.side_effect = ClientError(
        {'Error': {'Code': 'AccessDeniedException', 'Message': 'Not authorized'}},
        'DetectDocumentText'
    )

    mock_comprehend = mocker.MagicMock()
    mock_comprehend.detect_entities.return_value = {'Entities': []}

    def mock_boto3_client(service, *args, **kwargs):
        if service == 'textract':
            return mock_textract
        elif service == 'comprehend':
            return mock_comprehend
        return mocker.MagicMock()

    mocker.patch('boto3.client', side_effect=mock_boto3_client)

    import api.processing_worker
    api.processing_worker.table = mock_table
    api.processing_worker.textract = mock_textract
    api.processing_worker.comprehend = mock_comprehend

    event = {
        'Records': [
            {
                'body': json.dumps({
                    'bucketName': 'test-bucket',
                    'objectKey': '789-ghi.png'
                })
            }
        ]
    }

    api.processing_worker.lambda_handler(event, {})

    # Document completed with empty text (no OCR for images without Textract)
    assert mock_table.update_item.call_count == 2
    call_args = mock_table.update_item.call_args_list[1][1]
    assert call_args['ExpressionAttributeValues'][':status'] == 'COMPLETED'
    assert call_args['ExpressionAttributeValues'][':textContent'] == ''
