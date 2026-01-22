# Docker Swarm Performance Testing Guide

This guide explains how to deploy and run performance tests using Docker Swarm for distributed, scalable testing scenarios.

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Deployment](#deployment)
- [Scaling](#scaling)
- [Running Tests](#running-tests)
- [Monitoring](#monitoring)
- [Troubleshooting](#troubleshooting)

## Overview

Docker Swarm provides a native clustering solution for Docker, allowing you to:

- **Scale horizontally**: Run multiple orchestrator instances across multiple nodes
- **High availability**: Automatic failover and service recovery
- **Load balancing**: Built-in load balancing across service replicas
- **Resource management**: CPU and memory limits per service
- **Rolling updates**: Zero-downtime deployments

The Swarm setup includes:

- **Orchestrator**: Horizontally scalable (default: 3 replicas)
- **LocalStack**: SQS queue backend (1 replica)
- **Prometheus**: Metrics collection (1 replica)
- **Grafana**: Visualization dashboards (1 replica)
- **k6**: Load generator (run on-demand)

## Prerequisites

- Docker Engine 20.10+ with Swarm mode support
- Docker Swarm initialized (single-node or multi-node)
- 4GB+ RAM available per node
- Network connectivity between nodes (for multi-node)

### Initialize Docker Swarm

If Swarm is not initialized:

```bash
docker swarm init
```

For multi-node setup, join worker nodes:

```bash
# On manager node, get join token
docker swarm join-token worker

# On worker nodes, run the join command
docker swarm join --token <token> <manager-ip>:2377
```

## Quick Start

```bash
# Deploy the stack
./scripts/deploy-swarm-stack.sh deploy

# Check status
./scripts/deploy-swarm-stack.sh status

# Run a load test
./scripts/deploy-swarm-stack.sh run-k6 load

# Scale orchestrator to 5 replicas
./scripts/deploy-swarm-stack.sh scale --replicas=5

# View logs
./scripts/deploy-swarm-stack.sh logs orchestrator

# Remove stack
./scripts/deploy-swarm-stack.sh remove
```

## Deployment

### Method 1: Using Helper Script (Recommended)

```bash
./scripts/deploy-swarm-stack.sh deploy
```

The script will:
1. Check if Swarm is initialized
2. Build the orchestrator image if needed
3. Deploy the stack
4. Show service status and URLs

### Method 2: Manual Deployment

```bash
# Build orchestrator image
docker build -t stateless-orchestrator:latest .

# Deploy stack
docker stack deploy -c docker-compose.swarm.yml perf-test

# Check services
docker stack services perf-test
```

### Service URLs

After deployment, services are available at:

- **Orchestrator**: http://localhost:8080
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)
- **LocalStack**: http://localhost:4566

## Scaling

### Scale Orchestrator Instances

```bash
# Scale to 5 replicas
./scripts/deploy-swarm-stack.sh scale --replicas=5

# Or manually
docker service scale perf-test_orchestrator=5
```

### Verify Scaling

```bash
# Check service replicas
docker service ls | grep orchestrator

# Check running tasks
docker service ps perf-test_orchestrator
```

### Scaling Considerations

- **Single Node**: Limited by node resources
- **Multi-Node**: Can distribute replicas across nodes
- **Resource Limits**: Each replica uses 2 CPU cores and 2GB RAM (configurable)
- **Load Balancing**: Swarm automatically load balances across replicas

## Running Tests

### Using Helper Script

```bash
# Run load test
./scripts/deploy-swarm-stack.sh run-k6 load

# Run stress test
./scripts/deploy-swarm-stack.sh run-k6 stress

# Run spike test
./scripts/deploy-swarm-stack.sh run-k6 spike
```

### Manual k6 Execution

```bash
# Create k6 service
docker service create \
  --name perf-test_k6_load \
  --network perf-test_perf-network \
  --mount type=bind,source=$(pwd)/perf-tests,target=/scripts \
  --mount type=bind,source=$(pwd)/perf-tests/results,target=/results \
  -e ORCHESTRATOR_URL=http://orchestrator:8080 \
  -e EVENTS_PER_REQUEST=100 \
  --restart-condition none \
  grafana/k6:latest \
  run /scripts/load-test.js

# Monitor progress
docker service logs -f perf-test_k6_load

# Cleanup after test
docker service rm perf-test_k6_load
```

### Test Results

Results are saved to `perf-tests/results/`:
- JSON results files
- HTML reports (if generated)
- Logs and metrics

## Monitoring

### Service Status

```bash
# List all services
docker stack services perf-test

# Detailed service status
docker stack ps perf-test

# Service logs
./scripts/deploy-swarm-stack.sh logs orchestrator
./scripts/deploy-swarm-stack.sh logs prometheus
```

### Prometheus Metrics

Access Prometheus UI: http://localhost:9090

Key metrics:
- `orchestrator_events_processed_total`
- `orchestrator_events_routed_total`
- `orchestrator_routing_time_seconds`
- `jvm_memory_used_bytes`
- `process_cpu_usage`

### Grafana Dashboards

Access Grafana: http://localhost:3000
- Username: `admin`
- Password: `admin`

Pre-configured:
- Prometheus datasource
- Performance dashboards

### Resource Usage

```bash
# Container stats
docker stats $(docker ps -q --filter "name=perf-test")

# Service resource usage
docker service ps perf-test_orchestrator --no-trunc
```

## Configuration

### Resource Limits

Edit `docker-compose.swarm.yml` to adjust resources:

```yaml
deploy:
  resources:
    limits:
      cpus: '4'      # Increase CPU
      memory: 4G     # Increase memory
    reservations:
      cpus: '2'
      memory: 2G
```

### Replica Count

Default: 3 orchestrator replicas

```yaml
deploy:
  replicas: 5  # Increase replicas
```

### Placement Constraints

Place services on specific nodes:

```yaml
deploy:
  placement:
    constraints:
      - node.role == manager  # Manager nodes only
      - node.hostname == node1  # Specific node
```

### Update Configuration

```yaml
deploy:
  update_config:
    parallelism: 2      # Update 2 replicas at a time
    delay: 10s          # Wait 10s between batches
    failure_action: rollback
```

## Multi-Node Setup

### Architecture

```
Manager Node:
  - Prometheus
  - Grafana
  - LocalStack

Worker Nodes:
  - Orchestrator replicas (distributed)
```

### Deploying Across Nodes

```bash
# Label nodes
docker node update --label-add role=orchestrator node1
docker node update --label-add role=orchestrator node2

# Update compose file with placement constraints
deploy:
  placement:
    constraints:
      - node.labels.role == orchestrator
```

### Network Considerations

- Swarm uses overlay networks for multi-node communication
- Ensure ports are accessible across nodes
- Consider using ingress mode for published ports

## Troubleshooting

### Service Not Starting

```bash
# Check service status
docker service ps perf-test_orchestrator --no-trunc

# View logs
docker service logs perf-test_orchestrator

# Check node resources
docker node ls
docker node inspect <node-id>
```

### Image Not Found

```bash
# Build image
docker build -t stateless-orchestrator:latest .

# Or pull from registry
docker pull <registry>/stateless-orchestrator:latest
docker tag <registry>/stateless-orchestrator:latest stateless-orchestrator:latest
```

### Network Issues

```bash
# Check network
docker network ls | grep perf-network
docker network inspect perf-test_perf-network

# Test connectivity
docker run --rm --network perf-test_perf-network curlimages/curl:latest \
  curl -f http://orchestrator:8080/actuator/health
```

### Scaling Issues

```bash
# Check available resources
docker node inspect <node-id> | grep -A 10 Resources

# Check service constraints
docker service inspect perf-test_orchestrator | grep -A 10 Constraints
```

### Volume Issues

```bash
# List volumes
docker volume ls | grep perf-test

# Inspect volume
docker volume inspect perf-test_perf_prometheus_data

# Remove volumes (after stack removal)
docker volume rm perf-test_perf_localstack_data \
  perf-test_perf_prometheus_data \
  perf-test_perf_grafana_data
```

## Best Practices

1. **Start Small**: Begin with 1-2 replicas, then scale up
2. **Monitor Resources**: Watch CPU and memory usage
3. **Use Health Checks**: Services automatically restart on failure
4. **Rolling Updates**: Update services gradually to avoid downtime
5. **Clean Up**: Remove unused services and volumes
6. **Backup Data**: Backup Prometheus and Grafana volumes
7. **Multi-Node**: Distribute replicas across nodes for HA

## Comparison: Swarm vs Compose

| Feature | Docker Compose | Docker Swarm |
|---------|---------------|--------------|
| **Scaling** | Manual (scale command) | Built-in (replicas) |
| **HA** | Single node | Multi-node support |
| **Load Balancing** | None | Built-in |
| **Rolling Updates** | Manual | Automatic |
| **Service Discovery** | Container names | DNS-based |
| **Resource Limits** | Limited | Full support |
| **Use Case** | Development/Testing | Production-like testing |

## Advanced Usage

### Rolling Updates

```bash
# Update orchestrator image
docker service update --image stateless-orchestrator:v2 perf-test_orchestrator

# Rollback if needed
docker service rollback perf-test_orchestrator
```

### Service Constraints

```yaml
# Run only on manager nodes
placement:
  constraints:
    - node.role == manager

# Run on specific nodes
placement:
  constraints:
    - node.hostname == node1
    - node.hostname == node2
```

### Secrets Management

```bash
# Create secret
echo "my-secret" | docker secret create my_secret -

# Use in compose file
secrets:
  - my_secret
```

## Next Steps

1. Deploy stack and verify services
2. Run baseline performance tests
3. Scale orchestrator and measure impact
4. Monitor resource usage and bottlenecks
5. Optimize based on findings
6. Set up multi-node for production-like testing

## Additional Resources

- [Docker Swarm Documentation](https://docs.docker.com/engine/swarm/)
- [Docker Stack Deploy](https://docs.docker.com/engine/reference/commandline/stack_deploy/)
- [Service Scaling](https://docs.docker.com/engine/swarm/swarm-tutorial/scale-service/)
- [Performance Testing Guide](./PERFORMANCE_TESTING.md)
