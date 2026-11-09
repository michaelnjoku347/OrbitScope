#!/bin/sh
# Compile the ray tracer and drop the WASM module next to the page.
set -e
cd "$(dirname "$0")"
cargo build --release --target wasm32-unknown-unknown --lib
cp target/wasm32-unknown-unknown/release/cubelight.wasm web/cubelight.wasm
echo "wrote web/cubelight.wasm ($(wc -c < web/cubelight.wasm) bytes)"
