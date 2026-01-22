#!/usr/bin/env node

/**
 * Generate test data files for performance testing
 * Usage: node generate-test-data.js [count] [output-file]
 * 
 * Examples:
 *   node generate-test-data.js 10000 test-data-10k.jsonl
 *   node generate-test-data.js 50000 test-data-50k.jsonl
 *   node generate-test-data.js 100000 test-data-100k.jsonl
 */

const fs = require('fs');
const path = require('path');

// Configuration
const DEFAULT_COUNT = 10000;
const DEFAULT_OUTPUT_DIR = path.join(__dirname, 'test-data');
const CUSTOMER_TIERS = ['standard', 'premium', 'enterprise'];
const EVENT_TYPES = ['OrderCreated'];

// Generate a single order event
function generateOrderEvent(index) {
  const customerTier = CUSTOMER_TIERS[Math.floor(Math.random() * CUSTOMER_TIERS.length)];
  const orderTotal = Math.random() * 2000; // 0-2000 range
  const itemCount = Math.floor(Math.random() * 3) + 1; // 1-3 items
  
  const items = [];
  let remainingTotal = orderTotal;
  for (let i = 0; i < itemCount; i++) {
    const isLast = i === itemCount - 1;
    const itemPrice = isLast ? remainingTotal : Math.random() * (remainingTotal / 2);
    remainingTotal -= itemPrice;
    
    items.push({
      sku: `ITEM-${String(Math.floor(Math.random() * 10000)).padStart(5, '0')}`,
      quantity: Math.floor(Math.random() * 5) + 1,
      price: parseFloat(itemPrice.toFixed(2))
    });
  }
  
  return {
    type: 'OrderCreated',
    correlationId: `order-${String(index).padStart(6, '0')}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    orchestrationName: 'order-processing',
    context: {
      orderId: `ORD-${String(index).padStart(6, '0')}`,
      customerId: `CUST-${String(Math.floor(Math.random() * 100000)).padStart(5, '0')}`,
      customerTier: customerTier,
      orderTotal: parseFloat(orderTotal.toFixed(2)),
      items: items
    }
  };
}

// Generate test data file
function generateTestData(count, outputPath) {
  console.log(`Generating ${count} events...`);
  
  // Ensure output directory exists
  const outputDir = path.dirname(outputPath);
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }
  
  // Open write stream
  const writeStream = fs.createWriteStream(outputPath);
  let generated = 0;
  
  // Generate events in batches to avoid memory issues
  const BATCH_SIZE = 1000;
  const batches = Math.ceil(count / BATCH_SIZE);
  
  for (let batch = 0; batch < batches; batch++) {
    const batchStart = batch * BATCH_SIZE;
    const batchEnd = Math.min(batchStart + BATCH_SIZE, count);
    
    for (let i = batchStart; i < batchEnd; i++) {
      const event = generateOrderEvent(i + 1);
      writeStream.write(JSON.stringify(event) + '\n');
      generated++;
      
      // Progress indicator
      if (generated % 10000 === 0) {
        process.stdout.write(`\rGenerated ${generated}/${count} events...`);
      }
    }
  }
  
  writeStream.end();
  
  return new Promise((resolve, reject) => {
    writeStream.on('finish', () => {
      console.log(`\n✓ Generated ${generated} events`);
      console.log(`✓ Output file: ${outputPath}`);
      
      // Get file size
      const stats = fs.statSync(outputPath);
      const fileSizeMB = (stats.size / (1024 * 1024)).toFixed(2);
      console.log(`✓ File size: ${fileSizeMB} MB`);
      
      resolve(outputPath);
    });
    
    writeStream.on('error', reject);
  });
}

// Main execution
function main() {
  const args = process.argv.slice(2);
  
  let count = DEFAULT_COUNT;
  let outputFile = null;
  
  // Parse arguments
  if (args.length > 0) {
    const countArg = parseInt(args[0]);
    if (!isNaN(countArg) && countArg > 0) {
      count = countArg;
    } else {
      console.error(`Invalid count: ${args[0]}`);
      process.exit(1);
    }
  }
  
  if (args.length > 1) {
    outputFile = args[1];
  } else {
    // Generate default filename based on count
    const countLabel = count >= 1000000 ? `${Math.floor(count / 1000000)}M` :
                      count >= 1000 ? `${Math.floor(count / 1000)}k` :
                      count.toString();
    outputFile = path.join(DEFAULT_OUTPUT_DIR, `test-data-${countLabel}.jsonl`);
  }
  
  // Ensure absolute path
  if (!path.isAbsolute(outputFile)) {
    outputFile = path.resolve(process.cwd(), outputFile);
  }
  
  console.log(`Test Data Generator`);
  console.log(`===================`);
  console.log(`Count: ${count}`);
  console.log(`Output: ${outputFile}`);
  console.log('');
  
  generateTestData(count, outputFile)
    .then(() => {
      console.log('\n✓ Generation complete!');
      process.exit(0);
    })
    .catch((error) => {
      console.error('\n✗ Error generating test data:', error);
      process.exit(1);
    });
}

// Run if executed directly
if (require.main === module) {
  main();
}

module.exports = { generateOrderEvent, generateTestData };
