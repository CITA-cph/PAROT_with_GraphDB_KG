"""
Step 5: Build entity fragment file.
For each entity, record its graph neighbourhood as:
  <entity_id> TAB <InEntEdge>|<OutEntEdge>|<InEdge>|<OutEdge>|<Types>

Input:  step4_id_triples.tsv, ids_entity.tsv, ids_predicate.tsv
Output: step5_entity_fragments.tsv
"""

IN_TRIPLES = "step4_id_triples.tsv"
IN_ENTITY  = "ids_entity.tsv"
OUTPUT     = "step5_entity_fragments.tsv"

# Find the <type> predicate ID
IN_PRED    = "ids_predicate.tsv"
TYPE_PRED_ID = None
with open(IN_PRED, encoding="utf-8") as f:
    for line in f:
        parts = line.rstrip("\n").split("\t")
        if parts[0] == "<type>":
            TYPE_PRED_ID = parts[1]
            break
if TYPE_PRED_ID is None:
    print("WARNING: <type> predicate not found – type edges will be empty.")

# Count entities so we know how many IDs to iterate
num_entities = sum(1 for _ in open(IN_ENTITY, encoding="utf-8"))
print(f"Entity count: {num_entities:,}  |  type predicate id: {TYPE_PRED_ID}")

# Structures
inEnEdge  = {}   # entity_id → {neighbour_id → set(pred_id)}
outEnEdge = {}
inEdge    = {}   # entity_id → set(pred_id)
outEdge   = {}
types     = {}   # entity_id → set(type_id)

print("Reading triples …")
with open(IN_TRIPLES, encoding="utf-8") as f:
    for i, line in enumerate(f, 1):
        parts = line.rstrip("\n").split("\t")
        if len(parts) < 3:
            continue
        s, p, o = parts[0], parts[1], parts[2]

        if p == TYPE_PRED_ID and o != "-1":
            types.setdefault(s, set()).add(o)
        else:
            outEdge.setdefault(s, set()).add(p)
            if o != "-1":
                outEnEdge.setdefault(s, {}).setdefault(o, set()).add(p)
                inEdge.setdefault(o, set()).add(p)
                inEnEdge.setdefault(o, {}).setdefault(s, set()).add(p)

        if i % 200_000 == 0:
            print(f"  {i:,} triples read …")

print("Writing entity fragments …")
with open(OUTPUT, "w", encoding="utf-8") as fout:
    for eid in range(num_entities):
        eid_s = str(eid)
        parts = []

        # InEntEdge
        seg = ""
        if eid_s in inEnEdge:
            seg = ",".join(
                k + ":" + ";".join(v)
                for k, v in inEnEdge[eid_s].items()
            )
        parts.append(seg)

        # OutEntEdge
        seg = ""
        if eid_s in outEnEdge:
            seg = ",".join(
                k + ":" + ";".join(v)
                for k, v in outEnEdge[eid_s].items()
            )
        parts.append(seg)

        # InEdge
        parts.append(",".join(inEdge.get(eid_s, [])))

        # OutEdge
        parts.append(",".join(outEdge.get(eid_s, [])))

        # Types
        parts.append(",".join(types.get(eid_s, [])))

        fout.write(f"{eid_s}\t{'|'.join(parts)}\n")

        if eid % 200_000 == 0 and eid > 0:
            print(f"  {eid:,} entities written …")

print(f"Done → {OUTPUT}")
