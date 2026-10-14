"""
Step 6: Build type fragment file.
For each type, record:
  <type_id> TAB <InEdge predicates>|<OutEdge predicates>|<member entities>

Input:  step5_entity_fragments.tsv, step4_id_triples.tsv, ids_type.tsv, ids_predicate.tsv
Output: step6_type_fragments.tsv
"""

IN_ENTITY_FRAG = "step5_entity_fragments.tsv"
IN_TRIPLES     = "step4_id_triples.tsv"
IN_TYPE        = "ids_type.tsv"
IN_PRED        = "ids_predicate.tsv"
OUTPUT         = "step6_type_fragments.tsv"

# Find type predicate ID
TYPE_PRED_ID = None
with open(IN_PRED, encoding="utf-8") as f:
    for line in f:
        parts = line.rstrip("\n").split("\t")
        if parts[0] == "<type>":
            TYPE_PRED_ID = parts[1]
            break

num_types = sum(1 for _ in open(IN_TYPE, encoding="utf-8"))
print(f"Type count: {num_types:,}  |  type predicate id: {TYPE_PRED_ID}")

# Build entity → type set map from entity fragment file (column 5 = types)
print("Loading entity→type map from entity fragments …")
en2t = {}
with open(IN_ENTITY_FRAG, encoding="utf-8") as f:
    for line in f:
        cols = line.rstrip("\n").split("\t")
        if len(cols) < 2:
            continue
        eid = cols[0]
        sections = cols[1].split("|")
        if len(sections) >= 5:
            t_list = [t for t in sections[4].split(",") if t]
            en2t[eid] = set(t_list)
        else:
            en2t[eid] = set()

# type_id → [in_preds, out_preds, entities]
lisen = {str(i): [set(), set(), set()] for i in range(num_types)}

print("Reading triples to build type fragments …")
with open(IN_TRIPLES, encoding="utf-8") as f:
    for i, line in enumerate(f, 1):
        parts = line.rstrip("\n").split("\t")
        if len(parts) < 3:
            continue
        s, p, o = parts[0], parts[1], parts[2]

        if p == TYPE_PRED_ID:
            continue  # skip rdf:type triples themselves

        for t in en2t.get(s, []):
            if t in lisen:
                lisen[t][1].add(p)   # OutEdge predicate
                lisen[t][2].add(s)   # member entity

        if o != "-1":
            for t in en2t.get(o, []):
                if t in lisen:
                    lisen[t][0].add(p)   # InEdge predicate
                    lisen[t][2].add(o)   # member entity

        if i % 200_000 == 0:
            print(f"  {i:,} triples read …")

print("Writing type fragments …")
with open(OUTPUT, "w", encoding="utf-8") as fout:
    for k, v in lisen.items():
        line = (
            k + "\t"
            + ",".join(v[0]) + "|"
            + ",".join(v[1]) + "|"
            + ",".join(v[2]) + "\n"
        )
        fout.write(line)

print(f"Done → {OUTPUT}  ({num_types:,} type entries)")
