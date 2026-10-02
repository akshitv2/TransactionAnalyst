import json
import os
import re
import xml.etree.ElementTree as ET
from datetime import datetime
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


def parse_xml_to_df(file_path: str, year: int, month: int) -> pd.DataFrame:
    """Parses XML and returns all SMS records for the target year and month."""
    tree = ET.parse(file_path)
    root = tree.getroot()

    sms_data = []
    for sms in root.findall("sms"):
        timestamp_ms = int(sms.attrib.get("date", 0))
        sms_datetime = datetime.fromtimestamp(timestamp_ms / 1000.0)

        if sms_datetime.year == year and sms_datetime.month == month:
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

    def __init__(self, templates: list[dict]):
        self.templates = templates

    def parse_and_consume(self, df: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
        """Matches entries against templates and separates resolved from unresolved rows."""
        resolved_records = []
        unresolved_indices = []

        for idx, row in df.iterrows():
            body = row["body"]
            matched = False

            for tmpl in self.templates:
                match = tmpl["pattern"].search(body)
                if match:
                    extracted_fields = match.groupdict()
                    record = {
                        "date": row["date"],
                        "transaction": tmpl["name"],
                        "type": tmpl["type"],
                        "amount": extracted_fields.get("amount"),
                        "store_name": extracted_fields.get(
                            "store_name"
                        ).strip(),
                    }
                    resolved_records.append(record)
                    matched = True
                    break

            if not matched:
                unresolved_indices.append(idx)

        extracted_df = pd.DataFrame(
            resolved_records,
            columns=["date", "transaction","type", "amount", "store_name"],
        )
        remaining_df = df.loc[unresolved_indices].reset_index(drop=True)

        return extracted_df, remaining_df


def process_sms_backup(
    xml_file_path: str,
    templates_json_path: str,
    year: int,
    month: int,
    save_unfiltered: bool = True,
    unfiltered_csv_path: str = "unfiltered_sms.csv",
    extracted_csv_path: str = "extracted_data.csv",
    unresolved_csv_path: str = "unresolved_sms.csv",
):

    # 1. Load dynamic templates from JSON
    templates = load_templates_from_json(templates_json_path)

    # 2. Fetch SMS filtered by month and year
    filtered_df = parse_xml_to_df(xml_file_path, year, month)

    # 3. Save ALL unfiltered rows for selected month (if enabled)
    if save_unfiltered:
        filtered_df.to_csv(unfiltered_csv_path, index=False)
        print(
            f"Saved {len(filtered_df)} unfiltered records for {month}/{year} to '{unfiltered_csv_path}'"
        )

    # 4. Process static templates
    parser = SMSTemplateParser(templates)
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
    process_sms_backup(
        xml_file_path="data/sms-20261002204925.xml",
        templates_json_path="config/templates.json",
        year=2026,
        month=9,
        save_unfiltered=False,
        unfiltered_csv_path="output/unfiltered_month_sms.csv",
        extracted_csv_path="output/extracted_transactions.csv",
        unresolved_csv_path="output/unresolved_for_llm.csv",
    )