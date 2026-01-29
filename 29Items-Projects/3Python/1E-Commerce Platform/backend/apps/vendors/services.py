"""Vendor service layer for E-Commerce Platform."""
from decimal import Decimal
from typing import Optional

from django.db import transaction
from django.db.models import Avg, Count, Sum
from django.db.models.functions import TruncDate
from django.utils import timezone

from apps.products.models import Product

from .models import Vendor, VendorPayout


class VendorService:
    """Service class for vendor-related business logic."""

    @staticmethod
    @transaction.atomic
    def approve_vendor(vendor: Vendor) -> Vendor:
        """Approve a pending vendor."""
        if vendor.status != Vendor.Status.PENDING:
            raise ValueError("Vendor is not pending approval.")

        vendor.status = Vendor.Status.ACTIVE
        vendor.save()

        # Send approval email
        from celery_tasks.email_tasks import send_vendor_approval_email
        send_vendor_approval_email.delay(vendor.id)

        return vendor

    @staticmethod
    @transaction.atomic
    def reject_vendor(vendor: Vendor, reason: str) -> Vendor:
        """Reject a pending vendor application."""
        if vendor.status != Vendor.Status.PENDING:
            raise ValueError("Vendor is not pending approval.")

        vendor.status = Vendor.Status.REJECTED
        vendor.save()

        # Send rejection email
        from celery_tasks.email_tasks import send_vendor_rejection_email
        send_vendor_rejection_email.delay(vendor.id, reason)

        return vendor

    @staticmethod
    @transaction.atomic
    def suspend_vendor(vendor: Vendor, reason: str) -> Vendor:
        """Suspend a vendor."""
        if vendor.status == Vendor.Status.SUSPENDED:
            raise ValueError("Vendor is already suspended.")

        vendor.status = Vendor.Status.SUSPENDED
        vendor.save()

        # Deactivate all vendor products
        Product.objects.filter(vendor=vendor).update(status=Product.Status.INACTIVE)

        # Send suspension email
        from celery_tasks.email_tasks import send_vendor_suspension_email
        send_vendor_suspension_email.delay(vendor.id, reason)

        return vendor

    @staticmethod
    @transaction.atomic
    def reactivate_vendor(vendor: Vendor) -> Vendor:
        """Reactivate a suspended vendor."""
        if vendor.status != Vendor.Status.SUSPENDED:
            raise ValueError("Vendor is not suspended.")

        vendor.status = Vendor.Status.ACTIVE
        vendor.save()

        # Send reactivation email
        from celery_tasks.email_tasks import send_vendor_reactivation_email
        send_vendor_reactivation_email.delay(vendor.id)

        return vendor

    @staticmethod
    def update_vendor_metrics(vendor: Vendor) -> None:
        """Update vendor's denormalized metrics."""
        from apps.checkout.models import OrderItem

        # Count active products
        vendor.total_products = vendor.products.filter(
            status=Product.Status.ACTIVE
        ).count()

        # Calculate order metrics
        order_stats = OrderItem.objects.filter(
            vendor=vendor,
            order__status__in=['delivered', 'shipped', 'processing']
        ).aggregate(
            total_orders=Count('order', distinct=True),
            total_revenue=Sum('line_total')
        )

        vendor.total_orders = order_stats['total_orders'] or 0
        vendor.total_revenue = order_stats['total_revenue'] or Decimal('0.00')

        # Calculate average rating
        from apps.products.models import ProductReview
        avg_rating = ProductReview.objects.filter(
            product__vendor=vendor,
            is_approved=True
        ).aggregate(avg=Avg('rating'))['avg']

        vendor.avg_rating = Decimal(str(avg_rating or 0)).quantize(Decimal('0.01'))

        vendor.save()

    @staticmethod
    @transaction.atomic
    def create_payout(
        vendor: Vendor,
        period_start,
        period_end
    ) -> Optional[VendorPayout]:
        """Create a payout for a vendor's earnings."""
        from apps.checkout.models import OrderItem

        # Check for existing payout in this period
        existing = VendorPayout.objects.filter(
            vendor=vendor,
            period_start=period_start,
            period_end=period_end
        ).exists()

        if existing:
            raise ValueError("Payout already exists for this period.")

        # Calculate total earnings for period
        earnings = OrderItem.objects.filter(
            vendor=vendor,
            order__status='delivered',
            order__delivered_at__date__gte=period_start,
            order__delivered_at__date__lte=period_end
        ).aggregate(total=Sum('line_total'))['total']

        if not earnings or earnings <= 0:
            return None

        # Calculate commission
        commission = earnings * (vendor.commission_rate / 100)
        net_amount = earnings - commission

        return VendorPayout.objects.create(
            vendor=vendor,
            amount=earnings,
            commission_deducted=commission.quantize(Decimal('0.01')),
            net_amount=net_amount.quantize(Decimal('0.01')),
            period_start=period_start,
            period_end=period_end
        )

    @staticmethod
    @transaction.atomic
    def process_payout(payout: VendorPayout) -> VendorPayout:
        """Mark a payout as processed."""
        if payout.status != VendorPayout.Status.PENDING:
            raise ValueError("Payout is not pending.")

        payout.status = VendorPayout.Status.PROCESSING
        payout.save()

        # Send payout processing notification
        from celery_tasks.email_tasks import send_payout_notification_email
        send_payout_notification_email.delay(payout.id)

        return payout

    @staticmethod
    @transaction.atomic
    def complete_payout(payout: VendorPayout, transaction_id: str = '') -> VendorPayout:
        """Mark a payout as completed."""
        if payout.status != VendorPayout.Status.PROCESSING:
            raise ValueError("Payout is not processing.")

        payout.status = VendorPayout.Status.COMPLETED
        payout.paid_at = timezone.now()
        payout.transaction_id = transaction_id
        payout.save()

        return payout

    @staticmethod
    def get_vendor_analytics(vendor: Vendor, days: int = 30) -> dict:
        """Get vendor analytics for dashboard."""
        from datetime import timedelta
        from apps.checkout.models import OrderItem

        cutoff_date = timezone.now() - timedelta(days=days)

        order_items = OrderItem.objects.filter(
            vendor=vendor,
            order__created_at__gte=cutoff_date
        )

        # Daily revenue
        daily_revenue = order_items.annotate(
            day=TruncDate('order__created_at')
        ).values('day').annotate(
            revenue=Sum('line_total'),
            orders=Count('order', distinct=True)
        ).order_by('day')

        # Top products
        top_products = order_items.values(
            'product_name'
        ).annotate(
            total_sold=Sum('quantity'),
            revenue=Sum('line_total')
        ).order_by('-revenue')[:10]

        # Revenue by status
        revenue_by_status = order_items.values(
            'order__status'
        ).annotate(
            revenue=Sum('line_total'),
            count=Count('id')
        )

        # Recent orders
        recent_orders = order_items.select_related('order').order_by(
            '-order__created_at'
        )[:10]

        return {
            'daily_revenue': list(daily_revenue),
            'top_products': list(top_products),
            'revenue_by_status': list(revenue_by_status),
            'recent_orders': [
                {
                    'order_number': item.order.order_number,
                    'product_name': item.product_name,
                    'quantity': item.quantity,
                    'line_total': float(item.line_total),
                    'status': item.order.status,
                    'created_at': item.order.created_at.isoformat(),
                }
                for item in recent_orders
            ],
            'total_revenue': float(vendor.total_revenue),
            'total_orders': vendor.total_orders,
            'total_products': vendor.total_products,
            'avg_rating': float(vendor.avg_rating),
        }

    @staticmethod
    def get_vendor_products(vendor: Vendor, status: str = None, page: int = 1, page_size: int = 20) -> dict:
        """Get paginated vendor products."""
        queryset = Product.objects.filter(vendor=vendor)

        if status:
            queryset = queryset.filter(status=status)

        queryset = queryset.order_by('-created_at')

        total_count = queryset.count()
        offset = (page - 1) * page_size
        products = queryset[offset:offset + page_size]

        return {
            'products': products,
            'total_count': total_count,
            'page': page,
            'page_size': page_size,
            'total_pages': (total_count + page_size - 1) // page_size
        }

    @staticmethod
    def get_vendor_payouts(vendor: Vendor, status: str = None, page: int = 1, page_size: int = 10) -> dict:
        """Get paginated vendor payouts."""
        queryset = VendorPayout.objects.filter(vendor=vendor)

        if status:
            queryset = queryset.filter(status=status)

        queryset = queryset.order_by('-created_at')

        total_count = queryset.count()
        offset = (page - 1) * page_size
        payouts = queryset[offset:offset + page_size]

        return {
            'payouts': payouts,
            'total_count': total_count,
            'page': page,
            'page_size': page_size,
            'total_pages': (total_count + page_size - 1) // page_size
        }

    @staticmethod
    @transaction.atomic
    def update_vendor_profile(vendor: Vendor, data: dict) -> Vendor:
        """Update vendor profile information."""
        allowed_fields = [
            'business_name', 'business_email', 'business_phone',
            'business_address', 'description', 'website', 'logo'
        ]

        for field in allowed_fields:
            if field in data:
                setattr(vendor, field, data[field])

        vendor.save(update_fields=[f for f in allowed_fields if f in data])
        return vendor
