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
        
        echo -e "${CYAN}Running k6 ${TEST_TYPE} test in Swarm...${NC}"
        
        # Create a temporary service to run k6
        docker service create \
            --name "${STACK_NAME}_k6_${TEST_TYPE}_$(date +%s)" \
            --network "${STACK_NAME}_perf-network" \
            --mount type=bind,source="$PROJECT_ROOT/perf-tests",target=/scripts \
            --mount type=bind,source="$PROJECT_ROOT/examples",target=/examples \
            --mount type=bind,source="$PROJECT_ROOT/perf-tests/results",target=/results \
            -e ORCHESTRATOR_URL=http://orchestrator:8080 \
            -e EVENTS_PER_REQUEST=100 \
            --restart-condition none \
            grafana/k6:latest \
            run "/scripts/${TEST_TYPE}-test.js" || {
            echo -e "${RED}✗ Failed to run k6 test${NC}"
            exit 1
        }
        
        echo -e "${GREEN}✓ k6 test service created${NC}"
        echo -e "${YELLOW}Monitor progress:${NC}"
        echo -e "  docker service logs -f ${STACK_NAME}_k6_${TEST_TYPE}_*"
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
