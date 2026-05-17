#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Default task is to build the full APK
BUILD_TASK="assembleDebug"
IS_CHECK_ONLY=false
IS_RELEASE=false

# Parse flags
for arg in "$@"
do
    case $arg in
        -c|--check)
        BUILD_TASK="compileDebugKotlin"
        IS_CHECK_ONLY=true
        shift
        ;;
        -r|--release)
        BUILD_TASK="assembleRelease"
        IS_RELEASE=true
        shift
        ;;
    esac
done

# Step 0: Run local lightning-fast pre-flight XML validation check
python3 validate_assets.py

echo "=================================================="
if [ "$IS_CHECK_ONLY" = true ] ; then
    echo "🔍 Checking Kotlin & Java Syntax in Docker (No Mounts)"
elif [ "$IS_RELEASE" = true ] ; then
    echo "🏗️ Building Signed Release APK in Docker (No Mounts)"
else
    echo "🏗️ Building Parental Control APK in Docker (No Mounts)"
fi
echo "=================================================="

# Ensure the local artifacts directory exists
mkdir -p artifacts

# Step 1: Build the Docker builder image (passing our custom build task arg)
echo "🐳 Step 1: Building Docker image (task: $BUILD_TASK)..."
docker build -t parental-control-builder --build-arg BUILD_TASK=$BUILD_TASK -f Dockerfile.build .

# If checking only, we stop here (no container instantiation or copying required!)
if [ "$IS_CHECK_ONLY" = true ] ; then
    echo "=================================================="
    echo "✅ Check Success! The entire project compiled successfully."
    echo "=================================================="
    exit 0
fi

# Step 2: Create a temporary container instance
echo "📦 Step 2: Creating temporary container..."
CONTAINER_ID=$(docker create parental-control-builder)

# Step 3: Copy the compiled APK from the container into our artifacts folder
echo "💾 Step 3: Copying APK to local artifacts/ directory..."
if [ "$IS_RELEASE" = true ] ; then
    docker cp $CONTAINER_ID:/app/app/build/outputs/apk/release/app-release.apk ./artifacts/app-release.apk
else
    docker cp $CONTAINER_ID:/app/app/build/outputs/apk/debug/app-debug.apk ./artifacts/app-debug.apk
fi

# Step 4: Delete the temporary container
echo "🧹 Step 4: Cleaning up temporary container..."
docker rm -v $CONTAINER_ID

echo "=================================================="
if [ "$IS_RELEASE" = true ] ; then
    echo "🎉 Build Success! Signed Release APK copied to: ./artifacts/app-release.apk"
else
    echo "🎉 Build Success! APK copied to: ./artifacts/app-debug.apk"
fi
echo "=================================================="
