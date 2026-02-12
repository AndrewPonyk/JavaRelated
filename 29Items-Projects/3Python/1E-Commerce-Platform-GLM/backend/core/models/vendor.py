"""
Vendor Models

Models for vendor/store management.
"""

from django.db import models


class Vendor(models.Model):
    """
    Vendor model for sellers on the platform.
    """

    class Meta:
        db_table = "vendors"
        verbose_name = "Vendor"
        verbose_name_plural = "Vendors"
        ordering = ["name"]

    # Basic Information
    name = models.CharField(max_length=255)
    slug = models.SlugField(unique=True, max_length=255)
    description = models.TextField(blank=True)

    # Business Information
    business_name = models.CharField(max_length=255)
    tax_id = models.CharField(max_length=50, blank=True)
    business_type = models.CharField(
        max_length=50,
        choices=[
            ("individual", "Individual"),
            ("company", "Company"),
        ],
        default="individual",
    )

    # Contact
    email = models.EmailField()
    phone = models.CharField(max_length=20)
    website = models.URLField(blank=True)

    # Address
    address = models.JSONField(default=dict)

    # Branding
    logo = models.ImageField(upload_to="vendors/logos/", blank=True, null=True)
    banner = models.ImageField(upload_to="vendors/banners/", blank=True, null=True)

    # Settings
    commission_rate = models.DecimalField(
        max_digits=5,
        decimal_places=2,
        default=10.0,
        help_text="Platform commission percentage",
    )

    # Status
    is_active = models.BooleanField(default=False)
    is_verified = models.BooleanField(default=False)
    verification_date = models.DateTimeField(blank=True, null=True)

    # Stripe Connect for vendor payouts
    stripe_account_id = models.CharField(max_length=255, blank=True)

    # Timestamps
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return self.name

    @property
    def total_products(self) -> int:
        """Get total number of products for this vendor."""
        return self.products.filter(is_active=True).count()

    @property
    def total_sales(self) -> float:
        """Get total sales for this vendor."""
        # TODO: Calculate from OrderItems
        return 0.0

    def calculate_earnings(self, order_total: float) -> tuple[float, float]:
        """
        Calculate platform commission and vendor earnings.

        Returns:
            tuple: (commission_amount, vendor_earnings)
        """
        commission_amount = (self.commission_rate / 100) * order_total
        vendor_earnings = order_total - commission_amount
        return commission_amount, vendor_earnings


# TODO: Add VendorSettings model for more customization
# TODO: Add VendorPayout model for tracking payments to vendors
# TODO: Add VendorStats model for analytics
