#!/usr/bin/env bash

find . -name "*~" -delete
rm -f .nrepl-port

rm -rf resources/public/js/descryptors.js
rm -rf resources/public/js/out
rm -rf .cpcache

