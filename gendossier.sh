#!/bin/bash
#
# Generates the compiled dossier.js file.

python ./closure_library/closure/bin/calcdeps.py \
    -i ./src/main/js/dossier.js \
    -i ./src/main/js/deps.js \
    -d ./closure_library/closure/goog/deps.js \
    -o compiled \
    -c ./closure_compiler/compiler.jar \
    -f "--compilation_level=ADVANCED_OPTIMIZATIONS" \
    -f "--define=goog.DEBUG=false" \
    -f "--jscomp_error=accessControls" \
    -f "--jscomp_error=ambiguousFunctionDecl" \
    -f "--jscomp_error=checkRegExp" \
    -f "--jscomp_error=checkTypes" \
    -f "--jscomp_error=checkVars" \
    -f "--jscomp_error=constantProperty" \
    -f "--jscomp_error=deprecated" \
    -f "--jscomp_error=duplicateMessage" \
    -f "--jscomp_error=es5Strict" \
    -f "--jscomp_error=externsValidation" \
    -f "--jscomp_error=fileoverviewTags" \
    -f "--jscomp_error=globalThis" \
    -f "--jscomp_error=invalidCasts" \
    -f "--jscomp_error=missingProperties" \
    -f "--jscomp_error=nonStandardJsDocs" \
    -f "--jscomp_error=strictModuleDepCheck" \
    -f "--jscomp_error=typeInvalidation" \
    -f "--jscomp_error=undefinedVars" \
    -f "--jscomp_error=unknownDefines" \
    -f "--jscomp_error=uselessCode" \
    -f "--jscomp_error=visibility" \
    -f "--third_party=false" \
    -f "--output_wrapper=\"(function(){%output%;init();})();\"" \
    --output_file=./src/main/resources/dossier.js

lessc --yui-compress \
    src/main/js/dossier.less \
    src/main/resources/dossier.css
