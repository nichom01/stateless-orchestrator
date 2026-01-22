#!/bin/bash

# Run performance tests using Kubernetes with horizontal scaling
# Usage: ./run-perf-test-k8s.sh [test-type] [replicas]
#   test-type: load, stress, or spike (default: load)
#   replicas: number of orchestrator instances (default: 3)

set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

TEST_TYPE="${1:-load}"
REPLICAS="${2:-3}"
K8S_DIR="$PROJECT_ROOT/k8s/perf-test"
RESULTS_DIR="perf-tests/results"

# Colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}Kubernetes Performance Test Runner${NC}"
echo -e "${CYAN}===================================${NC}"
echo ""

# Validate test type
if [[ ! "$TEST_TYPE" =~ ^(load|stress|spike)$ ]]; then
    echo -e "${RED}Error: Invalid test type: ${TEST_TYPE}${NC}"
    echo "Valid types: load, stress, spike"
    exit 1
fi

# Validate replicas
if ! [[ "$REPLICAS" =~ ^[0-9]+$ ]] || [ "$REPLICAS" -lt 1 ]; then
    echo -e "${RED}Error: Invalid replica count: ${REPLICAS}${NC}"
    echo "Replicas must be a positive integer"
    exit 1
fi

# Check if kubectl is available
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}Error: kubectl is not installed or not in PATH${NC}"
    echo "Please install kubectl to use Kubernetes mode"
    exit 1
fi

# Check if cluster is accessible
if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}Error: Cannot connect to Kubernetes cluster${NC}"
    echo "Please ensure kubectl is configured correctly"
    exit 1
fi

echo -e "${CYAN}Configuration:${NC}"
echo -e "  Test Type: ${TEST_TYPE}"
echo -e "  Replicas: ${REPLICAS}"
echo -e "  Kubernetes Namespace: perf-test"
echo ""

# Create results directory
mkdir -p "$RESULTS_DIR"

# Build Docker image
echo -e "${CYAN}Building Docker image...${NC}"
cd "$PROJECT_ROOT"
docker build -t stateless-orchestrator:latest . || {
    echo -e "${RED}✗ Docker build failed${NC}"
    exit 1
}

# Load image into cluster (for local clusters)
echo -e "${CYAN}Loading image into cluster...${NC}"
if command -v minikube &> /dev/null && minikube status &> /dev/null; then
    echo "Detected minikube, loading image..."
    minikube image load stateless-orchestrator:latest || true
elif command -v kind &> /dev/null; then
    echo "Detected kind, loading image..."
    kind load docker-image stateless-orchestrator:latest || true
else
    echo -e "${YELLOW}⚠ Local cluster not detected. Ensure image is available in your cluster.${NC}"
fi

# Deploy Kubernetes resources
echo ""
echo -e "${CYAN}Deploying Kubernetes resources...${NC}"
kubectl apply -f "$K8S_DIR/namespace.yaml" || true
kubectl apply -f "$K8S_DIR/localstack-deployment.yaml"
kubectl apply -f "$K8S_DIR/service.yaml"

# Update replica count in deployment
echo -e "${CYAN}Scaling orchestrator to ${REPLICAS} replicas...${NC}"
kubectl apply -f "$K8S_DIR/deployment.yaml"
kubectl scale deployment stateless-orchestrator -n perf-test --replicas="$REPLICAS" || {
    echo -e "${YELLOW}⚠ Scaling failed, using deployment default${NC}"
}

# Wait for LocalStack
echo ""
echo -e "${YELLOW}Waiting for LocalStack...${NC}"
kubectl wait --for=condition=ready pod -l app=localstack -n perf-test --timeout=120s || {
    echo -e "${RED}✗ LocalStack failed to start${NC}"
    kubectl logs -l app=localstack -n perf-test --tail=50
    exit 1
}

# Initialize LocalStack queues
echo -e "${CYAN}Initializing LocalStack queues...${NC}"
LOCALSTACK_POD=$(kubectl get pod -l app=localstack -n perf-test -o jsonpath='{.items[0].metadata.name}')
kubectl cp "$PROJECT_ROOT/scripts/init-localstack.sh" "perf-test/$LOCALSTACK_POD:/tmp/init-localstack.sh" || true
kubectl exec -n perf-test "$LOCALSTACK_POD" -- bash -c "chmod +x /tmp/init-localstack.sh && /tmp/init-localstack.sh" || {
    echo -e "${YELLOW}⚠ Queue initialization failed, continuing...${NC}"
}

# Wait for orchestrator pods
echo ""
echo -e "${YELLOW}Waiting for orchestrator pods to be ready...${NC}"
kubectl wait --for=condition=ready pod -l app=orchestrator -n perf-test --timeout=300s || {
    echo -e "${RED}✗ Orchestrator pods failed to start${NC}"
    kubectl get pods -n perf-test
    kubectl logs -l app=orchestrator -n perf-test --tail=50
    exit 1
}

echo -e "${GREEN}✓ All orchestrator pods are ready${NC}"
echo ""

# Show pod status
echo -e "${CYAN}Orchestrator Pods:${NC}"
kubectl get pods -l app=orchestrator -n perf-test
echo ""

# Port forward orchestrator service
echo -e "${CYAN}Setting up port forwarding...${NC}"
kubectl port-forward -n perf-test service/orchestrator-service 8080:8080 > /dev/null 2>&1 &
PORT_FORWARD_PID=$!

# Wait for port forward to be ready
sleep 3

# Cleanup function
cleanup() {
    echo ""
    echo -e "${CYAN}Cleaning up...${NC}"
    kill $PORT_FORWARD_PID 2>/dev/null || true
    echo -e "${GREEN}✓ Cleanup complete${NC}"
}
trap cleanup EXIT

# Health check
echo -e "${CYAN}Checking orchestrator health...${NC}"
timeout 60 bash -c 'until curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; do sleep 2; done' || {
    echo -e "${RED}✗ Orchestrator health check failed${NC}"
    kill $PORT_FORWARD_PID 2>/dev/null || true
    exit 1
}

echo -e "${GREEN}✓ Orchestrator is healthy${NC}"
echo ""

# Generate test data if needed
if [ ! -f "$PROJECT_ROOT/perf-tests/test-data/test-data-10k.jsonl" ]; then
    echo -e "${CYAN}Generating test data...${NC}"
    cd "$PROJECT_ROOT"
    node perf-tests/generate-test-data.js 10000 perf-tests/test-data/test-data-10k.jsonl
fi

# Determine test script
TEST_SCRIPT="/scripts/${TEST_TYPE}-test.js"
RESULTS_FILE="${RESULTS_DIR}/${TEST_TYPE}-test-results.json"
SUMMARY_FILE="${RESULTS_DIR}/${TEST_TYPE}-test-summary.json"

echo -e "${CYAN}Running ${TEST_TYPE} test with ${REPLICAS} orchestrator instances...${NC}"
echo -e "${CYAN}Results will be saved to: ${RESULTS_DIR}${NC}"
echo ""

# Run k6 test in Docker (using host network to access port-forwarded service)
cd "$PROJECT_ROOT"
docker run --rm \
    --network host \
    -v "$PROJECT_ROOT/perf-tests:/scripts" \
    -v "$PROJECT_ROOT/examples:/examples" \
    -v "$PROJECT_ROOT/${RESULTS_DIR}:/results" \
    -e ORCHESTRATOR_URL=http://localhost:8080 \
    -e EVENTS_PER_REQUEST=100 \
    -e TEST_DATA_PATH=/scripts/test-data \
    grafana/k6:latest run \
    --out json="/results/${TEST_TYPE}-test-results.json" \
    --summary-export="/results/${TEST_TYPE}-test-summary.json" \
    "$TEST_SCRIPT" || {
    echo -e "${YELLOW}⚠ Test completed with errors (check results)${NC}"
}

# Generate HTML report
if [ -f "$SUMMARY_FILE" ]; then
    echo ""
    echo -e "${CYAN}Generating HTML report...${NC}"
    cd "$PROJECT_ROOT"
    node perf-tests/scripts/generate-report.js \
        --input "$SUMMARY_FILE" \
        --output "${RESULTS_DIR}/${TEST_TYPE}-test-report.html" \
        --type "$TEST_TYPE" || {
        echo -e "${YELLOW}⚠ Report generation failed${NC}"
    }
fi

# Collect logs from all pods
echo ""
echo -e "${CYAN}Collecting logs from orchestrator pods...${NC}"
kubectl logs -l app=orchestrator -n perf-test > "${RESULTS_DIR}/orchestrator-logs-${TEST_TYPE}.txt" 2>&1 || true

# Collect pod metrics
echo -e "${CYAN}Collecting pod metrics...${NC}"
kubectl get pods -l app=orchestrator -n perf-test -o wide > "${RESULTS_DIR}/pod-metrics-${TEST_TYPE}.txt" 2>&1 || true

echo ""
echo -e "${GREEN}✓ Test completed!${NC}"
echo -e "${CYAN}Results:${NC}"
echo -e "  - JSON: ${RESULTS_FILE}"
echo -e "  - Summary: ${SUMMARY_FILE}"
echo -e "  - Report: ${RESULTS_DIR}/${TEST_TYPE}-test-report.html"
echo -e "  - Logs: ${RESULTS_DIR}/orchestrator-logs-${TEST_TYPE}.txt"
echo -e "  - Pod Metrics: ${RESULTS_DIR}/pod-metrics-${TEST_TYPE}.txt"
echo ""
echo -e "${CYAN}Orchestrator instances: ${REPLICAS}${NC}"
kubectl get pods -l app=orchestrator -n perf-test
echo ""
echo -e "${YELLOW}To view results:${NC}"
echo -e "  open ${RESULTS_DIR}/${TEST_TYPE}-test-report.html"
echo ""
echo -e "${YELLOW}To cleanup Kubernetes resources:${NC}"
echo -e "  kubectl delete namespace perf-test"
