# Homebrew formula for npa (Network Packet Analyzer).
#
# Local install during development:
#   brew install --build-from-source ./packaging/homebrew/npa.rb
#
# For a tap/release, set `url` to the released tarball and fill in `sha256`
# (the release.yml workflow publishes both).
class Npa < Formula
  desc "Terminal network packet analyzer with anomaly detection"
  homepage "https://example.com/npa"
  version "0.1.0"
  # Release artifact (replace with the tagged tarball + checksum):
  url "https://example.com/npa/releases/npa-0.1.0.tar.gz"
  sha256 "0000000000000000000000000000000000000000000000000000000000000000"
  license "MIT"
  head "https://example.com/npa.git", branch: "main"

  depends_on "libpcap"
  depends_on "ncurses"

  def install
    system "make", "STRICT=1", "OPT=-O3"
    bin.install "build/bin/npa"
    man1.install "docs/npa.1"
    pkgshare.install "config/patterns.rules", "config/npa.conf.example"
  end

  test do
    # Generate a tiny fixture and verify headless JSON analysis runs.
    system "make", "fixtures"
    output = shell_output("#{bin}/npa -r tests/fixtures/sample.pcap --headless --json")
    assert_match "\"type\":\"summary\"", output
  end

  def caveats
    <<~EOS
      Live capture needs raw-socket privileges. Grant them without root via:
        sudo chmod +r /dev/bpf*        # macOS
      or run with sudo. On Linux:
        sudo setcap cap_net_raw,cap_net_admin+eip #{bin}/npa
    EOS
  end
end
