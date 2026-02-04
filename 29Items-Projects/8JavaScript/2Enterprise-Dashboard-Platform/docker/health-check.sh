#!/bin/sh
# Health check script for frontend container

# Check if the application is responding
if curl -f -s http://localhost:80/health > /dev/null; then
    echo "Frontend health check: PASS"
    exit 0
else
    echo "Frontend health check: FAIL"
    exit 1
fi