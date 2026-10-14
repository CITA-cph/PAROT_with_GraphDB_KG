"""
Step 4: Replace name strings in the triple file with their integer IDs.
Literals become -1.
Input:  step2_dedup.tsv, ids_entity.tsv, ids_predicate.tsv, ids_type.tsv
Output: step4_id_triples.tsv   (subject_id TAB predicate_id TAB object_id)
"""

INPUT         = "step2_dedup.tsv"
IN_ENTITY     = "ids_entity.tsv"
IN_PREDICATE  = "ids_predicate.tsv"
IN_TYPE       = "ids_type.tsv"
OUTPUT        = "step4_id_triples.tsv"

def load_ids(path):
    mapping = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) == 2:
                mapping[parts[0]] = parts[1]
    return mapping

print("Loading ID maps …")
eid = load_ids(IN_ENTITY)
pid = load_ids(IN_PREDICATE)
tid = load_ids(IN_TYPE)
print(f"  Entities: {len(eid):,}  Predicates: {len(pid):,}  Types: {len(tid):,}")

written = errors = 0
with open(INPUT, encoding="utf-8") as fin, \
     open(OUTPUT, "w", encoding="utf-8") as fout:

    for i, line in enumerate(fin, 1):
        parts = line.rstrip("\n").split("\t")
        if len(parts) < 3:
            continue
        s, p, o = parts[0], parts[1], parts[2]

        s_id = eid.get(s)
        p_id = pid.get(p)
        if s_id is None or p_id is None:
            errors += 1
            continue

        if p == "<type>":
            o_id = tid.get(o, "-1")
        elif o.startswith('"'):
            o_id = "-1"          # literal
        else:
            o_id = eid.get(o, "-1")

        fout.write(f"{s_id}\t{p_id}\t{o_id}\n")
        written += 1

        if i % 200_000 == 0:
            print(f"  {i:,} lines processed …")

print(f"Done. Written: {written:,}  |  Errors: {errors:,}")
