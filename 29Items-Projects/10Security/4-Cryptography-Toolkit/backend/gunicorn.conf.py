"""Gunicorn config — threads for I/O overlap; crypto is CPU-bound so
worker count matches vCPUs (TECH-NOTES / ARCHITECTURE §2.4)."""

import multiprocessing

bind = "0.0.0.0:5000"
workers = multiprocessing.cpu_count() * 1 + 1
threads = 4
timeout = 60  # paranoid Argon2 preset headroom
accesslog = "-"
errorlog = "-"
access_log_format = '{"t":"%(t)s","m":"%(m)s","u":"%(U)s","s":%(s)s,"b":%(b)s,"d":%(D)s,"r":"%(h)s"}'
