"""Find good MATCHED / PARTIAL similarity cut-offs from labelled skill-topic pairs.

Mirrors the backend:
  * skill vector  = embedding of the lower-cased skill (SkillEmbeddingCache key),
    optionally wrapped in a template (to test "skill glosses")
  * topic vector  = embedding of "name description" (SyllabusPersistenceService)
  * status        = MATCHED if sim >= m, PARTIAL if sim >= p, else GAP,
    then raised by the keyword rule (KeywordTopicMatcher): skill in topic name
    -> MATCHED, only in description -> at least PARTIAL.

Run from ai-service/ with the venv:
    python tools/calibrate.py ../data/eval/skill-topic-pairs.csv
"""

import csv
import re
import sys
from pathlib import Path

from fastembed import TextEmbedding

MODEL_ID = "sentence-transformers/all-MiniLM-L6-v2"
LABELS = ["GAP", "PARTIAL", "MATCHED"]
RANK = {label: i for i, label in enumerate(LABELS)}
TEMPLATES = {
    "plain": "{skill}",
    "skill-gloss": "knowledge of {skill}",
}


def normalize(text):
    text = (text or "").lower()
    text = re.sub(r"[^a-z0-9+#.]+", " ", text)
    text = re.sub(r"(?<![a-z0-9])\.|\.(?![a-z0-9])", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def keyword_status(skill, name, description):
    needle = normalize(skill)
    if not needle:
        return None
    short = len(needle.replace(" ", "")) < 3
    pattern = re.compile(r"(^| )" + re.escape(needle) + r"( |$)")
    if (normalize(name) == needle) if short else pattern.search(normalize(name)):
        return "MATCHED"
    if not short and pattern.search(normalize(description)):
        return "PARTIAL"
    return None


def classify(sim, m, p, kw):
    status = "MATCHED" if sim >= m else "PARTIAL" if sim >= p else "GAP"
    if kw and RANK[kw] > RANK[status]:
        status = kw
    return status


def score(rows, sims, m, p):
    correct = 0
    missed = 0  # labelled MATCHED/PARTIAL but predicted GAP (false gap)
    invented = 0  # labelled GAP but predicted MATCHED/PARTIAL
    for row, sim in zip(rows, sims):
        pred = classify(sim, m, p, row["kw"])
        correct += pred == row["label"]
        missed += row["label"] != "GAP" and pred == "GAP"
        invented += row["label"] == "GAP" and pred != "GAP"
    return correct / len(rows), missed, invented


def main(path):
    rows = list(csv.DictReader(Path(path).open(encoding="utf-8")))
    for row in rows:
        row["kw"] = keyword_status(row["skill"], row["topic_name"], row["topic_description"])

    model = TextEmbedding(model_name=MODEL_ID)
    topic_vecs = list(model.embed([f'{r["topic_name"]} {r["topic_description"]}' for r in rows]))

    def cos(a, b):
        dot = sum(x * y for x, y in zip(a, b))
        na = sum(x * x for x in a) ** 0.5
        nb = sum(y * y for y in b) ** 0.5
        return dot / (na * nb)

    print(f"{len(rows)} labelled pairs, model {MODEL_ID}\n")
    for name, template in TEMPLATES.items():
        skill_vecs = list(model.embed([template.format(skill=r["skill"].lower()) for r in rows]))
        sims = [cos(s, t) for s, t in zip(skill_vecs, topic_vecs)]

        for label in LABELS:
            values = sorted(s for r, s in zip(rows, sims) if r["label"] == label)
            mid = values[len(values) // 2]
            print(f"[{name}] {label:7s} n={len(values):3d} sim min={values[0]:.2f} median={mid:.2f} max={values[-1]:.2f}")

        current = score(rows, sims, 0.70, 0.50)
        best = None
        for m100 in range(40, 86, 2):
            for p100 in range(20, m100, 2):
                acc, missed, invented = score(rows, sims, m100 / 100, p100 / 100)
                # Prefer accuracy, then fewer false gaps.
                key = (round(acc, 4), -missed, -invented)
                if best is None or key > best[0]:
                    best = (key, m100 / 100, p100 / 100, acc, missed, invented)

        print(f"[{name}] current 0.70/0.50: accuracy {current[0]:.0%}, false gaps {current[1]}, invented {current[2]}")
        _, m, p, acc, missed, invented = best
        print(f"[{name}] best    {m:.2f}/{p:.2f}: accuracy {acc:.0%}, false gaps {missed}, invented {invented}\n")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "../data/eval/skill-topic-pairs.csv")
