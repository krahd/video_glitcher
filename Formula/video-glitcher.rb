class VideoGlitcher < Formula
  desc "Realtime desktop app for glitching video and exporting corrupted MP4 files"
  homepage "https://krahd.github.io/video_glitcher/"
  version "1.0.5"

  on_macos do
    url "https://github.com/krahd/video_glitcher/releases/download/v1.0.5/video_glitcher-macos-aarch64.zip"
    sha256 "fd795c3021c58f0339cb446c110800c75075d90b68096b2aada85880c8bce8c4"
  end

  on_linux do
    url "https://github.com/krahd/video_glitcher/releases/download/v1.0.5/video_glitcher-linux-amd64.zip"
    sha256 "f1ff63f5957472a6ccbec325a89ed7604b50a3fe23b47d85932bb2e2310ecc89"
  end

  depends_on "ffmpeg"
  depends_on "openjdk"

  def install
    if OS.mac? && !Hardware::CPU.arm?
      odie "video-glitcher only supports macOS Apple Silicon via Homebrew"
    end

    if OS.linux? && !Hardware::CPU.intel?
      odie "video-glitcher only supports Linux x86_64 via Homebrew"
    end

    pkgshare.install "video_glitcher"
    release_dir = pkgshare/"video_glitcher"

    chmod 0755, release_dir/"run-macos.sh" if OS.mac?
    chmod 0755, release_dir/"run-linux.sh" if OS.linux?

    (bin/"video_glitcher").write <<~SH
      #!/bin/sh
      set -eu

      export PATH="#{Formula["ffmpeg"].opt_bin}:#{Formula["openjdk"].opt_bin}:$PATH"
      BASE_DIR="#{release_dir}"

      case "$(uname -s)" in
        Darwin)
          exec "$BASE_DIR/run-macos.sh" "$@"
          ;;
        Linux)
          exec "$BASE_DIR/run-linux.sh" "$@"
          ;;
        *)
          echo "Unsupported operating system: $(uname -s)" >&2
          exit 1
          ;;
      esac
    SH

    chmod 0755, bin/"video_glitcher"
  end

  test do
    assert_predicate pkgshare/"video_glitcher/video_glitcher.jar", :exist?
  end
end
