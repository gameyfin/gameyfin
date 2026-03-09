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

# Fetch game data from GOG (max 100 per request, paginate if needed)
PAGE_SIZE=100
COLLECTED=0
CURRENT_PAGE=1

process_titles() {
  while read -r TITLE; do
    # Replace problematic characters in folder names
    SAFE_TITLE=$(echo "$TITLE" | tr -cd '[:alnum:] _-' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    GAME_DIR="$TARGET_DIR/$SAFE_TITLE"
    mkdir -p "$GAME_DIR"
    if $CREATE_README; then
      touch "$GAME_DIR/README.md"
    fi
    echo "Created: $GAME_DIR"
  done
}

while [ "$COLLECTED" -lt "$NUM_GAMES" ]; do
  REMAINING=$((NUM_GAMES - COLLECTED))
  LIMIT=$PAGE_SIZE
  if [ "$REMAINING" -lt "$PAGE_SIZE" ]; then
    LIMIT=$REMAINING
  fi

  API_URL="https://catalog.gog.com/v1/catalog?limit=$LIMIT&page=$CURRENT_PAGE&systems=in%3Awindows&order=desc%3Abestselling&productType=in%3Agame%2Cpack"
  RESPONSE=$(curl -s "$API_URL")

  TOTAL_PAGES=$(echo "$RESPONSE" | jq -r '.pages')
  TITLES=$(echo "$RESPONSE" | jq -r '.products[].title')
  COUNT=$(echo "$TITLES" | grep -c .)

  echo "$TITLES" | process_titles

  COLLECTED=$((COLLECTED + COUNT))

  # Stop if we've reached the last available page
  if [ "$CURRENT_PAGE" -ge "$TOTAL_PAGES" ]; then
    break
  fi

  CURRENT_PAGE=$((CURRENT_PAGE + 1))
done
