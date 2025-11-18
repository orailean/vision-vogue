# filename: app.py
from fastapi import FastAPI, File, UploadFile
from transformers import (
    AutoProcessor,
    PaliGemmaForConditionalGeneration,
)
from PIL import Image
import torch
import io
import json
import os
from typing import List, Dict, Any, Optional
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

app = FastAPI(title="Garment Classification & Attribute API")


def _load_hf_token() -> Optional[str]:
    """Fetch a Hugging Face token from common env vars if set."""
    for key in ("HF_TOKEN", "HUGGINGFACE_TOKEN", "HF_API_TOKEN"):
        value = os.getenv(key)
        if value:
            return value.strip()
    return None


hf_token = _load_hf_token()

# --- Garment classification via PaliGemma ---
paligemma_model_name = "google/paligemma-3b-mix-224"
paligemma_processor = AutoProcessor.from_pretrained(
    paligemma_model_name,
    token=hf_token,
)
_dtype_override = os.getenv("PALIGEMMA_DTYPE", "").lower()
if _dtype_override in {"bf16", "bfloat16"} and hasattr(torch, "bfloat16"):
    paligemma_dtype = torch.bfloat16
elif _dtype_override in {"fp16", "float16", "half"}:
    paligemma_dtype = torch.float16
elif _dtype_override in {"fp32", "float32"}:
    paligemma_dtype = torch.float32
else:
    paligemma_dtype = torch.bfloat16 if torch.cuda.is_available() else torch.float32
paligemma_device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
paligemma_model = PaliGemmaForConditionalGeneration.from_pretrained(
    paligemma_model_name,
    torch_dtype=paligemma_dtype,
    token=hf_token,
)
paligemma_model.to(paligemma_device)
paligemma_model.eval()

#############################################
# Attribute analysis and rich predictions   #
#############################################

# Candidate attribute vocabularies
ATTRIBUTE_SETS: Dict[str, List[str]] = {
    "color": [
        "black", "white", "gray", "silver", "beige", "brown", "tan", "khaki",
        "red", "maroon", "burgundy", "crimson", "orange", "coral", "peach",
        "yellow", "gold", "mustard", "cream", "ivory",
        "green", "olive", "emerald", "lime", "mint", "sage",
        "teal", "turquoise", "aqua", "cyan",
        "blue", "navy", "royal blue", "sky blue", "denim blue",
        "purple", "lavender", "violet", "magenta", "plum",
        "pink", "rose", "blush", "fuchsia",
        "multicolor", "pastel", "neon", "metallic",
    ],
    "pattern": [
        "solid", "plain", "striped", "horizontal stripes", "vertical stripes",
        "plaid", "checkered", "gingham", "houndstooth", "tartan",
        "polka dot", "spotted",
        "floral", "botanical", "tropical print",
        "animal print", "leopard print", "zebra print", "snake print",
        "camouflage", "camo",
        "graphic print", "logo print", "text print", "slogan print",
        "geometric", "abstract", "tie-dye", "ombre", "gradient",
        "embroidered", "lace", "paisley", "argyle", "herringbone",
        "color block", "patchwork", "jacquard",
    ],
    "sleeve": [
        "sleeveless", "cap sleeve", "short sleeve", "elbow sleeve",
        "three quarter sleeve", "long sleeve", "full sleeve",
        "off-shoulder", "one shoulder", "cold shoulder",
        "bell sleeve", "puff sleeve", "bishop sleeve", "flutter sleeve",
        "raglan sleeve", "dolman sleeve", "kimono sleeve",
        "rolled sleeve", "cuffed sleeve",
    ],
    "neckline": [
        "crew neck", "round neck", "v-neck", "deep v-neck",
        "scoop neck", "boat neck", "turtleneck", "mock neck",
        "collared", "polo collar", "shirt collar", "mandarin collar",
        "square neck", "sweetheart", "halter", "off-shoulder",
        "cowl neck", "asymmetric neck", "keyhole", "strapless",
        "henley", "notched", "tie neck",
    ],
    "fit": [
        "slim fit", "skinny fit", "regular fit", "relaxed fit",
        "oversized", "loose fit", "tailored fit", "athletic fit",
        "bodycon", "fitted", "form-fitting",
        "a-line", "straight", "tapered", "wide-leg", "bootcut",
        "flared", "balloon", "baggy", "boyfriend fit", "girlfriend fit",
        "compression", "stretchy",
    ],
    "length": [
        "cropped", "crop top", "bra length",
        "waist length", "hip length", "thigh length",
        "knee length", "midi length", "tea length",
        "ankle length", "floor length", "maxi length",
        "mini", "short", "regular", "long", "extra long",
    ],
    "material": [
        "cotton", "organic cotton", "cotton blend",
        "denim", "chambray",
        "wool", "merino wool", "cashmere", "angora", "mohair",
        "silk", "satin", "chiffon", "organza", "tulle",
        "linen", "canvas",
        "leather", "genuine leather", "faux leather", "vegan leather",
        "suede", "faux suede", "nubuck",
        "fleece", "sherpa", "teddy",
        "polyester", "nylon", "spandex", "elastane", "lycra",
        "knit", "jersey", "rib knit", "cable knit",
        "velvet", "corduroy", "tweed", "flannel",
        "mesh", "lace", "crochet", "sequin", "satin",
        "viscose", "rayon", "modal", "tencel",
        "acrylic", "rubber", "latex", "vinyl",
    ],
    "style": [
        "casual", "everyday", "streetwear", "urban", "sporty", "athletic",
        "business", "business casual", "professional", "formal", "elegant",
        "party", "cocktail", "evening", "occasion wear",
        "bohemian", "boho", "hippie", "festival",
        "vintage", "retro", "classic", "timeless",
        "minimalist", "modern", "contemporary",
        "preppy", "collegiate", "ivy league",
        "punk", "grunge", "edgy", "gothic",
        "romantic", "feminine", "girly",
        "western", "cowboy", "southwestern",
        "military", "utilitarian", "workwear",
        "resort", "vacation", "beachwear",
        "athleisure", "activewear", "loungewear",
    ],
    "rise": [
        "high rise", "super high rise", "mid rise", "low rise", "ultra low rise",
    ],
    "waist": [
        "high-waisted", "high waist", "mid-waisted", "natural waist",
        "low-waisted", "drop waist", "empire waist",
    ],
    "closure": [
        "zipper", "zip-up", "front zipper", "back zipper", "side zipper",
        "buttons", "button-up", "button-down", "snap buttons",
        "drawstring", "elastic waistband", "elastic",
        "belted", "tie waist", "wrap",
        "pullover", "pull-on", "slip-on",
        "hook and eye", "velcro", "lace-up", "buckle",
        "no closure", "open front",
    ],
    "gender": [
        "men's", "women's", "unisex", "gender neutral",
        "boys'", "girls'", "kids'", "children's",
    ],
    "occasion": [
        "everyday", "casual wear", "work", "office", "business meeting",
        "formal event", "wedding", "party", "cocktail party",
        "date night", "night out", "clubbing",
        "beach", "vacation", "travel", "resort",
        "gym", "workout", "yoga", "running", "sports",
        "outdoor", "hiking", "camping",
        "lounge", "sleepwear", "home",
    ],
    "season": [
        "spring", "summer", "fall", "autumn", "winter",
        "all season", "year-round", "transitional",
        "warm weather", "hot weather", "cold weather",
    ],
    "detail": [
        "pockets", "side pockets", "cargo pockets", "patch pockets",
        "ruffles", "pleats", "draping", "gathering",
        "cutout", "sheer panels", "mesh inserts",
        "distressed", "ripped", "frayed", "raw hem",
        "studded", "beaded", "sequined", "rhinestone",
        "embellished", "decorated", "applique",
        "ribbed", "quilted", "padded",
        "hooded", "hood", "drawstring hood",
        "fringed", "tasseled", "pompom",
        "reversible", "two-sided",
    ],
    "silhouette": [
        "structured", "unstructured", "tailored", "draped",
        "boxy", "cocoon", "column", "hourglass",
        "shift", "trapeze", "empire", "peplum",
        "wrap", "sarong", "asymmetric",
    ],
    "transparency": [
        "opaque", "semi-sheer", "sheer", "see-through", "transparent",
        "lined", "unlined", "double layer",
    ],
    "texture": [
        "smooth", "soft", "silky", "glossy", "shiny",
        "matte", "brushed", "fuzzy", "fluffy",
        "textured", "ribbed", "waffle", "cable",
        "rough", "coarse", "distressed",
        "stretchy", "elastic", "rigid", "stiff",
    ],
}

ATTRIBUTE_GROUPS: List[str] = list(ATTRIBUTE_SETS.keys())
ATTRIBUTE_HINT_LINES: List[str] = [
    f"{group}: {', '.join(ATTRIBUTE_SETS[group][: min(6, len(ATTRIBUTE_SETS[group]))])}"
    for group in ATTRIBUTE_GROUPS
]


def _paligemma_device() -> torch.device:
    return paligemma_device


def _paligemma_generate(
    image: Image.Image,
    prompt: str,
    *,
    max_new_tokens: int = 256,
    temperature: float = 0.2,
) -> str:
    device = _paligemma_device()
    inputs = paligemma_processor(text=prompt, images=image, return_tensors="pt")
    inputs = {k: v.to(device) for k, v in inputs.items()}
    if "pixel_values" in inputs:
        inputs["pixel_values"] = inputs["pixel_values"].to(device=device, dtype=paligemma_dtype)
    with torch.no_grad():
        generated_ids = paligemma_model.generate(
            **inputs,
            max_new_tokens=max_new_tokens,
            do_sample=False,
            temperature=temperature,
            top_p=0.95,
        )
    output = paligemma_processor.batch_decode(generated_ids, skip_special_tokens=True)[0]
    return output.strip()


PALIGEMMA_CATEGORY_PROMPT = (
    "You are a professional fashion product classifier. Consider the clothing item in the photo "
    "and respond ONLY with a JSON array containing at most {top_k} entries. Each entry must include "
    "\"label\" and \"confidence\" (0-1) describing the garment category (e.g., \"t-shirt\", "
    "\"hoodie\", \"maxi dress\", \"sneakers\")."
)


def _build_attribute_prompt(top_per_group: int) -> str:
    group_list = ", ".join(ATTRIBUTE_GROUPS)
    hints = "\n".join(ATTRIBUTE_HINT_LINES)
    example = '{{"color": [{{"label": "navy", "confidence": 0.7}}], "pattern": [{{"label": "solid", "confidence": 0.3}}]}}'
    return (
        "You are a senior fashion merchandiser. Analyze the garment photo and infer detailed attributes. "
        f"Return ONLY a JSON object with the following keys: {group_list}. "
        f"Each key must map to an array with up to {max(1, top_per_group)} objects containing "
        "\"label\" and \"confidence\" (0-1). Use concise fashion terminology and keep confidences normalized. "
        f"Attribute hints per group (use these as guidance, but other precise terms are allowed):\n{hints}\n"
        f"Example format: {example}"
    )


def _normalize_confidences(items: List[Dict[str, Any]]) -> None:
    scores = [max(0.0, float(item.get("confidence", 0.0))) for item in items]
    total = sum(scores)
    if total <= 0:
        even = 1.0 / len(items) if items else 0
        for item in items:
            item["confidence"] = round(float(even), 4)
        return
    for idx, item in enumerate(items):
        item["confidence"] = round(float(scores[idx] / total), 4)


def _parse_paligemma_categories(text: str, top_k: int) -> List[Dict[str, float]]:
    start = text.find("[")
    end = text.rfind("]")
    parsed: List[Dict[str, float]] = []
    if start != -1 and end != -1 and end > start:
        snippet = text[start:end+1]
        try:
            data = json.loads(snippet)
            if isinstance(data, dict):
                data = [data]
            if isinstance(data, list):
                for item in data:
                    if not isinstance(item, dict):
                        continue
                    label = str(item.get("label") or item.get("category") or "").strip()
                    if not label:
                        continue
                    confidence = item.get("confidence")
                    try:
                        conf_val = float(confidence) if confidence is not None else 0.0
                    except (TypeError, ValueError):
                        conf_val = 0.0
                    parsed.append({"label": label, "confidence": conf_val})
        except json.JSONDecodeError:
            parsed = []
    if not parsed:
        fallback_label = text.splitlines()[0].strip() if text else ""
        if not fallback_label:
            fallback_label = "unknown garment"
        parsed = [{"label": fallback_label, "confidence": 1.0}]
    parsed = parsed[: max(1, top_k)]
    _normalize_confidences(parsed)
    return parsed


def classify_garment(image: Image.Image, top_k: int) -> List[Dict[str, float]]:
    max_top_k = max(1, top_k)
    prompt = PALIGEMMA_CATEGORY_PROMPT.format(top_k=max_top_k)
    raw = _paligemma_generate(image, prompt, max_new_tokens=192)
    return _parse_paligemma_categories(raw, top_k=max(1, top_k))


def extract_dominant_colors(image: Image.Image, n_colors: int = 5):
    """Return top N dominant colors as hex with percentage using PIL quantization."""
    small = image.copy()
    small.thumbnail((256, 256))
    paletted = small.convert("P", palette=Image.ADAPTIVE, colors=max(n_colors, 5))
    palette = paletted.getpalette()
    color_counts = paletted.getcolors()
    if not color_counts:
        return []
    total = sum(count for count, _ in color_counts)

    def rgb_to_hex(r: int, g: int, b: int) -> str:
        return f"#{r:02x}{g:02x}{b:02x}"

    swatches = []
    for count, idx in sorted(color_counts, reverse=True)[:n_colors]:
        base = idx * 3
        r, g, b = palette[base:base+3]
        pct = count / total
        swatches.append({"hex": rgb_to_hex(r, g, b), "percent": round(pct, 4)})
    return swatches


def _coerce_attribute_items(raw: Any) -> List[Dict[str, float]]:
    items: List[Dict[str, float]] = []
    if isinstance(raw, list):
        for value in raw:
            items.extend(_coerce_attribute_items(value))
    elif isinstance(raw, dict):
        label = str(raw.get("label") or raw.get("value") or "").strip()
        if label:
            confidence = raw.get("confidence")
            try:
                conf_val = float(confidence) if confidence is not None else 0.0
            except (TypeError, ValueError):
                conf_val = 0.0
            items.append({"label": label, "confidence": conf_val})
    elif isinstance(raw, str):
        text = raw.strip()
        if text:
            items.append({"label": text, "confidence": 0.0})
    return items


def _parse_paligemma_attributes(text: str, top_per_group: int) -> Dict[str, List[Dict[str, float]]]:
    start = text.find("{")
    end = text.rfind("}")
    payload: Dict[str, Any] = {}
    if start != -1 and end != -1 and end > start:
        snippet = text[start:end+1]
        try:
            candidate = json.loads(snippet)
            if isinstance(candidate, dict):
                payload = candidate
        except json.JSONDecodeError:
            payload = {}
    results: Dict[str, List[Dict[str, float]]] = {}
    top_n = max(1, top_per_group)
    for group in ATTRIBUTE_GROUPS:
        raw_group = payload.get(group)
        if raw_group is None:
            raw_group = payload.get(group.capitalize())
        items = _coerce_attribute_items(raw_group)
        if not items:
            fallback = ATTRIBUTE_SETS[group][0] if ATTRIBUTE_SETS[group] else "unspecified"
            items = [{"label": fallback, "confidence": 1.0}]
        items = items[:top_n]
        _normalize_confidences(items)
        results[group] = items
    return results


def analyze_attributes(image: Image.Image, top_per_group: int = 1):
    """Infer garment attributes via Paligemma JSON prompting."""
    limit = max(1, top_per_group)
    prompt = _build_attribute_prompt(limit)
    raw = _paligemma_generate(image, prompt, max_new_tokens=512, temperature=0.3)
    return _parse_paligemma_attributes(raw, top_per_group=limit)


@app.post("/analyze")
async def analyze(
    file: UploadFile = File(...),
    top_k_category: int = 3,
    top_per_attribute: int = 1,
    n_colors: int = 5,
):
    """
    Comprehensive analysis: category, attribute predictions (via Paligemma), and dominant colors.
    """
    image_data = await file.read()
    image = Image.open(io.BytesIO(image_data)).convert("RGB")

    # Category via PaliGemma
    categories = classify_garment(image, top_k=int(max(1, top_k_category)))

    # Attributes via Paligemma
    attributes = analyze_attributes(image, top_per_group=int(max(1, top_per_attribute)))

    # Dominant colors via quantization
    colors = extract_dominant_colors(image, n_colors=int(max(1, n_colors)))

    return {
        "category": categories,
        "attributes": attributes,
        "colors": colors,
    }


# --- Text embedding model (all-MiniLM-L6-v2) ---
embed_model_name = "sentence-transformers/all-MiniLM-L6-v2"
embed_model = SentenceTransformer(embed_model_name)


class EmbedRequest(BaseModel):
    texts: List[str]
    normalize: bool = True
    batch_size: int = 32


class EmbedResponse(BaseModel):
    embeddings: List[List[float]]
    model: str
    dim: int


@app.post("/embed", response_model=EmbedResponse)
async def embed(req: EmbedRequest):
    if not req.texts:
        return {"embeddings": [], "model": embed_model_name, "dim": 0}
    vectors = embed_model.encode(
        req.texts,
        batch_size=max(1, int(req.batch_size)),
        convert_to_numpy=True,
        normalize_embeddings=bool(req.normalize),
        show_progress_bar=False,
    )
    embeddings = vectors.tolist()
    dim = len(embeddings[0]) if embeddings else 0
    return {"embeddings": embeddings, "model": embed_model_name, "dim": dim}
