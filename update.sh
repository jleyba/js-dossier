#!/bin/bash -x

cd $(dirname $0)

./gendossier.sh -j || exit
./gendossier.sh -l || exit

cp bazel-genfiles/src/js/dossier.js out/dossier.js
cp bazel-genfiles/src/js/dossier.js.map out/dossier.js.map
echo "//# sourceMappingURL=dossier.js.map" >> out/dossier.js

cp bazel-genfiles/src/js/serviceworker.js out/serviceworker.js
cp bazel-genfiles/src/js/serviceworker.js.map out/serviceworker.js.map
echo "//# sourceMappingURL=serviceworker.js.map" >> out/serviceworker.js
echo "// $(date)" >> out/serviceworker.js


cp out/dossier.js out/closure-fork/dossier.js
cp out/dossier.js.map out/closure-fork/dossier.js.map

cp out/serviceworker.js out/closure-fork/serviceworker.js
cp out/serviceworker.js.map out/closure-fork/serviceworker.js.map

cp out/dossier.js ../selenium/build/javascript/node/selenium-webdriver-docs/dossier.js
cp out/dossier.js.map ../selenium/build/javascript/node/selenium-webdriver-docs/dossier.js.map

cp out/serviceworker.js ../selenium/build/javascript/node/selenium-webdriver-docs/serviceworker.js
cp out/serviceworker.js.map ../selenium/build/javascript/node/selenium-webdriver-docs/serviceworker.js.map

cp src/java/com/github/jsdossier/resources/dossier.css out/dossier.css
cp out/dossier.css out/closure-fork/dossier.css
cp out/dossier.css ../selenium/build/javascript/node/selenium-webdriver-docs/dossier.css

node server.js "${@}"
