"""
Order Validators

Request validators for order operations.
"""

from rest_framework import serializers


class AddressValidator(serializers.Serializer):
    """Validator for address data."""

    first_name = serializers.CharField(max_length=150, required=True)
    last_name = serializers.CharField(max_length=150, required=True)
    street = serializers.CharField(max_length=255, required=True)
    apartment = serializers.CharField(max_length=100, required=False, allow_blank=True)
    city = serializers.CharField(max_length=100, required=True)
    state = serializers.CharField(max_length=100, required=True)
    zip_code = serializers.CharField(max_length=20, required=True)
    country = serializers.CharField(max_length=2, required=False, default="US")
    phone = serializers.CharField(max_length=20, required=True)


class OrderValidator:
    """Validators for order-related requests."""
    
    Address = AddressValidator

    class checkout(serializers.Serializer):
        """Validator for checkout requests."""

        shipping_address = AddressValidator(required=True)
        billing_address = AddressValidator(required=False)
        payment_method = serializers.ChoiceField(
            choices=["stripe", "paypal", "cod"],
            required=True,
        )
        payment_token = serializers.CharField(required=False, allow_blank=True)
        save_address = serializers.BooleanField(required=False, default=False)
        notes = serializers.CharField(required=False, allow_blank=True)

        def validate(self, attrs):
            """Validate checkout data."""
            payment_method = attrs.get("payment_method")

            # Stripe requires payment token
            if payment_method == "stripe" and not attrs.get("payment_token"):
                raise serializers.ValidationError({
                    "payment_token": "Payment token is required for Stripe payments"
                })

            # Use shipping address as billing if not provided
            if not attrs.get("billing_address"):
                attrs["billing_address"] = attrs["shipping_address"]

            return attrs
