#!/usr/bin/env bash

echo "cleaning up..."
./cleanup.sh

rm -rf release
mkdir release



echo "updating version string..."

TAG="`git tag | head -n1 | awk '{print $1}'`"
COMMIT="`git rev-parse --short HEAD`"
__version="
{:git/tag \"$TAG\"
 :git/commit \"$COMMIT\"}"

echo "$__version" > resources/version.edn



echo "compiling clojurescript..."
clj -A:prod
rm -rf resources/public/js/out


echo "compiling clojure..."

## build clj
clj -A:uberjar

echo "copying..."

## copy resources
cp -R resources release/resources
cp -R templates release/
cp -R scripts/* release/
cp -R conf release/
