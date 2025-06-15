#!/bin/bash

# Default values
CREATE_README=false
NUM_GAMES=50
TARGET_DIR="."

# Helper: print usage
usage() {
  echo "Usage: $0 [--create-readme|-r] [--games|-g <number>] [--directory|-d <path>]"
  exit 1
}

# Parse arguments
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --create-readme|-r)
      CREATE_README=true
      ;;
    --games|-g)
      shift
      NUM_GAMES="$1"
      ;;
    --directory|-d)
      shift
      TARGET_DIR="$1"
      ;;
    *)
      echo "Unknown parameter: $1"
      usage
      ;;
  esac
  shift
done

# Ensure target directory exists
mkdir -p "$TARGET_DIR"

# Fetch game data from GOG
API_URL="https://catalog.gog.com/v1/catalog?limit=$NUM_GAMES&systems=in%3Awindows&order=desc%3Abestselling&productType=in%3Agame%2Cpack"
RESPONSE=$(curl -s "$API_URL")

# Extract titles and create folders
echo "$RESPONSE" | jq -r '.products[].title' | while read -r TITLE; do
  # Replace problematic characters in folder names
  SAFE_TITLE=$(echo "$TITLE" | tr -cd '[:alnum:] _-')
  GAME_DIR="$TARGET_DIR/$SAFE_TITLE"
  mkdir -p "$GAME_DIR"
  if $CREATE_README; then
    touch "$GAME_DIR/README.md"
  fi
  echo "Created: $GAME_DIR"
done
