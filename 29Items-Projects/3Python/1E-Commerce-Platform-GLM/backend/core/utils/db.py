"""
Database Utilities

Helper functions for database operations.
"""

from typing import List, Type, TypeVar, Optional
from django.db import models
from django.db.models import Q, Count, Avg, Sum, F
from django.db.transaction import atomic
import logging

logger = logging.getLogger("backend")

T = TypeVar("T", bound=models.Model)


def bulk_create_or_update(
    model: Type[T],
    data_list: List[dict],
    unique_fields: List[str],
) -> tuple[List[T], List[T]]:
    """
    Bulk create or update records.

    Args:
        model: The Django model class
        data_list: List of dictionaries with record data
        unique_fields: Fields that uniquely identify a record

    Returns:
        Tuple of (created_records, updated_records)
    """
    created = []
    updated = []

    for data in data_list:
        # Build filter kwargs
        filter_kwargs = {field: data[field] for field in unique_fields if field in data}

        try:
            # Try to get existing record
            instance = model.objects.get(**filter_kwargs)

            # Update record
            for key, value in data.items():
                setattr(instance, key, value)
            instance.save()
            updated.append(instance)

        except model.DoesNotExist:
            # Create new record
            instance = model.objects.create(**data)
            created.append(instance)

    return created, updated


def get_or_none(model: Type[T], **kwargs) -> Optional[T]:
    """
    Get a model instance or return None.

    Args:
        model: The Django model class
        **kwargs: Lookup parameters

    Returns:
        Model instance or None
    """
    try:
        return model.objects.get(**kwargs)
    except model.DoesNotExist:
        return None


def chunked_queryset(queryset: models.QuerySet, chunk_size: int = 1000):
    """
    Yield chunks of a queryset for memory-efficient processing.

    Args:
        queryset: The queryset to chunk
        chunk_size: Size of each chunk

    Yields:
        List of model instances
    """
    offset = 0
    while True:
        chunk = list(queryset[offset : offset + chunk_size])
        if not chunk:
            break
        yield chunk
        offset += chunk_size


def safe_bulk_create(
    model: Type[T],
    objects: List[T],
    batch_size: int = 1000,
    ignore_conflicts: bool = False,
) -> int:
    """
    Safely bulk create objects with error handling.

    Args:
        model: The Django model class
        objects: List of model instances to create
        batch_size: Batch size for bulk creation
        ignore_conflicts: Whether to ignore conflicts

    Returns:
        Number of objects created
    """
    try:
        created = model.objects.bulk_create(
            objects,
            batch_size=batch_size,
            ignore_conflicts=ignore_conflicts,
        )
        return len(created)
    except Exception as e:
        logger.error(f"Failed to bulk create {model.__name__}: {e}")
        return 0


def update_with_retry(
    instance: models.Model,
    **kwargs,
) -> bool:
    """
    Update a model instance with retry on collision.

    Args:
        instance: The model instance to update
        **kwargs: Fields to update

    Returns:
        True if update succeeded
    """
    from django.db import DatabaseError
    import time

    max_retries = 3
    retry_delay = 0.1  # seconds

    for attempt in range(max_retries):
        try:
            for key, value in kwargs.items():
                setattr(instance, key, value)
            instance.save(update_fields=list(kwargs.keys()))
            return True

        except DatabaseError as e:
            if attempt < max_retries - 1:
                time.sleep(retry_delay)
                retry_delay *= 2  # Exponential backoff
            else:
                logger.error(f"Failed to update {instance.__class__.__name__} after {max_retries} attempts: {e}")
                return False

    return False
