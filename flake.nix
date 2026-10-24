{
  description = "Cinema for Android TV";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f (pkgsFor system));
      pkgsFor =
        system:
        import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfreePredicate =
              pkg:
              builtins.elem (nixpkgs.lib.removePrefix "android-sdk-" (nixpkgs.lib.getName pkg)) [
                "build-tools"
                "cmake"
                "cmdline-tools"
                "platform-tools"
                "platforms"
                "tools"
              ];
          };
        };
    in
    {
      packages = forAllSystems (pkgs: rec {
        cinema-tv = pkgs.callPackage ./nix/package.nix { };
        default = cinema-tv;
      });

      devShells = forAllSystems (pkgs: {
        default = pkgs.mkShell {
          inputsFrom = [ self.packages.${pkgs.stdenv.hostPlatform.system}.cinema-tv ];
          packages = [ pkgs.android-tools ];
        };
      });

      formatter = forAllSystems (pkgs: pkgs.nixfmt);
    };
}
