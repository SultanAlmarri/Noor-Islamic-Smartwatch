#!/bin/bash
# Noor: what a cloud session needs to build the Android apps and to make scenery pictures.
set -e
T=${NOOR_TOOLS:-/opt/noortools}
mkdir -p "$T/jdk" "$T/sdk/cmdline-tools" "$T/assets" "$T/work"
# JDK 17 (the modules ask for it; the machine has 21).
if ! ls "$T"/jdk/*/bin/java >/dev/null 2>&1; then
  curl -sSL --fail --retry 3 -o /tmp/jdk17.tgz 'https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse'
  tar -xzf /tmp/jdk17.tgz -C "$T/jdk" && rm /tmp/jdk17.tgz
fi
JDK=$(ls -d "$T"/jdk/*/ | head -1); JDK=${JDK%/}
# Android SDK: platforms 35 and 36, build-tools, platform-tools.
if [ ! -d "$T/sdk/platforms/android-36" ]; then
  Z=$(curl -sS --retry 3 https://dl.google.com/android/repository/repository2-3.xml | grep -o 'commandlinetools-linux-[0-9]*_latest.zip' | sort -V | tail -1)
  curl -sS --fail --retry 3 -o /tmp/clt.zip "https://dl.google.com/android/repository/$Z"
  rm -rf "$T/sdk/cmdline-tools/latest" /tmp/clt && unzip -q /tmp/clt.zip -d /tmp/clt
  mv /tmp/clt/cmdline-tools "$T/sdk/cmdline-tools/latest" && rm -rf /tmp/clt.zip /tmp/clt
  export JAVA_HOME="$JDK"
  yes | "$T/sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$T/sdk" --licenses >/dev/null 2>&1 || true
  "$T/sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$T/sdk" "platforms;android-35" "platforms;android-36" \
    "build-tools;35.0.0" "build-tools;36.0.0" "platform-tools" >/dev/null
fi
# Blender 4.5 LTS (Cycles on the CPU): the scenery and cloud pictures.
if [ ! -x "$T/blender-4.5.16-linux-x64/blender" ]; then
  curl -sS --fail --retry 3 -A 'scenery-tools/1.0' -o /tmp/blender.txz \
    https://download.blender.org/release/Blender4.5/blender-4.5.16-linux-x64.tar.xz
  tar -xJf /tmp/blender.txz -C "$T" && rm /tmp/blender.txz
fi
# Gradle: where JDK 17 is, and Google's mirror of Maven Central first (Maven Central answers 429 here).
mkdir -p ~/.gradle
grep -qs "installations.paths=$JDK" ~/.gradle/gradle.properties || {
  echo "org.gradle.java.installations.paths=$JDK" >> ~/.gradle/gradle.properties
  echo "org.gradle.java.installations.auto-download=false" >> ~/.gradle/gradle.properties
}
cat > ~/.gradle/init.gradle <<'G'
def mirror = 'https://maven-central.storage-download.googleapis.com/maven2/'
settingsEvaluated { settings ->
    settings.pluginManagement.repositories { r -> def m = r.maven { url mirror; name 'centralMirror' }; r.remove(m); r.addFirst(m) }
    settings.dependencyResolutionManagement.repositories { r -> def m = r.maven { url mirror; name 'centralMirror' }; r.remove(m); r.addFirst(m) }
}
G
# The scenery scripts look for their tools under ~/ProjectMinhad/NoorTools (as on the owner's PC).
mkdir -p ~/ProjectMinhad && ln -sfn "$T" ~/ProjectMinhad/NoorTools
# The repository: where the SDK is.
if [ -d Noor-Islamic-Smartwatch ]; then echo "sdk.dir=$T/sdk" > Noor-Islamic-Smartwatch/local.properties; fi
