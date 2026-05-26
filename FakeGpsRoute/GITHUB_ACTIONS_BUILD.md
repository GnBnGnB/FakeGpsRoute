# APK építés GitHub Actions-szel

Ez a projekt tartalmaz egy GitHub Actions workflow-t:

.github/workflows/build-apk.yml

A workflow a GitHub szerverén lefordítja az appot, és a kész debug APK-t Artifactként letölthetővé teszi.

## Lépések röviden

1. Hozz létre egy új GitHub repository-t.
2. Töltsd fel a projekt mappájának TELJES tartalmát.
3. Menj az Actions fülre.
4. Indítsd el a Build Debug APK workflow-t, vagy egyszerűen pusholj a main/master ágra.
5. A sikeres build végén töltsd le a FakeGpsRoute-debug-apk artifactot.
6. A ZIP-ben benne lesz az app-debug.apk.

## Telefonos telepítés

Másold át az app-debug.apk fájlt a telefonra, majd nyisd meg. Engedélyezni kell az ismeretlen forrásból történő telepítést.

A működéshez Androidon:

Developer options → Select mock location app → FakeGpsRoute
