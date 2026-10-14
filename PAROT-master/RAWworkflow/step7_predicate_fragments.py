"""
Step 7: Build predicate fragment file.
Each line represents one unique (subject-type-set, predicate, object-type-set) combination:
  [type_ids] TAB predicate_id TAB [type_ids] or "literal"

Input:  step5_entity_fragments.tsv, step4_id_triples.tsv, ids_predicate.tsv
Output: step7_predicate_fragments.tsv
"""

IN_ENTITY_FRAG = "step5_entity_fragments.tsv"
IN_TRIPLES     = "step4_id_triples.tsv"
IN_PRED        = "ids_predicate.tsv"
OUTPUT         = "step7_predicate_fragments.tsv"

# Find type predicate ID
TYPE_PRED_ID = None
with open(IN_PRED, encoding="utf-8") as f:
    for line in f:
        parts = line.rstrip("\n").split("\t")
        if parts[0] == "<type>":
            TYPE_PRED_ID = parts[1]
            break

# Build entity → type set from entity fragments
print("Loading entity→type map …")
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

# Collect unique predicate fragment strings
sen = set()               # non-literal object triples (deduped by type pattern)
literal_preds = {}        # pred_id → set of subject entity ids (for literal triples)

print("Reading triples …")
with open(IN_TRIPLES, encoding="utf-8") as f:
    for i, line in enumerate(f, 1):
        parts = line.rstrip("\n").split("\t")
        if len(parts) < 3:
            continue
        s, p, o = parts[0], parts[1], parts[2]

        if p == TYPE_PRED_ID:
            continue

        s_types = en2t.get(s, set())
        pre = "[" + ",".join(s_types) + "]"

        if o != "-1":
            o_types = en2t.get(o, set())
            pos = "[" + ",".join(o_types) + "]"
            sen.add(f"{pre}\t{p}\t{pos}\n")
        else:
            literal_preds.setdefault(p, set()).add(s)

        if i % 200_000 == 0:
            print(f"  {i:,} triples processed …")

# Add literal entries
for p, subj_set in literal_preds.items():
    all_s_types = set()
    for s in subj_set:
        all_s_types.update(en2t.get(s, set()))
    pre = "[" + ",".join(all_s_types) + "]"
    sen.add(f"{pre}\t{p}\tliteral\n")

print(f"Writing {len(sen):,} predicate fragment entries …")
with open(OUTPUT, "w", encoding="utf-8") as fout:
    for item in sen:
        fout.write(item)

print(f"Done → {OUTPUT}")