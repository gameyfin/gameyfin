#!/bin/bash

# Default values
CREATE_README=false
NUM_GAMES=50
DEST_DIR="./games"

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --create-readme|-r)
      CREATE_README=true
      shift
      ;;
    --games|-g)
      NUM_GAMES="$2"
      shift 2
      ;;
    --directory|-d)
      DEST_DIR="$2"
      shift 2
      ;;
    *)
      echo "Unknown parameter: $1"
      exit 1
      ;;
  esac
done

# Create parent directory
mkdir -p "$DEST_DIR"

# Fetch top-selling games from SteamSpy API
GAMES_JSON=$(curl -s "https://steamspy.com/api.php?request=all")

# Extract the top N game names
GAME_TITLES=$(echo "$GAMES_JSON" | jq -r 'to_entries | sort_by(.value.median_2weeks) | reverse | .[0:'"$NUM_GAMES"'] | .[].value.name')

# Accumulate sanitized paths to batch-create directories/files (much faster on Windows/Git Bash)
paths=()

# Create folders and README.md files
while IFS= read -r title; do
  # Start with the raw title and sanitize only filesystem-invalid characters
  folder_name="$title"

  # Remove any carriage returns (in case of CRLF sources)
  folder_name="${folder_name//$'\r'/}"

  # Replace Windows-invalid filename characters with a space: <>:"/\|?*
  # Keep Unicode characters (e.g., Japanese) intact
  folder_name="${folder_name//[<>:\"\/\\|?*]/ }"

  # Collapse multiple spaces into a single space
  while [[ "$folder_name" == *"  "* ]]; do
    folder_name="${folder_name//  / }"
  done

  # Trim leading/trailing whitespace while preserving internal spaces (pure Bash)
  folder_name="${folder_name#"${folder_name%%[!$'\t\n\r ']*}"}"
  folder_name="${folder_name%"${folder_name##*[!$'\t\n\r ']}"}"

  # Remove any trailing dots (invalid on Windows)
  while [[ "$folder_name" == *. ]]; do
    folder_name="${folder_name%.}"
  done

  # Skip creating a folder if name is empty after sanitization
  if [[ -z "$folder_name" ]]; then
    echo "Skipping invalid or empty folder name for title: $title"
    continue
  fi

  # Avoid Windows reserved device names (case-insensitive)
  lower=$(printf '%s' "$folder_name" | tr '[:upper:]' '[:lower:]')
  case "$lower" in
    con|prn|aux|nul|com[1-9]|lpt[1-9])
      folder_name="_${folder_name}"
      ;;
  esac

  # Safety: limit folder name length to 240 characters and re-trim trailing spaces/dots
  if (( ${#folder_name} > 240 )); then
    folder_name="${folder_name:0:240}"
    folder_name="${folder_name%"${folder_name##*[!$'\t\n\r ']}"}"
    while [[ "$folder_name" == *. ]]; do
      folder_name="${folder_name%.}"
    done
  fi

  game_path="$DEST_DIR/$folder_name"
  paths+=("$game_path")

done <<< "$GAME_TITLES"

# Batch create directories (single process is much faster under MSYS/Git Bash)
if (( ${#paths[@]} > 0 )); then
  mkdir -p "${paths[@]}"
  echo "Created ${#paths[@]} folders under: $DEST_DIR"

  if $CREATE_README; then
    # Build README paths and create in one call
    readme_paths=()
    for p in "${paths[@]}"; do
      readme_paths+=("$p/README.md")
    done
    touch "${readme_paths[@]}"
    echo "Created README.md in ${#readme_paths[@]} folders"
  fi
fi
