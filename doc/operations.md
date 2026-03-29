# Internal document about maintaining Monfluo

## Releasing

* Bump `versionCode` & `versionName` in `app/build.gradle.kts`
* Create changelog files `just create-changelog` and fill the main file (the one ending with 0)
* Commit and push changes
* Build native libraries and APKs: `just clean build-native-all build-release`
  * The APKs will be in `app/build/outputs/apk/release` directory
* Sign the APKs
```bash
for f in ./*.apk; do
    apksigner sign --ks ~/acx.jks "$f"
done
```
* Generate SHA256 hashes: `sha256sum *.apk >> sha256sum.txt`
* Sign the hashes: `gpg --clearsign sha256sum.txt`
* Test the APKs
* Create a new release on Codeberg
* Copy APKs to fdroid repo: `cp *.apk ~/fdroid/repo/`
* Deploy new APKs to fdroid: `cd ~/fdroid && fdroid-deploy-monfluo`
