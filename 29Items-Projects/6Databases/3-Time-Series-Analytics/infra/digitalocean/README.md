# DigitalOcean Deployment

Compose-first topology (docs/TECH-NOTES.md §3.3). DOKS is the growth path.

## Staging (single droplet)

| Item | Value |
|---|---|
| Droplet | 4 vCPU / 8 GB (Cassandra alone wants ~4 GB) |
| OS | Ubuntu 24.04 LTS |
| Network | VPC enabled; only 80/443 + SSH (key-only) in the cloud firewall |
| Storage | attached block storage volume for `/var/lib/docker` |

Bootstrap once:

```bash
scp infra/digitalocean/droplet-setup.sh root@<droplet-ip>:/tmp/
ssh root@<droplet-ip> 'bash /tmp/droplet-setup.sh'
# then copy compose files + a server .env into /opt/tsa (deploy pipeline keeps
# IMAGE_TAG updated; secrets are provisioned here once, never via git)
```

After bootstrap, `deploy:staging` in GitLab CI does everything else over SSH.

## Production (grown-up layout)

- 1–2 **app droplets** (backend, workers, frontend) behind a **DO Load Balancer**
  (TLS termination, health check → `/api/v1/health/ready`)
- **Cassandra** on dedicated droplet(s) + block storage, VPC-only
- **DO Managed Redis** (drop-in via `REDIS_URL`)
- InfluxDB + Grafana on a small utility droplet, Grafana exposed via the LB

TODO: capture this as Terraform (droplets, VPC, firewall, LB) once the
topology stops moving; until then this README is the source of truth.
