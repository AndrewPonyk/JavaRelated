# Runbook — Production Promotion & Rollback (order-service)

Audience: on-call / release engineer. Everything here is copy-paste ready.
Prereqs: `argocd` CLI logged in, `kubectl` context on the EKS cluster, repo cloned.

## Promotion procedure (normal path)

1. **Pick the candidate** — the SHA currently running (and verified) on staging:
   ```bash
   yq '.image.tag' deploy/helm/order-service/values-staging.yaml
   ```
2. **Dispatch** `CD — promote to production` (GitHub → Actions) with that `image_tag`.
   - The **risk gate** blocks scores ≥ 0.70 (`override_risk` bypasses — it is logged).
   - A **required reviewer** approves the `production` environment.
   - The workflow commits the values bump and creates a `release-*` tag.
3. **Sync** (the deliberate second key for prod):
   ```bash
   argocd app sync order-service-production
   argocd app wait order-service-production --health --timeout 600
   ```
4. **Verify:**
   ```bash
   BASE_URL=https://orders.example.com bash scripts/smoke-test.sh
   ```
   Watch the Grafana "Order Service — Overview" dashboard for ~15 minutes
   (5xx ratio < 5%, p95 < 500 ms — the alert thresholds).

### High-risk releases (risk score ≥ 0.6): blue-green

Flip `blueGreen.enabled: true` in `values-production.yaml` **in the same promotion
commit** (workload object is replaced — only flip at a release boundary). After sync:

```bash
kubectl argo rollouts -n orders-production get rollout order-service --watch
# green stack up → verify through the preview Service:
kubectl -n orders-production port-forward svc/order-service-preview 8081:80
BASE_URL=http://localhost:8081 bash scripts/smoke-test.sh
# happy → flip traffic;  unhappy → abort (instant traffic-back)
kubectl argo rollouts -n orders-production promote order-service
kubectl argo rollouts -n orders-production abort order-service
```

## Rollback decision tree (fastest first)

| Situation | Action | Time to recover |
|---|---|---|
| Blue-green not yet promoted | `kubectl argo rollouts abort order-service` | seconds |
| Bad rolling deploy, just synced | `argocd app rollback order-service-production` (previous rendered manifests) | ~1 min |
| Anything else / keep Git truthful | `git revert <values-bump-commit> && git push`, then `argocd app sync order-service-production` | ~3 min |

Images are immutable SHAs — rollback never rebuilds anything.

After any rollback: re-run the smoke test, then open an incident note that records
the reverted SHA — `tools/risk-score/data` wants the `failed=1` label (model training).

## Database caveats

- Migrations are **expand/contract**; rolling code back does NOT require rolling the
  schema back — the previous release runs fine on the newer schema. Never write
  "down" migrations.
- Data-corruption incidents (not schema): RDS point-in-time recovery to a new
  instance, repoint via the `SPRING_DATASOURCE_URL` ConfigMap value, sync. This is a
  data-loss decision — page the service owner first.

## If ArgoCD itself is unhealthy

Desired state is still Git. Emergency manual apply:
```bash
helm template order-service deploy/helm/order-service \
  -f deploy/helm/order-service/values.yaml -f deploy/helm/order-service/values-production.yaml \
  | kubectl -n orders-production apply -f -
```
Reconcile ArgoCD afterwards (it will show drift until it syncs the same commit).
