"""Convert O*NET database files into a CampusPulse job-catalog CSV.

O*NET is licensed CC BY 4.0 (US Department of Labor, Employment and Training
Administration). Keep the attribution line in data/README.md when you use it.

Put the O*NET "Text" (tab-separated) files here:
    data/external/onet/Occupation Data.txt
    data/external/onet/Technology Skills.txt

Run from ai-service/:
    python tools/onet_to_csv.py
Output:
    data/jobs/onet-technology-roles.csv   (import it as admin: Job catalog -> Import CSV)

Only occupations whose O*NET-SOC code starts with one of OCCUPATION_PREFIXES are kept
(default: computer and mathematical 15-, engineering 17-2), each with up to
MAX_SKILLS technology skills, "hot" and "in demand" ones first.
"""

import csv
import os
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(os.environ.get("ONET_DIR", ROOT / "data" / "external" / "onet"))
OUTPUT = ROOT / "data" / "jobs" / "onet-technology-roles.csv"
OCCUPATION_PREFIXES = ("15-", "17-2")
MAX_SKILLS = 15


def read_tsv(name):
    path = SOURCE / name
    if not path.exists():
        sys.exit(f"Missing {path}. Download the O*NET 'Text' files and place them there.")
    with path.open(encoding="utf-8", errors="replace", newline="") as handle:
        return list(csv.DictReader(handle, delimiter="\t"))


def main():
    occupations = {
        row["O*NET-SOC Code"]: row
        for row in read_tsv("Occupation Data.txt")
        if row["O*NET-SOC Code"].startswith(OCCUPATION_PREFIXES)
    }

    skills = defaultdict(list)
    for row in read_tsv("Technology Skills.txt"):
        code = row["O*NET-SOC Code"]
        if code in occupations:
            # Sort key: hot technology first, then in-demand, then name.
            hot = row.get("Hot Technology", "N") == "Y"
            demand = row.get("In Demand", "N") == "Y"
            skills[code].append((not hot, not demand, row["Example"].strip()))

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    written = 0
    with OUTPUT.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["title", "company", "location", "description", "skills"])
        for code, occupation in sorted(occupations.items()):
            names = []
            for _, _, name in sorted(skills.get(code, [])):
                # The importer splits skills on ; , and | — keep those out of names.
                name = " ".join(name.replace(";", " ").replace(",", " ").replace("|", " ").split())
                if name and name not in names:
                    names.append(name)
                if len(names) == MAX_SKILLS:
                    break
            if not names:
                continue
            writer.writerow([
                occupation["Title"][:200],
                "O*NET reference occupation",
                "Reference (US O*NET)",
                occupation.get("Description", ""),
                "; ".join(names),
            ])
            written += 1

    print(f"Wrote {written} occupations to {OUTPUT}")


if __name__ == "__main__":
    main()
