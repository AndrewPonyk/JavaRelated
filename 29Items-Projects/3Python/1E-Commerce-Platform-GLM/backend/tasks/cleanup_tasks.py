"""
Cleanup Tasks

Celery tasks for periodic cleanup and maintenance.
"""

from celery import shared_task
import logging

logger = logging.getLogger("backend")


@shared_task
def cleanup_expired_sessions() -> int:
    """
    Clean up expired sessions from database and cache.

    Scheduled task to run daily.

    Returns:
        Number of sessions cleaned up
    """
    from django.core.management import call_command

    try:
        # Run Django's clearsessions command
        call_command("clearsessions")
        logger.info("Expired sessions cleaned up")
        return 1

    except Exception as e:
        logger.error(f"Failed to cleanup expired sessions: {e}")
        return 0


@shared_task
def cleanup_old_carts() -> int:
    """
    Clean up old abandoned carts.

    Scheduled task to run weekly.

    Returns:
        Number of carts cleaned up
    """
    from django.utils import timezone
    from datetime import timedelta
    from backend.core.models import Cart

    # Delete carts older than 30 days that have no items
    expiry_date = timezone.now() - timedelta(days=30)

    deleted_count, _ = Cart.objects.filter(
        updated_at__lt=expiry_date,
        items__isnull=True,
    ).delete()

    logger.info(f"Cleaned up {deleted_count} old carts")
    return deleted_count


@shared_task
def cleanup_expired_tokens() -> int:
    """
    Clean up expired JWT tokens from blacklist.

    Scheduled task to run daily.

    Returns:
        Number of tokens cleaned up
    """
    # TODO: Implement token blacklist cleanup
    logger.info("Expired tokens cleanup task executed")
    return 0


@shared_task
def cleanup_old_logs() -> int:
    """
    Clean up old log files.

    Scheduled task to run weekly.

    Returns:
        Number of log files cleaned up
    """
    # TODO: Implement log file cleanup
    logger.info("Old logs cleanup task executed")
    return 0


@shared_task
def vacuum_database() -> bool:
    """
    Run VACUUM on PostgreSQL database.

    Scheduled task to run weekly during low traffic.

    Returns:
        True if successful
    """
    from django.db import connection

    try:
        with connection.cursor() as cursor:
            cursor.execute("VACUUM ANALYZE;")

        logger.info("Database VACUUM completed")
        return True

    except Exception as e:
        logger.error(f"Failed to vacuum database: {e}")
        return False
