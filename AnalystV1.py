import os
import re
import xml.etree.ElementTree as ET
from datetime import datetime
import pandas as pd

# --- Define Static Templates ---
TEMPLATES = [
    {
        "name": "hdfc_card_spend",
        "example": "Spent Rs. 1,499.00 On HDFC Bank Card XX4012 At Amazon Pay on 10-09-2026",
        "pattern": re.compile(
            r"Spent\s+Rs\.?\s*(?P<amount>[\d,]+(?:\.\d+)?)\s+On\s+HDFC\s+Bank\s+Card\s+[\dX]+\s+At\s+(?P<store_name>.+?)\s+On\s+\d{4}-\d{2}-\d{2}.*",
            re.IGNORECASE,
        ),
    },
    {
        "name": "icici_card_spend",
        "example": "INR 2,350.00 spent using ICICI Bank Card XX9012 on 12-09-2026 on Zomato. Avl Limit: INR 50000.00 If not you, call 1800 2662/SMS BLOCK 8000 to 9215676766.",
        "pattern": re.compile(
            r"INR\s+(?P<amount>[\d,]+(?:\.\d+)?)\s+spent\s+using\s+ICICI\s+Bank\s+Card\s+[\dX]+\s+on\s+[\d\-/\w]+\s+on\s+(?P<store_name>.+?)\.\s+Avl\s+Limit:",
            re.IGNORECASE,
        ),
    },
    {
        "name": "hdfc_ac_autodebit",
        "example": "PAYMENT ALERT! INR 500.00 deducted from HDFC Bank A/C No XX1234 towards Netflix Lt UMRN: 98765432",
        "pattern": re.compile(
            r"PAYMENT\s+ALERT!\s+INR\s+(?P<amount>[\d,]+(?:\.\d+)?)\s+deducted\s+from\s+HDFC\s+Bank\s+A/C\s+No\s+[\dX]+\s+towards\s+(?P<store_name>.+?)(?:\s+Lt\b|\s+UMRN:|\.\s*|$)",
            re.IGNORECASE,
        ),
    },
    {
        "name": "hdfc_card_refund",
        "example": "Alert! Rs. 450.00 refunded by Swiggy on 15-09-2026 adjusted against HDFC Bank Credit Card XX4012 View updated balance here: https://hdfcbk.io/HDFCBK/s/kkKanakk",
        "pattern": re.compile(
            r"Alert!\s+Rs\.\s*(?P<amount>[\d,]+(?:\.\d+)?)\s+refunded\s+by\s+(?P<store_name>.+?)\s+on\s+[\d\-/\w]+\s+adjusted\s+against\s+HDFC\s+Bank\s+Credit\s+Card\s+[\dX]+",
            re.IGNORECASE,
        ),
    },
    {
        "name": "kotak_upi_transfer",
        "example": "Sent Rs.149.00 from Kotak Bank AC XXXX to playstore@axisbank on 11-08-25.UPI Ref XXXXX. Not you, https://kotak.com/KBANKT/Fraud",
        "pattern": re.compile(
            r"Sent\s+Rs\.\s*(?P<amount>[\d,]+(?:\.\d+)?)\s+from\s+Kotak\s+Bank\s+AC\s+[\dX]+\s+to\s+(?P<store_name>.+?)\s+on\s+[\d\-/\w]+\.?\s*UPI\s+Ref",
            re.IGNORECASE,
        ),
    },
]

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


def process_sms_backup(
    xml_file_path: str,
    year: int,
    month: int,
    unfiltered_csv_path: str = "unfiltered_sms.csv",
    extracted_csv_path: str = "extracted_data.csv",
    unresolved_csv_path: str = "unresolved_sms.csv",
):
    save_unfiltered = False
    # 1. Fetch SMS filtered by month and year
    filtered_df = parse_xml_to_df(xml_file_path, year, month)

    # 2. Output 1: Save ALL unfiltered rows for selected month (overwrites on each run)
    if save_unfiltered:
        filtered_df.to_csv(unfiltered_csv_path, index=False)
        print(
            f"Saved {len(filtered_df)} unfiltered records for {month}/{year} to '{unfiltered_csv_path}'"
        )

    # 3. Process static templates
    parser = SMSTemplateParser(TEMPLATES)
    extracted_df, remaining_df = parser.parse_and_consume(filtered_df)

    # 4. Output 2: Save Extracted Data
    extracted_df.to_csv(extracted_csv_path, index=False)
    print(
        f"Saved {len(extracted_df)} extracted transactions to '{extracted_csv_path}'"
    )

    # Save remaining unresolved messages to prepare for the upcoming LLM stage
    remaining_df.to_csv(unresolved_csv_path, index=False)
    print(
        f"Saved {len(remaining_df)} unresolved messages to '{unresolved_csv_path}'"
    )


# --- Example Execution ---
if __name__ == "__main__":
    # Replace 'sms-sample.xml' with your backup file name
    process_sms_backup(
        xml_file_path="data/sms-20261002204925.xml",
        year=2026,
        month=9,
        unfiltered_csv_path="output/unfiltered_month_sms.csv",
        extracted_csv_path="output/extracted_transactions.csv",
        unresolved_csv_path="output/unresolved_for_llm.csv",
    )