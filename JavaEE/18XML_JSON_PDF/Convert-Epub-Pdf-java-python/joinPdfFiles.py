# Pre-requisites:
# 1. Install PyPDF2: pip install PyPDF2

import os
import sys
import logging
from PyPDF2 import PdfReader, PdfWriter

def join_pdfs(input_dir, output_path=None, sort_key=str.lower):
    """Join all PDF files in input_dir into a single PDF.

    Parameters
    ----------
    input_dir : str
        Directory containing PDF files to merge.
    output_path : str, optional
        Path to save the merged PDF. Defaults to '<input_dir>/merged.pdf'.
    sort_key : Callable[[str], Any]
        Function used to sort filenames before merging. Defaults to case-insensitive.
    """
    logging.basicConfig(level=logging.INFO,
                        format='%(asctime)s - %(levelname)s - %(message)s')

    pdf_files = [f for f in os.listdir(input_dir) if f.lower().endswith('.pdf')]
    if not pdf_files:
        logging.warning('No PDF files found in %s', input_dir)
        return

    pdf_files.sort(key=sort_key)
    logging.info('Found %d PDF files to merge', len(pdf_files))

    if output_path is None:
        base_names = [os.path.splitext(f)[0] for f in pdf_files]
        joined_name = '=+='.join(base_names)
        output_path = os.path.join(input_dir, f'{joined_name}.pdf')

    writer = PdfWriter()

    for idx, filename in enumerate(pdf_files, 1):
        path = os.path.join(input_dir, filename)
        logging.info('(%d/%d) Adding %s', idx, len(pdf_files), filename)
        try:
            reader = PdfReader(path)
        except Exception as e:
            logging.error('Failed to read %s: %s', filename, e)
            continue

        for page in reader.pages:
            writer.add_page(page)

    if not writer.pages:
        logging.error('No pages were added; exiting without writing file.')
        return

    try:
        with open(output_path, 'wb') as out_file:
            writer.write(out_file)
        logging.info('Merged PDF saved to %s', output_path)
    except Exception as e:
        logging.error('Failed to write merged PDF: %s', e)


if __name__ == '__main__':
    import argparse

    parser = argparse.ArgumentParser(description='Merge PDF files in a directory into a single PDF.')
    parser.add_argument('input_dir', nargs='?', help='Directory containing PDF files')
    parser.add_argument('-o', '--output', dest='output', help='Output PDF file path')
    args = parser.parse_args()

    if args.input_dir is None:
        args.input_dir = input('Enter directory containing PDF files: ').strip().strip('"')

    join_pdfs(args.input_dir, args.output)

