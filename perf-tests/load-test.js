// Load Test - Simulates expected production load
// Gradual ramp-up with steady state validation

import { check, sleep } from 'k6';
import http from 'k6/http';
import { 
  config, 
  generateOrderEvent, 
  submitBulkEvents, 
  checkHealth,
  commonThresholds,
  eventsSubmitted,
  eventErrors,
  bulkRequestLatency
} from './k6.config.js';

export const options = {
  stages: [
    // Gradual ramp-up: 0 → 100 VUs over 1 minute
    { duration: '1m', target: 100 },
    // Increase to 500 VUs over 2 minutes
    { duration: '2m', target: 500 },
    // Increase to 1000 VUs over 2 minutes
    { duration: '2m', target: 1000 },
    // Steady state: 1000 VUs for 10 minutes
    { duration: '10m', target: 1000 },
    // Ramp-down: 1000 → 0 over 2 minutes
    { duration: '2m', target: 0 },
  ],
  
  thresholds: {
    ...commonThresholds,
    // Load test specific thresholds
    'http_req_duration': ['p(95)<500', 'p(99)<1000'],
    'http_req_failed': ['rate<0.01'], // Less than 1% errors
    'events_submitted': ['count>100000'], // At least 100k events processed
    'bulk_request_latency_ms': ['p(95)<500', 'p(99)<1000'],
  },
  
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)', 'count'],
};

const ORCHESTRATOR_URL = config.orchestratorUrl;
const EVENTS_PER_REQUEST = config.eventsPerRequest;

export function setup() {
  // Verify orchestrator is healthy before starting test
  console.log(`Checking orchestrator health at ${ORCHESTRATOR_URL}`);
  try {
    const healthCheck = checkHealth();
    if (!healthCheck) {
      console.warn('Orchestrator health check failed, but continuing test...');
    } else {
      console.log('Orchestrator is healthy. Starting load test...');
    }
  } catch (error) {
    console.warn(`Health check error: ${error.message}, but continuing test...`);
  }
  
  return {
    startTime: Date.now(),
  };
}

export default function (data) {
  // Generate a batch of events
  const events = [];
  for (let i = 0; i < EVENTS_PER_REQUEST; i++) {
    events.push(generateOrderEvent());
  }
  
  // Submit bulk events
  const result = submitBulkEvents(events);
  
  // Small sleep to avoid overwhelming the system
  // This creates a realistic request rate
  sleep(0.1); // 100ms between requests = ~10 requests/sec per VU
}

export function teardown(data) {
  console.log(`Load test completed. Total duration: ${(Date.now() - data.startTime) / 1000}s`);
}
