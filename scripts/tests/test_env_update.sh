#!/usr/bin/env sh
# =============================================================================
# scripts/tests/test_env_update.sh
# Property tests: .env update (Property 2 and 3)
# Validates: Requirements 3.1, 3.2, 7.2, 7.3, 8.4
# =============================================================================
# Property 2: .env update không phá vỡ các biến khác
#   For any valid .env file, after updating VITE_API_BASE_URL, VITE_WS_URL,
#   and CORS_ALLOWED_ORIGINS, all other variables retain their original values.
#
# Property 3: Derived URLs có scheme đúng
#   For any valid tunnel URL https://xxx.trycloudflare.com:
#   - VITE_API_BASE_URL starts with https:// and ends with /api/v1
#   - VITE_WS_URL starts with wss:// and ends with /ws
#   - CORS_ALLOWED_ORIGINS equals exactly the tunnel URL
#
# Self-contained sh property test — no external dependencies.
# Runs at least 50 iterations.
# =============================================================================

set -e

# =============================================================================
# Inline the update_env_file logic for testing
# (mirrors the logic in scripts/start.sh)
# =============================================================================
apply_env_update() {
    local env_file="$1"
    local tunnel_url="$2"

    local api_url="${tunnel_url}/api/v1"
    local ws_url
    ws_url=$(echo "$tunnel_url" | sed 's|^https://|wss://|')/ws
    local cors_url="$tunnel_url"

    sed -i.bak "s|^VITE_API_BASE_URL=.*|VITE_API_BASE_URL=${api_url}|" "$env_file"
    sed -i.bak "s|^VITE_WS_URL=.*|VITE_WS_URL=${ws_url}|" "$env_file"
    sed -i.bak "s|^CORS_ALLOWED_ORIGINS=.*|CORS_ALLOWED_ORIGINS=${cors_url}|" "$env_file"
    rm -f "${env_file}.bak"
}

# =============================================================================
# Test helpers
# =============================================================================
PASS=0
FAIL=0
ERRORS=""

assert_equals() {
    local expected="$1"
    local actual="$2"
    local msg="$3"
    if [ "$expected" = "$actual" ]; then
        PASS=$((PASS + 1))
    else
        FAIL=$((FAIL + 1))
        ERRORS="${ERRORS}\n  FAIL: $msg\n    expected: '$expected'\n    actual:   '$actual'"
    fi
}

assert_starts_with() {
    local prefix="$1"
    local actual="$2"
    local msg="$3"
    case "$actual" in
        "$prefix"*)
            PASS=$((PASS + 1))
            ;;
        *)
            FAIL=$((FAIL + 1))
            ERRORS="${ERRORS}\n  FAIL: $msg\n    expected prefix: '$prefix'\n    actual: '$actual'"
            ;;
    esac
}

assert_ends_with() {
    local suffix="$1"
    local actual="$2"
    local msg="$3"
    case "$actual" in
        *"$suffix")
            PASS=$((PASS + 1))
            ;;
        *)
            FAIL=$((FAIL + 1))
            ERRORS="${ERRORS}\n  FAIL: $msg\n    expected suffix: '$suffix'\n    actual: '$actual'"
            ;;
    esac
}

# =============================================================================
# Generators
# =============================================================================

# Generate a random word of lowercase letters (length 3-8)
random_word() {
    local chars="abcdefghijklmnopqrstuvwxyz"
    local len=$((RANDOM % 6 + 3))
    local word=""
    local i=0
    while [ $i -lt $len ]; do
        local idx=$((RANDOM % 26))
        word="${word}$(echo "$chars" | cut -c$((idx + 1)))"
        i=$((i + 1))
    done
    echo "$word"
}

# Generate a valid trycloudflare.com URL
generate_valid_tunnel_url() {
    local w1
    local w2
    local w3
    w1=$(random_word)
    w2=$(random_word)
    w3=$(random_word)
    echo "https://${w1}-${w2}-${w3}.trycloudflare.com"
}

# Generate a random safe value for an env variable (no special chars that break sed)
random_env_value() {
    local words="hello world foo bar baz qux test value example data config secret key token"
    local word_count
    word_count=$(echo "$words" | wc -w)
    local idx=$((RANDOM % word_count + 1))
    echo "$words" | tr ' ' '\n' | sed -n "${idx}p"
}

# Generate a random env key (uppercase letters and underscores)
random_env_key() {
    local keys="DB_HOST DB_PORT APP_NAME LOG_LEVEL DEBUG_MODE REDIS_HOST REDIS_PORT JWT_SECRET APP_PORT FEATURE_FLAG"
    local key_count
    key_count=$(echo "$keys" | wc -w)
    local idx=$((RANDOM % key_count + 1))
    echo "$keys" | tr ' ' '\n' | sed -n "${idx}p"
}

# Generate a random .env file with some non-target variables
# Always includes VITE_API_BASE_URL, VITE_WS_URL, CORS_ALLOWED_ORIGINS as targets
generate_env_file() {
    local tmpfile="$1"
    local num_extra=$((RANDOM % 5 + 2))

    # Write target variables with placeholder values
    printf "VITE_API_BASE_URL=https://old-url-placeholder.trycloudflare.com/api/v1\n" > "$tmpfile"
    printf "VITE_WS_URL=wss://old-url-placeholder.trycloudflare.com/ws\n" >> "$tmpfile"
    printf "CORS_ALLOWED_ORIGINS=https://old-url-placeholder.trycloudflare.com\n" >> "$tmpfile"

    # Write random non-target variables
    local i=0
    while [ $i -lt $num_extra ]; do
        local key
        local val
        key=$(random_env_key)
        val=$(random_env_value)
        # Avoid duplicate keys and target keys
        case "$key" in
            VITE_API_BASE_URL|VITE_WS_URL|CORS_ALLOWED_ORIGINS)
                ;;
            *)
                printf "%s=%s\n" "$key" "$val" >> "$tmpfile"
                ;;
        esac
        i=$((i + 1))
    done
}

# =============================================================================
# Unit tests (deterministic)
# =============================================================================
echo "=== Unit Tests ==="

TMPDIR_TEST=$(mktemp -d)
trap 'rm -rf "$TMPDIR_TEST"' EXIT

# Test: basic update of all three target variables
ENV_FILE="$TMPDIR_TEST/test_basic.env"
printf "VITE_API_BASE_URL=https://old.trycloudflare.com/api/v1\nVITE_WS_URL=wss://old.trycloudflare.com/ws\nCORS_ALLOWED_ORIGINS=https://old.trycloudflare.com\nOTHER_VAR=unchanged\n" > "$ENV_FILE"
apply_env_update "$ENV_FILE" "https://new-abc-def.trycloudflare.com"
result_api=$(grep "^VITE_API_BASE_URL=" "$ENV_FILE" | cut -d= -f2-)
result_ws=$(grep "^VITE_WS_URL=" "$ENV_FILE" | cut -d= -f2-)
result_cors=$(grep "^CORS_ALLOWED_ORIGINS=" "$ENV_FILE" | cut -d= -f2-)
result_other=$(grep "^OTHER_VAR=" "$ENV_FILE" | cut -d= -f2-)
assert_equals "https://new-abc-def.trycloudflare.com/api/v1" "$result_api" "basic: VITE_API_BASE_URL updated"
assert_equals "wss://new-abc-def.trycloudflare.com/ws" "$result_ws" "basic: VITE_WS_URL updated"
assert_equals "https://new-abc-def.trycloudflare.com" "$result_cors" "basic: CORS_ALLOWED_ORIGINS updated"
assert_equals "unchanged" "$result_other" "basic: OTHER_VAR not changed"

# Test: VITE_WS_URL scheme conversion https → wss
ENV_FILE2="$TMPDIR_TEST/test_scheme.env"
printf "VITE_API_BASE_URL=placeholder\nVITE_WS_URL=placeholder\nCORS_ALLOWED_ORIGINS=placeholder\n" > "$ENV_FILE2"
apply_env_update "$ENV_FILE2" "https://abc-def-ghi.trycloudflare.com"
result_ws2=$(grep "^VITE_WS_URL=" "$ENV_FILE2" | cut -d= -f2-)
assert_starts_with "wss://" "$result_ws2" "scheme: VITE_WS_URL starts with wss://"
assert_ends_with "/ws" "$result_ws2" "scheme: VITE_WS_URL ends with /ws"

# Test: CORS_ALLOWED_ORIGINS has no trailing slash or path
ENV_FILE3="$TMPDIR_TEST/test_cors.env"
printf "VITE_API_BASE_URL=placeholder\nVITE_WS_URL=placeholder\nCORS_ALLOWED_ORIGINS=placeholder\n" > "$ENV_FILE3"
apply_env_update "$ENV_FILE3" "https://xyz-abc-def.trycloudflare.com"
result_cors3=$(grep "^CORS_ALLOWED_ORIGINS=" "$ENV_FILE3" | cut -d= -f2-)
assert_equals "https://xyz-abc-def.trycloudflare.com" "$result_cors3" "cors: no trailing slash or path"

# Test: .env with comment lines — comments should be preserved
ENV_FILE4="$TMPDIR_TEST/test_comments.env"
printf "# This is a comment\nVITE_API_BASE_URL=old\nVITE_WS_URL=old\nCORS_ALLOWED_ORIGINS=old\n# Another comment\nDB_HOST=localhost\n" > "$ENV_FILE4"
apply_env_update "$ENV_FILE4" "https://test-url-one.trycloudflare.com"
comment_count=$(grep -c "^#" "$ENV_FILE4" || true)
assert_equals "2" "$comment_count" "comments: comment lines preserved"
db_host=$(grep "^DB_HOST=" "$ENV_FILE4" | cut -d= -f2-)
assert_equals "localhost" "$db_host" "comments: DB_HOST unchanged"

echo "Unit tests: $PASS passed, $FAIL failed"

# =============================================================================
# Property tests (randomized, 50+ iterations)
# =============================================================================
echo ""
echo "=== Property Tests (50 iterations each) ==="

PROP_PASS=0
PROP_FAIL=0
PROP_ERRORS=""

# Property 2: .env update không phá vỡ các biến khác
echo "Property 2: non-target variables retain original values..."
i=0
while [ $i -lt 50 ]; do
    ENV_FILE_P2="$TMPDIR_TEST/p2_iter_${i}.env"
    generate_env_file "$ENV_FILE_P2"

    # Capture non-target variables before update
    before=$(grep -v "^VITE_API_BASE_URL=\|^VITE_WS_URL=\|^CORS_ALLOWED_ORIGINS=" "$ENV_FILE_P2" || true)

    tunnel_url=$(generate_valid_tunnel_url)
    apply_env_update "$ENV_FILE_P2" "$tunnel_url"

    # Capture non-target variables after update
    after=$(grep -v "^VITE_API_BASE_URL=\|^VITE_WS_URL=\|^CORS_ALLOWED_ORIGINS=" "$ENV_FILE_P2" || true)

    if [ "$before" = "$after" ]; then
        PROP_PASS=$((PROP_PASS + 1))
    else
        PROP_FAIL=$((PROP_FAIL + 1))
        PROP_ERRORS="${PROP_ERRORS}\n  FAIL P2 iter $i: non-target vars changed\n    before: $before\n    after:  $after"
    fi
    i=$((i + 1))
done

# Property 3: Derived URLs có scheme đúng
echo "Property 3: derived URLs have correct schemes and paths..."
i=0
while [ $i -lt 50 ]; do
    ENV_FILE_P3="$TMPDIR_TEST/p3_iter_${i}.env"
    printf "VITE_API_BASE_URL=placeholder\nVITE_WS_URL=placeholder\nCORS_ALLOWED_ORIGINS=placeholder\n" > "$ENV_FILE_P3"

    tunnel_url=$(generate_valid_tunnel_url)
    apply_env_update "$ENV_FILE_P3" "$tunnel_url"

    api_url=$(grep "^VITE_API_BASE_URL=" "$ENV_FILE_P3" | cut -d= -f2-)
    ws_url=$(grep "^VITE_WS_URL=" "$ENV_FILE_P3" | cut -d= -f2-)
    cors_url=$(grep "^CORS_ALLOWED_ORIGINS=" "$ENV_FILE_P3" | cut -d= -f2-)

    iter_pass=1

    # P3a: VITE_API_BASE_URL starts with https://
    case "$api_url" in
        https://*) ;;
        *) iter_pass=0; PROP_ERRORS="${PROP_ERRORS}\n  FAIL P3a iter $i: VITE_API_BASE_URL='$api_url' does not start with https://" ;;
    esac

    # P3b: VITE_API_BASE_URL ends with /api/v1
    case "$api_url" in
        */api/v1) ;;
        *) iter_pass=0; PROP_ERRORS="${PROP_ERRORS}\n  FAIL P3b iter $i: VITE_API_BASE_URL='$api_url' does not end with /api/v1" ;;
    esac

    # P3c: VITE_WS_URL starts with wss://
    case "$ws_url" in
        wss://*) ;;
        *) iter_pass=0; PROP_ERRORS="${PROP_ERRORS}\n  FAIL P3c iter $i: VITE_WS_URL='$ws_url' does not start with wss://" ;;
    esac

    # P3d: VITE_WS_URL ends with /ws
    case "$ws_url" in
        */ws) ;;
        *) iter_pass=0; PROP_ERRORS="${PROP_ERRORS}\n  FAIL P3d iter $i: VITE_WS_URL='$ws_url' does not end with /ws" ;;
    esac

    # P3e: CORS_ALLOWED_ORIGINS equals exactly the tunnel URL
    if [ "$cors_url" = "$tunnel_url" ]; then
        :
    else
        iter_pass=0
        PROP_ERRORS="${PROP_ERRORS}\n  FAIL P3e iter $i: CORS_ALLOWED_ORIGINS='$cors_url' != tunnel_url='$tunnel_url'"
    fi

    if [ "$iter_pass" -eq 1 ]; then
        PROP_PASS=$((PROP_PASS + 1))
    else
        PROP_FAIL=$((PROP_FAIL + 1))
    fi

    i=$((i + 1))
done

echo "Property tests: $PROP_PASS passed, $PROP_FAIL failed"

# =============================================================================
# Summary
# =============================================================================
echo ""
echo "=== Summary ==="
TOTAL_PASS=$((PASS + PROP_PASS))
TOTAL_FAIL=$((FAIL + PROP_FAIL))
echo "Total: $TOTAL_PASS passed, $TOTAL_FAIL failed"

if [ -n "$ERRORS" ]; then
    echo ""
    echo "Unit test failures:"
    printf "%b\n" "$ERRORS"
fi

if [ -n "$PROP_ERRORS" ]; then
    echo ""
    echo "Property test failures:"
    printf "%b\n" "$PROP_ERRORS"
fi

if [ "$TOTAL_FAIL" -gt 0 ]; then
    echo ""
    echo "❌ Tests FAILED"
    exit 1
else
    echo ""
    echo "✅ All tests PASSED"
    exit 0
fi
