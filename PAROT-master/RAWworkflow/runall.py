"""
runall.py — run the full PAROT fragment pipeline, steps 1–7.
Just leave this running. It stops and tells you exactly which step failed.
"""

import subprocess
import sys
import time

STEPS = [
    ("Step 1 — Clean N-Triples",          "step1_clean.py"),
    ("Step 2 — Deduplicate",               "step2_deduplicate.py"),
    ("Step 3 — Assign IDs",                "step3_assign_ids.py"),
    ("Step 4 — Triples → IDs",             "step4_triples_to_ids.py"),
    ("Step 5 — Entity fragments",          "step5_entity_fragments.py"),
    ("Step 6 — Type fragments",            "step6_type_fragments.py"),
    ("Step 7 — Predicate fragments",       "step7_predicate_fragments.py"),
]

SEP = "=" * 60

def hms(seconds):
    h, r = divmod(int(seconds), 3600)
    m, s = divmod(r, 60)
    if h:
        return f"{h}h {m}m {s}s"
    if m:
        return f"{m}m {s}s"
    return f"{s}s"

print(SEP)
print("  PAROT Fragment Pipeline")
print(SEP)

pipeline_start = time.time()

for i, (label, script) in enumerate(STEPS, 1):
    print(f"\n[{i}/{len(STEPS)}] {label}")
    print(f"      Running {script} …")
    step_start = time.time()

    result = subprocess.run(
        [sys.executable, script],
        capture_output=False,   # let output stream live to the terminal
    )

    elapsed = time.time() - step_start

    if result.returncode != 0:
        print()
        print(SEP)
        print(f"  PIPELINE STOPPED at step {i}: {label}")
        print(f"  Script : {script}")
        print(f"  Exit code: {result.returncode}")
        print(f"  Time before failure: {hms(elapsed)}")
        print(SEP)
        print()
        print("Fix the error above, then re-run from this step:")
        print(f"    python {script}")
        print("Or restart the whole pipeline:")
        print("    python runall.py")
        sys.exit(result.returncode)

    print(f"      ✓ Done in {hms(elapsed)}")

total = time.time() - pipeline_start
print()
print(SEP)
print(f"  ALL 7 STEPS COMPLETED  (total time: {hms(total)})")
print(SEP)
print()
print("Output files ready for PAROT Java system (Step 8 – Lucene index):")
print("  ids_entity.tsv")
print("  ids_predicate.tsv")
print("  ids_type.tsv")
print("  step5_entity_fragments.tsv")
print("  step6_type_fragments.tsv")
print("  step7_predicate_fragments.tsv")
