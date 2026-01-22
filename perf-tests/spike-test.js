// Spike Test - Sudden traffic bursts to validate recovery
// Pattern: baseline → 10x spike → baseline (repeat)

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
    // Baseline: 100 VUs for 2 minutes
    { duration: '2m', target: 100 },
    // SPIKE: Jump to 1000 VUs immediately (10x increase)
    { duration: '1m', target: 1000 },
    // Return to baseline
    { duration: '2m', target: 100 },
    // Second spike
    { duration: '1m', target: 1000 },
    // Return to baseline
    { duration: '2m', target: 100 },
    // Final spike
    { duration: '1m', target: 1000 },
    // Return to baseline and ramp down
    { duration: '2m', target: 100 },
    { duration: '1m', target: 0 },
  ],
  
  thresholds: {
    ...commonThresholds,
    // Spike test focuses on recovery time
    'http_req_duration': ['p(95)<1000', 'p(99)<2000'], // Higher latency during spikes
    'http_req_failed': ['rate<0.02'], // Up to 2% errors during spikes
    'events_submitted': ['count>200000'],
    // Recovery time: latency should return to normal after spike
    'bulk_request_latency_ms': ['p(95)<1000'],
  },
  
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'p(99.9)', 'count'],
};

const ORCHESTRATOR_URL = config.orchestratorUrl;
const EVENTS_PER_REQUEST = config.eventsPerRequest;

export function setup() {
  console.log(`Starting spike test against ${ORCHESTRATOR_URL}`);
  const healthCheck = checkHealth();
  if (!healthCheck) {
    throw new Error('Orchestrator health check failed. Aborting test.');
  }
  console.log('Orchestrator is healthy. Starting spike test...');
  console.log('Test pattern: baseline → 10x spike → baseline (repeated)');
  
  return {
    startTime: Date.now(),
    spikeCount: 0,
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
  
  // Variable sleep based on current VU count
  // During spikes, we want more aggressive load
  const vuCount = __VU || 100;
  if (vuCount > 500) {
    // During spike: minimal sleep
    sleep(0.05);
  } else {
    // During baseline: normal sleep
    sleep(0.1);
  }
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`Spike test completed. Total duration: ${duration}s`);
  console.log('Analyze metrics to validate recovery time and queue behavior.');
}
