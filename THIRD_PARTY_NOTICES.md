# Third-party notices

The MIT license at the repository root covers original Region Switch code. It does not replace the licenses below.

## Frida 17.9.11

The native controller links to the official Android arm64 Frida core devkit. Frida is licensed under the wxWindows Library Licence 3.1 (GNU Library GPL with its additional exception). Unmodified upstream source and dependency information are available from the tagged repositories:

- https://github.com/frida/frida/tree/17.9.11
- https://github.com/frida/frida-core/tree/17.9.11
- https://github.com/frida/frida-gum/tree/17.9.11
- https://github.com/frida/frida/releases/tag/17.9.11

Copies of `COPYING` and `COPYING.LIB` are in `licenses/frida/`. The devkit contains upstream dependencies under their own terms; source/build information remains available through Frida's tagged source and subprojects. Region Switch's controller source and link command are included in this repository.

## frida-java-bridge 7.0.13

`region.js` bundles frida-java-bridge. Its package declares `LGPL-2.0 WITH WxWindows-exception-3.1`.

- Source: https://github.com/frida/frida-java-bridge
- Exact package: https://www.npmjs.com/package/frida-java-bridge/v/7.0.13
- License text corresponding to that package's SPDX declaration is supplied in `licenses/frida-java-bridge/` (wxWindows text from Frida core and GNU Library GPL 2.0 text from GNU).

## Android Open Source Project country data

`country-data/MccTable.java` and `country-data/carrier_list.textpb` are upstream source snapshots used to derive `app/src/main/assets/countries.tsv`.

Copyright The Android Open Source Project. Licensed under Apache License 2.0; see `licenses/Apache-2.0.txt`. The original MCC source copyright/license header is retained. Source URLs and snapshot hashes are recorded in `country-data/sources.json` and `country-data/catalog-metadata.json`. The generator extracts representative country/network pairs rather than distributing these files as part of the APK.

## Gradle wrapper

The Gradle wrapper is distributed under Apache License 2.0. Copyright the original author or authors. Source: https://github.com/gradle/gradle/tree/v8.11.1/gradle/wrapper. See `licenses/Apache-2.0.txt`.
