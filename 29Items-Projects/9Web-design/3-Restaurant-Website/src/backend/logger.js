export const logger = {
  info(message, metadata = {}) {
    write(process.stdout, 'info', message, metadata);
  },
  error(message, metadata = {}) {
    write(process.stderr, 'error', message, metadata);
  }
};

function write(stream, level, message, metadata) {
  stream.write(`${JSON.stringify({ level, message, ...metadata, timestamp: new Date().toISOString() })}\n`);
}
