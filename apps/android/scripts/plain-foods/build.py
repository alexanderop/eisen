#!/usr/bin/env python3
"""Builds the app's plain foods (assets/foods/plain.json) from USDA FoodData Central.

The data is public domain. Download the CSV archives of "Foundation Foods" and "SR Legacy" from
https://fdc.nal.usda.gov/download-datasets (no key needed), unzip them anywhere outside the repo, and run:

    python3 -I apps/android/scripts/plain-foods/build.py <sr-legacy-folder> <foundation-folder>

Each folder is the one that holds food.csv and food_nutrient.csv. The list of foods, their short names, servings
and units is foods.tsv next to this script; the numbers per 100 g come from the download. The download itself is
not committed. Standard library only.
"""

import csv
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "foods", "plain.json")

# FoodData Central nutrient ids. Energy: kcal as published, else the Atwater factors Foundation Foods give.
ENERGY = ["1008", "2048", "2047"]
PROTEIN = ["1003"]
CARBS = ["1005", "1050"]
FAT = ["1004", "1085"]
WANTED = set(ENERGY + PROTEIN + CARBS + FAT)


def read_list(path):
    foods = []
    with open(path, encoding="utf-8") as handle:
        for number, line in enumerate(handle, 1):
            if not line.strip() or line.startswith("#"):
                continue
            parts = line.rstrip("\n").split("\t") + ["", "", ""]
            fdc_id, name, serving, unit = (part.strip() for part in parts[:4])
            if not fdc_id.isdigit() or not name:
                sys.exit(f"foods.tsv:{number}: expected 'id<TAB>name<TAB>serving<TAB>unit', got {line!r}")
            foods.append({"id": fdc_id, "name": name, "serving": float(serving) if serving else None, "unit": unit or "g"})
    return foods


def read_download(folders, ids):
    descriptions, nutrients = {}, {}
    for folder in folders:
        with open(os.path.join(folder, "food.csv"), encoding="utf-8") as handle:
            for row in csv.DictReader(handle):
                if row["fdc_id"] in ids:
                    descriptions[row["fdc_id"]] = row["description"]
        with open(os.path.join(folder, "food_nutrient.csv"), encoding="utf-8") as handle:
            for row in csv.DictReader(handle):
                if row["fdc_id"] in ids and row["nutrient_id"] in WANTED and row["amount"]:
                    nutrients.setdefault(row["fdc_id"], {})[row["nutrient_id"]] = float(row["amount"])
    return descriptions, nutrients


def first(values, keys):
    for key in keys:
        if key in values:
            return values[key]
    return None


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    folders = sys.argv[1:]
    foods = read_list(os.path.join(HERE, "foods.tsv"))
    ids = {food["id"] for food in foods}
    if len(ids) != len(foods):
        sys.exit("foods.tsv lists an id twice")
    names = [food["name"].lower() for food in foods]
    if len(set(names)) != len(names):
        sys.exit("foods.tsv gives two foods the same name")
    descriptions, nutrients = read_download(folders, ids)
    missing = sorted(ids - set(descriptions))
    if missing:
        sys.exit(f"not in the download: {', '.join(missing)}")
    out = []
    for food in foods:
        values = nutrients.get(food["id"], {})
        kcal = first(values, ENERGY)
        if kcal is None:
            sys.exit(f"{food['id']} {descriptions[food['id']]}: no energy")
        item = {"id": food["id"], "name": food["name"], "kcal": round(kcal, 1), "protein": round(first(values, PROTEIN) or 0.0, 1)}
        for key, keys in (("carbs", CARBS), ("fat", FAT)):
            value = first(values, keys)
            if value is not None:
                item[key] = round(value, 1)
        if food["serving"]:
            item["serving"] = food["serving"]
        if food["unit"] == "ml":
            item["unit"] = "ml"
        out.append(item)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as handle:
        handle.write("[\n" + ",\n".join(json.dumps(item, ensure_ascii=False) for item in out) + "\n]\n")
    print(f"{len(out)} plain foods written to {os.path.relpath(OUT)}")


if __name__ == "__main__":
    main()
