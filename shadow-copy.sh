#!/usr/bin/env bash
set -euo pipefail

export SHADOW_DIRECTORY_NAME="${SHADOW_DIRECTORY_NAME-.shadow-copy}"
export SHADOW_COPY_FILTER_SCRIPT="${SHADOW_DIRECTORY_NAME}-filter.sh"
export SOURCE_BASE_DIRECTORY=""
export SHADOW_BASE_DIRECTORY=""
export LAST_SHADOW_BASE_DIRECTORY=""

# ensure locale decimal_point is '.' and not ',' for `stat` output
export LC_NUMERIC="en_US.UTF-8"

shadow_copy_all_directories() {
  local HAS_RELATIVE_SOURCE_BASE_DIRECTORY="${1+true}"
  if [ "${HAS_RELATIVE_SOURCE_BASE_DIRECTORY}" == "true" ]; then
    while [ "${HAS_RELATIVE_SOURCE_BASE_DIRECTORY}" == "true" ]; do
      local RELATIVE_SOURCE_BASE_DIRECTORY="$1"
      shadow_copy_directory "${RELATIVE_SOURCE_BASE_DIRECTORY}"
      shift
      HAS_RELATIVE_SOURCE_BASE_DIRECTORY="${1+true}"
    done
  else
    shadow_copy_directory "."
  fi
}

shadow_copy_directory() {
  local RELATIVE_SOURCE_BASE_DIRECTORY="${1%/}/"
  if [ ! -d "${RELATIVE_SOURCE_BASE_DIRECTORY}" ]; then
    echo "Directory not found: ${RELATIVE_SOURCE_BASE_DIRECTORY}" 1>&2
    return 1
  fi
  local RELATIVE_SHADOW_BASE_DIRECTORY_PARENT
  if [ "${RELATIVE_SOURCE_BASE_DIRECTORY}" == "./" ]; then
    RELATIVE_SHADOW_BASE_DIRECTORY_PARENT="${SHADOW_DIRECTORY_NAME}"
  else
    RELATIVE_SHADOW_BASE_DIRECTORY_PARENT="${RELATIVE_SOURCE_BASE_DIRECTORY}${SHADOW_DIRECTORY_NAME}"
  fi
  export SOURCE_BASE_DIRECTORY
  SOURCE_BASE_DIRECTORY="$(realpath "${RELATIVE_SOURCE_BASE_DIRECTORY}")"
  local SHADOW_BASE_DIRECTORY_PARENT="${SOURCE_BASE_DIRECTORY}/${SHADOW_DIRECTORY_NAME}"
  if [ ! -d "${SHADOW_BASE_DIRECTORY_PARENT}" ]; then
    mkdir "${SHADOW_BASE_DIRECTORY_PARENT}"
  fi
  export LAST_SHADOW_BASE_DIRECTORY="$(ls -1A "${SHADOW_BASE_DIRECTORY_PARENT}" | tail -n 1)"
  if [ -n "${LAST_SHADOW_BASE_DIRECTORY}" ]; then
      LAST_SHADOW_BASE_DIRECTORY="${SHADOW_BASE_DIRECTORY_PARENT}/${LAST_SHADOW_BASE_DIRECTORY}"
  fi
  local CURRENT_DATE
  CURRENT_DATE="$(date "+%Y.%m.%d-%Hh%M")"
  local SUB_DIRECTORY_INDEX="1"
  export SHADOW_BASE_DIRECTORY="${SHADOW_BASE_DIRECTORY_PARENT}/${CURRENT_DATE}-${SUB_DIRECTORY_INDEX}"
  while [ -d "${SHADOW_BASE_DIRECTORY}" ]; do
    SUB_DIRECTORY_INDEX=$(( SUB_DIRECTORY_INDEX + 1 ))
    SHADOW_BASE_DIRECTORY="${SHADOW_BASE_DIRECTORY_PARENT}/${CURRENT_DATE}-${SUB_DIRECTORY_INDEX}"
  done
  mkdir "${SHADOW_BASE_DIRECTORY}"
  export RELATIVE_SHADOW_BASE_DIRECTORY="${RELATIVE_SHADOW_BASE_DIRECTORY_PARENT}/${CURRENT_DATE}-${SUB_DIRECTORY_INDEX}"
  echo "${RELATIVE_SHADOW_BASE_DIRECTORY}"
  local SHADOW_COPY_FILTER_SCRIPT_PATH="${SOURCE_BASE_DIRECTORY}/${SHADOW_COPY_FILTER_SCRIPT}"
  if [ -e "${SHADOW_COPY_FILTER_SCRIPT_PATH}" ]; then
    # shellcheck source=/dev/null
    source "${SHADOW_COPY_FILTER_SCRIPT_PATH}"
  fi
  shadow_copy_directory_recursive ""
}

shadow_copy_directory_recursive() {
  local CURRENT_PATH="$1"
  while read -r CHILD_ABSOLUTE_PATH; do
    local CHILD_FILENAME
    CHILD_FILENAME="$(basename -- "${CHILD_ABSOLUTE_PATH}")"
    local DIR_SUFFIX=""
    if [ -d "${CHILD_ABSOLUTE_PATH}" ]; then
      DIR_SUFFIX="/"
    fi
    local CHILD_RELATIVE_PATH
    if [ -z "${CURRENT_PATH}" ]; then
      CHILD_RELATIVE_PATH="${CHILD_FILENAME}"
    else
      CHILD_RELATIVE_PATH="${CURRENT_PATH}/${CHILD_FILENAME}"
    fi
    if call_shadow_copy_filter "${CHILD_ABSOLUTE_PATH}${DIR_SUFFIX}" "${CHILD_RELATIVE_PATH}${DIR_SUFFIX}" "${CHILD_FILENAME}${DIR_SUFFIX}"; then
      local SHADOW_ABSOLUTE_PATH="${SHADOW_BASE_DIRECTORY}/${CHILD_RELATIVE_PATH}"
      if [ -L "${CHILD_ABSOLUTE_PATH}" ]; then
        # symbolic link
        cp --no-dereference --no-target-directory "${CHILD_ABSOLUTE_PATH}" "${SHADOW_ABSOLUTE_PATH}"
      elif [ -f "${CHILD_ABSOLUTE_PATH}" ]; then
        # regular file
        # check if the file already exists in the last shadow copy with the exact same last modified time
        if [ -n "${LAST_SHADOW_BASE_DIRECTORY}" ] && \
           [ -f "${LAST_SHADOW_BASE_DIRECTORY}/${CHILD_RELATIVE_PATH}" ] && \
           [ "$(stat -c "%.Y" "${LAST_SHADOW_BASE_DIRECTORY}/${CHILD_RELATIVE_PATH}")" == "$(stat -c "%.Y" "${CHILD_ABSOLUTE_PATH}")" ]; then
          # create hardlink
          ln --no-target-directory "${LAST_SHADOW_BASE_DIRECTORY}/${CHILD_RELATIVE_PATH}" "${SHADOW_ABSOLUTE_PATH}"
        else
          # try to perform a lightweight copy where the data blocks are copied only when modified
          cp --reflink=auto --preserve=all --no-target-directory "${CHILD_ABSOLUTE_PATH}" "${SHADOW_ABSOLUTE_PATH}"
        fi
    elif [ -d "${CHILD_ABSOLUTE_PATH}" ]; then
        # directory
        mkdir "${SHADOW_ABSOLUTE_PATH}"
        shadow_copy_directory_recursive "${CHILD_RELATIVE_PATH}"
      else
        # unsupported file type
        local FILE_TYPE
        if [ -b "${CHILD_ABSOLUTE_PATH}" ]; then
          FILE_TYPE="block special"
        elif [ -c "${CHILD_ABSOLUTE_PATH}" ]; then
          FILE_TYPE="character special"
        elif [ -p "${CHILD_ABSOLUTE_PATH}" ]; then
          FILE_TYPE="named pipe"
        elif [ -S "${CHILD_ABSOLUTE_PATH}" ]; then
          FILE_TYPE="socket"
        else
          FILE_TYPE="unknown"
        fi
        stat -c "unsupported '${FILE_TYPE}' file type, time of last data modification: %y" "${CHILD_ABSOLUTE_PATH}" > "${SHADOW_ABSOLUTE_PATH}"
        touch --reference="${CHILD_ABSOLUTE_PATH}" "${SHADOW_ABSOLUTE_PATH}"
      fi
    fi
  done < <(find "${SOURCE_BASE_DIRECTORY}/${CURRENT_PATH}" -mindepth 1 -maxdepth 1 | sort)
}

call_shadow_copy_filter() {
  local ABSOLUTE="$1"
  local RELATIVE="$2"
  local FILENAME="$3"
  if [[ $FILENAME == "${SHADOW_DIRECTORY_NAME}/" ]]; then
    return 1
  elif [[ $FILENAME == "${SHADOW_COPY_FILTER_SCRIPT}" ]]; then
    return 1
  elif ! shadow_copy_filter "${ABSOLUTE}" "${RELATIVE}" "${FILENAME}"; then
    return 1
  fi
  return 0
}

# override this function in SHADOW_COPY_FILTER_SCRIPT
shadow_copy_filter() {
  #local ABSOLUTE="$1"
  #local RELATIVE="$2"
  #local FILENAME="$3"
  return 0
}

shadow_copy_all_directories "$@"
