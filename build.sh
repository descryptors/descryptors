#!/usr/bin/env bash

echo "cleaning up..."
./cleanup.sh

echo "updating version string..."
sed -i -re "s/build [a-zA-Z0-9_]+/build $(git rev-parse --short HEAD)/g" src/cljc/descryptors/schema.cljc

echo "compiling clojurescript..."
clj -A:prod
rm -rf resources/public/js/out


echo "making release..."

## cleanup
rm -rf release
mkdir release

## build clj
clj -A:uberjar

## copy resources
cp -R resources release/resources
cp -R templates release/
cp -R scripts/* release/
cp -R conf release/
