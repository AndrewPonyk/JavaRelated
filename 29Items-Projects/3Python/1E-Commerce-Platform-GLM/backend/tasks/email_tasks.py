"""
Email Tasks

Celery tasks for sending emails asynchronously.
"""

from celery import shared_task
from django.core.mail import send_mail
from django.conf import settings
from django.template.loader import render_to_string
from django.utils.html import strip_tags
import logging
from datetime import datetime, timedelta

logger = logging.getLogger("backend")


def _send_html_email(subject: str, to: list, template_name: str, context: dict) -> bool:
    """
    Helper function to send HTML email.

    Args:
        subject: Email subject
        to: List of recipient emails
        template_name: Template path
        context: Template context

    Returns:
        True if email sent successfully
    """
    try:
        # Add default context
        default_context = {
            "site_name": getattr(settings, "SITE_NAME", "E-Commerce Store"),
            "site_url": getattr(settings, "SITE_URL", "http://localhost:3000"),
        }
        context = {**default_context, **context}

        # Render HTML and plain text versions
        html_message = render_to_string(template_name, context)
        plain_message = strip_tags(html_message)

        send_mail(
            subject=subject,
            message=plain_message,
            from_email=getattr(settings, "DEFAULT_FROM_EMAIL", "noreply@example.com"),
            recipient_list=to,
            html_message=html_message,
            fail_silently=False,
        )

        return True
    except Exception as e:
        logger.error(f"Failed to send email: {e}")
        raise


@shared_task(
    bind=True,
    autoretry_for=(Exception,),
    retry_backoff=True,
    retry_kwargs={"max_retries": 3},
)
def send_verification_email(self, user_id: int) -> bool:
    """
    Send email verification link to user.

    Args:
        user_id: The user ID

    Returns:
        True if email sent successfully
    """
    from backend.core.models import User

    try:
        user = User.objects.get(id=user_id)
        token = user.generate_verification_token()

        verification_url = f"{getattr(settings, 'SITE_URL', 'http://localhost:3000')}/verify?token={token}"

        return _send_html_email(
            subject="Verify Your Email Address",
            to=[user.email],
            template_name="emails/verify_email.html",
            context={
                "user": user,
                "verification_url": verification_url,
            }
        )

    except Exception as e:
        logger.error(f"Failed to send verification email to user {user_id}: {e}")
        raise


@shared_task(
    bind=True,
    autoretry_for=(Exception,),
    retry_backoff=True,
    retry_kwargs={"max_retries": 3},
)
def send_password_reset_email(self, user_id: int) -> bool:
    """
    Send password reset email to user.

    Args:
        user_id: The user ID

    Returns:
        True if email sent successfully
    """
    from backend.core.models import User
    import secrets

    try:
        user = User.objects.get(id=user_id)

        # Generate secure reset token
        token = secrets.token_urlsafe(32)

        # Store token and expiration on user model
        user.reset_token = token
        user.reset_token_expires = datetime.now() + timedelta(hours=1)
        user.save()

        reset_url = f"{getattr(settings, 'SITE_URL', 'http://localhost:3000')}/reset-password?token={token}"

        return _send_html_email(
            subject="Reset Your Password",
            to=[user.email],
            template_name="emails/password_reset.html",
            context={
                "user": user,
                "reset_url": reset_url,
            }
        )

    except Exception as e:
        logger.error(f"Failed to send password reset email to user {user_id}: {e}")
        raise


@shared_task(
    bind=True,
    autoretry_for=(Exception,),
    retry_backoff=True,
    retry_kwargs={"max_retries": 3},
)
def send_order_confirmation_email(self, order_id: str) -> bool:
    """
    Send order confirmation email to customer.

    Args:
        order_id: The order ID

    Returns:
        True if email sent successfully
    """
    from backend.core.models import Order

    try:
        order = Order.objects.select_related('user').prefetch_related('items__product').get(id=order_id)

        return _send_html_email(
            subject=f"Order Confirmation - {order.order_number}",
            to=[order.user.email],
            template_name="emails/order_confirmation.html",
            context={
                "order": order,
            }
        )

    except Exception as e:
        logger.error(f"Failed to send order confirmation email for order {order_id}: {e}")
        raise


@shared_task(
    bind=True,
    autoretry_for=(Exception,),
    retry_backoff=True,
    retry_kwargs={"max_retries": 3},
)
def send_shipping_confirmation(self, order_id: str) -> bool:
    """
    Send shipping confirmation email to customer.

    Args:
        order_id: The order ID

    Returns:
        True if email sent successfully
    """
    from backend.core.models import Order

    try:
        order = Order.objects.select_related('user').get(id=order_id)

        return _send_html_email(
            subject=f"Your Order Has Shipped - {order.order_number}",
            to=[order.user.email],
            template_name="emails/shipping_confirmation.html",
            context={
                "order": order,
            }
        )

    except Exception as e:
        logger.error(f"Failed to send shipping confirmation for order {order_id}: {e}")
        raise


@shared_task
def send_order_reminders() -> int:
    """
    Send reminder emails for abandoned carts (scheduled task).

    Returns:
        Number of emails sent
    """
    from backend.core.models import Cart, User
    from django.utils import timezone

    try:
        # Find carts abandoned more than 2 hours ago but less than 24 hours
        abandonment_threshold = timezone.now() - timedelta(hours=2)
        abandonment_limit = timezone.now() - timedelta(hours=24)

        abandoned_carts = Cart.objects.filter(
            updated_at__gte=abandonment_limit,
            updated_at__lte=abandonment_threshold,
            items__isnull=False
        ).distinct()

        emails_sent = 0

        for cart in abandoned_carts:
            if cart.total_items > 0 and cart.total_price > 0:
                # Check if user hasn't already received a reminder recently
                if not cart.last_reminder_sent or cart.last_reminder_sent < abandonment_threshold:
                    try:
                        _send_html_email(
                            subject="You Left Something Behind!",
                            to=[cart.user.email],
                            template_name="emails/abandoned_cart.html",
                            context={
                                "user": cart.user,
                                "cart": cart,
                                "cart_items": cart.items.all()[:5],  # Show first 5 items
                                "discount_offer": True,
                                "discount_code": "SAVE10",
                                "discount_percentage": 10,
                                "expires_in": 24,
                            }
                        )

                        cart.last_reminder_sent = timezone.now()
                        cart.save(update_fields=['last_reminder_sent'])
                        emails_sent += 1

                    except Exception as e:
                        logger.error(f"Failed to send abandoned cart email to {cart.user.email}: {e}")
                        continue

        logger.info(f"Sent {emails_sent} abandoned cart reminder emails")
        return emails_sent

    except Exception as e:
        logger.error(f"Failed to send order reminders: {e}")
        return 0


@shared_task(
    bind=True,
    autoretry_for=(Exception,),
    retry_backoff=True,
    retry_kwargs={"max_retries": 3},
)
def send_welcome_email(self, user_id: int) -> bool:
    """
    Send welcome email to new user after registration.

    Args:
        user_id: The user ID

    Returns:
        True if email sent successfully
    """
    from backend.core.models import User

    try:
        user = User.objects.get(id=user_id)

        return _send_html_email(
            subject=f"Welcome to {getattr(settings, 'SITE_NAME', 'E-Commerce Store')}!",
            to=[user.email],
            template_name="emails/welcome.html",
            context={
                "user": user,
                "welcome_discount": 15,
                "welcome_code": "WELCOME15",
            }
        )

    except Exception as e:
        logger.error(f"Failed to send welcome email to user {user_id}: {e}")
        raise


@shared_task(
    bind=True,
    autoretry_for=(Exception,),
    retry_backoff=True,
    retry_kwargs={"max_retries": 3},
)
def send_low_stock_alert(self, product_id: int, stock_level: int, warehouse_id: int = None) -> bool:
    """
    Send low stock alert to vendor.

    Args:
        product_id: The product ID
        stock_level: Current stock level
        warehouse_id: Optional warehouse ID

    Returns:
        True if email sent successfully
    """
    from backend.core.models import Product, Warehouse

    try:
        product = Product.objects.select_related('vendor').get(id=product_id)

        context = {
            "product": product,
            "stock_level": stock_level,
            "warehouse": None,
        }

        if warehouse_id:
            warehouse = Warehouse.objects.get(id=warehouse_id)
            context["warehouse"] = warehouse

        return _send_html_email(
            subject=f"Low Stock Alert: {product.name}",
            to=[product.vendor.email],
            template_name="emails/low_stock_alert.html",
            context=context,
        )

    except Exception as e:
        logger.error(f"Failed to send low stock alert for product {product_id}: {e}")
        raise
