"""User views for E-Commerce Platform."""
from django.contrib.auth import get_user_model
from rest_framework import generics, permissions, status
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.viewsets import ModelViewSet
from rest_framework_simplejwt.views import TokenObtainPairView

from .models import Address, WishlistItem
from .serializers import (
    AddressSerializer,
    CustomTokenObtainPairSerializer,
    PasswordChangeSerializer,
    UserRegistrationSerializer,
    UserSerializer,
    WishlistItemSerializer,
)

User = get_user_model()


class CustomTokenObtainPairView(TokenObtainPairView):
    """Custom JWT token view."""
    serializer_class = CustomTokenObtainPairSerializer


class UserRegistrationView(generics.CreateAPIView):
    """View for user registration."""
    queryset = User.objects.all()
    permission_classes = [permissions.AllowAny]
    serializer_class = UserRegistrationSerializer


class UserProfileView(generics.RetrieveUpdateAPIView):
    """View for retrieving and updating user profile."""
    serializer_class = UserSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_object(self):
        return self.request.user


class PasswordChangeView(generics.UpdateAPIView):
    """View for changing user password."""
    serializer_class = PasswordChangeSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_object(self):
        return self.request.user

    def update(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        user = self.get_object()
        user.set_password(serializer.validated_data['new_password'])
        user.save()

        return Response(
            {'message': 'Password updated successfully.'},
            status=status.HTTP_200_OK
        )


class AddressViewSet(ModelViewSet):
    """ViewSet for managing user addresses."""
    serializer_class = AddressSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return Address.objects.filter(user=self.request.user)

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)

    @action(detail=True, methods=['post'])
    def set_default(self, request, pk=None):
        """Set an address as the default for its type."""
        address = self.get_object()
        address.is_default = True
        address.save()
        return Response(
            {'message': f'Address set as default {address.address_type}.'},
            status=status.HTTP_200_OK
        )


class WishlistViewSet(ModelViewSet):
    """ViewSet for managing user wishlist."""
    serializer_class = WishlistItemSerializer
    permission_classes = [permissions.IsAuthenticated]
    http_method_names = ['get', 'post', 'delete']

    def get_queryset(self):
        return WishlistItem.objects.filter(user=self.request.user).select_related('product')

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)

    def create(self, request, *args, **kwargs):
        """Add product to wishlist (handle duplicates gracefully)."""
        product_id = request.data.get('product_id')
        if WishlistItem.objects.filter(user=request.user, product_id=product_id).exists():
            return Response(
                {'message': 'Product already in wishlist.'},
                status=status.HTTP_200_OK
            )
        return super().create(request, *args, **kwargs)

    @action(detail=False, methods=['get'])
    def check(self, request):
        """Check if a product is in the wishlist."""
        product_id = request.query_params.get('product_id')
        if not product_id:
            return Response(
                {'error': 'product_id is required.'},
                status=status.HTTP_400_BAD_REQUEST
            )
        exists = WishlistItem.objects.filter(
            user=request.user, product_id=product_id
        ).exists()
        return Response({'in_wishlist': exists})

    @action(detail=False, methods=['delete'])
    def remove_by_product(self, request):
        """Remove a product from wishlist by product_id."""
        product_id = request.query_params.get('product_id')
        if not product_id:
            return Response(
                {'error': 'product_id is required.'},
                status=status.HTTP_400_BAD_REQUEST
            )
        deleted, _ = WishlistItem.objects.filter(
            user=request.user, product_id=product_id
        ).delete()
        if deleted:
            return Response(status=status.HTTP_204_NO_CONTENT)
        return Response(
            {'error': 'Product not in wishlist.'},
            status=status.HTTP_404_NOT_FOUND
        )
