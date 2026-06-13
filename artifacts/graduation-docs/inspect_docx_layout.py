from pathlib import Path
import sys

from docx import Document


def main(path_str: str) -> None:
    path = Path(path_str)
    doc = Document(path)
    print(f"FILE {path.name}")
    for i, section in enumerate(doc.sections):
        print(
            "SECTION",
            i,
            "page",
            section.page_width,
            section.page_height,
            "margins",
            section.top_margin,
            section.bottom_margin,
            section.left_margin,
            section.right_margin,
        )
    for ti, table in enumerate(doc.tables):
        print(f"TABLE {ti}: {len(table.rows)} rows x {len(table.columns)} cols")
        for ri, row in enumerate(table.rows):
            texts = [cell.text.replace("\n", "/")[:60] for cell in row.cells]
            print(" ROW", ri, "height", row.height, "rule", row.height_rule, texts)
            if ri == 0:
                print(" WIDTHS", [cell.width for cell in row.cells])


if __name__ == "__main__":
    main(sys.argv[1])
