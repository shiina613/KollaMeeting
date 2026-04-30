#!/usr/bin/env bash
# =============================================================================
# check_migration_integrity.sh
# Kolla Meeting Rebuild — Migration Integrity Verification Runner
# Requirements: 17.6
#
# Usage:
#   ./backend/scripts/check_migration_integrity.sh [OPTIONS]
#
# Options:
#   -h HOST       MySQL host          (default: localhost)
#   -P PORT       MySQL port          (default: 3306)
#   -u USER       MySQL user          (default: $MYSQL_USER or kolla)
#   -p PASSWORD   MySQL password      (default: $MYSQL_PASSWORD or kollapass)
#   -d DATABASE   Database name       (default: $MYSQL_DATABASE or kolla_meeting)
#   --docker      Connect via docker-compose exec mysql (ignores host/port)
#   --help        Show this help
#
# Exit codes:
#   0  All checks passed
#   1  One or more checks failed
#   2  Could not connect to MySQL
# =============================================================================

set -euo pipefail

# ─── Defaults (overridable via env or flags) ──────────────────────────────────
DB_HOST="${MYSQL_HOST:-localhost}"
DB_PORT="${MYSQL_PORT:-3306}"
DB_USER="${MYSQL_USER:-kolla}"
DB_PASS="${MYSQL_PASSWORD:-kollapass}"
DB_NAME="${MYSQL_DATABASE:-kolla_meeting}"
USE_DOCKER=false

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/../src/main/resources/db/migration/check_migration_integrity.sql"

# ─── Colour helpers ───────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Colour

info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[PASS]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[FAIL]${NC}  $*" >&2; }

# ─── Argument parsing ─────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        -h) DB_HOST="$2";   shift 2 ;;
        -P) DB_PORT="$2";   shift 2 ;;
        -u) DB_USER="$2";   shift 2 ;;
        -p) DB_PASS="$2";   shift 2 ;;
        -d) DB_NAME="$2";   shift 2 ;;
        --docker) USE_DOCKER=true; shift ;;
        --help)
            sed -n '2,30p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *) error "Unknown option: $1"; exit 1 ;;
    esac
done

# ─── Resolve SQL file path ────────────────────────────────────────────────────
if [[ ! -f "$SQL_FILE" ]]; then
    error "SQL script not found: $SQL_FILE"
    exit 1
fi

info "Migration integrity check starting"
info "Database : ${DB_NAME}@${DB_HOST}:${DB_PORT}"
info "SQL file : ${SQL_FILE}"
echo ""

# ─── Build mysql command ──────────────────────────────────────────────────────
run_sql() {
    if [[ "$USE_DOCKER" == "true" ]]; then
        docker compose exec -T mysql \
            mysql -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" "$@"
    else
        mysql \
            -h "${DB_HOST}" \
            -P "${DB_PORT}" \
            -u "${DB_USER}" \
            -p"${DB_PASS}" \
            "${DB_NAME}" \
            --connect-timeout=10 \
            "$@"
    fi
}

# ─── Connectivity check ───────────────────────────────────────────────────────
info "Testing database connectivity..."
if ! run_sql -e "SELECT 1" > /dev/null 2>&1; then
    error "Cannot connect to MySQL. Check credentials and host."
    exit 2
fi
success "Connected to MySQL"
echo ""

# ─── Run the integrity SQL ────────────────────────────────────────────────────
REPORT_FILE="/tmp/kolla_migration_integrity_$(date +%Y%m%d_%H%M%S).txt"

info "Running integrity checks..."
run_sql \
    --table \
    --verbose \
    < "$SQL_FILE" \
    | tee "$REPORT_FILE"

echo ""
info "Full report saved to: ${REPORT_FILE}"
echo ""

# ─── Parse results for FAIL lines ────────────────────────────────────────────
FAIL_COUNT=$(grep -c '\bFAIL\b' "$REPORT_FILE" || true)
PASS_COUNT=$(grep -c '\bPASS\b' "$REPORT_FILE" || true)
MISSING_COUNT=$(grep -c '\bMISSING\b' "$REPORT_FILE" || true)

echo "────────────────────────────────────────────────────────"
echo "  PASS  : ${PASS_COUNT}"
echo "  FAIL  : ${FAIL_COUNT}"
echo "  MISSING tables: ${MISSING_COUNT}"
echo "────────────────────────────────────────────────────────"

if [[ "$FAIL_COUNT" -gt 0 || "$MISSING_COUNT" -gt 0 ]]; then
    echo ""
    error "Migration integrity check FAILED (${FAIL_COUNT} failures, ${MISSING_COUNT} missing tables)"
    echo ""
    echo "Failed checks:"
    grep -E '\bFAIL\b|\bMISSING\b' "$REPORT_FILE" || true
    exit 1
else
    echo ""
    success "All migration integrity checks PASSED"
    exit 0
fi
