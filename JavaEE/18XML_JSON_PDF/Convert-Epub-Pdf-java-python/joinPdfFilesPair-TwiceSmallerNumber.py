# Pre-requisites:
# 1. Install PyPDF2: pip install PyPDF2

import os
import sys
import logging
from PyPDF2 import PdfReader, PdfWriter

def join_pdfs(input_dir, sort_key=str.lower):
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
    logging.info('Found %d PDF files to merge in pairs', len(pdf_files))

    for i in range(0, len(pdf_files), 2):
        pair = pdf_files[i:i+2]
        base_names = [os.path.splitext(f)[0] for f in pair]
        output_name = '+=+'.join(base_names) + '.pdf'
        output_path = os.path.join(input_dir, output_name)
        writer = PdfWriter()
        for filename in pair:
            path = os.path.join(input_dir, filename)
            logging.info('Adding %s to %s', filename, output_name)
            try:
                reader = PdfReader(path)
            except Exception as e:
                logging.error('Failed to read %s: %s', filename, e)
                continue
            for page in reader.pages:
                writer.add_page(page)
        if not writer.pages:
            logging.warning('No pages added for %s, skipping write.', output_name)
            continue
        try:
            with open(output_path, 'wb') as out_file:
                writer.write(out_file)
            logging.info('Created %s', output_path)
            # Remove source files after successful merge
            for src_file in pair:
                src_path = os.path.join(input_dir, src_file)
                try:
                    os.remove(src_path)
                    logging.info('Deleted source file %s', src_file)
                except Exception as rm_err:
                    logging.warning('Could not delete %s: %s', src_file, rm_err)
        except Exception as e:
            logging.error('Failed to write %s: %s', output_path, e)


if __name__ == '__main__':
    import argparse

    parser = argparse.ArgumentParser(description='Merge PDF files in a directory into consecutive pairs.')
    parser.add_argument('input_dir', nargs='?', help='Directory containing PDF files')

    args = parser.parse_args()

    if args.input_dir is None:
        args.input_dir = input('Enter directory containing PDF files: ').strip().strip('"')

    join_pdfs(args.input_dir)

