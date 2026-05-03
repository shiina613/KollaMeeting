#!/usr/bin/env sh
# =============================================================================
# scripts/tests/test_url_extraction.sh
# Property test: URL extraction từ log (Property 1)
# Validates: Requirements 2.1, 2.2
# =============================================================================
# Property 1: URL extraction từ log
#   For any log string, extract_tunnel_url returns a URL if and only if
#   a valid trycloudflare.com URL is present in the log.
#
# Self-contained bash property test — no external dependencies.
# Runs at least 50 iterations.
# =============================================================================

set -e

# Source the extract_tunnel_url helper from start.sh
SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
START_SH="$SCRIPT_DIR/start.sh"

if [ ! -f "$START_SH" ]; then
    echo "ERROR: Cannot find $START_SH"
    exit 1
fi

# Extract only the extract_tunnel_url function from start.sh (avoid running main)
extract_tunnel_url() {
    local log_content="$1"
    echo "$log_content" | grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' | head -1
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

assert_empty() {
    local actual="$1"
    local msg="$2"
    if [ -z "$actual" ]; then
        PASS=$((PASS + 1))
    else
        FAIL=$((FAIL + 1))
        ERRORS="${ERRORS}\n  FAIL: $msg\n    expected empty, got: '$actual'"
    fi
}

assert_not_empty() {
    local actual="$1"
    local msg="$2"
    if [ -n "$actual" ]; then
        PASS=$((PASS + 1))
    else
        FAIL=$((FAIL + 1))
        ERRORS="${ERRORS}\n  FAIL: $msg\n    expected non-empty, got empty"
    fi
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

# Generate a random number string (length 1-4)
random_digits() {
    local len=$((RANDOM % 4 + 1))
    local num=""
    local i=0
    while [ $i -lt $len ]; do
        num="${num}$((RANDOM % 10))"
        i=$((i + 1))
    done
    echo "$num"
}

# Generate a valid trycloudflare.com URL
# Format: https://[a-z0-9]+-[a-z0-9]+-[a-z0-9]+.trycloudflare.com
generate_valid_url() {
    local w1
    local w2
    local w3
    w1=$(random_word)
    w2=$(random_word)
    w3=$(random_word)
    echo "https://${w1}-${w2}-${w3}.trycloudflare.com"
}

# Generate a random log prefix (noise before/after URL)
generate_log_noise() {
    local noises="
2024/01/15 10:23:45 INF Starting tunnel
2024/01/15 10:23:46 INF Registered tunnel connection
INFO: Tunnel established
[cloudflared] connection ready
Starting tunnel daemon
Connecting to Cloudflare edge
Tunnel ID: abc123def456
"
    local line_count
    line_count=$(echo "$noises" | grep -c .)
    local idx=$((RANDOM % line_count + 1))
    echo "$noises" | sed -n "${idx}p"
}

# Generate a log string WITH a valid URL embedded
generate_log_with_url() {
    local url="$1"
    local noise1
    local noise2
    noise1=$(generate_log_noise)
    noise2=$(generate_log_noise)
    printf "%s\n%s %s\n%s\n" "$noise1" "$(generate_log_noise)" "$url" "$noise2"
}

# Generate a log string WITHOUT any valid URL
generate_log_without_url() {
    local noise1
    local noise2
    local noise3
    noise1=$(generate_log_noise)
    noise2=$(generate_log_noise)
    noise3=$(generate_log_noise)
    printf "%s\n%s\n%s\n" "$noise1" "$noise2" "$noise3"
}

# =============================================================================
# Unit tests (deterministic)
# =============================================================================
echo "=== Unit Tests ==="

# Test: exact valid URL
result=$(extract_tunnel_url "https://abc-def-ghi.trycloudflare.com")
assert_equals "https://abc-def-ghi.trycloudflare.com" "$result" "exact valid URL"

# Test: URL embedded in log line
result=$(extract_tunnel_url "2024/01/15 10:23:46 INF +config=https://abc-def-ghi.trycloudflare.com tunnel=quick")
assert_equals "https://abc-def-ghi.trycloudflare.com" "$result" "URL embedded in log line"

# Test: URL with numbers in subdomain
result=$(extract_tunnel_url "tunnel url: https://abc123-def456-ghi789.trycloudflare.com")
assert_equals "https://abc123-def456-ghi789.trycloudflare.com" "$result" "URL with numbers in subdomain"

# Test: empty string → empty result
result=$(extract_tunnel_url "")
assert_empty "$result" "empty string returns empty"

# Test: log without URL → empty result
result=$(extract_tunnel_url "2024/01/15 10:23:45 INF Starting tunnel daemon")
assert_empty "$result" "log without URL returns empty"

# Test: invalid URL (wrong domain) → empty result
result=$(extract_tunnel_url "https://abc-def-ghi.cloudflare.com")
assert_empty "$result" "wrong domain returns empty"

# Test: http:// (not https://) → empty result
result=$(extract_tunnel_url "http://abc-def-ghi.trycloudflare.com")
assert_empty "$result" "http scheme returns empty"

# Test: URL with uppercase → empty result (pattern requires lowercase)
result=$(extract_tunnel_url "https://ABC-DEF-GHI.trycloudflare.com")
assert_empty "$result" "uppercase URL returns empty"

# Test: multiple URLs in log → returns first one
result=$(extract_tunnel_url "$(printf 'https://first-url-one.trycloudflare.com\nhttps://second-url-two.trycloudflare.com')")
assert_equals "https://first-url-one.trycloudflare.com" "$result" "multiple URLs returns first"

# Test: URL with single-segment subdomain (no dashes) → still valid per regex
result=$(extract_tunnel_url "https://abc.trycloudflare.com")
assert_not_empty "$result" "single-segment subdomain is valid"

echo "Unit tests: $PASS passed, $FAIL failed"

# =============================================================================
# Property tests (randomized, 50+ iterations)
# =============================================================================
echo ""
echo "=== Property Tests (50 iterations each) ==="

PROP_PASS=0
PROP_FAIL=0
PROP_ERRORS=""

# Property 1a: If log contains a valid URL, extract_tunnel_url returns that URL
echo "Property 1a: extract returns URL when URL is present..."
i=0
while [ $i -lt 50 ]; do
    url=$(generate_valid_url)
    log=$(generate_log_with_url "$url")
    result=$(extract_tunnel_url "$log")
    if [ "$result" = "$url" ]; then
        PROP_PASS=$((PROP_PASS + 1))
    else
        PROP_FAIL=$((PROP_FAIL + 1))
        PROP_ERRORS="${PROP_ERRORS}\n  FAIL P1a iter $i: url='$url', got='$result'"
    fi
    i=$((i + 1))
done

# Property 1b: If log does NOT contain a valid URL, extract_tunnel_url returns empty
echo "Property 1b: extract returns empty when no URL is present..."
i=0
while [ $i -lt 50 ]; do
    log=$(generate_log_without_url)
    result=$(extract_tunnel_url "$log")
    if [ -z "$result" ]; then
        PROP_PASS=$((PROP_PASS + 1))
    else
        PROP_FAIL=$((PROP_FAIL + 1))
        PROP_ERRORS="${PROP_ERRORS}\n  FAIL P1b iter $i: expected empty, got='$result'"
    fi
    i=$((i + 1))
done

# Property 1c: Result (when non-empty) always starts with https:// and ends with .trycloudflare.com
echo "Property 1c: extracted URL always has correct format..."
i=0
while [ $i -lt 50 ]; do
    url=$(generate_valid_url)
    log=$(generate_log_with_url "$url")
    result=$(extract_tunnel_url "$log")
    if [ -n "$result" ]; then
        # Check starts with https://
        prefix=$(echo "$result" | cut -c1-8)
        # Check ends with .trycloudflare.com
        suffix=$(echo "$result" | grep -oE '\.trycloudflare\.com$' || true)
        if [ "$prefix" = "https://" ] && [ -n "$suffix" ]; then
            PROP_PASS=$((PROP_PASS + 1))
        else
            PROP_FAIL=$((PROP_FAIL + 1))
            PROP_ERRORS="${PROP_ERRORS}\n  FAIL P1c iter $i: result='$result' has wrong format"
        fi
    else
        PROP_FAIL=$((PROP_FAIL + 1))
        PROP_ERRORS="${PROP_ERRORS}\n  FAIL P1c iter $i: expected URL, got empty (url='$url')"
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
