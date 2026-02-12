"""
End-to-End Tests for User Journeys

Full browser automation tests for critical user paths.
These tests use Playwright to simulate real user interactions.
"""

import pytest
from playwright.sync_api import Page, expect


@pytest.mark.e2e
@pytest.mark.skip(reason="E2E tests require browser setup - configure CI/CD before running")
class TestGuestUserJourney:
    """E2E tests for guest (unauthenticated) user journeys."""

    def test_guest_browsing_and_search(self, page: Page):
        """Test guest user can browse and search products."""
        # Navigate to homepage
        page.goto("http://localhost:3000")

        # Wait for page to load
        expect(page).to_have_title("E-Commerce Platform")

        # Browse featured products
        page.click("text=Featured Products")

        # Verify products are displayed
        product_cards = page.locator(".product-card")
        expect(product_cards).to_have_count(lambda count: count > 0)

        # Search for a product
        search_input = page.locator("input[placeholder*='Search']")
        search_input.fill("laptop")
        search_input.press("Enter")

        # Verify search results
        expect(page).to_have_url(r"/search/")
        expect(page.locator(".product-card")).to_have_count(lambda count: count >= 0)

    def test_guest_product_detail_view(self, page: Page):
        """Test guest user can view product details."""
        # Navigate to a product page
        page.goto("http://localhost:3000/products/laptop-15-pro")

        # Verify product details are displayed
        expect(page.locator("h1")).to_contain_text("Laptop 15 Pro")

        # Verify product information
        expect(page.locator(".price")).to_be_visible()
        expect(page.locator(".description")).to_be_visible()
        expect(page.locator(".stock-status")).to_be_visible()

        # Check that related products section exists
        expect(page.locator("text=Related Products")).to_be_visible()

    def test_guest_redirects_to_login_on_cart_access(self, page: Page):
        """Test guest is redirected to login when accessing cart."""
        # Try to access cart page
        page.goto("http://localhost:3000/cart")

        # Should be redirected to login
        expect(page).to_have_url(r"/login/")


@pytest.mark.e2e
@pytest.mark.skip(reason="E2E tests require browser setup")
class TestRegisteredUserJourney:
    """E2E tests for registered user journeys."""

    def test_new_user_registration_and_onboarding(self, page: Page):
        """Test complete new user registration flow."""
        # Navigate to registration page
        page.goto("http://localhost:3000/register")

        # Fill registration form
        page.fill("input[name='email']", "newuser@example.com")
        page.fill("input[name='password']", "SecurePass123!")
        page.fill("input[name='confirmPassword']", "SecurePass123!")
        page.fill("input[name='firstName']", "New")
        page.fill("input[name='lastName']", "User")

        # Submit form
        page.click("button[type='submit']")

        # Should be redirected to dashboard or home
        expect(page).to_have_url(r"/(dashboard|home)/")

        # Verify user is logged in
        expect(page.locator("text=Welcome, New")).to_be_visible()

    def test_user_login_flow(self, page: Page):
        """Test user login flow."""
        # Navigate to login page
        page.goto("http://localhost:3000/login")

        # Fill login form
        page.fill("input[name='email']", "test@example.com")
        page.fill("input[name='password']", "testpass123")

        # Submit form
        page.click("button[type='submit']")

        # Verify successful login
        expect(page).to_have_url(r"/home/")
        expect(page.locator("text=Logout")).to_be_visible()

    def test_complete_shopping_journey(self, page: Page):
        """Test complete shopping journey from login to order placement."""
        # Login
        page.goto("http://localhost:3000/login")
        page.fill("input[name='email']", "shopper@example.com")
        page.fill("input[name='password']", "testpass123")
        page.click("button[type='submit']")

        # Browse products
        page.goto("http://localhost:3000/products")

        # Click on a product
        page.click(".product-card:first-child")

        # Add to cart
        page.click("button:has-text('Add to Cart')")

        # Verify cart update notification
        expect(page.locator(".notification-success")).to_be_visible()

        # Go to cart
        page.click("text=Cart")

        # Verify item in cart
        expect(page.locator(".cart-item")).to_have_count(1)

        # Proceed to checkout
        page.click("button:has-text('Checkout')")

        # Fill shipping information
        page.fill("input[name='street']", "123 Main St")
        page.fill("input[name='city']", "New York")
        page.fill("input[name='state']", "NY")
        page.fill("input[name='zipCode']", "10001")
        page.fill("input[name='phone']", "+1234567890")

        # Continue to payment
        page.click("button:has-text('Continue')")

        # Fill payment information (test mode)
        page.fill("input[name='cardNumber']", "4242424242424242")
        page.fill("input[name='cardExpiry']", "1225")
        page.fill("input[name='cardCvc']", "123")

        # Place order
        page.click("button:has-text('Place Order')")

        # Verify order confirmation
        expect(page.locator("text=Order confirmed")).to_be_visible()
        expect(page.locator(".order-number")).to_be_visible()


@pytest.mark.e2e
@pytest.mark.skip(reason="E2E tests require browser setup")
class TestWishlistManagement:
    """E2E tests for wishlist management."""

    def test_add_to_wishlist(self, page: Page):
        """Test adding product to wishlist."""
        # Login
        page.goto("http://localhost:3000/login")
        page.fill("input[name='email']", "user@example.com")
        page.fill("input[name='password']", "testpass123")
        page.click("button[type='submit']")

        # Navigate to product
        page.goto("http://localhost:3000/products/wireless-headphones")

        # Add to wishlist
        page.click("button:has-text('Add to Wishlist')")

        # Verify button state changed
        expect(page.locator("button:has-text('In Wishlist')")).to_be_visible()

        # Go to wishlist page
        page.click("text=Wishlist")

        # Verify product is in wishlist
        expect(page.locator(".wishlist-item")).to_have_count(1)

    def test_move_wishlist_to_cart(self, page: Page):
        """Test moving item from wishlist to cart."""
        # Login and go to wishlist
        page.goto("http://localhost:3000/login")
        page.fill("input[name='email']", "user@example.com")
        page.fill("input[name='password']", "testpass123")
        page.click("button[type='submit']")

        page.goto("http://localhost:3000/wishlist")

        # Click "Move to Cart" button
        page.click(".wishlist-item:first-child button:has-text('Move to Cart')")

        # Verify success message
        expect(page.locator(".notification-success")).to_be_visible()

        # Go to cart to verify
        page.click("text=Cart")
        expect(page.locator(".cart-item")).to_have_count(1)


@pytest.mark.e2e
@pytest.mark.skip(reason="E2E tests require browser setup")
class TestOrderManagement:
    """E2E tests for order management."""

    def test_view_order_history(self, page: Page):
        """Test viewing order history."""
        # Login
        page.goto("http://localhost:3000/login")
        page.fill("input[name='email']", "user@example.com")
        page.fill("input[name='password']", "testpass123")
        page.click("button[type='submit']")

        # Navigate to orders
        page.click("text=My Orders")

        # Verify orders list
        expect(page.locator(".order-item")).to_have_count(lambda count: count >= 0)

        # Click on first order
        if page.locator(".order-item").count() > 0:
            page.click(".order-item:first-child")

            # Verify order details
            expect(page.locator(".order-details")).to_be_visible()
            expect(page.locator(".order-status")).to_be_visible()

    def test_cancel_pending_order(self, page: Page):
        """Test cancelling a pending order."""
        # Login
        page.goto("http://localhost:3000/login")
        page.fill("input[name='email']", "user@example.com")
        page.fill("input[name='password']", "testpass123")
        page.click("button[type='submit']")

        # Navigate to orders
        page.goto("http://localhost:3000/orders")

        # Find a pending order
        pending_orders = page.locator(".order-item:has-text('Pending')")

        if pending_orders.count() > 0:
            pending_orders.first.click()

            # Click cancel button
            page.click("button:has-text('Cancel Order')")

            # Confirm cancellation
            page.click("button:has-text('Confirm')")

            # Verify status updated
            expect(page.locator(".order-status:has-text('Cancelled')")).to_be_visible()


@pytest.mark.e2e
@pytest.mark.skip(reason="E2E tests require browser setup")
class TestAccountManagement:
    """E2E tests for account management."""

    def test_update_profile(self, page: Page):
        """Test updating user profile."""
        # Login
        page.goto("http://localhost:3000/login")
        page.fill("input[name='email']", "user@example.com")
        page.fill("input[name='password']", "testpass123")
        page.click("button[type='submit']")

        # Go to profile settings
        page.click("text=Account")
        page.click("text=Profile Settings")

        # Update first name
        page.fill("input[name='firstName']", "Updated")
        page.click("button:has-text('Save')")

        # Verify success message
        expect(page.locator(".notification-success")).to_be_visible()

        # Verify name updated in UI
        expect(page.locator("text=Updated")).to_be_visible()

    def test_change_password(self, page: Page):
        """Test changing password."""
        # Login
        page.goto("http://localhost:3000/login")
        page.fill("input[name='email']", "user@example.com")
        page.fill("input[name='password']", "oldpass123")
        page.click("button[type='submit']")

        # Go to security settings
        page.click("text=Account")
        page.click("text=Security")

        # Fill password change form
        page.fill("input[name='currentPassword']", "oldpass123")
        page.fill("input[name='newPassword']", "newpass456!")
        page.fill("input[name='confirmPassword']", "newpass456!")

        # Submit
        page.click("button:has-text('Change Password')")

        # Verify success message
        expect(page.locator(".notification-success")).to_be_visible()

        # Logout and login with new password
        page.click("text=Logout")

        page.fill("input[name='email']", "user@example.com")
        page.fill("input[name='password']", "newpass456!")
        page.click("button[type='submit']")

        # Verify logged in
        expect(page.locator("text=Logout")).to_be_visible()

    def test_manage_addresses(self, page: Page):
        """Test managing saved addresses."""
        # Login
        page.goto("http://localhost:3000/login")
        page.fill("input[name='email']", "user@example.com")
        page.fill("input[name='password']", "testpass123")
        page.click("button[type='submit']")

        # Go to addresses
        page.click("text=Account")
        page.click("text=Addresses")

        # Add new address
        page.click("button:has-text('Add Address')")

        page.fill("input[name='street']", "456 Oak Ave")
        page.fill("input[name='city']", "Los Angeles")
        page.fill("input[name='state']", "CA")
        page.fill("input[name='zipCode']", "90001")
        page.fill("input[name='phone']", "+1987654321")

        page.click("button:has-text('Save')")

        # Verify address added
        expect(page.locator(".address-card:has-text('Los Angeles')")).to_be_visible()

        # Set as default
        page.click(".address-card:first-child button:has-text('Set Default')")

        # Verify default badge
        expect(page.locator(".address-card:first-child .badge-default")).to_be_visible()


@pytest.mark.e2e
@pytest.mark.skip(reason="E2E tests require browser setup")
class TestSearchAndFilter:
    """E2E tests for search and filtering."""

    def test_advanced_search_filters(self, page: Page):
        """Test advanced search with filters."""
        # Navigate to products page
        page.goto("http://localhost:3000/products")

        # Apply category filter
        page.select_option("select[name='category']", "electronics")

        # Apply price range filter
        page.fill("input[name='minPrice']", "100")
        page.fill("input[name='maxPrice']", "500")

        # Apply filters
        page.click("button:has-text('Apply Filters')")

        # Verify filtered results
        expect(page.locator(".product-card")).to_have_count(lambda count: count >= 0)

        # Verify all products are in price range
        products = page.locator(".product-card .price")
        count = products.count()
        for i in range(count):
            price_text = products.nth(i).text_content()
            price = float(price_text.replace("$", ""))
            assert 100 <= price <= 500

    def test_search_autocomplete(self, page: Page):
        """Test search autocomplete suggestions."""
        # Navigate to homepage
        page.goto("http://localhost:3000")

        # Click search input
        search_input = page.locator("input[placeholder*='Search']")
        search_input.click()

        # Type search query
        search_input.fill("lap")

        # Wait for autocomplete
        expect(page.locator(".autocomplete-dropdown")).to_be_visible()

        # Verify suggestions
        suggestions = page.locator(".autocomplete-suggestion")
        expect(suggestions).to_have_count(lambda count: count > 0)

        # Click first suggestion
        suggestions.first.click()

        # Verify navigated to product page
        expect(page).to_have_url(r"/products/")


@pytest.mark.e2e
@pytest.mark.skip(reason="E2E tests require browser setup")
class TestResponsiveDesign:
    """E2E tests for responsive design across devices."""

    def test_mobile_product_view(self, page: Page):
        """Test product page on mobile viewport."""
        # Set mobile viewport
        page.set_viewport_size({"width": 375, "height": 667})

        # Navigate to product
        page.goto("http://localhost:3000/products/laptop-15-pro")

        # Verify mobile-specific elements
        expect(page.locator(".mobile-image-gallery")).to_be_visible()

        # Verify product info is below images on mobile
        images = page.locator(".product-images")
        info = page.locator(".product-info")

        # Images should appear before info in DOM
        expect(images).to_be_visible()
        expect(info).to_be_visible()

    def test_tablet_category_view(self, page: Page):
        """Test category page on tablet viewport."""
        # Set tablet viewport
        page.set_viewport_size({"width": 768, "height": 1024})

        # Navigate to category
        page.goto("http://localhost:3000/categories/electronics")

        # Verify grid layout
        products = page.locator(".product-grid")
        expect(products).to_have_class(r"/grid-cols-2/")

    def test_desktop_navigation(self, page: Page):
        """Test desktop navigation menu."""
        # Set desktop viewport
        page.set_viewport_size({"width": 1920, "height": 1080})

        # Navigate to homepage
        page.goto("http://localhost:3000")

        # Verify desktop nav is visible
        expect(page.locator(".desktop-navigation")).to_be_visible()

        # Verify dropdown menus work
        page.hover("text=Categories")

        # Verify dropdown appears
        expect(page.locator(".category-dropdown")).to_be_visible()


@pytest.mark.e2e
@pytest.mark.skip(reason="E2E tests require browser setup")
class TestAccessibility:
    """E2E tests for accessibility compliance."""

    def test_keyboard_navigation(self, page: Page):
        """Test keyboard navigation works properly."""
        # Navigate to homepage
        page.goto("http://localhost:3000")

        # Tab through elements
        page.keyboard.press("Tab")

        # Verify focus indicator
        focused = page.locator(":focus")
        expect(focused).to_be_visible()

        # Press Enter on focused link
        page.keyboard.press("Enter")

        # Verify navigation occurred
        expect(page).not_to_have_url("http://localhost:3000")

    def test_aria_labels(self, page: Page):
        """Test proper ARIA labels on interactive elements."""
        # Navigate to product page
        page.goto("http://localhost:3000/products/laptop-15-pro")

        # Check add to cart button has accessible label
        add_to_cart = page.locator("button:has-text('Add to Cart')")
        expect(add_to_cart).to_have_attribute("aria-label")

        # Check form inputs have labels
        quantity_input = page.locator("input[name='quantity']")
        expect(quantity_input).to_have_attribute("aria-label")


# Configuration fixtures
@pytest.fixture(scope="session")
def browser_context_args(browser_context_args):
    """Configure browser context for E2E tests."""
    return {
        **browser_context_args,
        "viewport": {"width": 1280, "height": 720},
        "locale": "en-US",
        "timezone_id": "America/New_York",
    }
