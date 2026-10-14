"""
Step 2: Remove duplicate triples.
Input:  step1_clean.tsv
Output: step2_dedup.tsv
"""

INPUT  = "step1_clean.tsv"
OUTPUT = "step2_dedup.tsv"

seen = set()
with open(INPUT, encoding="utf-8") as fin:
    for line in fin:
        seen.add(line)

with open(OUTPUT, "w", encoding="utf-8") as fout:
    for line in seen:
        fout.write(line)

print(f"Done. Unique triples: {len(seen):,}")
