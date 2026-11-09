#!/bin/sh
cd "$(dirname "$0")/web"
echo "Cubelight  http://127.0.0.1:8731"
exec python3 -m http.server 8731 --bind 127.0.0.1
