# Pre-requisites:
# 1. Install Calibre: https://calibre-ebook.com/download
# 2. Add Calibre's CLI tool (ebook-convert) to your system PATH

import os
import sys
import subprocess  # We'll call Calibre's CLI tool via a subprocess
import logging

def convert_epub_to_pdf(input_dir):
    """
    Convert all EPUB files in input_dir to PDF format using Calibre's Python API.
    Output PDFs will be saved in the same directory with .pdf extension.
    """
    logging.basicConfig(level=logging.INFO,
                        format='%(asctime)s - %(levelname)s - %(message)s')
    original_argv = sys.argv

    epub_files = [f for f in os.listdir(input_dir) if f.lower().endswith('.epub')]
    total = len(epub_files)
    if total == 0:
        logging.warning('No EPUB files found in %s', input_dir)
        return

    success_count = 0
    for idx, filename in enumerate(epub_files, 1):
        input_path = os.path.join(input_dir, filename)
        output_path = os.path.splitext(input_path)[0] + '===Converted.pdf'
        logging.info('(%d/%d) Converting %s', idx, total, filename)
        try:
            # Call Calibre's CLI (`ebook-convert`) via subprocess
            result = subprocess.run(
                ['ebook-convert', input_path, output_path],
                capture_output=True,
                text=True
            )
            if result.returncode != 0:
                raise RuntimeError(result.stderr.strip() or 'Unknown error during conversion')
            logging.info('Successfully converted %s', filename)
            success_count += 1
        except Exception as e:
            logging.error('Failed to convert %s: %s', filename, e)
        finally:
            pass

    logging.info('Conversion finished: %d/%d files converted successfully', success_count, total)

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description='Convert EPUB files to PDF using Calibre API')
    parser.add_argument('input_dir', nargs='?', help='Directory containing EPUB files')
    args = parser.parse_args()

    if args.input_dir is None:
        # Fallback to interactive prompt
        args.input_dir = input('Enter directory containing EPUB files: ').strip().strip('"')

    convert_epub_to_pdf(args.input_dir)
