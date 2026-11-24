"""Turn the sketch template into the one file people open: every assets/... becomes a data URI.

    python docs/mockups/build-sketch.py

Edit macro-redesign.template.html (small, greppable); macro-redesign.html is generated from it, so it
opens anywhere -- a phone, a chat preview, a raw file: URL -- with no folder beside it.
"""
import base64
import pathlib
import re

here = pathlib.Path(__file__).resolve().parent
template = here / "macro-redesign.template.html"
out = here / "macro-redesign.html"


def inline(match):
    name = match.group(1)
    path = here / "assets" / name
    if not path.is_file():
        return match.group(0)
    mime = "image/jpeg" if path.suffix.lower() in (".jpg", ".jpeg") else "image/png"
    return "data:%s;base64,%s" % (mime, base64.b64encode(path.read_bytes()).decode("ascii"))


text = re.sub(r"assets/([A-Za-z0-9._-]+)", inline, template.read_text(encoding="utf-8"))
out.write_text(text, encoding="utf-8")
print("wrote %s, %d KB" % (out.name, len(text) / 1024))
