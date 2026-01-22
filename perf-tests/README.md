# Performance Tests

This directory contains performance testing infrastructure for the Stateless Orchestrator.

## Quick Start

```bash
# Run a load test locally
./scripts/run-perf-test-local.sh load

# View results
open perf-tests/results/load-test-report.html
```

## Directory Structure

```
perf-tests/
├── k6.config.js              # Shared k6 configuration and utilities
├── load-test.js              # Load test scenario
├── stress-test.js            # Stress test scenario
├── spike-test.js             # Spike test scenario
├── generate-test-data.js     # Test data generator
├── scripts/                  # Helper scripts
│   ├── generate-report.js   # HTML report generator
│   ├── analyze-results.sh    # Results analyzer
│   ├── capture-metrics.sh    # Metrics capture utility
│   └── monitor-queues-during-test.sh  # Queue monitoring
├── test-data/                # Generated test data files
├── results/                  # Test results (gitignored)
├── prometheus/               # Prometheus configuration
└── grafana/                  # Grafana configuration
```

## Test Scripts

### k6.config.js
Shared configuration and utilities:
- Custom metrics definitions
- Event generation functions
- Bulk event submission helpers
- Common thresholds

### load-test.js
Gradual ramp-up load test:
- 0 → 1000 VUs over 5 minutes
- 1000 VUs steady state for 10 minutes
- Target: 2000-5000 events/sec

### stress-test.js
Aggressive stress test:
- 0 → 5000 VUs over 10 minutes
- Finds breaking point
- Target: >10000 events/sec

### spike-test.js
Traffic spike test:
- Baseline → 10x spike → baseline (repeat)
- Validates recovery time

## Usage

See [PERFORMANCE_TESTING.md](../docs/PERFORMANCE_TESTING.md) for detailed documentation.

## Test Data

Generate test data:
```bash
node generate-test-data.js 10000 test-data/test-data-10k.jsonl
```

## Results

Test results are stored in `results/` directory:
- `*-test-results.json` - Raw k6 JSON output
- `*-test-summary.json` - k6 summary metrics
- `*-test-report.html` - HTML visualization
- `orchestrator-logs-*.txt` - Application logs
- `queue-stats-*.txt` - Queue statistics

## CI/CD

Tests run automatically via GitHub Actions:
- Weekly scheduled runs
- Manual workflow dispatch
- PR label trigger (`performance-test`)

See `.github/workflows/performance-test.yml` for details.
