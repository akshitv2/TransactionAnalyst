import json
import os
import re
import xml.etree.ElementTree as ET
from datetime import datetime
from typing import Optional
import pandas as pd


def load_templates_from_json(json_file_path: str) -> list[dict]:
    """Loads template rules from a JSON file and compiles string patterns into regex objects."""
    with open(json_file_path, "r", encoding="utf-8") as f:
        templates_raw = json.load(f)

    compiled_templates = []
    for tmpl in templates_raw:
        compiled_templates.append({
            "name": tmpl["name"],
            "type": tmpl["type"],
            "example": tmpl.get("example", ""),
            "pattern": re.compile(tmpl["pattern"], re.IGNORECASE),
        })

    return compiled_templates


def load_store_map(store_map_path: str) -> dict[str, str]:
    """Loads store-to-category mapping from a JSON file."""
    if os.path.exists(store_map_path):
        with open(store_map_path, "r", encoding="utf-8") as f:
            return json.load(f)
    return {}


def classify_store(
    store_name: str, store_map: dict[str, str], default_category: str = "Others"
) -> str:
    """Classifies a store name by checking if any keyword in store_map exists within store_name."""
    if not store_name:
        return default_category

    store_name_upper = store_name.upper()

    for keyword, category in store_map.items():
        if keyword.upper() in store_name_upper:
            return category

    return default_category


def parse_xml_to_df(
    file_path: str, year: Optional[int] = None, month: Optional[int] = None
) -> pd.DataFrame:
    """Parses XML and returns SMS records.

    If year and month are provided, filters by date; otherwise reads ALL
    records.
    """
    tree = ET.parse(file_path)
    root = tree.getroot()

    sms_data = []
    for sms in root.findall("sms"):
        timestamp_ms = int(sms.attrib.get("date", 0))
        sms_datetime = datetime.fromtimestamp(timestamp_ms / 1000.0)

        # Apply date filtering only if both year and month are specified
        if year is not None and month is not None:
            if sms_datetime.year != year or sms_datetime.month != month:
                continue

        sms_data.append({
            "address": sms.attrib.get("address"),
            "date": sms_datetime,
            "body": sms.attrib.get("body"),
            "type": sms.attrib.get("type"),
            "readable_date": sms.attrib.get("readable_date"),
            "contact_name": sms.attrib.get("contact_name"),
        })

    return pd.DataFrame(sms_data)


class SMSTemplateParser:

    def __init__(self, templates: list[dict], store_map: dict[str, str]):
        self.templates = templates
        self.store_map = store_map

    def parse_and_consume(self, df: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
        """Matches entries against templates, classifies store categories, and separates resolved from unresolved rows."""
        resolved_records = []
        unresolved_indices = []

        for idx, row in df.iterrows():
            body = row["body"]
            matched = False

            for tmpl in self.templates:
                match = tmpl["pattern"].search(body)
                if match:
                    extracted_fields = match.groupdict()
                    raw_store_name = extracted_fields.get("store_name", "").strip()
                    category = classify_store(raw_store_name, self.store_map)

                    record = {
                        "date": row["date"],
                        "transaction": tmpl["name"],
                        "type": tmpl["type"],
                        "amount": extracted_fields.get("amount"),
                        "store_name": raw_store_name,
                        "category": category,
                    }
                    resolved_records.append(record)
                    matched = True
                    break

            if not matched:
                unresolved_indices.append(idx)

        extracted_df = pd.DataFrame(
            resolved_records,
            columns=[
                "date",
                "transaction",
                "type",
                "amount",
                "store_name",
                "category",
            ],
        )
        remaining_df = df.loc[unresolved_indices].reset_index(drop=True)

        return extracted_df, remaining_df


def process_sms_backup(
    xml_file_path: str,
    templates_json_path: str,
    store_map_json_path: str,
    year: Optional[int] = None,
    month: Optional[int] = None,
    save_unfiltered: bool = True,
    unfiltered_csv_path: str = "unfiltered_sms.csv",
    extracted_csv_path: str = "extracted_data.csv",
    unresolved_csv_path: str = "unresolved_sms.csv",
):

    # 1. Load dynamic templates & store map from JSON
    templates = load_templates_from_json(templates_json_path)
    store_map = load_store_map(store_map_json_path)

    # 2. Fetch SMS (either filtered by year/month or all SMS if year/month are None)
    filtered_df = parse_xml_to_df(xml_file_path, year=year, month=month)

    filter_desc = f"{month}/{year}" if year and month else "ALL dates"

    # 3. Save ALL unfiltered rows fetched (if enabled)
    if save_unfiltered:
        filtered_df.to_csv(unfiltered_csv_path, index=False)
        print(
            f"Saved {len(filtered_df)} raw records ({filter_desc}) to '{unfiltered_csv_path}'"
        )

    # 4. Process static templates and categorize stores
    parser = SMSTemplateParser(templates, store_map)
    extracted_df, remaining_df = parser.parse_and_consume(filtered_df)

    # 5. Output Extracted Data
    extracted_df.to_csv(extracted_csv_path, index=False)
    print(
        f"Saved {len(extracted_df)} extracted transactions to '{extracted_csv_path}'"
    )

    # 6. Save remaining unresolved messages for LLM stage
    remaining_df.to_csv(unresolved_csv_path, index=False)
    print(
        f"Saved {len(remaining_df)} unresolved messages to '{unresolved_csv_path}'"
    )


# --- Execution Example ---
if __name__ == "__main__":
    # Example 1: Run for all dates (Non-filtering mode)
    process_sms_backup(
        xml_file_path="data/sms-20261002204925.xml",
        templates_json_path="config/templates.json",
        store_map_json_path="config/store_map.json",
        year=None,  # Set to None to process all messages
        month=None, # Set to None to process all messages
        save_unfiltered=False,
        unfiltered_csv_path="output/unfiltered_all_sms.csv",
        extracted_csv_path="output/extracted_transactions.csv",
        unresolved_csv_path="output/unresolved_for_llm.csv",
    )