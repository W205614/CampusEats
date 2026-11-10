"""Collect matching source/build inputs before redistributing application images.

Uses each installed APK's aports commit and checksums, not today's aports branch.
Only runs against explicit images without networking or business mounts.
"""
import argparse
import concurrent.futures
import hashlib
import http.client
import io
import json
from pathlib import Path
import re
import subprocess
import time
import urllib.request
import urllib.parse
import urllib.error
import zipfile


def download(url):
    data = bytearray()
    validator = None
    total = None
    attempt = 0
    while True:
        headers = {"User-Agent": "CampusEats-sources"}
        if data and validator:
            headers.update({"Range": f"bytes={len(data)}-", "If-Range": validator})
        else:
            data.clear()
        request = urllib.request.Request(url, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                if response.status == 206:
                    content_range = re.fullmatch(r"bytes (\d+)-(\d+)/(\d+)", response.headers.get("Content-Range", ""))
                    if not data or content_range is None or int(content_range[1]) != len(data) or int(content_range[3]) != total:
                        raise ValueError(f"Source download range differs from this transfer: {url}")
                elif response.status == 200:
                    # A changed validator or a server ignoring Range starts a fresh response.
                    data.clear()
                    length = response.headers.get("Content-Length")
                    total = int(length) if length else None
                    validator = response.headers.get("ETag") or response.headers.get("Last-Modified")
                else:
                    raise ValueError(f"Unexpected source download status: {response.status}")
                try:
                    data.extend(response.read())
                except http.client.IncompleteRead as error:
                    data.extend(error.partial)
                    raise
                if total is not None and len(data) != total:
                    raise http.client.IncompleteRead(b"", total - len(data))
                return bytes(data)
        except (http.client.IncompleteRead, http.client.RemoteDisconnected, TimeoutError, urllib.error.HTTPError) as error:
            if isinstance(error, urllib.error.HTTPError) and error.code not in (502, 503, 504):
                raise
            attempt += 1
            if attempt == 3:
                raise RuntimeError(f"Source transfer failed after bounded attempts: {url}") from error
            time.sleep(2)


def run(image, *command):
    return subprocess.check_output([
        "docker", "run", "--rm", "--network", "none", "--entrypoint", command[0],
        image, *command[1:],
    ])


def apk_records(data):
    records = []
    for block in data.decode().split("\n\n"):
        # File inventory fields may repeat; package metadata comes before F:.
        fields = dict(line.split(":", 1) for line in block.split("\nF:", 1)[0].splitlines() if ":" in line)
        if "P" in fields:
            records.append(fields)
    return records


def collect_aport(item, output, branch):
    origin, commit = item
    if not re.fullmatch(r"[a-z0-9+_.-]+", origin) or not re.fullmatch(r"[a-f0-9]{40}", commit):
        raise ValueError("Missing or invalid installed APK source identity")
    url = f"https://api.github.com/repos/alpinelinux/aports/contents/main/{origin}?ref={commit}"
    recipe = output / "alpine" / f"{origin}-{commit}"
    recipe.mkdir(parents=True)
    try:
        members = json.loads(download(url))
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        url = url.replace("/contents/main/", "/contents/community/")
        members = json.loads(download(url))
    def copy_directory(entries, directory):
        for member in entries:
            name = member["name"]
            if Path(name).name != name or name in (".", ".."):
                raise ValueError("Unsafe source recipe member")
            target = directory / name
            if member["type"] == "dir":
                target.mkdir()
                copy_directory(json.loads(download(member["url"])), target)
            elif member["type"] == "file":
                target.write_bytes(download(member["download_url"]))
            else:
                raise ValueError("Unsupported aports recipe member")
    copy_directory(members, recipe)
    apkbuild = (recipe / "APKBUILD").read_text()
    checksum_block = re.search(r'(?ms)^sha512sums="(.*?)"', apkbuild)
    if checksum_block is None and re.search(r'(?m)^source=', apkbuild):
        raise ValueError(f"Unsupported aports checksum declaration: {origin}")
    checksums = re.findall(r"(?m)^([a-f0-9]{128})\s+([^\s]+)$", checksum_block.group(1).strip()) if checksum_block else []
    for expected, name in checksums:
        if Path(name).name != name:
            raise ValueError("Unexpected source filename")
        target = recipe / name
        if not target.exists():
            target.write_bytes(download(f"https://distfiles.alpinelinux.org/distfiles/{branch}/{name}?download=1"))
        if hashlib.sha512(target.read_bytes()).hexdigest() != expected:
            raise ValueError(f"Source checksum mismatch: {origin}/{name}")
    # Source-less metadata packages still have their complete recipe/install files.
    print(f"Collected {origin} at {commit}: {len(checksums)} source files", flush=True)
    return {"origin": origin, "commit": commit, "recipeUrl": url, "sourceFiles": len(checksums)}


def collect(server, web, output):
    output.mkdir(parents=True, exist_ok=False)
    inventory = {}
    origins = set()
    for service, image in (("server", server), ("web", web)):
        packages = apk_records(run(image, "cat", "/lib/apk/db/installed"))
        branch = "v" + ".".join(run(image, "cat", "/etc/alpine-release").decode().strip().split(".")[:2])
        inventory[service] = {"image": image, "alpineBranch": branch, "packages": packages}
        if any("c" not in p and p["P"] != "nginx" for p in packages):
            raise ValueError("An installed runtime package has no source recipe identity")
        origins.update((p["o"], p["c"], branch) for p in packages if "c" in p)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(lambda item: collect_aport(item[:2], output, item[2]), sorted(origins)))
    # Nginx's official package is not built by Alpine and has no aports commit.
    nginx = [p for p in inventory["web"]["packages"] if p["P"] == "nginx"]
    if len(nginx) != 1:
        raise ValueError("Expected one Nginx runtime package")
    version = nginx[0]["V"].split("-r", 1)[0]
    (output / f"nginx-{version}.tar.gz").write_bytes(download(f"https://nginx.org/download/nginx-{version}.tar.gz"))
    release = run(server, "cat", "/opt/java/openjdk/release").decode()
    java_version = re.search(r'^JAVA_RUNTIME_VERSION="([^"]+)"', release, re.M).group(1)
    java_tag = "jdk-" + java_version.removesuffix("-LTS")
    api = "https://api.github.com/repos/adoptium/temurin21-binaries/releases/tags/" + urllib.parse.quote(java_tag, safe="")
    metadata = json.loads(download(api))
    assets = [a for a in metadata["assets"] if re.fullmatch(r"OpenJDK21U-jdk-sources_.*\.tar\.gz", a["name"])]
    if len(assets) != 1:
        raise ValueError("Exact Temurin sources asset is unavailable")
    asset = assets[0]
    source = download(asset["browser_download_url"] + "?download=1")
    expected = download(asset["browser_download_url"] + ".sha256.txt?download=1").decode().split()[0]
    if hashlib.sha256(source).hexdigest() != expected:
        raise ValueError("Temurin source checksum mismatch")
    (output / asset["name"]).write_bytes(source)
    build_commit = re.search(r'^BUILD_SOURCE="git:([a-f0-9]{40})"', release, re.M).group(1)
    (output / "temurin-build.tar.gz").write_bytes(download(f"https://codeload.github.com/adoptium/temurin-build/tar.gz/{build_commit}"))
    # Keep the original JRE legal notices together with the exact source archive.
    (output / "temurin-legal.tar").write_bytes(run(server, "tar", "-c", "-C", "/opt/java/openjdk", "legal", "NOTICE", "release"))
    sbom = json.loads(run(server, "cat", "/app/sbom.cdx.json"))
    (output / "java-sbom.json").write_text(json.dumps(sbom, indent=2))
    with zipfile.ZipFile(io.BytesIO(run(server, "cat", "/app/app.jar"))) as app:
        for member in app.namelist():
            if not member.startswith("BOOT-INF/lib/") or not member.endswith(".jar"):
                continue
            directory = output / "java-notices" / Path(member).name
            with zipfile.ZipFile(io.BytesIO(app.read(member))) as library:
                for notice in library.namelist():
                    if notice.endswith("/") or not any(word in Path(notice).name.upper() for word in ("LICENSE", "NOTICE", "COPYING", "COPYRIGHT")):
                        continue
                    if ".." in Path(notice).parts or notice.startswith("/"):
                        raise ValueError("Unsafe Java notice path")
                    target = directory / notice
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(library.read(notice))
    # Include all upstream runtime source jars, POMs and their packaged license/NOTICE files.
    java_output = output / "java"
    java_output.mkdir()
    def java_component(component):
        if component.get("group") == "com.campuseats":
            return
        group, name, version = component["group"], component["name"], component["version"]
        base = f"https://repo.maven.apache.org/maven2/{group.replace('.', '/')}/{name}/{version}/{name}-{version}"
        directory = java_output / group / name / version
        directory.mkdir(parents=True, exist_ok=True)
        (directory / f"{name}-{version}.pom").write_bytes(download(base + ".pom"))
        # A few metadata-only components have no runtime code/source archive.
        if component.get("type") != "library":
            return
        try:
            sources = download(base + "-sources.jar")
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
            licenses = json.dumps(component.get("licenses", []))
            if not licenses or any(x in licenses for x in ("GPL", "MPL", "EPL", "CDDL", "General Public License")):
                raise ValueError(f"Required source archive unavailable: {name}") from error
            return
        with zipfile.ZipFile(io.BytesIO(sources)) as archive:
            if not archive.namelist():
                raise ValueError("Empty Java source archive")
        (directory / f"{name}-{version}-sources.jar").write_bytes(sources)
    components = {(c.get("group"), c["name"], c["version"]): c for c in sbom["components"]}
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(java_component, components.values()))
    (output / "inventory.json").write_text(json.dumps({"images": inventory, "aports": results, "temurinAsset": asset["browser_download_url"]}, indent=2))
    (output / "README.txt").write_text(
        "Corresponding runtime sources and build recipes for the exact images in inventory.json.\n"
        "Alpine recipes, patches and checksum-verified distfiles are grouped by installed aports commit.\n"
        "Use Alpine abuild with the recorded architecture and recipe. Temurin sources include upstream legal notices;\n"
        "see the source tree's build instructions. Java sources/POMs retain upstream licenses.\n"
        "CampusEats image build recipes and application sources are at the matching release tag.\n"
        "Components are unmodified except for the explicit image build recipes in that tag.\n"
        "Replacing LGPL libraries in the image is permitted; they are dynamically linked.\n"
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server", required=True)
    parser.add_argument("--web", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    collect(args.server, args.web, args.output)
    print(f"Matching runtime sources collected: {args.output}")
