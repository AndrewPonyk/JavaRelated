# Homebrew Packaging

TODO: Add a generated Formula/devopsctl.rb that downloads signed release archives from GitHub Releases.

Expected release flow:

1. Build cross-platform artifacts in GitHub Actions.
2. Generate SHA256 checksums.
3. Open a pull request against the Homebrew tap repository.
