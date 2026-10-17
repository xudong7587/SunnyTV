#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/host
${CXX:-g++} -std=c++17 -O1 -g -Wall -Wextra -Werror -Wno-deprecated-declarations \
  ${SANITIZE:+-fsanitize=address,undefined -fno-omit-frame-pointer} \
  -Iinclude $(pkg-config --cflags libcurl) \
  source/json.cpp source/network.cpp source/media.cpp source/gateway.cpp tests/core.cpp \
  -pthread -lcurl -o build/host/core-tests
build/host/core-tests
if [[ "${NETWORK_TESTS:-0}" == 1 ]]; then
  ${CXX:-g++} -std=c++17 -O1 -g -Wall -Wextra -Werror -Wno-deprecated-declarations \
    ${SANITIZE:+-fsanitize=address,undefined -fno-omit-frame-pointer} \
    -Iinclude $(pkg-config --cflags libcurl) \
    source/json.cpp source/network.cpp source/gateway.cpp tests/network.cpp \
    -pthread -lcurl -o build/host/network-tests
  python3 tests/network.py
fi
