"""
Step 3: Extract all unique entity / predicate / type names and assign integer IDs.
Input:  step2_dedup.tsv
Output: ids_entity.tsv   (<name> TAB id)
        ids_predicate.tsv
        ids_type.tsv
"""

INPUT          = "step2_dedup.tsv"
OUT_ENTITY     = "ids_entity.tsv"
OUT_PREDICATE  = "ids_predicate.tsv"
OUT_TYPE       = "ids_type.tsv"

entities   = set()
predicates = set()
types      = set()

with open(INPUT, encoding="utf-8") as fin:
    for i, line in enumerate(fin, 1):
        parts = line.rstrip("\n").split("\t")
        if len(parts) < 3:
            continue
        s, p, o = parts[0], parts[1], parts[2]

        entities.add(s)
        predicates.add(p)

        # Object is an entity only if it is a URI token (starts with <)
        if o.startswith("<"):
            entities.add(o)
            if p == "<type>":
                types.add(o)

        if i % 200_000 == 0:
            print(f"  {i:,} lines read …")

def write_ids(names, path):
    with open(path, "w", encoding="utf-8") as f:
        for idx, name in enumerate(sorted(names)):
            f.write(f"{name}\t{idx}\n")
    return len(names)

ne = write_ids(entities,   OUT_ENTITY)
np = write_ids(predicates, OUT_PREDICATE)
nt = write_ids(types,      OUT_TYPE)

print(f"Done.")
print(f"  Entities  : {ne:,}  → {OUT_ENTITY}")
print(f"  Predicates: {np:,}  → {OUT_PREDICATE}")
print(f"  Types     : {nt:,}  → {OUT_TYPE}")
