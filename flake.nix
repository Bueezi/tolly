{
  description = "Tolly – unofficial clients for KU Leuven Toledo (Android app, website, browser extension)";

  # Pinned by flake.lock: Android Studio and the SDK only change on `nix flake update`
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      buildToolsVersion = "37.0.0";

      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "36" "37" ];
        buildToolsVersions = [ buildToolsVersion ];
        includeEmulator = true;
        includeSystemImages = true;
        systemImageTypes = [ "google_apis" ];
        abiVersions = [ "x86_64" ];
        includeNDK = false;
      };

      sdkRoot = "${android.androidsdk}/libexec/android-sdk";
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.jdk21
          android.androidsdk
          pkgs.android-studio   # `android-studio`, with this shell's SDK and JDK
        ];

        ANDROID_HOME = sdkRoot;
        ANDROID_SDK_ROOT = sdkRoot;
        JAVA_HOME = pkgs.jdk21.home;
        BUILD_TOOLS_VERSION = buildToolsVersion;

        # The aapt2 binary Gradle downloads from Maven is dynamically linked; point AGP at
        # the one from the Nix SDK so builds also work without nix-ld.
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";

        shellHook = ''
          export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"
          echo "Tolly dev shell: JDK $(java -version 2>&1 | head -1 | cut -d'"' -f2), Android SDK at $ANDROID_HOME, android-studio"
        '';
      };
    };
}
