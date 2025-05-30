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
GAMES_JSON=$(curl -s "https://steamspy.com/api.php?request=top100in2weeks")

# Extract the top N game names
GAME_TITLES=$(echo "$GAMES_JSON" | jq -r 'to_entries | sort_by(.value.median_forever) | reverse | .[0:'"$NUM_GAMES"'] | .[].value.name')

# Create folders and README.md files
while IFS= read -r title; do
  # Sanitize title to make a valid folder name
  folder_name=$(echo "$title" | tr -cd '[:alnum:] _-')
  game_path="$DEST_DIR/$folder_name"
  mkdir -p "$game_path"

  if $CREATE_README; then
    touch "$game_path/README.md"
  fi

  echo "Created folder: $game_path"
done <<< "$GAME_TITLES"
