"""
Integration Tests for API Endpoints

Tests for end-to-end API flows across multiple endpoints.
"""

import pytest
from decimal import Decimal
from rest_framework.test import APIClient

from backend.core.models import User, Product, Category, Vendor, Cart, Order


@pytest.mark.django_db
@pytest.mark.integration
class TestAuthFlowIntegration:
    """Integration tests for authentication flow."""

    def test_complete_registration_and_login_flow(self, api_client):
        """Test full registration and login flow."""
        # Step 1: Register new user
        register_data = {
            "email": "newuser@example.com",
            "password": "SecurePass123!",
            "first_name": "New",
            "last_name": "User",
        }

        response = api_client.post("/api/v1/auth/register/", register_data, format="json")

        assert response.status_code == 201
        assert "access_token" in response.json()

        # Step 2: Login with the same credentials
        login_data = {
            "email": "newuser@example.com",
            "password": "SecurePass123!",
        }

        response = api_client.post("/api/v1/auth/login/", login_data, format="json")

        assert response.status_code == 200
        assert "access_token" in response.json()

        # Step 3: Access protected endpoint
        token = response.json()["access_token"]
        api_client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

        response = api_client.get("/api/v1/auth/me/")

        assert response.status_code == 200
        assert response.json()["email"] == "newuser@example.com"


@pytest.mark.django_db
@pytest.mark.integration
class TestShoppingCartFlowIntegration:
    """Integration tests for shopping cart flow."""

    def test_complete_cart_flow(self, api_client):
        """Test add, update, remove cart items flow."""
        # Setup: Create user and authenticate
        user = User.objects.create_user(
            email="shopper@example.com",
            password="testpass123",
            first_name="Shopper",
            last_name="User"
        )

        # Login
        response = api_client.post(
            "/api/v1/auth/login/",
            {"email": "shopper@example.com", "password": "testpass123"},
            format="json"
        )
        token = response.json()["access_token"]
        api_client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

        # Setup: Create product
        category = Category.objects.create(name="Electronics", slug="electronics")
        vendor = Vendor.objects.create(name="Tech Vendor", slug="tech-vendor")

        product = Product.objects.create(
            name="Laptop",
            slug="laptop",
            price=Decimal("999.99"),
            category=category,
            vendor=vendor,
            stock=50
        )

        # Step 1: Get empty cart
        response = api_client.get("/api/v1/cart/")
        assert response.status_code == 200
        assert response.json()["items"] == []

        # Step 2: Add item to cart
        response = api_client.post(
            "/api/v1/cart/add_item/",
            {"product_id": product.id, "quantity": 2},
            format="json"
        )
        assert response.status_code == 201
        assert response.json()["total_items"] == 2

        # Step 3: Update cart item
        cart = Cart.objects.get(user=user)
        cart_item = cart.items.first()

        response = api_client.patch(
            f"/api/v1/cart/items/update_item/?id={cart_item.id}",
            {"quantity": 3},
            format="json"
        )
        assert response.status_code == 200
        assert response.json()["items"][0]["quantity"] == 3

        # Step 4: Remove item from cart
        response = api_client.delete(f"/api/v1/cart/items/remove_item/?id={cart_item.id}")
        assert response.status_code == 200
        assert response.json()["total_items"] == 0


@pytest.mark.django_db
@pytest.mark.integration
class TestCheckoutFlowIntegration:
    """Integration tests for checkout flow."""

    def test_complete_checkout_flow(self, api_client):
        """Test from cart to order creation."""
        # Setup: User and product
        user = User.objects.create_user(
            email="buyer@example.com",
            password="testpass123",
            first_name="Buyer",
            last_name="User"
        )

        category = Category.objects.create(name="Books", slug="books")
        vendor = Vendor.objects.create(name="Book Store", slug="book-store")

        product = Product.objects.create(
            name="Programming Book",
            slug="programming-book",
            price=Decimal("49.99"),
            category=category,
            vendor=vendor,
            stock=100
        )

        # Login
        response = api_client.post(
            "/api/v1/auth/login/",
            {"email": "buyer@example.com", "password": "testpass123"},
            format="json"
        )
        token = response.json()["access_token"]
        api_client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

        # Add to cart
        api_client.post(
            "/api/v1/cart/add_item/",
            {"product_id": product.id, "quantity": 2},
            format="json"
        )

        # Create address
        address_response = api_client.post(
            "/api/v1/addresses/",
            {
                "address_type": "shipping",
                "first_name": "Buyer",
                "last_name": "User",
                "street": "123 Book St",
                "city": "Reading",
                "state": "PA",
                "zip_code": "19601",
                "country": "US",
                "phone": "+1234567890"
            },
            format="json"
        )
        assert address_response.status_code == 201

        # Checkout
        checkout_data = {
            "shipping_address_id": address_response.json()["id"],
            "billing_address_id": address_response.json()["id"],
            "payment_method": "stripe"
        }

        response = api_client.post("/api/v1/orders/checkout/", checkout_data, format="json")

        assert response.status_code in [200, 201]
        assert "order_number" in response.json() or "id" in response.json()


@pytest.mark.django_db
@pytest.mark.integration
class TestOrderManagementFlowIntegration:
    """Integration tests for order management flow."""

    def test_order_lifecycle(self, api_client):
        """Test complete order lifecycle from creation to delivery."""
        # Setup: Create confirmed order
        user = User.objects.create_user(
            email="customer@example.com",
            password="testpass123",
            first_name="Customer",
            last_name="User"
        )

        category = Category.objects.create(name="Furniture", slug="furniture")
        vendor = Vendor.objects.create(name="Furniture Co", slug="furniture-co")

        product = Product.objects.create(
            name="Chair",
            slug="chair",
            price=Decimal("149.99"),
            category=category,
            vendor=vendor,
            stock=50
        )

        # Login
        response = api_client.post(
            "/api/v1/auth/login/",
            {"email": "customer@example.com", "password": "testpass123"},
            format="json"
        )
        token = response.json()["access_token"]
        api_client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

        # Create order directly
        order = Order.objects.create(
            user=user,
            status="confirmed",
            total_amount=Decimal("149.99"),
            shipping_address={
                "first_name": "Customer",
                "last_name": "User",
                "street": "123 Main St",
                "city": "Anytown",
                "state": "CA",
                "zip_code": "90210",
                "country": "US"
            }
        )

        # Step 1: Get order list
        response = api_client.get("/api/v1/orders/")
        assert response.status_code == 200
        assert len(response.json()["results"]) >= 1

        # Step 2: Get specific order
        response = api_client.get(f"/api/v1/orders/{order.id}/")
        assert response.status_code == 200
        assert response.json()["id"] == order.id

        # Step 3: Cancel order
        response = api_client.post(f"/api/v1/orders/{order.id}/cancel/")
        assert response.status_code == 200
        assert response.json()["status"] == "cancelled"


@pytest.mark.django_db
@pytest.mark.integration
class TestProductSearchAndFilterIntegration:
    """Integration tests for product search and filtering."""

    def test_product_search_flow(self, api_client):
        """Test product search with filters."""
        # Setup: Create products
        category = Category.objects.create(name="Electronics", slug="electronics")
        vendor = Vendor.objects.create(name="Tech Store", slug="tech-store")

        Product.objects.create(
            name="iPhone 15",
            slug="iphone-15",
            price=Decimal("999.99"),
            category=category,
            vendor=vendor,
            stock=50,
            is_featured=True
        )

        Product.objects.create(
            name="Samsung Galaxy",
            slug="samsung-galaxy",
            price=Decimal("899.99"),
            category=category,
            vendor=vendor,
            stock=30,
            is_featured=False
        )

        Product.objects.create(
            name="iPhone Case",
            slug="iphone-case",
            price=Decimal("19.99"),
            category=category,
            vendor=vendor,
            stock=100
        )

        # Step 1: Search by name
        response = api_client.get("/api/v1/products/?search=iPhone")
        assert response.status_code == 200
        assert len(response.json()["results"]) >= 2

        # Step 2: Filter by featured
        response = api_client.get("/api/v1/products/?is_featured=true")
        assert response.status_code == 200
        results = response.json()["results"]
        assert all(p["is_featured"] for p in results)

        # Step 3: Filter by price range
        response = api_client.get("/api/v1/products/?min_price=100&max_price=1000")
        assert response.status_code == 200
        results = response.json()["results"]
        for product in results:
            assert 100 <= float(product["price"]) <= 1000

        # Step 4: Order by price
        response = api_client.get("/api/v1/products/?ordering=price")
        assert response.status_code == 200
        prices = [p["price"] for p in response.json()["results"]]
        assert prices == sorted(prices)


@pytest.mark.django_db
@pytest.mark.integration
class TestProductDetailsIntegration:
    """Integration tests for product details and related products."""

    def test_product_details_flow(self, api_client):
        """Test getting product details and related products."""
        # Setup: Create category, vendor and products
        category = Category.objects.create(name="Clothing", slug="clothing")
        vendor = Vendor.objects.create(name="Fashion Store", slug="fashion-store")

        main_product = Product.objects.create(
            name="Cotton T-Shirt",
            slug="cotton-tshirt",
            description="Comfortable cotton t-shirt",
            price=Decimal("29.99"),
            category=category,
            vendor=vendor,
            stock=100,
            is_featured=True
        )

        # Create related products
        for i in range(3):
            Product.objects.create(
                name=f"Related Product {i}",
                slug=f"related-product-{i}",
                price=Decimal(str(20 + i * 10)),
                category=category,
                vendor=vendor,
                stock=50
            )

        # Step 1: Get product details
        response = api_client.get(f"/api/v1/products/{main_product.id}/")
        assert response.status_code == 200
        assert response.json()["name"] == "Cotton T-Shirt"

        # Step 2: Get related products
        response = api_client.get(f"/api/v1/products/{main_product.id}/related/")
        assert response.status_code == 200
        assert "results" in response.json()


@pytest.mark.django_db
@pytest.mark.integration
class TestReviewFlowIntegration:
    """Integration tests for product review flow."""

    def test_review_creation_and_display(self, api_client):
        """Test creating and retrieving product reviews."""
        # Setup: User, product, and order
        user = User.objects.create_user(
            email="reviewer@example.com",
            password="testpass123"
        )

        category = Category.objects.create(name="Gaming", slug="gaming")
        vendor = Vendor.objects.create(name="Game Store", slug="game-store")

        product = Product.objects.create(
            name="Video Game",
            slug="video-game",
            price=Decimal("59.99"),
            category=category,
            vendor=vendor,
            stock=100
        )

        # Create order to enable review
        order = Order.objects.create(
            user=user,
            status="delivered",
            total_amount=Decimal("59.99")
        )

        # Login
        response = api_client.post(
            "/api/v1/auth/login/",
            {"email": "reviewer@example.com", "password": "testpass123"},
            format="json"
        )
        token = response.json()["access_token"]
        api_client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

        # Step 1: Create review
        review_data = {
            "order_id": order.id,
            "product_id": product.id,
            "rating": 5,
            "title": "Amazing game!",
            "comment": "Best game I've ever played"
        }

        response = api_client.post(f"/api/v1/products/{product.id}/reviews/", review_data, format="json")
        assert response.status_code == 201

        # Step 2: Get product reviews
        response = api_client.get(f"/api/v1/products/{product.id}/reviews/")
        assert response.status_code == 200
        assert len(response.json()["results"]) >= 1

        # Step 3: Check product rating updated
        response = api_client.get(f"/api/v1/products/{product.id}/")
        assert response.json()["average_rating"] == 5.0
        assert response.json()["review_count"] == 1


@pytest.mark.django_db
@pytest.mark.integration
class TestWishlistFlowIntegration:
    """Integration tests for wishlist flow."""

    def test_wishlist_management(self, api_client):
        """Test adding and managing wishlist items."""
        # Setup: User and products
        user = User.objects.create_user(
            email="wishlist_user@example.com",
            password="testpass123"
        )

        category = Category.objects.create(name="Toys", slug="toys")
        vendor = Vendor.objects.create(name="Toy Store", slug="toy-store")

        product1 = Product.objects.create(
            name="Toy Car",
            slug="toy-car",
            price=Decimal("19.99"),
            category=category,
            vendor=vendor,
            stock=50
        )

        product2 = Product.objects.create(
            name="Toy Doll",
            slug="toy-doll",
            price=Decimal("24.99"),
            category=category,
            vendor=vendor,
            stock=30
        )

        # Login
        response = api_client.post(
            "/api/v1/auth/login/",
            {"email": "wishlist_user@example.com", "password": "testpass123"},
            format="json"
        )
        token = response.json()["access_token"]
        api_client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

        # Step 1: Create wishlist
        response = api_client.post(
            "/api/v1/wishlists/",
            {"name": "My Toys"},
            format="json"
        )
        assert response.status_code == 201
        wishlist_id = response.json()["id"]

        # Step 2: Add items to wishlist
        response = api_client.post(
            f"/api/v1/wishlists/{wishlist_id}/add_item/",
            {"product_id": product1.id},
            format="json"
        )
        assert response.status_code == 201

        response = api_client.post(
            f"/api/v1/wishlists/{wishlist_id}/add_item/",
            {"product_id": product2.id},
            format="json"
        )
        assert response.status_code == 201

        # Step 3: Get wishlist
        response = api_client.get(f"/api/v1/wishlists/{wishlist_id}/")
        assert response.status_code == 200
        assert len(response.json()["items"]) == 2

        # Step 4: Remove item
        from backend.core.models import Wishlist, WishlistItem
        wishlist = Wishlist.objects.get(id=wishlist_id)
        item = wishlist.items.first()

        response = api_client.delete(f"/api/v1/wishlists/{wishlist_id}/remove_item/?item_id={item.id}")
        assert response.status_code == 200


@pytest.mark.django_db
@pytest.mark.integration
class TestAddressManagementIntegration:
    """Integration tests for address management."""

    def test_address_crud(self, api_client):
        """Test creating, updating, and deleting addresses."""
        # Setup: User
        user = User.objects.create_user(
            email="address_user@example.com",
            password="testpass123"
        )

        # Login
        response = api_client.post(
            "/api/v1/auth/login/",
            {"email": "address_user@example.com", "password": "testpass123"},
            format="json"
        )
        token = response.json()["access_token"]
        api_client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

        # Step 1: Create address
        address_data = {
            "address_type": "shipping",
            "first_name": "John",
            "last_name": "Doe",
            "street": "123 Main St",
            "city": "New York",
            "state": "NY",
            "zip_code": "10001",
            "country": "US",
            "phone": "+1234567890"
        }

        response = api_client.post("/api/v1/addresses/", address_data, format="json")
        assert response.status_code == 201
        address_id = response.json()["id"]

        # Step 2: Get addresses
        response = api_client.get("/api/v1/addresses/")
        assert response.status_code == 200
        assert len(response.json()) == 1

        # Step 3: Update address
        response = api_client.patch(
            f"/api/v1/addresses/{address_id}/",
            {"city": "Los Angeles"},
            format="json"
        )
        assert response.status_code == 200
        assert response.json()["city"] == "Los Angeles"

        # Step 4: Set as default
        response = api_client.post(f"/api/v1/addresses/{address_id}/set_default/")
        assert response.status_code == 200

        # Step 5: Delete address
        response = api_client.delete(f"/api/v1/addresses/{address_id}/")
        assert response.status_code == 204
