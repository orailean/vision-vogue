#!/bin/bash

# Image downloader script using DuckDuckGo image search
# Usage: ./fetch-images.sh "search term" [count]

set -e

# Check if search term is provided
if [ -z "$1" ]; then
    echo "Usage: $0 \"search term\" [count]"
    echo "Example: $0 \"cute cats\" 10"
    exit 1
fi

SEARCH_TERM="$1"
COUNT="${2:-10}"  # Default to 10 images if count not specified

# Create folder name (replace spaces with underscores)
FOLDER_NAME=$(echo "$SEARCH_TERM" | tr ' ' '_')
FILE_PREFIX=$(echo "$SEARCH_TERM" | tr ' ' '_')

# Create directory if it doesn't exist
mkdir -p "$FOLDER_NAME"

echo "Searching for '$SEARCH_TERM'..."
echo "Will download $COUNT images to folder: $FOLDER_NAME"

# URL encode the search term (replace spaces with +)
ENCODED_TERM=$(echo "$SEARCH_TERM" | sed 's/ /+/g')

# Fetch DuckDuckGo image search page
echo "Fetching image URLs from DuckDuckGo..."
DDG_URL="https://duckduckgo.com/?t=h_&q=${ENCODED_TERM}&ia=images&iax=images"

# Get the page and extract vqd token
PAGE=$(curl -s -A "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" "$DDG_URL")

# Extract vqd token
VQD=$(echo "$PAGE" | grep -o 'vqd="[^"]*"' | head -1 | sed 's/vqd="//;s/"//')

if [ -z "$VQD" ]; then
    echo "Error: Could not get search token"
    exit 1
fi

echo "Got search token, fetching images..."

# Call the DuckDuckGo image API
API_URL="https://duckduckgo.com/i.js?l=us-en&o=json&q=${ENCODED_TERM}&vqd=${VQD}&f=,,,&p=1&v7=1"

RESPONSE=$(curl -s -A "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36" \
    -H "Referer: https://duckduckgo.com/" \
    "$API_URL")

# Extract image URLs from JSON response
# Look for "image":"url" patterns
IMAGE_URLS=$(echo "$RESPONSE" | sed 's/},{/}\n{/g' | grep -o '"image":"[^"]*"' | sed 's/"image":"//;s/"$//' | head -n "$COUNT")

if [ -z "$IMAGE_URLS" ]; then
    echo "Error: No images found in response"
    echo "This might be due to DuckDuckGo rate limiting or changes in their API"
    exit 1
fi

# Count actual URLs found
URL_COUNT=$(echo "$IMAGE_URLS" | grep -c "^http" || true)
echo "Found $URL_COUNT image URLs"

# Download images
COUNTER=1
SUCCESSFUL=0

echo "$IMAGE_URLS" | while IFS= read -r url; do
    if [ -n "$url" ] && [ "$url" != " " ]; then
        # Unescape URL if needed
        url=$(echo "$url" | sed 's/\\//g')

        # Get file extension from URL or default to jpg
        EXT=$(echo "$url" | grep -o '\.\(jpg\|jpeg\|png\|gif\|webp\)' | tail -1)
        [ -z "$EXT" ] && EXT=".jpg"

        FILENAME="${FOLDER_NAME}/${FILE_PREFIX}_${COUNTER}${EXT}"

        echo "Downloading image $COUNTER/$URL_COUNT..."

        if curl -s -L --max-time 30 -A "Mozilla/5.0" -o "$FILENAME" "$url" 2>/dev/null; then
            # Check if file was actually downloaded and has content
            if [ -s "$FILENAME" ]; then
                FILE_SIZE=$(du -h "$FILENAME" | cut -f1)
                echo "✓ Saved: $FILENAME ($FILE_SIZE)"
                SUCCESSFUL=$((SUCCESSFUL + 1))
            else
                echo "✗ Failed: Empty file"
                rm -f "$FILENAME"
            fi
        else
            echo "✗ Failed to download"
        fi

        COUNTER=$((COUNTER + 1))

        # Small delay to be respectful
        sleep 0.5
    fi
done

echo ""
echo "Download complete!"
echo "Successfully downloaded images to: $FOLDER_NAME/"