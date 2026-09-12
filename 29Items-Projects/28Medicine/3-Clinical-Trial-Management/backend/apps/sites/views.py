from __future__ import annotations

from apps.common.viewsets import BaseModelViewSet

from .models import DelegationLog, Site
from .serializers import DelegationLogSerializer, SiteSerializer


class SiteViewSet(BaseModelViewSet):
    serializer_class = SiteSerializer
    queryset = Site.objects.select_related("principal_investigator").all()


class DelegationLogViewSet(BaseModelViewSet):
    serializer_class = DelegationLogSerializer

    def get_queryset(self):
        qs = DelegationLog.objects.select_related("site", "user").all()
        if site := self.request.query_params.get("site"):
            qs = qs.filter(site__public_id=site)
        return qs
