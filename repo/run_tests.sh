#!/bin/bash
set -e
cd "$(dirname "$0")/meridianmart"
chmod +x run_tests.sh
exec ./run_tests.sh
