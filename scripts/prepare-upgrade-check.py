"""Test-only cache setup and data-preservation assertions for a real UI APK upgrade."""
import hashlib
import json
import pathlib
import sys
import xml.etree.ElementTree as ET

directory = pathlib.Path("upgrade-fixtures")
if "--verify" in sys.argv:
    root = ET.parse(directory / "after.xml").getroot()
    values = {item.attrib["name"]: item.text for item in root if item.tag == "string"}
    assert values.get("upgradeSentinel") == "preserve-after-system-install"
    points = json.loads(values["points"])
    assert any(p["id"] == "saved-test" and p["name"] == "Saved upgrade point" for p in points)
    assert any(p["id"] == "me" and p["dynamic"] for p in points)
    print("Preferences and saved points survived the system installer: OK")
else:
    digest = hashlib.sha256((directory / "upgrade.apk").read_bytes()).hexdigest()
    root = ET.Element("map")
    for name, value in {
        "pendingUpdateDigest": "sha256:" + digest,
        "upgradeSentinel": "preserve-after-system-install",
        "points": json.dumps([
            {"id": "me", "name": "My location", "lat": 55.16, "lon": 61.40, "dynamic": True, "tariffs": ["econom"]},
            {"id": "saved-test", "name": "Saved upgrade point", "lat": 55.17, "lon": 61.41, "dynamic": False, "tariffs": ["econom"]},
        ]),
    }.items():
        ET.SubElement(root, "string", name=name).text = value
    ET.ElementTree(root).write(directory / "radar.xml", encoding="utf-8", xml_declaration=True)
