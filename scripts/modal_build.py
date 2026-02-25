# /// script
# requires-python = ">=3.11"
# dependencies = [
#   "modal>=0.73.0",
# ]
# ///
"""Build XLCR on Modal.

Builds assembly JAR and GraalVM native image using Modal's cloud infrastructure.
Replaces GitHub's 16-core runners for memory-intensive native image compilation.

Usage:
  uv run scripts/modal_build.py --tag v0.3.0
  uv run scripts/modal_build.py --tag v0.3.0 --output-dir ./release-artifacts

Requires:
  - Modal account with 'aspose-license' secret configured
  - MODAL_TOKEN_ID / MODAL_TOKEN_SECRET env vars (for CI)
"""

import os
import shutil
import subprocess

import modal

app = modal.App("xlcr-build")

# Persistent volume for build artifacts
vol = modal.Volume.from_name("xlcr-build-artifacts", create_if_missing=True)

# Aspose license secret
aspose_secret = modal.Secret.from_name("aspose-license")

# Build image: Ubuntu 24.04 + GraalVM 25.0.2 + Mill 1.1.2
GRAALVM_VERSION = "25.0.2"
MILL_VERSION = "1.1.2"

build_image = (
    modal.Image.from_registry("ubuntu:24.04", add_python="3.12")
    .apt_install(
        "curl", "git", "make", "build-essential", "zlib1g-dev",
        "libreoffice-core", "libreoffice-writer",
        "libreoffice-calc", "libreoffice-impress",
    )
    .run_commands(
        "ARCH=$(dpkg --print-architecture) && "
        'if [ "$ARCH" = "arm64" ]; then GRAAL_ARCH="aarch64"; else GRAAL_ARCH="x64"; fi && '
        "curl -fL "
        f'"https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-{GRAALVM_VERSION}/'
        f'graalvm-community-jdk-{GRAALVM_VERSION}_linux-${{GRAAL_ARCH}}_bin.tar.gz" '
        "| tar xz -C /opt && "
        "ln -s /opt/graalvm-community-openjdk-* /opt/graalvm",
    )
    .env(
        {
            "JAVA_HOME": "/opt/graalvm",
            "PATH": "/opt/graalvm/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LD_LIBRARY_PATH": "/opt/graalvm/lib:/opt/graalvm/lib/server",
        }
    )
    .run_commands(
        "curl -fLo /usr/local/bin/mill "
        f'"https://repo1.maven.org/maven2/com/lihaoyi/mill-dist/{MILL_VERSION}/mill-dist-{MILL_VERSION}-mill.sh" && '
        "chmod +x /usr/local/bin/mill",
    )
)


@app.function(
    image=build_image,
    secrets=[aspose_secret],
    volumes={"/artifacts": vol},
    memory=32768,
    timeout=3600,
    cpu=8,
)
def build(tag: str = "main", repo: str = "https://github.com/TJC-LP/xlcr.git"):
    """Build XLCR assembly JAR and native image."""
    import base64

    # Clone repo at specified tag/ref
    print(f"Cloning {repo} at {tag}...")
    subprocess.run(
        ["git", "clone", "--depth", "1", "--branch", tag, repo, "/build"],
        check=True,
    )
    os.chdir("/build")

    # NOTE: Aspose license is NOT bundled into published artifacts.
    # Users supply it at runtime via ASPOSE_TOTAL_LICENSE_B64 env var.

    # Build assembly JAR
    print("Building assembly JAR...")
    subprocess.run(["mill", "xlcr.assembly"], check=True)

    # Build native image
    print("Building native image...")
    subprocess.run(["mill", "xlcr.nativeImage"], check=True)

    # Copy artifacts to volume
    jar_src = "out/xlcr/assembly.dest/out.jar"
    native_src = "out/xlcr/nativeImage.dest/native-executable"

    shutil.copy2(jar_src, "/artifacts/xlcr.jar")
    shutil.copy2(native_src, "/artifacts/xlcr-native")

    jar_size = os.path.getsize(jar_src) / 1024 / 1024
    native_size = os.path.getsize(native_src) / 1024 / 1024
    print(f"JAR: {jar_size:.1f} MB")
    print(f"Native: {native_size:.1f} MB")

    vol.commit()
    print("Artifacts saved to volume 'xlcr-build-artifacts'")


@app.local_entrypoint()
def main(tag: str = "main", output_dir: str = "./artifacts"):
    """Build XLCR and download artifacts."""
    build.remote(tag=tag)

    os.makedirs(output_dir, exist_ok=True)

    for name in ["xlcr.jar", "xlcr-native"]:
        path = os.path.join(output_dir, name)
        with open(path, "wb") as f:
            for chunk in vol.read_file(name):
                f.write(chunk)
        size = os.path.getsize(path) / 1024 / 1024
        print(f"Downloaded {name}: {size:.1f} MB")

    # Make native binary executable
    os.chmod(os.path.join(output_dir, "xlcr-native"), 0o755)
    print(f"\nArtifacts ready in {output_dir}/")
