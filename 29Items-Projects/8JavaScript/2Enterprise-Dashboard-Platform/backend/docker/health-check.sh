#!/bin/sh
# Health check script for backend container

# Check if the API is responding
if curl -f -s http://localhost:3001/health > /dev/null; then
    echo "Backend health check: PASS"
    exit 0
else
    echo "Backend health check: FAIL"
    exit 1
fi