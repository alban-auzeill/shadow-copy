shadow_copy_filter() {
  local ABSOLUTE="$1"
  local RELATIVE="$2"
  local FILENAME="$3"

  # ignore a file with a given name
  if [[ $FILENAME == "a.out" ]]; then
    return 1
  fi

  # ignore a directory with a given name
  if [[ $FILENAME == "tmp/" ]]; then
    return 1
  fi

  # ignore a relative path
  if [[ $RELATIVE == "build/tmp/" ]]; then
    return 1
  fi

  # ignore symbolic links
  if [[ -L $ABSOLUTE ]]; then
    return 1
  fi

  # ignore file using a regular expression
  if [[ $FILENAME =~ ^file[0-9]+$ ]]; then
    return 1
  fi

  # ignore file extensions using a regular expression
  if [[ $FILENAME =~ \.json$ ]]; then
    return 1
  fi

  # ignore big file with more than 100_000_000 bytes
  if (( $(stat -c%s "$ABSOLUTE") > 100000000 )); then
    return 1
  fi

  # the filter accept the file
  return 0
}
