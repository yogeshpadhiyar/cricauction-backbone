#!/usr/bin/env bash
# ==============================================================================
# CricAuction Automation Task: 5,000 Concurrent Users Module 4 (Tournaments)
# Targets Actual Local PostgreSQL Database (jdbc:postgresql://localhost:5432/cricauction)
# Database entries are KEPT INTACT for cross-verification.
# ==============================================================================

set -e

# Set OpenJDK 23
if [ -d "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
elif [ -d "/opt/homebrew/opt/openjdk" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "================================================================================"
echo " CricAuction Backbone: 5,000 Concurrent Operations (Module 4: Tournaments)"
echo "================================================================================"
echo "Target DB: cricauction (PostgreSQL on localhost:5432)"
echo "Java: $(java -version 2>&1 | head -n 1)"
echo "Mode: Actual Database Transactions (Data will NOT be cleared)"
echo "Simulating 5,000 Concurrent Operations:"
echo "  - 1,250 Users: POST /tournaments (Create Tournament Draft - Screen 16)"
echo "  - 1,250 Users: PUT /tournaments/{id}/settings (Configure Rules & Purse - Screen 17)"
echo "  - 1,250 Users: POST /tournaments/{id}/publish (Publish & Generate Share Links)"
echo "  - 1,250 Users: GET /tournaments/public/{slug} (High-Speed L1 Cache Landing Page Read)"
echo "================================================================================"
echo ""

mvn test -Dtest=TournamentConcurrency5000UsersActualDbTest

echo ""
echo "================================================================================"
echo " Verification: Querying Actual PostgreSQL Database (Module 4 Tables)"
echo "================================================================================"
psql -U $(whoami) -d cricauction -c "SELECT COUNT(*) AS total_persisted_tournaments FROM tournaments;"
psql -U $(whoami) -d cricauction -c "SELECT COUNT(*) AS total_persisted_settings FROM tournament_settings;"
psql -U $(whoami) -d cricauction -c "SELECT status, count(*) FROM tournaments GROUP BY status;"
echo ""
echo "--- Sample Published Tournaments ---"
psql -U $(whoami) -d cricauction -c "SELECT public_id, name, slug, status, step, registration_link FROM tournaments WHERE status = 'PUBLISHED' ORDER BY id DESC LIMIT 5;"
echo ""
echo "--- Sample Tournament Rules & Settings ---"
psql -U $(whoami) -d cricauction -c "SELECT tournament_id, total_teams, squad_minimum, squad_maximum, team_budget, default_base_price, bid_increment, bid_timer_seconds FROM tournament_settings ORDER BY tournament_id DESC LIMIT 5;"
echo "================================================================================"
echo " Automation Task Completed Successfully! All data preserved in PostgreSQL."
echo "================================================================================"
