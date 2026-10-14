"""
Step 1: Parse N-Triples file and clean URIs to human-readable names.

Label priority for entity names (first match wins):
  1. basic_name   — human-readable material/product name ("Hemp fiber", "Cement")
  2. nickname     — short name for knit samples and yarns
  3. yarnNickname — yarn-specific short name
  4. rdfs:label   — generic RDF label (fallback)
  5. URI local name (after last / or #)

Predicates always use URI local name (labels not applied to predicates).

Input:  query-result.nt
Output: step1_clean.tsv   (tab-separated: <Name>\t<predicate>\t<obj>)
"""
import re

INPUT  = "query-result.nt"
OUTPUT = "step1_clean.tsv"

# Ordered from highest to lowest priority
LABEL_PREDICATES = [
    "<http://example.org/material/properties/basic_name>",
    "<http://example.org/fiber/ontology/nickname>",
    "<http://example.org/knit/ontology/nickname>",
    "<http://example.org/knit/ontology/yarnNickname>",
    "<http://www.w3.org/2000/01/rdf-schema#label>",
]
LABEL_PRIORITY = {p: i for i, p in enumerate(LABEL_PREDICATES)}

def local_name(uri: str) -> str:
    """Extract local part after last / or # from a full URI <...>."""
    inner = uri[1:-1]
    pos = max(inner.rfind("/"), inner.rfind("#"))
    return "<" + inner[pos + 1:] + ">"

def clean_literal(raw: str) -> str:
    """Keep only the quoted string value, drop ^^datatype and @lang."""
    raw = raw.split("^^")[0].split("@")[0]
    m = re.search(r'"(.*)"', raw, re.DOTALL)
    return '"' + (m.group(1) if m else "") + '"'

def parse_triple(line):
    """Parse one N-Triples line into (subj_uri, pred_uri, obj_raw) or None."""
    line = line.strip()
    if not line or line.startswith("#"):
        return None
    if line.endswith(" ."):
        line = line[:-2].rstrip()
    elif line.endswith("."):
        line = line[:-1].rstrip()
    if not line.startswith("<"):
        return None
    try:
        end_s = line.index(">") + 1
        subj  = line[:end_s]
        rest  = line[end_s:].lstrip()
        if not rest.startswith("<"):
            return None
        end_p = rest.index(">") + 1
        pred  = rest[:end_p]
        obj   = rest[end_p:].lstrip()
        return subj, pred, obj
    except Exception:
        return None

# ── Pass 1: build URI → label map using priority chain ─────────────────────
print("Pass 1: collecting entity labels …")
labels = {}        # uri → best label string so far
label_rank = {}    # uri → priority index of current best (lower = better)

with open(INPUT, encoding="utf-8", errors="replace") as fin:
    for lineno, line in enumerate(fin, 1):
        t = parse_triple(line)
        if t is None:
            continue
        subj, pred, obj = t
        rank = LABEL_PRIORITY.get(pred)
        if rank is None or not obj.startswith('"'):
            continue
        # Only overwrite if this predicate has higher priority (lower rank)
        if rank < label_rank.get(subj, len(LABEL_PREDICATES)):
            inner = clean_literal(obj)[1:-1].strip()
            if inner:
                labels[subj] = "<" + inner.lower() + ">"
                label_rank[subj] = rank
        if lineno % 200_000 == 0:
            print(f"  {lineno:,} lines scanned …")

del label_rank  # free memory
print(f"  Labels found: {len(labels):,}")

# ── Pass 2: write cleaned triple file using labels where available ──────────
print("Pass 2: writing cleaned triples …")
written = skipped = 0

with open(INPUT, encoding="utf-8", errors="replace") as fin, \
     open(OUTPUT, "w", encoding="utf-8") as fout:

    for lineno, line in enumerate(fin, 1):
        t = parse_triple(line)
        if t is None:
            skipped += 1
            continue
        subj, pred, obj = t

        # Subject: use label if available, else local name
        subj_out = labels.get(subj, local_name(subj))

        # Predicate: always local name (labels not used for predicates)
        pred_out = local_name(pred)

        # Object
        if obj.startswith("<"):
            obj_out = labels.get(obj, local_name(obj))
        elif obj.startswith('"'):
            obj_out = clean_literal(obj)
        else:
            skipped += 1   # blank node
            continue

        fout.write(f"{subj_out}\t{pred_out}\t{obj_out}\n")
        written += 1

        if lineno % 200_000 == 0:
            print(f"  {lineno:,} lines read, {written:,} written …")

print(f"Done. Triples written: {written:,}  |  Skipped: {skipped:,}")