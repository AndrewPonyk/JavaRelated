"""Notification API — users see only their own notifications."""
from __future__ import annotations

from rest_framework import mixins, permissions, viewsets
from rest_framework.decorators import action
from rest_framework.request import Request
from rest_framework.response import Response

from .models import Notification
from .serializers import NotificationSerializer


class NotificationViewSet(
    mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet
):
    serializer_class = NotificationSerializer
    permission_classes = [permissions.IsAuthenticated]
    queryset = Notification.objects.all()  # for schema introspection; filtered below

    def get_queryset(self):
        qs = Notification.objects.filter(recipient=self.request.user)
        unread = self.request.query_params.get("unread")
        if unread and unread.lower() == "true":
            qs = qs.filter(read=False)
        return qs

    @action(detail=True, methods=["post"], url_path="mark-read")
    def mark_read(self, request: Request, pk: str | None = None) -> Response:
        notification = self.get_object()
        notification.read = True
        notification.save(update_fields=["read"])
        return Response(self.get_serializer(notification).data)

    @action(detail=False, methods=["post"], url_path="mark-all-read")
    def mark_all_read(self, request: Request) -> Response:
        count = self.get_queryset().filter(read=False).update(read=True)
        return Response({"marked_read": count})
