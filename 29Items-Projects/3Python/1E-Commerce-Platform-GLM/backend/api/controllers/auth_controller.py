"""
Authentication Controller (ViewSets)

REST API endpoints for user authentication.
"""

from rest_framework import viewsets, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.settings import api_settings as jwt_settings
from django.contrib.auth import get_user_model

from backend.api.serializers import (
    UserSerializer,
    RegisterSerializer,
    LoginSerializer,
    ChangePasswordSerializer,
)
from backend.api.validators.auth_validator import AuthValidator

User = get_user_model()


class AuthViewSet(viewsets.ViewSet):
    """
    ViewSet for authentication operations.
    """

    permission_classes = [AllowAny]

    @action(detail=False, methods=["post"], permission_classes=[AllowAny])
    def register(self, request):
        """
        Register a new user account.

        Creates a new user and returns JWT tokens.
        """
        serializer = RegisterSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()

        # Generate tokens
        refresh = RefreshToken.for_user(user)

        # Send verification email
        from backend.tasks.email_tasks import send_verification_email

        send_verification_email.delay(user.id)

        return Response(
            {
                "user": UserSerializer(user).data,
                "access_token": str(refresh.access_token),
                "refresh_token": str(refresh),
            },
            status=status.HTTP_201_CREATED,
        )

    @action(detail=False, methods=["post"], permission_classes=[AllowAny])
    def login(self, request):
        """
        Authenticate user and return JWT tokens.
        """
        serializer = LoginSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        user = serializer.validated_data["user"]
        refresh = RefreshToken.for_user(user)

        return Response(
            {
                "user": UserSerializer(user).data,
                "access_token": str(refresh.access_token),
                "refresh_token": str(refresh),
            }
        )

    @action(detail=False, methods=["post"], permission_classes=[IsAuthenticated])
    def logout(self, request):
        """
        Logout user and blacklist refresh token.
        """
        try:
            refresh_token = request.data.get("refresh_token")
            if refresh_token:
                token = RefreshToken(refresh_token)
                # Blacklist the refresh token
                token.blacklist()

            return Response({"message": "Successfully logged out"})
        except Exception as e:
            return Response(
                {"error": "Invalid token"},
                status=status.HTTP_400_BAD_REQUEST,
            )

    @action(detail=False, methods=["post"], permission_classes=[AllowAny])
    def refresh(self, request):
        """
        Refresh access token using refresh token.
        """
        refresh_token = request.data.get("refresh")
        if not refresh_token:
            return Response(
                {"error": "refresh_token is required"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            refresh = RefreshToken(refresh_token)
            return Response({
                "access_token": str(refresh.access_token),
            })
        except Exception:
            return Response(
                {"error": "Invalid refresh token"},
                status=status.HTTP_401_UNAUTHORIZED,
            )

    @action(detail=False, methods=["get"], permission_classes=[AllowAny])
    def me(self, request):
        """Get current authenticated user."""
        if not request.user.is_authenticated:
            return Response(
                {"error": "Authentication required"},
                status=status.HTTP_401_UNAUTHORIZED,
            )

        serializer = UserSerializer(request.user)
        return Response(serializer.data)

    @action(detail=False, methods=["post"], permission_classes=[IsAuthenticated])
    def verify_email(self, request):
        """Verify user email with token."""
        token = request.data.get("token")
        if not token:
            return Response(
                {"error": "Verification token is required"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        if request.user.verify_email(token):
            return Response({"message": "Email verified successfully"})
        return Response(
            {"error": "Invalid or expired verification token"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    @action(detail=False, methods=["post"], permission_classes=[AllowAny])
    def request_password_reset(self, request):
        """Request password reset email."""
        email = request.data.get("email")
        if not email:
            return Response(
                {"error": "Email is required"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            user = User.objects.get(email=email)
            from backend.tasks.email_tasks import send_password_reset_email

            send_password_reset_email.delay(user.id)
        except User.DoesNotExist:
            # Don't reveal if email exists
            pass

        return Response({
            "message": "If the email exists, a password reset link has been sent",
        })

    @action(detail=False, methods=["post"], permission_classes=[AllowAny])
    def reset_password(self, request):
        """Reset password with token."""
        token = request.data.get("token")
        new_password = request.data.get("new_password")

        if not token or not new_password:
            return Response(
                {"error": "Token and new_password are required"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        # Validate password strength
        validator = AuthValidator.validate_password(new_password)
        if not validator["is_valid"]:
            return Response(
                {"error": "Password is too weak"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        # Find user by reset token
        try:
            user = User.objects.get(reset_token=token)
        except User.DoesNotExist:
            return Response(
                {"error": "Invalid or expired reset token"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        # Reset password
        if user.reset_password(token, new_password):
            return Response({"message": "Password reset successfully"})

        return Response(
            {"error": "Invalid or expired reset token"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    @action(detail=False, methods=["post"], permission_classes=[IsAuthenticated])
    def change_password(self, request):
        """Change password for authenticated user."""
        serializer = ChangePasswordSerializer(
            data=request.data,
            context={"request": request}
        )
        serializer.is_valid(raise_exception=True)
        serializer.save()

        return Response({"message": "Password changed successfully"})

    @action(detail=False, methods=["post"], permission_classes=[IsAuthenticated])
    def update_profile(self, request):
        """Update user profile."""
        serializer = UserSerializer(
            request.user,
            data=request.data,
            partial=True
        )
        serializer.is_valid(raise_exception=True)
        serializer.save()

        return Response(serializer.data)

    @action(detail=False, methods=["post"], permission_classes=[AllowAny])
    def social_login(self, request):
        """
        Social login endpoint (Google, Facebook, etc.).

        Expects:
            provider: 'google', 'facebook', 'apple'
            access_token: OAuth access token from provider

        Note: Requires additional setup:
        - Install social-auth-app-django
        - Configure OAuth credentials in settings
        - Set up redirect URIs with providers
        """
        provider = request.data.get("provider")
        access_token = request.data.get("access_token")

        if not provider or not access_token:
            return Response(
                {"error": "Provider and access_token are required"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        # Validate provider
        valid_providers = ["google", "facebook", "apple"]
        if provider not in valid_providers:
            return Response(
                {"error": f"Invalid provider. Must be one of: {', '.join(valid_providers)}"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        # For now, return a message indicating setup is needed
        # In production, this would:
        # 1. Validate access token with provider
        # 2. Get user email and profile info
        # 3. Create or link user account
        # 4. Generate and return JWT tokens

        return Response(
            {
                "message": "Social login requires additional setup",
                "instructions": "Install django-rest-framework-social-oauth2 and configure OAuth credentials"
            },
            status=status.HTTP_501_NOT_IMPLEMENTED,
        )

    @action(detail=False, methods=["post"], permission_classes=[IsAuthenticated])
    def enable_2fa(self, request):
        """
        Enable Two-Factor Authentication (TOTP).

        Note: Requires:
        - Install pyotp or django-otp
        - Add TOTP secret field to User model
        - Generate QR code for user to scan
        """
        # Check if user already has 2FA enabled
        if hasattr(request.user, 'two_factor_enabled') and request.user.two_factor_enabled:
            return Response(
                {"error": "2FA is already enabled for this account"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        # For now, return setup instructions
        # In production, this would:
        # 1. Generate a TOTP secret for the user
        # 2. Generate QR code for authenticator app
        # 3. Return secret and QR code URL

        return Response(
            {
                "message": "2FA requires additional setup",
                "instructions": "Install django-otp and add two_factor fields to User model"
            },
            status=status.HTTP_501_NOT_IMPLEMENTED,
        )

    @action(detail=False, methods=["post"], permission_classes=[AllowAny])
    def verify_2fa(self, request):
        """
        Verify 2FA code during login.

        Expects:
            user_id: User ID
            code: 6-digit TOTP code
        """
        user_id = request.data.get("user_id")
        code = request.data.get("code")

        if not user_id or not code:
            return Response(
                {"error": "user_id and code are required"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        # For now, return setup instructions
        # In production, this would:
        # 1. Verify TOTP code using pyotp
        # 2. Generate and return JWT tokens if valid

        return Response(
            {
                "message": "2FA requires additional setup",
                "instructions": "Install django-otp and configure TOTP verification"
            },
            status=status.HTTP_501_NOT_IMPLEMENTED,
        )

    @action(detail=False, methods=["post"], permission_classes=[IsAuthenticated])
    def disable_2fa(self, request):
        """
        Disable Two-Factor Authentication.

        Requires password confirmation for security.
        """
        password = request.data.get("password")
        if not password:
            return Response(
                {"error": "Password is required to disable 2FA"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        if not request.user.check_password(password):
            return Response(
                {"error": "Incorrect password"},
                status=status.HTTP_401_UNAUTHORIZED,
            )

        # For now, return setup instructions
        # In production, this would:
        # 1. Clear user's TOTP secret
        # 2. Set two_factor_enabled to False

        return Response(
            {
                "message": "2FA requires additional setup",
                "instructions": "Install django-otp and add two_factor fields to User model"
            },
            status=status.HTTP_501_NOT_IMPLEMENTED,
        )
