# filename: app.py
from fastapi import FastAPI, File, UploadFile, Form
from transformers import (
    AutoProcessor,
    AutoModelForImageClassification,
    CLIPProcessor,
    CLIPModel
)
from PIL import Image
import torch
import io
from typing import List, Dict, Tuple
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

app = FastAPI(title="Garment Classification & Fashion CLIP API")

# --- Model 1: ViT clothes classification ---
vit_model_name = "jolual2747/vit-clothes-classification"
vit_processor = AutoProcessor.from_pretrained(vit_model_name)
vit_model = AutoModelForImageClassification.from_pretrained(vit_model_name)


@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    """Predict garment type using ViT-based classification"""
    image_data = await file.read()
    image = Image.open(io.BytesIO(image_data)).convert("RGB")

    inputs = vit_processor(images=image, return_tensors="pt")

    with torch.no_grad():
        outputs = vit_model(**inputs)
        probs = torch.nn.functional.softmax(outputs.logits, dim=-1)
        top_prob, top_idx = torch.topk(probs, k=3)
        results = [
            {"label": vit_model.config.id2label[idx.item()], "confidence": prob.item()}
            for prob, idx in zip(top_prob[0], top_idx[0])
        ]

    return {"predictions": results}


# --- Model 2: Fashion CLIP (for text-image similarity) ---
clip_model_name = "patrickjohncyh/fashion-clip"
clip_model = CLIPModel.from_pretrained(clip_model_name)
clip_processor = CLIPProcessor.from_pretrained(clip_model_name)


@app.post("/clip-predict")
async def clip_predict(
    file: UploadFile = File(...),
    labels: str = Form(...)
):
    """
    Compare an image with a list of fashion labels (comma-separated)
    Example: "T-shirt,Jeans,Coat,Sneakers"
    """
    image_data = await file.read()
    image = Image.open(io.BytesIO(image_data)).convert("RGB")

    # Split labels
    text_labels = [label.strip() for label in labels.split(",") if label.strip()]

    # Process inputs
    inputs = clip_processor(
        text=text_labels,
        images=image,
        return_tensors="pt",
        padding=True
    )

    # Compute similarity
    with torch.no_grad():
        outputs = clip_model(**inputs)
        logits_per_image = outputs.logits_per_image  # (1, num_labels)
        probs = logits_per_image.softmax(dim=1)

    # Sort and return results
    sorted_probs, indices = torch.sort(probs, descending=True)
    results = [
        {"label": text_labels[idx], "confidence": sorted_probs[0, i].item()}
        for i, idx in enumerate(indices[0])
    ]

    return {"predictions": results}


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


def _clip_rank(image: Image.Image, candidates: List[str], prompt_template: str = "{}"):
    """Rank candidate labels for an image using CLIP zero-shot prompting.

    Returns list of (label, probability) sorted desc.
    """
    texts = [prompt_template.format(lbl) for lbl in candidates]
    inputs = clip_processor(text=texts, images=image, return_tensors="pt", padding=True)
    with torch.no_grad():
        outputs = clip_model(**inputs)
        logits_per_image = outputs.logits_per_image
        probs = logits_per_image.softmax(dim=1)
    sorted_probs, indices = torch.sort(probs, descending=True)
    ranked = [(candidates[idx], float(sorted_probs[0, i].item())) for i, idx in enumerate(indices[0])]
    return ranked


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


def analyze_attributes(image: Image.Image, top_per_group: int = 1):
    """Analyze several attribute groups via CLIP zero-shot ranking."""
    templates = {
        "color": "a photo of a {} clothing item",
        "pattern": "{} pattern clothing",
        "sleeve": "a {} sleeve shirt",
        "neckline": "a {} neckline top",
        "fit": "a {} fit clothing",
        "length": "a {} length garment",
        "material": "{} material clothing",
        "style": "{} style outfit",
        "rise": "{} jeans",
        "waist": "{} skirt",
        "closure": "a clothing with {}",
        "gender": "a {} clothing item",
        "occasion": "{} clothing",
        "season": "{} clothing",
        "detail": "clothing with {}",
        "silhouette": "a {} silhouette garment",
        "transparency": "{} fabric clothing",
        "texture": "{} texture clothing",
    }

    results: Dict[str, List[Dict[str, float]]] = {}
    for group, candidates in ATTRIBUTE_SETS.items():
        prompt = templates.get(group, "{}")
        ranked = _clip_rank(image, candidates, prompt)
        topk = [
            {"label": lbl, "confidence": float(prob)}
            for lbl, prob in ranked[: max(1, top_per_group)]
        ]
        results[group] = topk
    return results


@app.post("/analyze")
async def analyze(
    file: UploadFile = File(...),
    top_k_category: int = 3,
    top_per_attribute: int = 1,
    n_colors: int = 5,
):
    """
    Comprehensive analysis: category, attribute predictions (via CLIP), and dominant colors.
    """
    image_data = await file.read()
    image = Image.open(io.BytesIO(image_data)).convert("RGB")

    # Category via ViT
    inputs = vit_processor(images=image, return_tensors="pt")
    with torch.no_grad():
        outputs = vit_model(**inputs)
        probs = torch.nn.functional.softmax(outputs.logits, dim=-1)
        k = max(1, min(int(top_k_category), probs.shape[-1]))
        top_prob, top_idx = torch.topk(probs, k=k)
        categories = [
            {"label": vit_model.config.id2label[idx.item()], "confidence": float(prob.item())}
            for prob, idx in zip(top_prob[0], top_idx[0])
        ]

    # Attributes via CLIP
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
