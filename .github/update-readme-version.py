"""Update Database Seeder's stable dependency examples after a release."""

import argparse
import re
from pathlib import Path

README = Path(__file__).resolve().parents[1] / "README.md"
RELEASE = r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)"
STABLE_VERSION = rf"^\*\*Latest stable release:\*\* `({RELEASE})`"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version", help="published release version, such as 0.1.0")
    args = parser.parse_args()
    if not re.fullmatch(RELEASE, args.version):
        parser.error("use a release version in X.Y.Z format")

    content = README.read_text(encoding="utf-8")
    versions = re.findall(STABLE_VERSION, content, re.MULTILINE)
    if len(versions) != 1:
        parser.error("expected one 'Latest stable release' line in README.md")

    old_version = re.escape(versions[0])
    examples = (
        (rf"^(\*\*Latest stable release:\*\* `){old_version}(`)", 1),
        (rf"(io\.github\.brayandm:database-seeder-(?:cli|spring-boot-starter):){old_version}([`\"])", 2),
        (rf"(<artifactId>database-seeder-(?:cli|spring-boot-starter)</artifactId>\s*<version>){old_version}(</version>)", 2),
    )
    for pattern, expected in examples:
        content, count = re.subn(
            pattern, rf"\g<1>{args.version}\g<2>", content, flags=re.MULTILINE
        )
        if count != expected:
            parser.error("the README dependency examples do not match the current stable version")

    README.write_text(content, encoding="utf-8")
    print(f"README stable version: {args.version}")


if __name__ == "__main__":
    main()
