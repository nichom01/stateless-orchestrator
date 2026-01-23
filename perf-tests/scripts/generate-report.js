#!/usr/bin/env node

/**
 * Generate HTML report from k6 JSON results
 * Usage: node generate-report.js --input results.json --output report.html --type load
 */

const fs = require('fs');
const path = require('path');

// Parse command line arguments
function parseArgs() {
  const args = process.argv.slice(2);
  const config = {
    input: null,
    output: null,
    type: 'load',
  };
  
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--input' && i + 1 < args.length) {
      config.input = args[i + 1];
      i++;
    } else if (args[i] === '--output' && i + 1 < args.length) {
      config.output = args[i + 1];
      i++;
    } else if (args[i] === '--type' && i + 1 < args.length) {
      config.type = args[i + 1];
      i++;
    }
  }
  
  return config;
}

// Parse k6 summary JSON format (from --summary-export)
function parseSummaryJSON(jsonPath) {
  try {
    const content = fs.readFileSync(jsonPath, 'utf8');
    const data = JSON.parse(content);
    
    // Debug logging to verify data extraction
    console.log('Debug: Extracting metrics from summary JSON...');
    console.log('Debug: events_submitted:', data.metrics?.events_submitted);
    console.log('Debug: bulk_request_latency_ms:', data.metrics?.bulk_request_latency_ms);
    console.log('Debug: http_req_duration:', data.metrics?.http_req_duration);
    
    const result = {
      totalEvents: data.metrics?.events_submitted?.count || 0,
      errorRate: data.metrics?.http_req_failed?.value || 0,
      httpReqDuration: data.metrics?.http_req_duration || {},
      bulkLatency: data.metrics?.bulk_request_latency_ms || {},
      iterations: data.metrics?.iterations?.count || 0,
      checks: data.metrics?.checks || {},
      isSummaryFormat: true
    };
    
    console.log('Debug: Extracted totalEvents:', result.totalEvents);
    console.log('Debug: Extracted bulkLatency:', result.bulkLatency);
    
    return result;
  } catch (error) {
    console.error(`Error parsing summary JSON: ${error.message}`);
    return { isSummaryFormat: false };
  }
}

// Parse k6 JSON results (NDJSON format from --out json)
function parseK6Results(jsonPath) {
  try {
    const content = fs.readFileSync(jsonPath, 'utf8');
    // k6 JSON output is newline-delimited JSON
    const lines = content.trim().split('\n');
    const metrics = {};
    const dataPoints = [];
    
    lines.forEach(line => {
      if (line.trim()) {
        try {
          const entry = JSON.parse(line);
          
          // Extract metric data
          if (entry.type === 'Metric') {
            if (!metrics[entry.metric]) {
              metrics[entry.metric] = {
                name: entry.metric,
                values: [],
              };
            }
            if (entry.data && entry.data.value !== undefined) {
              metrics[entry.metric].values.push({
                time: entry.data.time || Date.now(),
                value: entry.data.value,
              });
            }
          }
          
          // Extract summary data
          if (entry.type === 'Summary') {
            dataPoints.push(entry);
          }
        } catch (e) {
          // Skip invalid JSON lines
        }
      }
    });
    
    return { metrics, dataPoints, isSummaryFormat: false };
  } catch (error) {
    console.error(`Error parsing k6 results: ${error.message}`);
    return { metrics: {}, dataPoints: [], isSummaryFormat: false };
  }
}

// Detect file format and parse accordingly
function parseK6File(jsonPath) {
  try {
    const content = fs.readFileSync(jsonPath, 'utf8').trim();
    
    // Check if it's summary JSON format (single JSON object with metrics property)
    if (content.startsWith('{') && content.includes('"metrics"')) {
      try {
        const parsed = JSON.parse(content);
        if (parsed.metrics && typeof parsed.metrics === 'object') {
          console.log('Detected summary JSON format');
          return parseSummaryJSON(jsonPath);
        }
      } catch (e) {
        // Not valid summary JSON, try NDJSON
      }
    }
    
    // Check if it's NDJSON format (multiple lines with type field)
    if (content.includes('"type":"Metric"') || content.includes('"type":"Summary"')) {
      console.log('Detected NDJSON format');
      return parseK6Results(jsonPath);
    }
    
    // Try parsing as summary JSON first (most common)
    const summaryResult = parseSummaryJSON(jsonPath);
    if (summaryResult.isSummaryFormat) {
      return summaryResult;
    }
    
    // Fall back to NDJSON
    return parseK6Results(jsonPath);
  } catch (error) {
    console.error(`Error detecting file format: ${error.message}`);
    // Try both parsers
    const summaryResult = parseSummaryJSON(jsonPath);
    if (summaryResult.isSummaryFormat) {
      return summaryResult;
    }
    return parseK6Results(jsonPath);
  }
}

// Calculate statistics
function calculateStats(values) {
  if (!values || values.length === 0) return null;
  
  const sorted = [...values].sort((a, b) => a - b);
  const len = sorted.length;
  
  return {
    min: sorted[0],
    max: sorted[len - 1],
    avg: sorted.reduce((a, b) => a + b, 0) / len,
    p50: sorted[Math.floor(len * 0.5)],
    p90: sorted[Math.floor(len * 0.9)],
    p95: sorted[Math.floor(len * 0.95)],
    p99: sorted[Math.floor(len * 0.99)],
    count: len,
  };
}

// Safe formatting helper - handles undefined/null values gracefully
function safeFormat(value, decimals = 2) {
  if (value === null || value === undefined || isNaN(value)) {
    return 'N/A';
  }
  return Number(value).toFixed(decimals);
}

// Safe formatting for integers
function safeFormatInt(value) {
  if (value === null || value === undefined || isNaN(value)) {
    return 'N/A';
  }
  return Math.round(Number(value)).toLocaleString();
}

// Generate HTML report
function generateHTMLReport(data, testType) {
  let totalEvents, errorRate, durationStats, latencyStats;
  
  // Handle summary JSON format (pre-calculated stats)
  if (data.isSummaryFormat) {
    totalEvents = data.totalEvents || 0;
    errorRate = data.errorRate || 0;
    
    // Extract pre-calculated statistics from summary format
    const httpDuration = data.httpReqDuration || {};
    const bulkLat = data.bulkLatency || {};
    
    // Validate that we have data
    if (totalEvents === 0 && (!httpDuration.count && !bulkLat.count)) {
      console.warn('Warning: Summary JSON format detected but no metrics found. Report may be empty.');
    }
    
    durationStats = {
      min: httpDuration.min,
      max: httpDuration.max,
      avg: httpDuration.avg,
      p50: httpDuration.med,
      p90: httpDuration['p(90)'],
      p95: httpDuration['p(95)'],
      p99: httpDuration['p(99)'],
      count: httpDuration.count
    };
    
    latencyStats = {
      min: bulkLat.min,
      max: bulkLat.max,
      avg: bulkLat.avg,
      p50: bulkLat.med,
      p90: bulkLat['p(90)'],
      p95: bulkLat['p(95)'],
      p99: bulkLat['p(99)'],
      count: bulkLat.count
    };
  } else {
    // Handle NDJSON format (calculate from raw values)
    const { metrics } = data;
    
    const httpReqDuration = metrics['http_req_duration']?.values || [];
    const httpReqFailed = metrics['http_req_failed']?.values || [];
    const eventsSubmitted = metrics['events_submitted']?.values || [];
    const bulkLatency = metrics['bulk_request_latency_ms']?.values || [];
    
    durationStats = calculateStats(httpReqDuration.map(v => v.value));
    latencyStats = calculateStats(bulkLatency.map(v => v.value));
    
    totalEvents = eventsSubmitted.reduce((sum, v) => sum + (v.value || 0), 0);
    errorRate = httpReqFailed.length > 0 
      ? httpReqFailed.reduce((sum, v) => sum + (v.value || 0), 0) / httpReqFailed.length 
      : 0;
  }
  
  return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Performance Test Report - ${testType.toUpperCase()}</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            background: #f5f5f5;
            color: #333;
            line-height: 1.6;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }
        header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 30px;
            border-radius: 8px;
            margin-bottom: 30px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        }
        h1 { font-size: 2em; margin-bottom: 10px; }
        .test-type { opacity: 0.9; font-size: 1.1em; }
        .timestamp { margin-top: 10px; opacity: 0.8; font-size: 0.9em; }
        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        .metric-card {
            background: white;
            padding: 20px;
            border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .metric-label {
            font-size: 0.9em;
            color: #666;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            margin-bottom: 10px;
        }
        .metric-value {
            font-size: 2em;
            font-weight: bold;
            color: #667eea;
        }
        .metric-unit {
            font-size: 0.6em;
            color: #999;
            margin-left: 5px;
        }
        .section {
            background: white;
            padding: 25px;
            border-radius: 8px;
            margin-bottom: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .section h2 {
            color: #333;
            margin-bottom: 15px;
            padding-bottom: 10px;
            border-bottom: 2px solid #667eea;
        }
        table {
            width: 100%;
            border-collapse: collapse;
        }
        th, td {
            padding: 12px;
            text-align: left;
            border-bottom: 1px solid #eee;
        }
        th {
            background: #f8f9fa;
            font-weight: 600;
            color: #555;
        }
        .status-pass { color: #10b981; font-weight: bold; }
        .status-fail { color: #ef4444; font-weight: bold; }
        .footer {
            text-align: center;
            padding: 20px;
            color: #666;
            font-size: 0.9em;
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>Performance Test Report</h1>
            <div class="test-type">Test Type: ${testType.toUpperCase()}</div>
            <div class="timestamp">Generated: ${new Date().toLocaleString()}</div>
        </header>
        
        <div class="metrics-grid">
            <div class="metric-card">
                <div class="metric-label">Total Events</div>
                <div class="metric-value">${safeFormatInt(totalEvents)}<span class="metric-unit">events</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Error Rate</div>
                <div class="metric-value">${safeFormat(errorRate * 100)}<span class="metric-unit">%</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Avg Latency</div>
                <div class="metric-value">${safeFormatInt(latencyStats?.avg)}<span class="metric-unit">ms</span></div>
            </div>
            <div class="metric-card">
                <div class="metric-label">P95 Latency</div>
                <div class="metric-value">${safeFormatInt(latencyStats?.p95)}<span class="metric-unit">ms</span></div>
            </div>
        </div>
        
        <div class="section">
            <h2>Latency Statistics</h2>
            <table>
                <thead>
                    <tr>
                        <th>Metric</th>
                        <th>Min</th>
                        <th>Avg</th>
                        <th>P50</th>
                        <th>P90</th>
                        <th>P95</th>
                        <th>P99</th>
                        <th>Max</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>HTTP Request Duration (ms)</td>
                        <td>${safeFormat(durationStats?.min)}</td>
                        <td>${safeFormat(durationStats?.avg)}</td>
                        <td>${safeFormat(durationStats?.p50)}</td>
                        <td>${safeFormat(durationStats?.p90)}</td>
                        <td>${safeFormat(durationStats?.p95)}</td>
                        <td>${safeFormat(durationStats?.p99)}</td>
                        <td>${safeFormat(durationStats?.max)}</td>
                    </tr>
                    <tr>
                        <td>Bulk Request Latency (ms)</td>
                        <td>${safeFormat(latencyStats?.min)}</td>
                        <td>${safeFormat(latencyStats?.avg)}</td>
                        <td>${safeFormat(latencyStats?.p50)}</td>
                        <td>${safeFormat(latencyStats?.p90)}</td>
                        <td>${safeFormat(latencyStats?.p95)}</td>
                        <td>${safeFormat(latencyStats?.p99)}</td>
                        <td>${safeFormat(latencyStats?.max)}</td>
                    </tr>
                </tbody>
            </table>
        </div>
        
        <div class="section">
            <h2>Test Summary</h2>
            <table>
                <tbody>
                    <tr>
                        <td><strong>Test Type</strong></td>
                        <td>${testType}</td>
                    </tr>
                    <tr>
                        <td><strong>Total Events Processed</strong></td>
                        <td>${safeFormatInt(totalEvents)}</td>
                    </tr>
                    <tr>
                        <td><strong>Error Rate</strong></td>
                        <td class="${errorRate < 0.01 ? 'status-pass' : 'status-fail'}">${safeFormat(errorRate * 100)}%</td>
                    </tr>
                    <tr>
                        <td><strong>Average Latency</strong></td>
                        <td>${safeFormat(latencyStats?.avg)} ms</td>
                    </tr>
                    <tr>
                        <td><strong>P95 Latency</strong></td>
                        <td class="${latencyStats && latencyStats.p95 < 500 ? 'status-pass' : 'status-fail'}">${safeFormat(latencyStats?.p95)} ms</td>
                    </tr>
                </tbody>
            </table>
        </div>
        
        <div class="footer">
            Generated by k6 Performance Testing Framework
        </div>
    </div>
</body>
</html>`;
}

// Main execution
function main() {
  const config = parseArgs();
  
  if (!config.input) {
    console.error('Error: --input parameter is required');
    console.error('Usage: node generate-report.js --input results.json --output report.html --type load');
    process.exit(1);
  }
  
  if (!fs.existsSync(config.input)) {
    console.error(`Error: Input file not found: ${config.input}`);
    process.exit(1);
  }
  
  const outputPath = config.output || config.input.replace('.json', '.html');
  
  console.log(`Generating report from ${config.input}...`);
  const data = parseK6File(config.input);
  const html = generateHTMLReport(data, config.type);
  
  // Ensure output directory exists
  const outputDir = path.dirname(outputPath);
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }
  
  fs.writeFileSync(outputPath, html);
  console.log(`✓ Report generated: ${outputPath}`);
}

if (require.main === module) {
  main();
}

module.exports = { parseK6Results, parseSummaryJSON, parseK6File, generateHTMLReport };
