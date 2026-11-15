#!/usr/bin/env python3
"""Read-only offline scanner for raw SFNT fonts embedded in a Kanzi KZB backup.

Does not connect to QNX or modify the input. Compressed/obfuscated fonts
cannot be discovered by scanning for SFNT signatures.
"""
import argparse
import hashlib
import json
import mmap
from pathlib import Path

MAGICS = (b"\x00\x01\x00\x00", b"OTTO", b"true", b"ttcf")
MAX_TABLES = 128
MAX_FONT_BYTES = 16 * 1024 * 1024


def u16(data, pos):
    return int.from_bytes(data[pos:pos + 2], "big")


def u32(data, pos):
    return int.from_bytes(data[pos:pos + 4], "big")


def check_sfnt(data, pos):
    if pos + 12 > len(data) or data[pos:pos + 4] == b"ttcf":
        return None
    count = u16(data, pos + 4)
    if not 1 <= count <= MAX_TABLES or pos + 12 + count * 16 > len(data):
        return None
    end = 12 + count * 16
    tags = set()
    for n in range(count):
        base = pos + 12 + n * 16
        tag = bytes(data[base:base + 4])
        if tag in tags or any(c < 32 or c > 126 for c in tag):
            return None
        tags.add(tag)
        offset = u32(data, base + 8)
        size = u32(data, base + 12)
        if offset < 12 + count * 16 or size == 0:
            return None
        end = max(end, offset + size)
        if end > MAX_FONT_BYTES or pos + end > len(data):
            return None
    if b"head" not in tags or b"maxp" not in tags or b"cmap" not in tags:
        return None
    return end


def scan(path):
    results = []
    with path.open("rb") as stream:
        with mmap.mmap(stream.fileno(), 0, access=mmap.ACCESS_READ) as data:
            offsets = set()
            for magic in MAGICS:
                start = 0
                while True:
                    at = data.find(magic, start)
                    if at < 0:
                        break
                    offsets.add(at)
                    start = at + 1
            for offset in sorted(offsets):
                size = check_sfnt(data, offset)
                if size is None:
                    continue
                payload = data[offset:offset + size]
                results.append({
                    "offset": offset,
                    "offset_hex": hex(offset),
                    "size": size,
                    "sha256": hashlib.sha256(payload).hexdigest(),
                    "signature": payload[:4].hex(),
                })
    return results


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kzb", type=Path, help="Local backup of resource.kzb")
    args = parser.parse_args()
    if not args.kzb.is_file():
        parser.error("file does not exist")
    print(json.dumps({"file": str(args.kzb), "fonts": scan(args.kzb)},
                     ensure_ascii=False, indent=2))
