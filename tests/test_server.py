import base64
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import server


class ProtocolTests(unittest.TestCase):
    def test_multiple_models_nodes_are_merged_with_per_model_protocols(self):
        xml = """<?xml version="1.0" encoding="utf-8"?>
<apiPlatforms version="1">
  <platform id="mixed" name="Mixed" default="true" defaultModel="nano-banana-2">
    <url>https://proxy.example/v1</url>
    <key>test-key</key>
    <models separator="|" protocol="gemini-generate-content">nano-banana-pro|nano-banana-2</models>
    <models separator="|" protocol="openai-images">gpt-image-2-vip</models>
  </platform>
</apiPlatforms>
"""
        with tempfile.TemporaryDirectory() as directory:
            config_path = Path(directory) / "api-platforms.xml"
            config_path.write_text(xml, encoding="utf-8")
            with mock.patch.object(server, "API_PLATFORMS_FILE", config_path):
                platforms = server.read_image_platforms()
                response = server.build_image_platforms_response(platforms)
                selected = server.resolve_image_generation_platform("mixed", "gpt-image-2-vip")

        self.assertEqual(
            response["items"][0]["models"],
            ["nano-banana-pro", "nano-banana-2", "gpt-image-2-vip"],
        )
        self.assertEqual(selected["protocol"], server.PROTOCOL_OPENAI_IMAGES)
        self.assertEqual(selected["api_url"], "https://proxy.example/v1")

    def test_protocol_alias_and_legacy_inference(self):
        self.assertEqual(
            server.normalize_image_protocol(
                "nanobananapro",
                "https://example.com/v1beta/models/{model}:generateContent",
                ["nano-banana-2"],
            ),
            server.PROTOCOL_GEMINI_GENERATE_CONTENT,
        )
        self.assertEqual(
            server.infer_image_protocol(
                "https://example.com/v1/api/generate",
                ["nano-banana-2"],
            ),
            server.PROTOCOL_GRSAI_GENERATE,
        )
        self.assertEqual(
            server.infer_image_protocol("https://example.com/v1", ["gpt-image-2"]),
            server.PROTOCOL_OPENAI_IMAGES,
        )

    def test_openai_endpoint_switches_between_generate_and_edit(self):
        base_url = "https://api.openai.com/v1"
        self.assertEqual(
            server.resolve_openai_images_endpoint(base_url, False),
            "https://api.openai.com/v1/images/generations",
        )
        self.assertEqual(
            server.resolve_openai_images_endpoint(base_url, True),
            "https://api.openai.com/v1/images/edits",
        )
        self.assertEqual(
            server.resolve_openai_images_endpoint(
                "https://proxy.example/v1/images/generations?tenant=demo",
                True,
            ),
            "https://proxy.example/v1/images/edits?tenant=demo",
        )

    def test_openai_sizes_satisfy_documented_constraints(self):
        for size_key in ("1K", "2K", "4K"):
            for ratio in ("1:1", "4:3", "3:4", "16:9", "9:16", "5:4", "4:5"):
                width, height = map(int, server.map_openai_image_size(ratio, size_key).split("x"))
                self.assertEqual(width % 16, 0)
                self.assertEqual(height % 16, 0)
                self.assertLessEqual(max(width, height), 3840)
                self.assertLessEqual(max(width, height) / min(width, height), 3)
                self.assertGreaterEqual(width * height, 655_360)
                self.assertLessEqual(width * height, 8_294_400)


class OpenAIRequestTests(unittest.TestCase):
    def setUp(self):
        self.platform = {
            "api_url": "https://api.openai.com/v1",
            "api_key": "test-key",
            "image_model": "gpt-image-2",
            "protocol": server.PROTOCOL_OPENAI_IMAGES,
        }
        self.base_image = {
            "filename": "base.png",
            "mime_type": "image/png",
            "data": b"base-image-bytes",
            "base64": base64.b64encode(b"base-image-bytes").decode("ascii"),
        }
        self.reference_image = {
            "filename": "reference.jpg",
            "mime_type": "image/jpeg",
            "data": b"reference-image-bytes",
            "base64": base64.b64encode(b"reference-image-bytes").decode("ascii"),
        }

    def test_text_to_image_uses_json_generations_endpoint(self):
        with mock.patch.object(server, "post_json_to_upstream", return_value={"data": []}) as post_json:
            server.post_openai_images_request(
                image_platform=self.platform,
                prompt="draw a house",
                base_image=None,
                reference_images=[],
                aspect_ratio="16:9",
                image_size="2K",
                timeout=30,
            )

        kwargs = post_json.call_args.kwargs
        self.assertEqual(kwargs["api_url"], "https://api.openai.com/v1/images/generations")
        payload = json.loads(kwargs["body"])
        self.assertEqual(payload["model"], "gpt-image-2")
        self.assertEqual(payload["size"], "2048x1152")
        self.assertEqual(payload["output_format"], "png")

    def test_image_edit_uses_multipart_and_preserves_image_order(self):
        with mock.patch.object(server, "post_bytes_to_upstream", return_value={"data": []}) as post_bytes:
            server.post_openai_images_request(
                image_platform=self.platform,
                prompt="use the reference style",
                base_image=self.base_image,
                reference_images=[self.reference_image],
                aspect_ratio="4:3",
                image_size="1K",
                timeout=30,
            )

        kwargs = post_bytes.call_args.kwargs
        self.assertEqual(kwargs["api_url"], "https://api.openai.com/v1/images/edits")
        self.assertTrue(kwargs["content_type"].startswith("multipart/form-data; boundary="))
        body = kwargs["body"]
        self.assertEqual(body.count(b'name="image[]"'), 2)
        self.assertLess(body.index(b"base-image-bytes"), body.index(b"reference-image-bytes"))
        self.assertIn(b'name="size"\r\n\r\n1152x864', body)

    def test_openai_base64_response_is_reused_by_existing_parser(self):
        expected = b"generated-png"
        payload = {"data": [{"b64_json": base64.b64encode(expected).decode("ascii")}]}
        image_bytes, mime_type, _ = server.parse_response_image(payload)
        self.assertEqual(image_bytes, expected)
        self.assertEqual(mime_type, "image/png")


if __name__ == "__main__":
    unittest.main()
