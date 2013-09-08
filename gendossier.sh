#!/bin/bash
#
# Generates the compiled dossier.js file.

python ./closure_library/closure/bin/calcdeps.py \
    -i ./src/main/js/dossier.js \
    -d ./closure_library/closure/goog/deps.js \
    -o compiled \
    -c ./closure_compiler/compiler.jar \
    -f "--compilation_level=ADVANCED_OPTIMIZATIONS" \
    -f "--define=goog.DEBUG=false" \
    -f "--output_wrapper=\"(function(){%output%;init();})();\"" \
    --output_file=./src/main/resources/dossier.js
