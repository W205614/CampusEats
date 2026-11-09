import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("image_sources", Path(__file__).parents[1] / "collect-image-sources.py")
collector = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(collector)


class SourceCollectionTest(unittest.TestCase):
    def setUp(self):
        # Keep disposable source fixtures under the project's normal ignored workspace.
        root = Path(__file__).parents[2] / ".local"
        root.mkdir(exist_ok=True)
        self.output = Path(tempfile.mkdtemp(prefix="image-sources-test-", dir=root))

    def fetch(self, recipe, source=b"source with original copyright"):
        def boundary(url):
            if "/contents/" in url:
                self.assertIn("ref=" + "a" * 40, url)
                return json.dumps([{"name": "APKBUILD", "type": "file", "download_url": "https://raw.githubusercontent.com/fixture/APKBUILD"}]).encode()
            if url.endswith("APKBUILD"):
                return recipe.encode()
            self.assertTrue(url.endswith("/v3.24/source.tar.gz?download=1"))
            return source
        return boundary

    def test_checksums_bind_actual_source_files(self):
        source = b"source with original copyright"
        digest = hashlib.sha512(source).hexdigest()
        for index, declaration in enumerate((f'sha512sums="{digest}  source.tar.gz"', f'sha512sums="\n{digest}  source.tar.gz\n"')):
            destination = self.output / str(index)
            destination.mkdir()
            with patch.object(collector, "download", side_effect=self.fetch(declaration)):
                result = collector.collect_aport(("fixture", "a" * 40), destination, "v3.24")
            self.assertEqual(result["sourceFiles"], 1)
            self.assertEqual((destination / "alpine" / ("fixture-" + "a" * 40) / "source.tar.gz").read_bytes(), source)

    def test_corrupt_source_is_rejected(self):
        recipe = 'sha512sums="' + "b" * 128 + '  source.tar.gz"'
        with patch.object(collector, "download", side_effect=self.fetch(recipe)):
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                collector.collect_aport(("fixture", "a" * 40), self.output, "v3.24")

    def test_metadata_only_recipe_has_no_distfiles(self):
        with patch.object(collector, "download", side_effect=self.fetch('pkgname=fixture\npackage() { :; }')):
            result = collector.collect_aport(("fixture", "a" * 40), self.output, "v3.24")
        self.assertEqual(result["sourceFiles"], 0)

    def test_unverified_source_declaration_is_rejected(self):
        with patch.object(collector, "download", side_effect=self.fetch('source="https://example.com/source.tar.gz"')):
            with self.assertRaisesRegex(ValueError, "checksum declaration"):
                collector.collect_aport(("fixture", "a" * 40), self.output, "v3.24")

    def test_missing_source_identity_is_rejected_before_network(self):
        with patch.object(collector, "download") as fetch:
            with self.assertRaisesRegex(ValueError, "source identity"):
                collector.collect_aport(("fixture", "main"), self.output, "v3.24")
            fetch.assert_not_called()


if __name__ == "__main__":
    unittest.main()
