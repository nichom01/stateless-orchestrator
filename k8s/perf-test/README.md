# Kubernetes Performance Testing Setup

This directory contains Kubernetes manifests for running performance tests with horizontal scaling.

## Prerequisites

- Kubernetes cluster (local: minikube, kind, or Docker Desktop Kubernetes)
- kubectl configured to access your cluster
- Docker image built and available (either locally or in a registry)

## Quick Start

1. **Build the Docker image:**
   ```bash
   docker build -t stateless-orchestrator:latest .
   ```

2. **Load image into cluster** (if using local cluster):
   ```bash
   # For minikube
   minikube image load stateless-orchestrator:latest
   
   # For kind
   kind load docker-image stateless-orchestrator:latest
   ```

3. **Deploy all resources:**
   ```bash
   kubectl apply -f namespace.yaml
   kubectl apply -f localstack-deployment.yaml
   kubectl apply -f deployment.yaml
   kubectl apply -f service.yaml
   ```

4. **Wait for pods to be ready:**
   ```bash
   kubectl wait --for=condition=ready pod -l app=orchestrator -n perf-test --timeout=300s
   ```

5. **Port forward to access orchestrator:**
   ```bash
   kubectl port-forward -n perf-test service/orchestrator-service 8080:8080
   ```

6. **Run performance tests:**
   ```bash
   # Update ORCHESTRATOR_URL in your test script to use http://localhost:8080
   ./scripts/run-perf-test-local.sh load
   ```

## Scaling

To scale the orchestrator instances:

```bash
# Scale to 3 replicas (default)
kubectl scale deployment stateless-orchestrator -n perf-test --replicas=3

# Scale to 5 replicas
kubectl scale deployment stateless-orchestrator -n perf-test --replicas=5

# Scale to 10 replicas
kubectl scale deployment stateless-orchestrator -n perf-test --replicas=10
```

## Monitoring

Check pod status:
```bash
kubectl get pods -n perf-test
kubectl logs -l app=orchestrator -n perf-test --tail=100
```

Check service endpoints (to see all orchestrator instances):
```bash
kubectl get endpoints orchestrator-service -n perf-test
```

## Cleanup

```bash
kubectl delete namespace perf-test
```

## Configuration

The deployment uses:
- **3 replicas** by default (horizontal scaling)
- **2 CPU cores** and **2GB memory** per instance
- **LocalStack** for SQS (dev profile)
- **Health checks** for liveness and readiness

Adjust resources and replica count in `deployment.yaml` as needed.
