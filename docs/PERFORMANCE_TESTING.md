# Performance Testing Guide

This guide explains how to run automated performance tests for the Stateless Orchestrator using k6, Docker containers, and GitHub Actions.

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Test Types](#test-types)
- [Running Tests Locally](#running-tests-locally)
- [CI/CD Integration](#cicd-integration)
- [Understanding Results](#understanding-results)
- [Troubleshooting](#troubleshooting)

## Overview

The performance testing infrastructure includes:

- **k6**: Modern load testing tool for generating traffic
- **Docker Compose**: Isolated test environment with orchestrator, LocalStack, and Prometheus
- **GitHub Actions**: Automated test execution in CI/CD
- **Custom Metrics**: Events/second, routing success rate, queue depth monitoring

## Quick Start

### Prerequisites

- Docker and Docker Compose installed
- Node.js (for test data generation and report generation)
- 4GB+ RAM available for containers

### Run a Quick Load Test

```bash
# Start the test environment and run load test
./scripts/run-perf-test-local.sh load

# View results
open perf-tests/results/load-test-report.html
```

## Test Types

### Load Test

Simulates expected production load with gradual ramp-up.

**Characteristics:**
- Gradual ramp-up: 0 → 100 → 500 → 1000 VUs over 5 minutes
- Steady state: 1000 VUs for 10 minutes
- Ramp-down: 1000 → 0 over 2 minutes
- **Target**: Sustain 2000-5000 events/sec
- **Thresholds**: p95 latency < 500ms, error rate < 1%

**When to use:**
- Validate system can handle expected production load
- Establish baseline performance metrics
- Before major releases

### Stress Test

Finds breaking point and maximum capacity.

**Characteristics:**
- Aggressive ramp-up: 0 → 5000 VUs over 10 minutes
- Identifies: Max throughput, resource limits, failure modes
- **Target**: Push beyond 10000 events/sec until degradation
- **Thresholds**: More lenient (up to 5% errors acceptable at breaking point)

**When to use:**
- Determine maximum capacity
- Identify bottlenecks
- Plan capacity scaling

### Spike Test

Validates recovery from sudden traffic bursts.

**Characteristics:**
- Pattern: baseline (100 VUs) → 10x spike (1000 VUs) → baseline (repeat)
- Validates: Recovery time, queue behavior
- **Focus**: System stability under sudden load changes

**When to use:**
- Validate autoscaling behavior
- Test queue buffering capacity
- Ensure graceful degradation

## Running Tests Locally

### Method 1: Using Helper Script (Recommended)

```bash
# Run load test
./scripts/run-perf-test-local.sh load

# Run stress test
./scripts/run-perf-test-local.sh stress

# Run spike test
./scripts/run-perf-test-local.sh spike
```

The script automatically:
1. Starts LocalStack and Prometheus
2. Builds and starts the orchestrator
3. Waits for services to be healthy
4. Generates test data if needed
5. Runs the k6 test
6. Generates HTML report
7. Collects logs and metrics

### Method 2: Manual Docker Compose

```bash
# Start test environment
docker-compose -f docker-compose.perf.yml up -d localstack prometheus orchestrator

# Wait for services
./scripts/wait-for-services.sh

# Run k6 test
docker-compose -f docker-compose.perf.yml run --rm \
  -e ORCHESTRATOR_URL=http://orchestrator:8080 \
  -e EVENTS_PER_REQUEST=100 \
  k6 run \
  --out json=/results/load-test-results.json \
  /scripts/load-test.js

# Generate report
node perf-tests/scripts/generate-report.js \
  --input perf-tests/results/load-test-results.json \
  --output perf-tests/results/load-test-report.html \
  --type load

# Cleanup
docker-compose -f docker-compose.perf.yml down
```

### Method 3: Direct k6 Execution (Advanced)

If you have k6 installed locally:

```bash
# Set environment variables
export ORCHESTRATOR_URL=http://localhost:8080
export EVENTS_PER_REQUEST=100

# Run test
k6 run --out json=results.json perf-tests/load-test.js

# Generate report
node perf-tests/scripts/generate-report.js \
  --input results.json \
  --output report.html \
  --type load
```

## Test Data

### Using Existing Test Data

The project includes sample test data:
- `examples/test-orders-2500.jsonl` - 2,500 sample orders

### Generating Custom Test Data

```bash
# Generate 10,000 events
node perf-tests/generate-test-data.js 10000 perf-tests/test-data/test-data-10k.jsonl

# Generate 50,000 events
node perf-tests/generate-test-data.js 50000 perf-tests/test-data/test-data-50k.jsonl

# Generate 100,000 events
node perf-tests/generate-test-data.js 100000 perf-tests/test-data/test-data-100k.jsonl
```

**Test Data Format:**
Each line is a JSON event following the Event model:
```json
{
  "type": "OrderCreated",
  "correlationId": "order-0001-abc123",
  "orchestrationName": "order-processing",
  "context": {
    "orderId": "ORD-0001",
    "customerId": "CUST-002",
    "customerTier": "premium",
    "orderTotal": 99.99,
    "items": [...]
  }
}
```

## CI/CD Integration

### GitHub Actions Workflow

The performance tests run automatically:

1. **Scheduled**: Weekly on Sunday at 2 AM UTC
2. **Manual**: Via workflow_dispatch with options
3. **PR Label**: Add `performance-test` label to trigger

### Manual Trigger

```bash
# Via GitHub CLI
gh workflow run performance-test.yml

# With parameters
gh workflow run performance-test.yml \
  -f test_type=stress \
  -f duration=15m \
  -f vus=2000
```

### Workflow Options

- **test_type**: `load`, `stress`, `spike`, or `all`
- **duration**: Custom test duration (e.g., `10m`, `30m`)
- **vus**: Maximum virtual users

### Viewing Results

1. Go to Actions tab in GitHub
2. Select the workflow run
3. Download artifacts (HTML reports, logs, metrics)
4. Open HTML reports in browser

## Understanding Results

### Key Metrics

**Throughput:**
- Events processed per second
- Target: 2000-5000 events/sec (load test)
- Higher is better

**Latency:**
- p50 (median): Typical response time
- p95: 95% of requests faster than this
- p99: 99% of requests faster than this
- Target: p95 < 500ms, p99 < 1000ms

**Error Rate:**
- Percentage of failed requests
- Target: < 1% (load test), < 5% (stress test)
- Lower is better

**Queue Depth:**
- Messages waiting in queues
- Should remain stable or drain properly
- Sudden growth indicates bottleneck

### HTML Report

The generated HTML report includes:

1. **Summary Metrics**: Total events, error rate, average latency
2. **Latency Statistics**: Min, avg, p50, p90, p95, p99, max
3. **Test Summary**: Key findings and pass/fail status

### JSON Results

Raw k6 JSON output includes:
- Detailed metric data points
- Timestamped measurements
- Custom metrics (events_submitted, routing_success_rate)

### Analyzing Results

```bash
# Analyze results
./perf-tests/scripts/analyze-results.sh perf-tests/results

# View queue statistics
cat perf-tests/results/queue-stats.txt

# Check orchestrator logs
tail -f perf-tests/results/orchestrator-logs.txt
```

## Monitoring During Tests

### Prometheus Metrics

Access Prometheus UI:
```bash
# Port forward (if running locally)
open http://localhost:9090

# Query metrics
# orchestrator_events_processed_total
# orchestrator_events_routed_total
# orchestrator_routing_time_seconds
# jvm_memory_used_bytes
# process_cpu_usage
```

### Grafana Dashboards

If Grafana is running:
```bash
# Access Grafana
open http://localhost:3000
# Login: admin/admin

# Prometheus datasource is pre-configured
```

### Queue Monitoring

```bash
# Real-time queue dashboard
./scripts/monitor-queues.sh

# One-time statistics
./scripts/queue-stats.sh

# Monitor during test
./perf-tests/scripts/monitor-queues-during-test.sh 5 300
```

### Container Statistics

```bash
# View container resource usage
docker stats perf-orchestrator

# View logs
docker logs -f perf-orchestrator
```

## Performance Targets

| Metric | Load Test Target | Stress Test Goal |
|--------|------------------|------------------|
| Throughput | 2000-5000 events/sec | Find max (target >10k) |
| p95 Latency | < 500ms | Track degradation point |
| p99 Latency | < 1000ms | Acceptable under stress |
| Error Rate | < 1% | < 5% at breaking point |
| Queue Depth | Steady state | Monitor buildup |
| Memory | < 1.5GB | Track growth |
| CPU | < 80% | Identify bottleneck |

## Troubleshooting

### Orchestrator Not Starting

**Symptoms:** Health check fails, container exits

**Solutions:**
```bash
# Check logs
docker logs perf-orchestrator

# Verify LocalStack is running
curl http://localhost:4566/_localstack/health

# Check resource limits
docker stats perf-orchestrator
```

### High Error Rates

**Symptoms:** Error rate > 1% in load test

**Possible Causes:**
- Insufficient resources (CPU/memory)
- Queue connection issues
- Configuration errors

**Solutions:**
- Increase container resources in `docker-compose.perf.yml`
- Check LocalStack logs: `docker logs perf-localstack`
- Verify queue initialization: `./scripts/queue-stats.sh`

### Low Throughput

**Symptoms:** Throughput < 2000 events/sec

**Possible Causes:**
- Resource constraints
- Network latency
- Queue processing bottleneck

**Solutions:**
- Increase `EVENTS_PER_REQUEST` (default: 100)
- Reduce sleep time in k6 scripts
- Scale orchestrator instances
- Check queue processing speed

### Out of Memory

**Symptoms:** Container killed, OOM errors

**Solutions:**
```bash
# Increase memory limit in docker-compose.perf.yml
deploy:
  resources:
    limits:
      memory: 4G  # Increase from 2G

# Adjust JVM heap size
environment:
  - JAVA_OPTS=-Xmx3072m  # Increase heap
```

### Test Timeout

**Symptoms:** Test hangs or times out

**Solutions:**
- Increase timeout in GitHub Actions workflow
- Check for deadlocks in orchestrator logs
- Verify queues are processing messages
- Reduce test duration or VU count

## Advanced Configuration

### Custom Test Scenarios

Edit k6 test scripts in `perf-tests/`:
- `load-test.js` - Adjust ramp-up stages
- `stress-test.js` - Modify VU scaling pattern
- `spike-test.js` - Change spike magnitude

### Resource Limits

Edit `docker-compose.perf.yml`:
```yaml
deploy:
  resources:
    limits:
      cpus: '4'      # Increase CPU
      memory: 4G     # Increase memory
```

### Custom Metrics

Add custom metrics in `k6.config.js`:
```javascript
export const myCustomMetric = new Counter('my_custom_metric');
```

## Best Practices

1. **Run Baseline First**: Establish performance baseline before changes
2. **Regular Testing**: Run weekly to catch regressions
3. **Compare Results**: Track metrics over time
4. **Resource Monitoring**: Watch CPU, memory, queue depth
5. **Gradual Scaling**: Start with load test before stress test
6. **Clean Environment**: Ensure clean state before each test
7. **Document Findings**: Record bottlenecks and optimizations

## Next Steps

1. Run baseline tests to establish current performance profile
2. Optimize based on bottlenecks found
3. Set up alerts for performance degradations in CI
4. Integrate with production monitoring for comparison
5. Schedule regular endurance tests (24-hour runs)

## Additional Resources

- [k6 Documentation](https://k6.io/docs/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Prometheus Querying](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
