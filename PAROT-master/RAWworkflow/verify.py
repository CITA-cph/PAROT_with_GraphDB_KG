"""
verify.py — sanity-check the 6 PAROT fragment output files.
Run this after all 7 steps complete. No network or Java needed.
"""

import os

SEP = "=" * 60

def file_stats(path):
    size = os.path.getsize(path)
    with open(path, encoding="utf-8") as f:
        lines = sum(1 for _ in f)
    mb = size / 1_048_576
    return lines, mb

def sample_lines(path, n=3):
    lines = []
    with open(path, encoding="utf-8") as f:
        for i, line in enumerate(f):
            if i >= n:
                break
            lines.append(line.rstrip("\n"))
    return lines

def check_entity_fragments(path):
    """Check that every line has exactly 5 pipe-separated sections."""
    bad = 0
    with open(path, encoding="utf-8") as f:
        for i, line in enumerate(f):
            cols = line.rstrip("\n").split("\t")
            if len(cols) < 2 or cols[1].count("|") != 4:
                bad += 1
    return bad

def check_type_fragments(path):
    """Check that every line has exactly 3 pipe-separated sections."""
    bad = 0
    with open(path, encoding="utf-8") as f:
        for i, line in enumerate(f):
            cols = line.rstrip("\n").split("\t")
            if len(cols) < 2 or cols[1].count("|") != 2:
                bad += 1
    return bad

def check_pred_fragments(path):
    """Check that every line has 3 tab-separated fields."""
    bad = 0
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.rstrip("\n").count("\t") != 2:
                bad += 1
    return bad

FILES = [
    ("ids_entity.tsv",                "Entity ID map"),
    ("ids_predicate.tsv",             "Predicate ID map"),
    ("ids_type.tsv",                  "Type ID map"),
    ("step5_entity_fragments.tsv",    "Entity fragments"),
    ("step6_type_fragments.tsv",      "Type fragments"),
    ("step7_predicate_fragments.tsv", "Predicate fragments"),
]

print(SEP)
print("  PAROT Fragment Output Verification")
print(SEP)

all_ok = True

for fname, label in FILES:
    print(f"\n[{label}]  {fname}")
    if not os.path.exists(fname):
        print(f"  ❌  FILE NOT FOUND")
        all_ok = False
        continue

    lines, mb = file_stats(fname)
    print(f"  Lines : {lines:,}   Size: {mb:.1f} MB")

    # Structure check
    if fname == "step5_entity_fragments.tsv":
        bad = check_entity_fragments(fname)
        if bad:
            print(f"  ⚠️   {bad:,} lines with wrong structure (expected 5 pipe-sections)")
            all_ok = False
        else:
            print(f"  ✅  Structure OK (all lines have 5 pipe-sections)")

    elif fname == "step6_type_fragments.tsv":
        bad = check_type_fragments(fname)
        if bad:
            print(f"  ⚠️   {bad:,} lines with wrong structure (expected 3 pipe-sections)")
            all_ok = False
        else:
            print(f"  ✅  Structure OK (all lines have 3 pipe-sections)")

    elif fname == "step7_predicate_fragments.tsv":
        bad = check_pred_fragments(fname)
        if bad:
            print(f"  ⚠️   {bad:,} lines with wrong structure (expected 3 tab-fields)")
            all_ok = False
        else:
            print(f"  ✅  Structure OK (all lines have 3 tab-fields)")

    # Sample
    print("  Sample lines:")
    for s in sample_lines(fname, 3):
        display = s if len(s) <= 120 else s[:117] + "…"
        print(f"    {display}")

# Cross-check counts
print(f"\n{SEP}")
print("  Cross-checks")
print(SEP)

try:
    with open("ids_entity.tsv") as f:
        ne = sum(1 for _ in f)
    with open("ids_predicate.tsv") as f:
        np = sum(1 for _ in f)
    with open("ids_type.tsv") as f:
        nt = sum(1 for _ in f)
    with open("step5_entity_fragments.tsv") as f:
        nfrag = sum(1 for _ in f)

    print(f"  Entity IDs    : {ne:,}")
    print(f"  Predicate IDs : {np:,}")
    print(f"  Type IDs      : {nt:,}")
    print(f"  Entity frags  : {nfrag:,}  (should equal entity IDs)")

    if ne == nfrag:
        print(f"  ✅  Entity count matches fragment count")
    else:
        print(f"  ⚠️   Mismatch: {ne:,} entity IDs but {nfrag:,} fragment rows")
        all_ok = False

except FileNotFoundError as e:
    print(f"  Could not cross-check: {e}")

print(f"\n{SEP}")
if all_ok:
    print("  ✅  ALL CHECKS PASSED — ready for Step 8 (Java Lucene index)")
else:
    print("  ⚠️   Some checks failed — review warnings above before Step 8")
print(SEP)
