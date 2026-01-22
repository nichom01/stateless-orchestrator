// Shared k6 configuration and utilities for performance tests

import { Counter, Rate, Trend } from 'k6/metrics';
import { check, sleep } from 'k6';
import http from 'k6/http';

// Custom metrics
export const eventsSubmitted = new Counter('events_submitted');
export const eventErrors = new Rate('event_errors');
export const eventLatency = new Trend('event_latency_ms');
export const routingSuccessRate = new Rate('routing_success_rate');
export const bulkRequestLatency = new Trend('bulk_request_latency_ms');
export const eventsPerSecond = new Rate('events_per_second');

// Configuration from environment variables
export const config = {
  orchestratorUrl: __ENV.ORCHESTRATOR_URL || 'http://orchestrator:8080',
  eventsPerRequest: parseInt(__ENV.EVENTS_PER_REQUEST || '100'),
  testDataPath: __ENV.TEST_DATA_PATH || '/scripts/test-data/test-orders-2500.jsonl',
};

// Generate a single OrderCreated event
export function generateOrderEvent(orderId) {
  const customerTiers = ['standard', 'premium', 'enterprise'];
  const tier = customerTiers[Math.floor(Math.random() * customerTiers.length)];
  const orderTotal = Math.random() * 2000; // 0-2000 range
  
  return {
    type: 'OrderCreated',
    correlationId: `order-${orderId || Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    orchestrationName: 'order-processing',
    context: {
      orderId: `ORD-${orderId || Date.now()}`,
      customerId: `CUST-${Math.floor(Math.random() * 10000)}`,
      customerTier: tier,
      orderTotal: parseFloat(orderTotal.toFixed(2)),
      items: [{
        sku: `ITEM-${Math.floor(Math.random() * 1000)}`,
        quantity: Math.floor(Math.random() * 5) + 1,
        price: parseFloat((orderTotal / (Math.floor(Math.random() * 3) + 1)).toFixed(2))
      }]
    }
  };
}

// Generate NDJSON content from events array
export function generateNdjsonContent(events) {
  return events.map(e => JSON.stringify(e)).join('\n');
}

// Load test data from file (if available)
export function loadTestData() {
  try {
    // In k6, we can't directly read files, so this is a placeholder
    // Test data will be passed via environment or generated on-the-fly
    return null;
  } catch (e) {
    return null;
  }
}

// Submit bulk events via NDJSON endpoint
export function submitBulkEvents(events, url = null) {
  const targetUrl = url || `${config.orchestratorUrl}/api/orchestrator/events/bulk-ndjson`;
  const ndjsonContent = generateNdjsonContent(events);
  
  const params = {
    headers: {
      'Content-Type': 'text/plain',
    },
    tags: {
      name: 'bulk-ndjson',
    },
  };
  
  const startTime = Date.now();
  const response = http.post(targetUrl, ndjsonContent, params);
  const latency = Date.now() - startTime;
  
  // Record metrics
  bulkRequestLatency.add(latency);
  eventsSubmitted.add(events.length);
  
  const success = check(response, {
    'status is 202': (r) => r.status === 202,
    'response has successful count': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.successful !== undefined;
      } catch (e) {
        return false;
      }
    },
  });
  
  if (!success) {
    eventErrors.add(1);
  } else {
    routingSuccessRate.add(1);
    try {
      const body = JSON.parse(response.body);
      if (body.successful !== undefined) {
        eventsPerSecond.add(body.successful);
      }
    } catch (e) {
      // Ignore parsing errors
    }
  }
  
  return {
    response,
    latency,
    success,
  };
}

// Health check function
export function checkHealth(url = null) {
  try {
    const targetUrl = url || `${config.orchestratorUrl}/api/orchestrator/health`;
    const response = http.get(targetUrl, { timeout: '10s' });
    
    const checks = check(response, {
      'health check status is 200': (r) => r.status === 200,
      'health check response is UP': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body.status === 'UP' || body.status === 'UP';
        } catch (e) {
          return false;
        }
      },
    });
    
    return checks;
  } catch (error) {
    console.error(`Health check failed: ${error.message}`);
    return false;
  }
}

// Common thresholds (can be overridden in individual tests)
export const commonThresholds = {
  'http_req_duration': ['p(95)<500', 'p(99)<1000'],
  'http_req_failed': ['rate<0.01'], // Less than 1% errors
  'event_errors': ['rate<0.01'],
  'routing_success_rate': ['rate>0.99'],
};
