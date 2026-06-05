#!/usr/bin/env bash
# Creates secrets/signing.p12 for local PDF digital signature testing.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SECRETS_DIR="$REPO_ROOT/secrets"
P12="$SECRETS_DIR/signing.p12"
PASSWORD="kolla-signing-dev"

mkdir -p "$SECRETS_DIR"
if [[ -f "$P12" ]]; then
  echo "Keystore already exists: $P12"
  exit 0
fi

command -v keytool >/dev/null 2>&1 || { echo "keytool not found (install JDK 17+)"; exit 1; }

keytool -genkeypair \
  -alias kolla-signing \
  -keyalg RSA \
  -keysize 2048 \
  -sigalg SHA256withRSA \
  -validity 3650 \
  -storetype PKCS12 \
  -keystore "$P12" \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -dname "CN=Kolla Meeting Dev Signer, OU=Dev, O=KollaMeeting, L=Hanoi, C=VN"

echo "Created $P12"
echo "DIGITAL_SIGNATURE_ENABLED=true"
echo "DIGITAL_SIGNATURE_KEYSTORE_PASSWORD=$PASSWORD"
