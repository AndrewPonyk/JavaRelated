#!/usr/bin/env bash
# Generates a local CA + SAN leaf cert for the TLS 1.3 demo edge.
# Run from repo root: ./scripts/gen_dev_certs.sh   (bash / Git-Bash / WSL)
set -euo pipefail

OUT="nginx/tls"
mkdir -p "$OUT"

echo "==> CA key + self-signed cert"
openssl req -x509 -newkey rsa:2048 -nodes -days 30 \
  -keyout "$OUT/ca.key" -out "$OUT/ca.crt" \
  -subj "/CN=CryptoToolkit Dev CA"

echo "==> Leaf key + CSR (SAN: localhost + 127.0.0.1)"
openssl req -newkey rsa:2048 -nodes \
  -keyout "$OUT/server.key" -out "$OUT/server.csr" \
  -subj "/CN=localhost"

cat > "$OUT/san.ext" <<'EOF'
subjectAltName=DNS:localhost,IP:127.0.0.1
extendedKeyUsage=serverAuth
keyUsage=digitalSignature,keyEncipherment
EOF

echo "==> Sign leaf with CA"
openssl x509 -req -in "$OUT/server.csr" -CA "$OUT/ca.crt" -CAkey "$OUT/ca.key" \
  -CAcreateserial -days 30 -sha256 -extfile "$OUT/san.ext" -out "$OUT/server.crt"

chmod 600 "$OUT/server.key" "$OUT/ca.key"
echo "Done. Trust $OUT/ca.crt in your browser, then: docker compose up --build"
