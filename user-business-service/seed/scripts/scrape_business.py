#!/usr/bin/env python3
"""
scrape_business.py
==================

Crawl a business website and produce CSV files matching the seed schema
used by user-business-service (`business.*`) and knowledge-service
(`knowledge.*`).

Usage
-----
    export GEMINI_API_KEY=...           # required
    python3 scrape_business.py https://www.invoguebuildings.com/ \
        --out ./out --max-pages 8

Output (in --out directory)
---------------------------
    businesses.csv               -> business.businesses
    business_phone_numbers.csv   -> business.business_phone_numbers
    rating_config.csv            -> business.rating_config
    business_profile.csv         -> knowledge.business_profile
    business_faq.csv             -> knowledge.business_faq
    business_freeform.csv        -> knowledge.business_freeform

Once CSVs look good, generate SQL via `\copy` in psql or with the
companion `csv_to_sql.py` (not included here).
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import secrets
import sys
import time
from pathlib import Path
from urllib.parse import urldefrag, urljoin, urlparse

import requests
from bs4 import BeautifulSoup

# --------------------------------------------------------------------- #
# ULID generator (Crockford base32, 26 chars)
# --------------------------------------------------------------------- #
_CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

def ulid() -> str:
    t = int(time.time() * 1000)
    r = secrets.randbits(80)
    n = (t << 80) | r
    out = []
    for _ in range(26):
        out.append(_CROCKFORD[n & 31])
        n >>= 5
    return "".join(reversed(out))


# --------------------------------------------------------------------- #
# Crawl
# --------------------------------------------------------------------- #
INTERESTING_PATH_HINTS = (
    "about", "contact", "services", "service", "product", "products",
    "faq", "faqs", "support", "pricing", "location", "team", "company",
)

UA = "Mozilla/5.0 (compatible; BusinessSeederBot/1.0; +https://example.com/bot)"


def fetch(url: str, timeout: int = 15) -> str | None:
    try:
        r = requests.get(url, headers={"User-Agent": UA}, timeout=timeout)
        r.raise_for_status()
        ct = r.headers.get("content-type", "")
        if "html" not in ct and "text" not in ct:
            return None
        return r.text
    except Exception as e:
        print(f"  ! fetch failed {url}: {e}", file=sys.stderr)
        return None


def same_host(a: str, b: str) -> bool:
    ha, hb = urlparse(a).netloc.lower(), urlparse(b).netloc.lower()
    return ha.replace("www.", "") == hb.replace("www.", "")


def extract_text(html: str) -> tuple[str, list[str]]:
    """Return (visible_text, list_of_internal_links)."""
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup(["script", "style", "noscript"]):
        tag.decompose()
    text = re.sub(r"\s+\n", "\n", soup.get_text("\n"))
    text = re.sub(r"\n{3,}", "\n\n", text).strip()
    links = [a.get("href") for a in soup.find_all("a", href=True)]
    return text, links


def crawl(start_url: str, max_pages: int) -> str:
    """BFS within the same host, prioritising 'about/contact/faq' pages.
    Returns one giant text blob (each section prefixed with its URL).
    """
    seen: set[str] = set()
    queue: list[str] = [start_url]
    chunks: list[str] = []

    def score(url: str) -> int:
        p = urlparse(url).path.lower()
        return sum(1 for h in INTERESTING_PATH_HINTS if h in p)

    while queue and len(seen) < max_pages:
        queue.sort(key=score, reverse=True)
        url = queue.pop(0)
        url, _ = urldefrag(url)
        if url in seen:
            continue
        seen.add(url)
        print(f"  ↳ fetching {url}")
        html = fetch(url)
        if not html:
            continue
        text, links = extract_text(html)
        chunks.append(f"\n\n===== URL: {url} =====\n{text}")
        for href in links:
            absu = urljoin(url, href)
            absu, _ = urldefrag(absu)
            if absu in seen or not absu.startswith(("http://", "https://")):
                continue
            if not same_host(start_url, absu):
                continue
            if any(absu.lower().endswith(ext) for ext in (".jpg", ".png", ".pdf", ".zip", ".mp4")):
                continue
            if absu not in queue:
                queue.append(absu)

    return "\n".join(chunks)[:120_000]  # cap context for the LLM


# --------------------------------------------------------------------- #
# LLM extraction (Gemini)
# --------------------------------------------------------------------- #
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
GEMINI_BASE = os.getenv("GEMINI_BASE", "https://generativelanguage.googleapis.com")

EXTRACTION_PROMPT = """You are a structured-data extractor for a VOICE AI agent that will
read this content aloud on live phone calls. Every string you produce must sound
natural when SPOKEN, not when read on a screen. The AI streams responses token-by-token
so SHORT answers reach the caller faster — keep things tight.

Output ONE JSON object matching the schema below. No prose, no markdown, no code fences.

VOICE STYLE RULES (apply to description / services_offered.description / faq answers / freeform):
- Conversational, first-person plural ("we", "our team"), contractions ok ("we're", "you'll").
- Sentences <= 18 words. Prefer 2 short sentences over 1 long one.
- No bullet points, no markdown, no parentheticals, no semicolons, no em-dashes.
- Spell out things a TTS engine would mangle:
    * numbers/years -> words for small numbers ("twenty eleven" not "2011" inside spoken fields;
      but keep ISO times like "09:30" in business_hours since those are NOT spoken directly).
    * "&" -> "and",  "Pvt Ltd" -> "Private Limited",  "GST: ..." -> skip in voice fields.
    * phone numbers in spoken fields: group digits ("eight zero four five, eight one, two six, two three").
- Open FAQ answers with a quick acknowledgement: "Sure,", "Absolutely,", "Yeah,", "Of course,".
- Never list more than 3 items in one spoken sentence — break into separate sentences.
- Avoid jargon unless the caller would use it. Define it in one short clause if you must.
- No URLs, no emails inside spoken fields (the AI reads those from structured columns).

Schema:
{
  "name": "string, official legal name (used on screen, can stay formal)",
  "email": "string or null",
  "category": "string, short label (screen-only, can stay formal)",
  "description": "string, 1-2 SPOKEN sentences, <= 35 words total",
  "location": "string, HQ one-liner (screen-only)",
  "operating_hours": "string, short label like 'Mon-Sat 09:30-18:30' (screen-only)",
  "phone_numbers": [{"number":"+CC... E.164","label":"Primary | Sales | Support"}],
  "profile": {
    "business_hours": {"mon":"HH:MM-HH:MM","tue":"...","wed":"...","thu":"...","fri":"...","sat":"...","sun":"closed"},
    "address": "string (screen-only)",
    "location_notes": "string SPOKEN, <= 25 words, or null",
    "alt_phone": "string E.164 or null",
    "contact_email": "string or null",
    "website_url": "string",
    "languages_spoken": ["en","hi",...],
    "services_offered": [
      {"name":"string short label","description":"SPOKEN, 1 sentence, <= 20 words"}
    ],
    "payment_methods": ["cash","upi","card","bank_transfer",...],
    "appointment_policy": "SPOKEN, 1 sentence, <= 25 words, or null",
    "cancellation_policy": "SPOKEN, 1 sentence, <= 25 words, or null",
    "refund_policy": "SPOKEN, 1 sentence, <= 25 words, or null",
    "completeness_score": "integer 0-100"
  },
  "faqs": [
    {
      "question": "phrased the way a caller would actually ASK it out loud",
      "answer": "SPOKEN, 1-2 sentences, <= 30 words, opens with a soft acknowledgement",
      "priority": "integer 1-10 (10 = asked most often)"
    }
  ],
  "freeform": "SPOKEN paragraphs separated by \\n\\n. Each paragraph <= 3 sentences. Total <= 250 words. Written as if the receptionist is speaking, NOT as marketing copy."
}

Rules:
- Use null (not "") when unknown.
- Generate 6-10 FAQs, ordered by likely call frequency (priority 10 first).
- Include at least one FAQ for: hours, location, services, pricing/quotation, contact, scope (who they serve).
- Output ONLY the JSON object.

Website text follows:
---
"""


def gemini_extract(site_text: str, api_key: str) -> dict:
    url = f"{GEMINI_BASE}/v1beta/models/{GEMINI_MODEL}:generateContent?key={api_key}"
    payload = {
        "contents": [{"role": "user", "parts": [{"text": EXTRACTION_PROMPT + site_text}]}],
        "generationConfig": {
            "temperature": 0.2,
            "responseMimeType": "application/json",
        },
    }
    r = requests.post(url, json=payload, timeout=120)
    r.raise_for_status()
    data = r.json()
    try:
        text = data["candidates"][0]["content"]["parts"][0]["text"]
    except (KeyError, IndexError) as e:
        raise RuntimeError(f"Unexpected Gemini response: {json.dumps(data)[:500]}") from e
    return json.loads(text)


# --------------------------------------------------------------------- #
# CSV writers
# --------------------------------------------------------------------- #
DEFAULT_RATING_CONFIG = [
    ("LONG_CALL", 2),
    ("POSITIVE_FEEDBACK", 2),
    ("CALLBACK_REQUESTED", 3),
    ("NEGATIVE_FEEDBACK", -1),
    ("SHORT_CALL", -2),
    ("AI_COULD_NOT_ANSWER", 1),
]


def write_csv(path: Path, header: list[str], rows: list[list]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, quoting=csv.QUOTE_MINIMAL)
        w.writerow(header)
        for row in rows:
            w.writerow(row)
    print(f"  ✓ wrote {path} ({len(rows)} rows)")


def slug_email(name: str, host: str) -> str:
    base = re.sub(r"[^a-z0-9]+", "", name.lower())[:20] or "business"
    return f"contact@{host}"


def to_csvs(data: dict, website: str, out_dir: Path, password_hash: str) -> None:
    business_id = ulid()
    host = urlparse(website).netloc.replace("www.", "")
    email = data.get("email") or slug_email(data.get("name") or "", host)

    # businesses
    write_csv(
        out_dir / "businesses.csv",
        ["id", "name", "email", "password_hash", "category", "description", "location", "operating_hours", "is_active"],
        [[
            business_id,
            data.get("name") or "",
            email,
            password_hash,
            data.get("category") or "",
            data.get("description") or "",
            data.get("location") or "",
            data.get("operating_hours") or "",
            "true",
        ]],
    )

    # phone numbers
    phones = data.get("phone_numbers") or []
    phone_rows = [
        [ulid(), business_id, p.get("number", ""), p.get("label") or "Primary", "true"]
        for p in phones if p.get("number")
    ]
    write_csv(
        out_dir / "business_phone_numbers.csv",
        ["id", "business_id", "twilio_number", "label", "is_active"],
        phone_rows,
    )

    # rating config defaults
    rc_rows = [[ulid(), business_id, k, v] for k, v in DEFAULT_RATING_CONFIG]
    write_csv(
        out_dir / "rating_config.csv",
        ["id", "business_id", "signal_key", "score_value"],
        rc_rows,
    )

    # knowledge.business_profile
    p = data.get("profile") or {}
    write_csv(
        out_dir / "business_profile.csv",
        ["id", "business_id", "business_hours_json", "address", "location_notes",
         "alt_phone", "contact_email", "website_url", "languages_spoken_arr",
         "services_offered_json", "payment_methods_arr",
         "appointment_policy", "cancellation_policy", "refund_policy", "completeness_score"],
        [[
            ulid(), business_id,
            json.dumps(p.get("business_hours") or {}),
            p.get("address") or "",
            p.get("location_notes") or "",
            p.get("alt_phone") or "",
            p.get("contact_email") or "",
            p.get("website_url") or website,
            "{" + ",".join(p.get("languages_spoken") or []) + "}",
            json.dumps(p.get("services_offered") or []),
            "{" + ",".join(p.get("payment_methods") or []) + "}",
            p.get("appointment_policy") or "",
            p.get("cancellation_policy") or "",
            p.get("refund_policy") or "",
            p.get("completeness_score") or 50,
        ]],
    )

    # knowledge.business_faq
    faqs = data.get("faqs") or []
    faq_rows = [
        [ulid(), business_id, f.get("question", ""), f.get("answer", ""),
         int(f.get("priority") or 5), "true"]
        for f in faqs if f.get("question") and f.get("answer")
    ]
    write_csv(
        out_dir / "business_faq.csv",
        ["id", "business_id", "question", "answer", "priority", "is_active"],
        faq_rows,
    )

    # knowledge.business_freeform
    write_csv(
        out_dir / "business_freeform.csv",
        ["id", "business_id", "content"],
        [[ulid(), business_id, data.get("freeform") or ""]],
    )

    # also dump the raw extracted JSON for review
    (out_dir / "extracted.json").write_text(
        json.dumps({"business_id": business_id, **data}, indent=2, ensure_ascii=False)
    )
    print(f"\nbusiness_id = {business_id}")
    print(f"login email = {email}")


# --------------------------------------------------------------------- #
# Main
# --------------------------------------------------------------------- #
def main() -> int:
    ap = argparse.ArgumentParser(description="Scrape a business website into seed CSVs.")
    ap.add_argument("url", help="Business website URL")
    ap.add_argument("--out", default="./out", help="Output directory")
    ap.add_argument("--max-pages", type=int, default=8, help="Max pages to crawl")
    ap.add_argument(
        "--password-hash",
        default="$2b$10$xaYIWby0Nz1l0448VpQ7eu/JwaGOYAqpuyQSvHiXhRJY4/YbUaR9m",
        help="Bcrypt(10) hash for the business login (default = hash of 'Invogue@2026' — replace!)",
    )
    args = ap.parse_args()

    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        print("ERROR: GEMINI_API_KEY env var not set.", file=sys.stderr)
        return 2

    print(f"[1/3] Crawling {args.url} (max {args.max_pages} pages)...")
    site_text = crawl(args.url, args.max_pages)
    if not site_text.strip():
        print("ERROR: No text scraped — site may be JS-rendered or blocked.", file=sys.stderr)
        return 3

    print(f"[2/3] Extracting structured data via Gemini ({GEMINI_MODEL})...")
    data = gemini_extract(site_text, api_key)

    print("[3/3] Writing CSVs...")
    to_csvs(data, args.url, Path(args.out), args.password_hash)
    print("\nDone. Review extracted.json, then convert CSVs → SQL or \\copy into Postgres.")
    return 0


if __name__ == "__main__":
    sys.exit(main())