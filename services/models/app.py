# filename: app.py
import logging
from fastapi import FastAPI, File, UploadFile
from transformers import (
    ViTImageProcessor,
    AutoModelForImageClassification,
    CLIPProcessor,
    CLIPModel
)
from PIL import Image
import torch
import io
from typing import List, Dict
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")
logger = logging.getLogger("app")

app = FastAPI(title="Garment Classification & Fashion CLIP API")

# --- Model 1: ViT clothes classification ---
# jolual2747/vit-clothes-classification is a fine-tune of google/vit-base-patch16-224-in21k
# but its repo has no preprocessor_config.json, so we load the processor from the base model.
vit_model_name = "jolual2747/vit-clothes-classification"
logger.info("Loading ViT processor from google/vit-base-patch16-224 ...")
vit_processor = ViTImageProcessor.from_pretrained("google/vit-base-patch16-224")
logger.info("Loading ViT model %s ...", vit_model_name)
vit_model = AutoModelForImageClassification.from_pretrained(vit_model_name)
vit_model.eval()
logger.info("ViT model loaded.")

# Fashion CLIP used for attribute ranking
clip_model_name = "patrickjohncyh/fashion-clip"
logger.info("Loading Fashion-CLIP model %s ...", clip_model_name)
clip_model = CLIPModel.from_pretrained(clip_model_name)
clip_model.eval()
clip_processor = CLIPProcessor.from_pretrained(clip_model_name)
logger.info("Fashion-CLIP model loaded.")


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


def _get_image_features(image: Image.Image) -> torch.Tensor:
    """Encode image once for reuse across CLIP attribute groups."""
    inputs = clip_processor(images=image, return_tensors="pt")
    with torch.no_grad():
        vision_outputs = clip_model.vision_model(pixel_values=inputs["pixel_values"])
        # Use pooler_output (CLS token projection) when available, else mean-pool
        if vision_outputs.pooler_output is not None:
            feats = clip_model.visual_projection(vision_outputs.pooler_output)
        else:
            feats = clip_model.visual_projection(vision_outputs.last_hidden_state[:, 0, :])
    return feats / feats.norm(dim=-1, keepdim=True)


def _clip_rank_with_features(image_features: torch.Tensor, candidates: List[str], prompt_template: str = "{}"):
    """Rank candidates using pre-computed image features. Batches text in chunks of 64."""
    BATCH = 64
    scores = []
    for i in range(0, len(candidates), BATCH):
        batch_candidates = candidates[i:i + BATCH]
        texts = [prompt_template.format(lbl) for lbl in batch_candidates]
        inputs = clip_processor(text=texts, return_tensors="pt", padding=True, truncation=True)
        with torch.no_grad():
            text_outputs = clip_model.text_model(
                input_ids=inputs["input_ids"],
                attention_mask=inputs["attention_mask"],
            )
            if text_outputs.pooler_output is not None:
                text_features = clip_model.text_projection(text_outputs.pooler_output)
            else:
                text_features = clip_model.text_projection(text_outputs.last_hidden_state[:, 0, :])
        text_features = text_features / text_features.norm(dim=-1, keepdim=True)
        batch_scores = (image_features @ text_features.T).squeeze(0)
        scores.extend(zip(batch_candidates, batch_scores.tolist()))

    scores.sort(key=lambda x: x[1], reverse=True)
    raw = torch.tensor([s for _, s in scores])
    probs = raw.softmax(dim=0).tolist()
    return [(label, prob) for (label, _), prob in zip(scores, probs)]


def _clip_rank(image: Image.Image, candidates: List[str], prompt_template: str = "{}"):
    """Rank candidate labels for an image using CLIP zero-shot prompting.

    Returns list of (label, probability) sorted desc.
    """
    image_features = _get_image_features(image)
    return _clip_rank_with_features(image_features, candidates, prompt_template)


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
    """Analyze several attribute groups via CLIP zero-shot ranking.

    The image is encoded once; text candidates for each group are batched in chunks.
    """
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

    # Encode the image a single time and reuse across all attribute groups
    image_features = _get_image_features(image)

    results: Dict[str, List[Dict[str, float]]] = {}
    for group, candidates in ATTRIBUTE_SETS.items():
        prompt = templates.get(group, "{}")
        ranked = _clip_rank_with_features(image_features, candidates, prompt)
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
    logger.info(
        "Received /analyze request: file=%s, top_k_category=%d, top_per_attribute=%d, n_colors=%d",
        file.filename, top_k_category, top_per_attribute, n_colors,
    )
    image_data = await file.read()
    image = Image.open(io.BytesIO(image_data)).convert("RGB")
    logger.info("Image loaded: size=%s, mode=%s", image.size, image.mode)

    # Category via ViT
    logger.info("Classifying garment category...")
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
    logger.info("Categories predicted: %s", categories)

    # Attributes via CLIP
    logger.info("Analyzing garment attributes...")
    attributes = analyze_attributes(image, top_per_group=int(max(1, top_per_attribute)))
    logger.info("Attributes analyzed.")

    # Dominant colors via quantization
    logger.info("Extracting dominant colors...")
    colors = extract_dominant_colors(image, n_colors=int(max(1, n_colors)))
    logger.info("Colors extracted: %s", colors)

    return {
        "category": categories,
        "attributes": attributes,
        "colors": colors,
    }


# --- Text embedding model (all-MiniLM-L6-v2) ---
embed_model_name = "sentence-transformers/all-MiniLM-L6-v2"
logger.info("Loading sentence-transformer %s ...", embed_model_name)
embed_model = SentenceTransformer(embed_model_name)
logger.info("All models loaded. API ready.")


@app.get("/health")
async def health():
    return {"status": "ok"}


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
