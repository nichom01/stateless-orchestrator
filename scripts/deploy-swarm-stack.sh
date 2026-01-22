#!/bin/bash

# Deploy Docker Swarm stack for performance testing
# Usage: ./deploy-swarm-stack.sh [action] [options]
#   action: deploy, remove, scale, status (default: deploy)
#   options: --replicas=N for scale action

set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

ACTION="${1:-deploy}"
STACK_NAME="perf-test"
COMPOSE_FILE="docker-compose.swarm.yml"
ORCHESTRATOR_IMAGE="stateless-orchestrator:latest"

# Colors
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}Docker Swarm Performance Test Stack Manager${NC}"
echo -e "${CYAN}===========================================${NC}"
echo ""

# Check if Docker Swarm is initialized
if ! docker info | grep -q "Swarm: active"; then
    echo -e "${YELLOW}Docker Swarm is not initialized. Initializing...${NC}"
    docker swarm init || {
        echo -e "${RED}✗ Failed to initialize Docker Swarm${NC}"
        exit 1
    }
    echo -e "${GREEN}✓ Docker Swarm initialized${NC}"
fi

case "$ACTION" in
    deploy)
        echo -e "${CYAN}Deploying ${STACK_NAME} stack...${NC}"
        
        # Check if orchestrator image exists
        if ! docker image inspect "$ORCHESTRATOR_IMAGE" &>/dev/null; then
            echo -e "${YELLOW}Orchestrator image not found. Building...${NC}"
            cd "$PROJECT_ROOT"
            docker build -t "$ORCHESTRATOR_IMAGE" . || {
                echo -e "${RED}✗ Failed to build orchestrator image${NC}"
                exit 1
            }
            echo -e "${GREEN}✓ Orchestrator image built${NC}"
        else
            echo -e "${GREEN}✓ Orchestrator image found${NC}"
        fi
        
        # Deploy the stack
        cd "$PROJECT_ROOT"
        docker stack deploy -c "$COMPOSE_FILE" "$STACK_NAME" || {
            echo -e "${RED}✗ Failed to deploy stack${NC}"
            exit 1
        }
        
        echo -e "${GREEN}✓ Stack deployed${NC}"
        echo ""
        echo -e "${CYAN}Waiting for services to start...${NC}"
        sleep 5
        
        # Show service status
        docker stack services "$STACK_NAME"
        echo ""
        echo -e "${CYAN}Service URLs:${NC}"
        echo -e "  - Orchestrator: http://localhost:8080"
        echo -e "  - Prometheus: http://localhost:9090"
        echo -e "  - Grafana: http://localhost:3000 (admin/admin)"
        echo -e "  - LocalStack: http://localhost:4566"
        echo ""
        echo -e "${YELLOW}To view logs:${NC}"
        echo -e "  docker service logs -f ${STACK_NAME}_orchestrator"
        echo ""
        echo -e "${YELLOW}To scale orchestrator:${NC}"
        echo -e "  ./deploy-swarm-stack.sh scale --replicas=5"
        ;;
        
    remove)
        echo -e "${CYAN}Removing ${STACK_NAME} stack...${NC}"
        docker stack rm "$STACK_NAME" || {
            echo -e "${RED}✗ Failed to remove stack${NC}"
            exit 1
        }
        echo -e "${GREEN}✓ Stack removed${NC}"
        echo ""
        echo -e "${YELLOW}Note: Volumes are preserved. To remove volumes:${NC}"
        echo -e "  docker volume rm ${STACK_NAME}_perf_localstack_data ${STACK_NAME}_perf_prometheus_data ${STACK_NAME}_perf_grafana_data"
        ;;
        
    scale)
        REPLICAS="${2#--replicas=}"
        if [ -z "$REPLICAS" ]; then
            REPLICAS="${2}"
        fi
        
        if [ -z "$REPLICAS" ] || ! [[ "$REPLICAS" =~ ^[0-9]+$ ]]; then
            echo -e "${RED}Error: Please specify number of replicas${NC}"
            echo "Usage: ./deploy-swarm-stack.sh scale --replicas=N"
            exit 1
        fi
        
        echo -e "${CYAN}Scaling orchestrator to ${REPLICAS} replicas...${NC}"
        docker service scale "${STACK_NAME}_orchestrator=${REPLICAS}" || {
            echo -e "${RED}✗ Failed to scale service${NC}"
            exit 1
        }
        echo -e "${GREEN}✓ Service scaled${NC}"
        ;;
        
    status)
        echo -e "${CYAN}Stack Status:${NC}"
        docker stack services "$STACK_NAME"
        echo ""
        echo -e "${CYAN}Service Details:${NC}"
        docker stack ps "$STACK_NAME" --no-trunc
        ;;
        
    logs)
        SERVICE="${2:-orchestrator}"
        echo -e "${CYAN}Showing logs for ${SERVICE}...${NC}"
        docker service logs -f "${STACK_NAME}_${SERVICE}" || {
            echo -e "${RED}✗ Service not found${NC}"
            echo "Available services: orchestrator, localstack, prometheus, grafana"
            exit 1
        }
        ;;
        
    run-k6)
        TEST_TYPE="${2:-load}"
        if [[ ! "$TEST_TYPE" =~ ^(load|stress|spike)$ ]]; then
            echo -e "${RED}Error: Invalid test type: ${TEST_TYPE}${NC}"
            echo "Valid types: load, stress, spike"
            exit 1
        fi
        
        # Check if Docker Swarm is initialized
        if ! docker info | grep -q "Swarm: active"; then
            echo -e "${YELLOW}Docker Swarm is not initialized. Initializing...${NC}"
            docker swarm init || {
                echo -e "${RED}✗ Failed to initialize Docker Swarm${NC}"
                exit 1
            }
            echo -e "${GREEN}✓ Docker Swarm initialized${NC}"
        fi
        
        # Check if stack is deployed
        if ! docker stack ls | grep -q "^${STACK_NAME} "; then
            echo -e "${YELLOW}Stack ${STACK_NAME} is not deployed. Deploying...${NC}"
            cd "$PROJECT_ROOT"
            
            # Check if orchestrator image exists
            if ! docker image inspect "$ORCHESTRATOR_IMAGE" &>/dev/null; then
                echo -e "${YELLOW}Orchestrator image not found. Building...${NC}"
                docker build -t "$ORCHESTRATOR_IMAGE" . || {
                    echo -e "${RED}✗ Failed to build orchestrator image${NC}"
                    exit 1
                }
                echo -e "${GREEN}✓ Orchestrator image built${NC}"
            fi
            
            # Deploy the stack
            docker stack deploy -c "$COMPOSE_FILE" "$STACK_NAME" || {
                echo -e "${RED}✗ Failed to deploy stack${NC}"
                exit 1
            }
            echo -e "${GREEN}✓ Stack deployed${NC}"
            echo -e "${YELLOW}Waiting for network to be created...${NC}"
            sleep 5
            
            # Verify network exists
            for i in {1..30}; do
                if docker network ls | grep -q "${STACK_NAME}_perf-network"; then
                    echo -e "${GREEN}✓ Network ${STACK_NAME}_perf-network found${NC}"
                    break
                fi
                if [ $i -eq 30 ]; then
                    echo -e "${RED}✗ Network not found after 30 seconds${NC}"
                    docker network ls
                    docker stack services "$STACK_NAME"
                    exit 1
                fi
                sleep 1
            done
        else
            # Verify network exists
            if ! docker network ls | grep -q "${STACK_NAME}_perf-network"; then
                echo -e "${RED}Error: Network ${STACK_NAME}_perf-network not found${NC}"
                echo "Available networks:"
                docker network ls
                echo "Stack services:"
                docker stack services "$STACK_NAME"
                exit 1
            fi
        fi
        
        echo -e "${CYAN}Running k6 ${TEST_TYPE} test in Swarm...${NC}"
        
        # Create results directory
        mkdir -p "$PROJECT_ROOT/perf-tests/results"
        
        TIMESTAMP=$(date +%s)
        SERVICE_NAME="${STACK_NAME}_k6_${TEST_TYPE}_${TIMESTAMP}"
        
        # Create a temporary service to run k6
        docker service create \
            --name "${SERVICE_NAME}" \
            --network "${STACK_NAME}_perf-network" \
            --mount type=bind,source="$PROJECT_ROOT/perf-tests",target=/scripts \
            --mount type=bind,source="$PROJECT_ROOT/examples",target=/examples \
            --mount type=bind,source="$PROJECT_ROOT/perf-tests/results",target=/results \
            -e ORCHESTRATOR_URL=http://orchestrator:8080 \
            -e EVENTS_PER_REQUEST=100 \
            --restart-condition none \
            grafana/k6:latest \
            run --out json=/results/${TEST_TYPE}-test-results.json --summary-export=/results/${TEST_TYPE}-test-summary.json "/scripts/${TEST_TYPE}-test.js" || {
            echo -e "${RED}✗ Failed to create k6 test service${NC}"
            exit 1
        }
        
        echo -e "${GREEN}✓ k6 test service created${NC}"
        echo -e "${YELLOW}Waiting for k6 service to complete...${NC}"
        
        # Wait for service to complete
        for i in {1..600}; do
            SERVICE_STATE=$(docker service ps "${SERVICE_NAME}" --format "{{.CurrentState}}" --no-trunc 2>/dev/null | head -n1 || echo "")
            if [ -z "$SERVICE_STATE" ] || echo "$SERVICE_STATE" | grep -q "Complete\|Shutdown\|Failed"; then
                echo -e "${GREEN}✓ Service completed${NC}"
                break
            fi
            if [ $i -eq 600 ]; then
                echo -e "${YELLOW}⚠ Service timeout after 10 minutes${NC}"
                break
            fi
            sleep 1
        done
        
        # Get logs
        echo -e "${CYAN}Collecting k6 logs...${NC}"
        docker service logs "${SERVICE_NAME}" 2>&1 || true
        
        # Check exit code
        TASK_ID=$(docker service ps "${SERVICE_NAME}" --format "{{.ID}}" --filter "desired-state=shutdown" | head -n1)
        if [ -n "$TASK_ID" ]; then
            EXIT_CODE=$(docker inspect --format '{{.Status.ContainerStatus.ExitCode}}' "$TASK_ID" 2>/dev/null || echo "0")
            if [ "$EXIT_CODE" != "0" ]; then
                echo -e "${RED}✗ k6 test failed with exit code: ${EXIT_CODE}${NC}"
                docker service rm "${SERVICE_NAME}" || true
                exit 1
            fi
        fi
        
        # Cleanup
        docker service rm "${SERVICE_NAME}" || true
        
        echo -e "${GREEN}✓ Test completed!${NC}"
        echo -e "${CYAN}Results:${NC}"
        echo -e "  - JSON: perf-tests/results/${TEST_TYPE}-test-results.json"
        echo -e "  - Summary: perf-tests/results/${TEST_TYPE}-test-summary.json"
        ;;
        
    *)
        echo -e "${RED}Error: Unknown action: ${ACTION}${NC}"
        echo ""
        echo "Usage: ./deploy-swarm-stack.sh [action] [options]"
        echo ""
        echo "Actions:"
        echo "  deploy          - Deploy the stack (default)"
        echo "  remove          - Remove the stack"
        echo "  scale           - Scale orchestrator replicas (requires --replicas=N)"
        echo "  status          - Show stack status"
        echo "  logs [service]  - Show logs for a service (default: orchestrator)"
        echo "  run-k6 [type]   - Run k6 test (load|stress|spike, default: load)"
        echo ""
        echo "Examples:"
        echo "  ./deploy-swarm-stack.sh deploy"
        echo "  ./deploy-swarm-stack.sh scale --replicas=5"
        echo "  ./deploy-swarm-stack.sh logs prometheus"
        echo "  ./deploy-swarm-stack.sh run-k6 stress"
        exit 1
        ;;
esac
