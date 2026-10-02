import xml.etree.ElementTree as ET
from datetime import datetime
import pandas as pd

import re

pd.set_option('display.max_columns', None)
pd.set_option('display.width', 1000)


def parse_sms_backup(file_path: str, year: int, month: int) -> pd.DataFrame:
    """Parses SMS Backup & Restore XML and returns a DataFrame of SMS for a specific year and month.

    :param file_path: Path to the XML file
    :param year: Target year (e.g., 2026)
    :param month: Target month (1-12)
    :return: pandas DataFrame containing filtered SMS entries
    """
    # Fast incremental parsing using ElementTree
    tree = ET.parse(file_path)
    root = tree.getroot()

    sms_data = []

    for sms in root.findall("sms"):
        # The 'date' attribute is epoch timestamp in milliseconds
        timestamp_ms = int(sms.attrib.get("date", 0))
        sms_datetime = datetime.fromtimestamp(timestamp_ms / 1000.0)

        # Filter by selected month and year
        if sms_datetime.year == year and sms_datetime.month == month:
            sms_data.append({
                "address": sms.attrib.get("address"),
                "date": sms_datetime,
                "body": sms.attrib.get("body"),
                "type": sms.attrib.get(
                    "type"
                ),  # 1 = Received, 2 = Sent (standard Android convention)
                "readable_date": sms.attrib.get("readable_date"),
                "contact_name": sms.attrib.get("contact_name"),
            })

    return pd.DataFrame(sms_data)


class SMSTemplateParser:

    def __init__(self, templates: list[dict]):
        self.templates = templates

    def parse_and_consume(self, df: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
        """Iterates over the DataFrame, extracts required fields via templates,

        and removes resolved entries.
        """
        resolved_records = []
        unresolved_indices = []

        for idx, row in df.iterrows():
            body = re.sub(r"\s+", " ", str(row["body"])).strip()
            matched = False

            for tmpl in self.templates:
                match = tmpl["pattern"].search(body)
                if match:
                    extracted_fields = match.groupdict()

                    # Restrict output strictly to date, template_name, amount, and store_name
                    record = {
                        "date": row["date"],  # Retained from SMS metadata
                        "template_name": tmpl["name"],
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
            columns=["date", "template_name", "amount", "store_name"],
        )
        remaining_df = df.loc[unresolved_indices].reset_index(drop=True)

        return extracted_df, remaining_df


# --- Updated Template Definition ---
# Matches: "Spent Rs.{AMOUNT} On HDFC Bank Card XXXX At {STORE NAME} on {IGNORED DATE}"
TEMPLATES = [
    {
        "name": "hdfc_card_spend",
        "pattern": re.compile(
            r"Spent\s+Rs\.?\s*(?P<amount>[\d,]+(?:\.\d+)?)\s+On\s+HDFC\s+Bank\s+Card\s+[\dX]+\s+At\s+(?P<store_name>.+?)\s+on\s+.+",
            re.IGNORECASE,
        ),
    },
    {
        "name": "icici_card_spend",
        "pattern": re.compile(
            r"INR\s+(?P<amount>[\d,]+(?:\.\d+)?)\s+spent\s+using\s+ICICI\s+Bank\s+Card\s+[\dX]+\s+on\s+[\d\-/\w]+\s+on\s+(?P<store_name>.+?)\.\s+Avl\s+Limit:",
            re.IGNORECASE,
        ),
    },
    {
        "name": "hdfc_ac_autodebit",
        "pattern": re.compile(
            r"PAYMENT\s+ALERT!\s+INR\s+(?P<amount>[\d,]+(?:\.\d+)?)\s+deducted\s+from\s+HDFC\s+Bank\s+A/C\s+No\s+[\dX]+\s+towards\s+(?P<store_name>.+?)(?:\s+Lt\b|\s+UMRN:|\.\s*|$)",
            re.IGNORECASE,
        ),
    },
    {
        "name": "hdfc_card_refund",
        "pattern": re.compile(
            r"Alert!\s+Rs\.\s*(?P<amount>[\d,]+(?:\.\d+)?)\s+refunded\s+by\s+(?P<store_name>.+?)\s+on\s+[\d\-/\w]+\s+adjusted\s+against\s+HDFC\s+Bank\s+Credit\s+Card\s+[\dX]+",
            re.IGNORECASE,
        ),
    },
    {
        "name": "kotak_upi_transfer",
        "pattern": re.compile(
            r"Sent\s+Rs\.\s*(?P<amount>[\d,]+(?:\.\d+)?)\s+from\s+Kotak\s+Bank\s+AC\s+[\dX]+\s+to\s+(?P<store_name>.+?)\s+on\s+[\d\-/\w]+\.?\s*UPI\s+Ref",
            re.IGNORECASE,
        ),
    }
]

# --- Usage Example ---
if __name__ == "__main__":
    xml_file_path = "data/sms-20261002204925.xml"

    # Fetch all SMS for September 2026
    filtered_df = parse_sms_backup(xml_file_path, year=2026, month=9)

    print(f"Total SMS sefound: {len(filtered_df)}")
    print(filtered_df.head())

    parser = SMSTemplateParser(TEMPLATES)
    extracted_df, remaining_df = parser.parse_and_consume(filtered_df)

    print("--- Extracted Data (Resolved) ---")
    print(extracted_df)

    print("\n--- Remaining Unresolved Messages (Ready for LLM fallback) ---")
    print(remaining_df[["address", "body"]])
