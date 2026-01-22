// Stress Test - Finds breaking point and maximum capacity
// Aggressive ramp-up to identify limits

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
    // Start with baseline: 100 VUs for 1 minute
    { duration: '1m', target: 100 },
    // Aggressive ramp-up: 100 → 1000 VUs over 2 minutes
    { duration: '2m', target: 1000 },
    // Continue ramping: 1000 → 2500 VUs over 3 minutes
    { duration: '3m', target: 2500 },
    // Push harder: 2500 → 5000 VUs over 4 minutes
    { duration: '4m', target: 5000 },
    // Hold at peak for 5 minutes to observe behavior
    { duration: '5m', target: 5000 },
    // Gradual ramp-down to see recovery
    { duration: '3m', target: 1000 },
    { duration: '2m', target: 0 },
  ],
  
  thresholds: {
    // Stress test has more lenient thresholds - we're looking for breaking point
    'http_req_duration': ['p(95)<2000', 'p(99)<5000'], // Higher latency acceptable under stress
    'http_req_failed': ['rate<0.05'], // Up to 5% errors acceptable at breaking point
    'events_submitted': ['count>500000'], // Target: process at least 500k events
    'bulk_request_latency_ms': ['p(95)<2000', 'p(99)<5000'],
  },
  
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)', 'count'],
};

const ORCHESTRATOR_URL = config.orchestratorUrl;
const EVENTS_PER_REQUEST = config.eventsPerRequest;

export function setup() {
  console.log(`Starting stress test against ${ORCHESTRATOR_URL}`);
  const healthCheck = checkHealth();
  if (!healthCheck) {
    throw new Error('Orchestrator health check failed. Aborting test.');
  }
  console.log('Orchestrator is healthy. Starting stress test...');
  
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
  
  // Minimal sleep - stress test pushes the system hard
  sleep(0.05); // 50ms between requests = ~20 requests/sec per VU
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`Stress test completed. Total duration: ${duration}s`);
  console.log('Check metrics to identify breaking point and maximum throughput.');
}
