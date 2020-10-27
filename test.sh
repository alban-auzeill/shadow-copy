#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
export SHADOW_COPY_SH="${SCRIPT_DIR}/shadow-copy.sh"

trap_failed_test() {
  local ERROR_LOCATION="$1"
  printf '%s \033[91m[FAILED]\n== FAILURE ==\033[0m\n' "${ERROR_LOCATION}"
}

run_one_test() {
  export TEST_DIR="$1"
  export TEST_NAME="$(basename -- "${TEST_DIR}")"

  printf '\033[93m-- %s\033[0m\n' "${TEST_NAME}"
  (
    cd "${TEST_DIR}"
    export TEST_FILE="${TEST_DIR}/clean.sh"
    trap 'trap_failed_test "${TEST_FILE}:$LINENO"' ERR
    # shellcheck source=unit-tests/simple-case/clean.sh
    source "${TEST_FILE}"

    cd "${TEST_DIR}"
    export TEST_FILE="${TEST_DIR}/test.sh"
    # shellcheck source=unit-tests/simple-case/test.sh
    source "${TEST_FILE}"
    trap - ERR
  )
}

run_all_tests() {
  while read -r TEST_DIR; do
    run_one_test "${TEST_DIR}"
  done < <(find "${SCRIPT_DIR}/unit-tests" -mindepth 1 -maxdepth 1 -type d | sort)
  printf '\033[32m== SUCCESS ==\033[0m\n'
}

run_all_tests
