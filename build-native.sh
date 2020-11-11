#!/usr/bin/env bash

cd target
native-image -cp ./ccc-marker.jar site.pegasis.ccc.marker.MainKt --static --no-fallback
mv site.pegasis.ccc.marker.mainkt ccc-marker
strip ccc-marker
