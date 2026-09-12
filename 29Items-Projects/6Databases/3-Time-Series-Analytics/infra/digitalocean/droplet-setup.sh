#!/usr/bin/env bash
# One-time DigitalOcean droplet bootstrap (Ubuntu 24.04).
# Usage: bash droplet-setup.sh
set -euo pipefail

echo "==> docker engine + compose plugin"
apt-get update -y
apt-get install -y ca-certificates curl gnupg
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

echo "==> deploy user + app directory"
id -u deploy &>/dev/null || useradd -m -s /bin/bash -G docker deploy
mkdir -p /opt/tsa
chown deploy:deploy /opt/tsa
# TODO: add the GitLab CI public key to /home/deploy/.ssh/authorized_keys

echo "==> basic hardening"
# TODO: ufw allow only 22/80/443 (or rely on the DO cloud firewall), fail2ban,
#       unattended-upgrades, disable password SSH auth.

cat <<'EOF'
Done. Next steps (manual, once):
  1. Copy docker-compose.yml + docker-compose.prod.yml into /opt/tsa
  2. Create /opt/tsa/.env from .env.example with real secrets + IMAGE_TAG
  3. Copy backend/migrations + scripts/apply_migrations.sh, run migrations
  4. Let the GitLab deploy job take over (ci/deploy.gitlab-ci.yml)
EOF
