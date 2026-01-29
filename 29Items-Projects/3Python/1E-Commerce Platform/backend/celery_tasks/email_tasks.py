"""Email-related Celery tasks."""
import html

from celery import shared_task
from django.conf import settings
from django.core.mail import send_mail


def _escape(value: str) -> str:
    """Escape HTML entities in a string for safe email rendering."""
    if not value:
        return ''
    return html.escape(str(value))


def _get_base_url():
    """Get the base URL for email links."""
    return getattr(settings, 'FRONTEND_URL', 'http://localhost:3000')


@shared_task(bind=True, max_retries=3, soft_time_limit=60)
def send_verification_email(self, user_id: str, token: str):
    """Send email verification to new user."""
    from django.contrib.auth import get_user_model
    User = get_user_model()

    try:
        user = User.objects.get(id=user_id)

        verification_url = f"{_get_base_url()}/verify-email?token={token}"

        subject = 'Verify Your Email Address'
        html_message = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body {{ font-family: Arial, sans-serif; line-height: 1.6; color: #333; }}
                .container {{ max-width: 600px; margin: 0 auto; padding: 20px; }}
                .header {{ background: #4F46E5; color: white; padding: 20px; text-align: center; }}
                .content {{ padding: 30px; background: #f9fafb; }}
                .button {{ display: inline-block; padding: 12px 24px; background: #4F46E5; color: white; text-decoration: none; border-radius: 6px; margin: 20px 0; }}
                .footer {{ padding: 20px; text-align: center; color: #666; font-size: 12px; }}
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>Welcome to Our Store!</h1>
                </div>
                <div class="content">
                    <h2>Hi {user.first_name or user.email},</h2>
                    <p>Thank you for creating an account. Please verify your email address to complete your registration.</p>
                    <p style="text-align: center;">
                        <a href="{verification_url}" class="button">Verify Email Address</a>
                    </p>
                    <p>Or copy and paste this link into your browser:</p>
                    <p style="word-break: break-all; color: #4F46E5;">{verification_url}</p>
                    <p>This link will expire in 24 hours.</p>
                </div>
                <div class="footer">
                    <p>If you didn't create an account, you can safely ignore this email.</p>
                </div>
            </div>
        </body>
        </html>
        """

        text_message = f"""
        Hi {user.first_name or user.email},

        Thank you for creating an account. Please verify your email address by visiting:
        {verification_url}

        This link will expire in 24 hours.

        If you didn't create an account, you can safely ignore this email.
        """

        send_mail(
            subject=subject,
            message=text_message,
            from_email=settings.DEFAULT_FROM_EMAIL,
            recipient_list=[user.email],
            html_message=html_message,
            fail_silently=False,
        )

    except Exception as exc:
        self.retry(exc=exc, countdown=60)


@shared_task(bind=True, max_retries=3, soft_time_limit=60)
def send_password_reset_email(self, user_id: str, token: str):
    """Send password reset email."""
    from django.contrib.auth import get_user_model
    User = get_user_model()

    try:
        user = User.objects.get(id=user_id)

        reset_url = f"{_get_base_url()}/reset-password?token={token}"

        subject = 'Reset Your Password'
        html_message = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body {{ font-family: Arial, sans-serif; line-height: 1.6; color: #333; }}
                .container {{ max-width: 600px; margin: 0 auto; padding: 20px; }}
                .header {{ background: #DC2626; color: white; padding: 20px; text-align: center; }}
                .content {{ padding: 30px; background: #f9fafb; }}
                .button {{ display: inline-block; padding: 12px 24px; background: #DC2626; color: white; text-decoration: none; border-radius: 6px; margin: 20px 0; }}
                .footer {{ padding: 20px; text-align: center; color: #666; font-size: 12px; }}
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>Password Reset Request</h1>
                </div>
                <div class="content">
                    <h2>Hi {user.first_name or user.email},</h2>
                    <p>We received a request to reset your password. Click the button below to create a new password:</p>
                    <p style="text-align: center;">
                        <a href="{reset_url}" class="button">Reset Password</a>
                    </p>
                    <p>Or copy and paste this link into your browser:</p>
                    <p style="word-break: break-all; color: #DC2626;">{reset_url}</p>
                    <p><strong>This link will expire in 1 hour.</strong></p>
                </div>
                <div class="footer">
                    <p>If you didn't request a password reset, you can safely ignore this email. Your password will remain unchanged.</p>
                </div>
            </div>
        </body>
        </html>
        """

        text_message = f"""
        Hi {user.first_name or user.email},

        We received a request to reset your password. Visit this link to create a new password:
        {reset_url}

        This link will expire in 1 hour.

        If you didn't request a password reset, you can safely ignore this email.
        """

        send_mail(
            subject=subject,
            message=text_message,
            from_email=settings.DEFAULT_FROM_EMAIL,
            recipient_list=[user.email],
            html_message=html_message,
            fail_silently=False,
        )

    except Exception as exc:
        self.retry(exc=exc, countdown=60)


@shared_task(bind=True, max_retries=3, soft_time_limit=60)
def send_order_confirmation_email(self, order_id: str):
    """Send order confirmation email to customer."""
    from apps.checkout.models import Order

    try:
        order = Order.objects.select_related('user').prefetch_related('items').get(id=order_id)

        items_html = ""
        for item in order.items.all():
            items_html += f"""
            <tr>
                <td style="padding: 10px; border-bottom: 1px solid #eee;">{_escape(item.product_name)}</td>
                <td style="padding: 10px; border-bottom: 1px solid #eee; text-align: center;">{item.quantity}</td>
                <td style="padding: 10px; border-bottom: 1px solid #eee; text-align: right;">${item.unit_price:.2f}</td>
                <td style="padding: 10px; border-bottom: 1px solid #eee; text-align: right;">${item.line_total:.2f}</td>
            </tr>
            """

        shipping_addr = order.shipping_address
        address_html = f"""
            {_escape(shipping_addr.get('full_name', ''))}<br>
            {_escape(shipping_addr.get('street_address', ''))}<br>
            {_escape(shipping_addr.get('apartment', ''))}<br>
            {_escape(shipping_addr.get('city', ''))}, {_escape(shipping_addr.get('state', ''))} {_escape(shipping_addr.get('postal_code', ''))}<br>
            {_escape(shipping_addr.get('country', ''))}
        """

        subject = f'Order Confirmation - {order.order_number}'
        html_message = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body {{ font-family: Arial, sans-serif; line-height: 1.6; color: #333; }}
                .container {{ max-width: 600px; margin: 0 auto; padding: 20px; }}
                .header {{ background: #059669; color: white; padding: 20px; text-align: center; }}
                .content {{ padding: 30px; background: #f9fafb; }}
                .order-table {{ width: 100%; border-collapse: collapse; margin: 20px 0; }}
                .order-table th {{ background: #f3f4f6; padding: 10px; text-align: left; }}
                .totals {{ margin-top: 20px; text-align: right; }}
                .total-row {{ padding: 5px 0; }}
                .grand-total {{ font-size: 18px; font-weight: bold; color: #059669; }}
                .footer {{ padding: 20px; text-align: center; color: #666; font-size: 12px; }}
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>Order Confirmed!</h1>
                </div>
                <div class="content">
                    <h2>Thank you for your order!</h2>
                    <p>Order Number: <strong>{order.order_number}</strong></p>
                    <p>Order Date: {order.created_at.strftime('%B %d, %Y at %I:%M %p')}</p>

                    <h3>Order Details</h3>
                    <table class="order-table">
                        <thead>
                            <tr>
                                <th>Product</th>
                                <th style="text-align: center;">Qty</th>
                                <th style="text-align: right;">Price</th>
                                <th style="text-align: right;">Total</th>
                            </tr>
                        </thead>
                        <tbody>
                            {items_html}
                        </tbody>
                    </table>

                    <div class="totals">
                        <div class="total-row">Subtotal: ${order.subtotal:.2f}</div>
                        <div class="total-row">Tax: ${order.tax_amount:.2f}</div>
                        <div class="total-row">Shipping: ${order.shipping_amount:.2f}</div>
                        {'<div class="total-row">Discount: -$' + str(order.discount_amount) + '</div>' if order.discount_amount > 0 else ''}
                        <div class="total-row grand-total">Total: ${order.total:.2f}</div>
                    </div>

                    <h3>Shipping Address</h3>
                    <p>{address_html}</p>

                    <p style="text-align: center; margin-top: 30px;">
                        <a href="{_get_base_url()}/orders/{order.id}" style="display: inline-block; padding: 12px 24px; background: #059669; color: white; text-decoration: none; border-radius: 6px;">View Order</a>
                    </p>
                </div>
                <div class="footer">
                    <p>Questions? Contact us at support@example.com</p>
                </div>
            </div>
        </body>
        </html>
        """

        text_message = f"""
        Thank you for your order!

        Order Number: {order.order_number}
        Order Date: {order.created_at.strftime('%B %d, %Y at %I:%M %p')}

        Total: ${order.total:.2f}

        View your order at: {_get_base_url()}/orders/{order.id}
        """

        send_mail(
            subject=subject,
            message=text_message,
            from_email=settings.DEFAULT_FROM_EMAIL,
            recipient_list=[order.email],
            html_message=html_message,
            fail_silently=False,
        )

    except Order.DoesNotExist:
        pass
    except Exception as exc:
        self.retry(exc=exc, countdown=60)


@shared_task(bind=True, max_retries=3, soft_time_limit=60)
def send_shipping_notification_email(self, order_id: str, tracking_number: str = None):
    """Send shipping notification email to customer."""
    from apps.checkout.models import Order

    try:
        order = Order.objects.get(id=order_id)

        tracking_html = ""
        if tracking_number:
            tracking_html = f"""
            <p><strong>Tracking Number:</strong> {_escape(tracking_number)}</p>
            <p>You can track your package using this number on the carrier's website.</p>
            """

        subject = f'Your Order Has Shipped - {order.order_number}'
        html_message = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body {{ font-family: Arial, sans-serif; line-height: 1.6; color: #333; }}
                .container {{ max-width: 600px; margin: 0 auto; padding: 20px; }}
                .header {{ background: #0284C7; color: white; padding: 20px; text-align: center; }}
                .content {{ padding: 30px; background: #f9fafb; }}
                .button {{ display: inline-block; padding: 12px 24px; background: #0284C7; color: white; text-decoration: none; border-radius: 6px; margin: 20px 0; }}
                .footer {{ padding: 20px; text-align: center; color: #666; font-size: 12px; }}
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>Your Order Has Shipped!</h1>
                </div>
                <div class="content">
                    <p>Great news! Your order <strong>{order.order_number}</strong> is on its way.</p>
                    {tracking_html}
                    <p style="text-align: center;">
                        <a href="{_get_base_url()}/orders/{order.id}" class="button">Track Your Order</a>
                    </p>
                </div>
                <div class="footer">
                    <p>Questions? Contact us at support@example.com</p>
                </div>
            </div>
        </body>
        </html>
        """

        text_message = f"""
        Great news! Your order {order.order_number} is on its way.

        {'Tracking Number: ' + tracking_number if tracking_number else ''}

        Track your order at: {_get_base_url()}/orders/{order.id}
        """

        send_mail(
            subject=subject,
            message=text_message,
            from_email=settings.DEFAULT_FROM_EMAIL,
            recipient_list=[order.email],
            html_message=html_message,
            fail_silently=False,
        )

    except Order.DoesNotExist:
        pass
    except Exception as exc:
        self.retry(exc=exc, countdown=60)


@shared_task(bind=True, max_retries=3, soft_time_limit=60)
def send_vendor_approval_email(self, vendor_id: int):
    """Send approval notification to vendor."""
    from apps.vendors.models import Vendor

    try:
        vendor = Vendor.objects.select_related('user').get(id=vendor_id)

        subject = 'Your Vendor Application Has Been Approved!'
        html_message = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body {{ font-family: Arial, sans-serif; line-height: 1.6; color: #333; }}
                .container {{ max-width: 600px; margin: 0 auto; padding: 20px; }}
                .header {{ background: #059669; color: white; padding: 20px; text-align: center; }}
                .content {{ padding: 30px; background: #f9fafb; }}
                .button {{ display: inline-block; padding: 12px 24px; background: #059669; color: white; text-decoration: none; border-radius: 6px; margin: 20px 0; }}
                .footer {{ padding: 20px; text-align: center; color: #666; font-size: 12px; }}
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>Congratulations!</h1>
                </div>
                <div class="content">
                    <h2>Your vendor application has been approved!</h2>
                    <p>Hi {_escape(vendor.user.first_name or vendor.business_name)},</p>
                    <p>Great news! Your vendor application for <strong>{_escape(vendor.business_name)}</strong> has been approved.</p>
                    <p>You can now start listing your products on our platform.</p>
                    <p style="text-align: center;">
                        <a href="{_get_base_url()}/vendor/dashboard" class="button">Go to Vendor Dashboard</a>
                    </p>
                </div>
                <div class="footer">
                    <p>Questions? Contact us at vendor-support@example.com</p>
                </div>
            </div>
        </body>
        </html>
        """

        text_message = f"""
        Congratulations! Your vendor application for {vendor.business_name} has been approved.

        You can now start listing products at: {_get_base_url()}/vendor/dashboard
        """

        recipients = [vendor.user.email]
        if vendor.business_email and vendor.business_email != vendor.user.email:
            recipients.append(vendor.business_email)

        send_mail(
            subject=subject,
            message=text_message,
            from_email=settings.DEFAULT_FROM_EMAIL,
            recipient_list=recipients,
            html_message=html_message,
            fail_silently=False,
        )

    except Vendor.DoesNotExist:
        pass
    except Exception as exc:
        self.retry(exc=exc, countdown=60)


@shared_task(bind=True, max_retries=3, soft_time_limit=60)
def send_vendor_rejection_email(self, vendor_id: int, reason: str):
    """Send rejection notification to vendor."""
    from apps.vendors.models import Vendor

    try:
        vendor = Vendor.objects.select_related('user').get(id=vendor_id)

        subject = 'Vendor Application Update'
        html_message = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body {{ font-family: Arial, sans-serif; line-height: 1.6; color: #333; }}
                .container {{ max-width: 600px; margin: 0 auto; padding: 20px; }}
                .header {{ background: #DC2626; color: white; padding: 20px; text-align: center; }}
                .content {{ padding: 30px; background: #f9fafb; }}
                .footer {{ padding: 20px; text-align: center; color: #666; font-size: 12px; }}
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>Application Status Update</h1>
                </div>
                <div class="content">
                    <p>Hi {_escape(vendor.user.first_name or vendor.business_name)},</p>
                    <p>We've reviewed your vendor application for <strong>{_escape(vendor.business_name)}</strong>.</p>
                    <p>Unfortunately, we are unable to approve your application at this time.</p>
                    <p><strong>Reason:</strong> {_escape(reason)}</p>
                    <p>If you have questions or would like to reapply, please contact our vendor support team.</p>
                </div>
                <div class="footer">
                    <p>Questions? Contact us at vendor-support@example.com</p>
                </div>
            </div>
        </body>
        </html>
        """

        text_message = f"""
        Hi {vendor.user.first_name or vendor.business_name},

        We've reviewed your vendor application for {vendor.business_name}.
        Unfortunately, we are unable to approve your application at this time.

        Reason: {reason}

        Contact vendor-support@example.com if you have questions.
        """

        send_mail(
            subject=subject,
            message=text_message,
            from_email=settings.DEFAULT_FROM_EMAIL,
            recipient_list=[vendor.user.email],
            html_message=html_message,
            fail_silently=False,
        )

    except Vendor.DoesNotExist:
        pass
    except Exception as exc:
        self.retry(exc=exc, countdown=60)


@shared_task(bind=True, max_retries=3, soft_time_limit=60)
def send_vendor_suspension_email(self, vendor_id: int, reason: str):
    """Send suspension notification to vendor."""
    from apps.vendors.models import Vendor

    try:
        vendor = Vendor.objects.select_related('user').get(id=vendor_id)

        subject = 'Important: Your Vendor Account Has Been Suspended'
        html_message = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body {{ font-family: Arial, sans-serif; line-height: 1.6; color: #333; }}
                .container {{ max-width: 600px; margin: 0 auto; padding: 20px; }}
                .header {{ background: #DC2626; color: white; padding: 20px; text-align: center; }}
                .content {{ padding: 30px; background: #f9fafb; }}
                .footer {{ padding: 20px; text-align: center; color: #666; font-size: 12px; }}
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>Account Suspended</h1>
                </div>
                <div class="content">
                    <p>Hi {_escape(vendor.user.first_name or vendor.business_name)},</p>
                    <p>Your vendor account for <strong>{_escape(vendor.business_name)}</strong> has been suspended.</p>
                    <p><strong>Reason:</strong> {_escape(reason)}</p>
                    <p>All your product listings have been temporarily deactivated.</p>
                    <p>To resolve this issue and reactivate your account, please contact our vendor support team.</p>
                </div>
                <div class="footer">
                    <p>Contact: vendor-support@example.com</p>
                </div>
            </div>
        </body>
        </html>
        """

        text_message = f"""
        Hi {vendor.user.first_name or vendor.business_name},

        Your vendor account for {vendor.business_name} has been suspended.

        Reason: {reason}

        All your product listings have been temporarily deactivated.
        Contact vendor-support@example.com to resolve this issue.
        """

        recipients = [vendor.user.email]
        if vendor.business_email and vendor.business_email != vendor.user.email:
            recipients.append(vendor.business_email)

        send_mail(
            subject=subject,
            message=text_message,
            from_email=settings.DEFAULT_FROM_EMAIL,
            recipient_list=recipients,
            html_message=html_message,
            fail_silently=False,
        )

    except Vendor.DoesNotExist:
        pass
    except Exception as exc:
        self.retry(exc=exc, countdown=60)


@shared_task(bind=True, max_retries=3, soft_time_limit=60)
def send_vendor_reactivation_email(self, vendor_id: int):
    """Send reactivation notification to vendor."""
    from apps.vendors.models import Vendor

    try:
        vendor = Vendor.objects.select_related('user').get(id=vendor_id)

        subject = 'Your Vendor Account Has Been Reactivated'
        html_message = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body {{ font-family: Arial, sans-serif; line-height: 1.6; color: #333; }}
                .container {{ max-width: 600px; margin: 0 auto; padding: 20px; }}
                .header {{ background: #059669; color: white; padding: 20px; text-align: center; }}
                .content {{ padding: 30px; background: #f9fafb; }}
                .button {{ display: inline-block; padding: 12px 24px; background: #059669; color: white; text-decoration: none; border-radius: 6px; margin: 20px 0; }}
                .footer {{ padding: 20px; text-align: center; color: #666; font-size: 12px; }}
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>Welcome Back!</h1>
                </div>
                <div class="content">
                    <p>Hi {_escape(vendor.user.first_name or vendor.business_name)},</p>
                    <p>Great news! Your vendor account for <strong>{_escape(vendor.business_name)}</strong> has been reactivated.</p>
                    <p>You can now access your vendor dashboard and manage your products.</p>
                    <p style="text-align: center;">
                        <a href="{_get_base_url()}/vendor/dashboard" class="button">Go to Dashboard</a>
                    </p>
                </div>
                <div class="footer">
                    <p>Questions? Contact us at vendor-support@example.com</p>
                </div>
            </div>
        </body>
        </html>
        """

        text_message = f"""
        Great news! Your vendor account for {vendor.business_name} has been reactivated.

        Access your dashboard at: {_get_base_url()}/vendor/dashboard
        """

        send_mail(
            subject=subject,
            message=text_message,
            from_email=settings.DEFAULT_FROM_EMAIL,
            recipient_list=[vendor.user.email],
            html_message=html_message,
            fail_silently=False,
        )

    except Vendor.DoesNotExist:
        pass
    except Exception as exc:
        self.retry(exc=exc, countdown=60)


@shared_task(bind=True, max_retries=3, soft_time_limit=60)
def send_payout_notification_email(self, payout_id: int):
    """Send payout processing notification to vendor."""
    from apps.vendors.models import VendorPayout

    try:
        payout = VendorPayout.objects.select_related('vendor__user').get(id=payout_id)
        vendor = payout.vendor

        subject = f'Payout Processing - ${payout.net_amount:.2f}'
        html_message = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body {{ font-family: Arial, sans-serif; line-height: 1.6; color: #333; }}
                .container {{ max-width: 600px; margin: 0 auto; padding: 20px; }}
                .header {{ background: #059669; color: white; padding: 20px; text-align: center; }}
                .content {{ padding: 30px; background: #f9fafb; }}
                .footer {{ padding: 20px; text-align: center; color: #666; font-size: 12px; }}
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>Payout Processing</h1>
                </div>
                <div class="content">
                    <p>Hi {_escape(vendor.user.first_name or vendor.business_name)},</p>
                    <p>Your payout is being processed.</p>
                    <table style="width: 100%; margin: 20px 0;">
                        <tr><td>Period:</td><td style="text-align: right;">{payout.period_start} - {payout.period_end}</td></tr>
                        <tr><td>Gross Amount:</td><td style="text-align: right;">${payout.amount:.2f}</td></tr>
                        <tr><td>Commission:</td><td style="text-align: right;">-${payout.commission_deducted:.2f}</td></tr>
                        <tr style="font-weight: bold;"><td>Net Payout:</td><td style="text-align: right;">${payout.net_amount:.2f}</td></tr>
                    </table>
                    <p>Funds should arrive within 3-5 business days.</p>
                </div>
                <div class="footer">
                    <p>Questions? Contact us at vendor-support@example.com</p>
                </div>
            </div>
        </body>
        </html>
        """

        text_message = f"""
        Hi {vendor.user.first_name or vendor.business_name},

        Your payout is being processed.

        Period: {payout.period_start} - {payout.period_end}
        Net Payout: ${payout.net_amount:.2f}

        Funds should arrive within 3-5 business days.
        """

        send_mail(
            subject=subject,
            message=text_message,
            from_email=settings.DEFAULT_FROM_EMAIL,
            recipient_list=[vendor.user.email],
            html_message=html_message,
            fail_silently=False,
        )

    except VendorPayout.DoesNotExist:
        pass
    except Exception as exc:
        self.retry(exc=exc, countdown=60)


@shared_task(bind=True, max_retries=3, soft_time_limit=60)
def send_low_stock_alert_email(self, alert_id: int):
    """Send low stock alert to vendor."""
    from apps.inventory.models import StockAlert

    try:
        alert = StockAlert.objects.select_related(
            'inventory_item__product__vendor__user'
        ).get(id=alert_id)

        vendor = alert.inventory_item.product.vendor
        product = alert.inventory_item.product

        subject = f'Low Stock Alert - {_escape(product.name)}'
        html_message = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body {{ font-family: Arial, sans-serif; line-height: 1.6; color: #333; }}
                .container {{ max-width: 600px; margin: 0 auto; padding: 20px; }}
                .header {{ background: #F59E0B; color: white; padding: 20px; text-align: center; }}
                .content {{ padding: 30px; background: #f9fafb; }}
                .alert-box {{ background: #FEF3C7; border: 1px solid #F59E0B; padding: 15px; border-radius: 6px; margin: 20px 0; }}
                .button {{ display: inline-block; padding: 12px 24px; background: #F59E0B; color: white; text-decoration: none; border-radius: 6px; margin: 20px 0; }}
                .footer {{ padding: 20px; text-align: center; color: #666; font-size: 12px; }}
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>Low Stock Alert</h1>
                </div>
                <div class="content">
                    <p>Hi {_escape(vendor.user.first_name or vendor.business_name)},</p>
                    <div class="alert-box">
                        <strong>{_escape(product.name)}</strong> is running low on stock.
                        <br><br>
                        Current Quantity: <strong>{alert.current_quantity}</strong><br>
                        Reorder Point: <strong>{alert.threshold_triggered}</strong>
                    </div>
                    <p>Consider restocking soon to avoid running out.</p>
                    <p style="text-align: center;">
                        <a href="{_get_base_url()}/vendor/inventory" class="button">Manage Inventory</a>
                    </p>
                </div>
                <div class="footer">
                    <p>Manage your alert settings in your vendor dashboard.</p>
                </div>
            </div>
        </body>
        </html>
        """

        text_message = f"""
        Low Stock Alert for {product.name}

        Current Quantity: {alert.current_quantity}
        Reorder Point: {alert.threshold_triggered}

        Manage inventory at: {_get_base_url()}/vendor/inventory
        """

        recipients = [vendor.user.email]
        if vendor.business_email and vendor.business_email != vendor.user.email:
            recipients.append(vendor.business_email)

        send_mail(
            subject=subject,
            message=text_message,
            from_email=settings.DEFAULT_FROM_EMAIL,
            recipient_list=recipients,
            html_message=html_message,
            fail_silently=False,
        )

    except StockAlert.DoesNotExist:
        pass
    except Exception as exc:
        self.retry(exc=exc, countdown=60)
