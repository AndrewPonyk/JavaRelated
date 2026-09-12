import 'dotenv/config';
import { createApp } from './app.js';
import { logger } from './logger.js';

const port = Number(process.env.PORT || 8080);
const app = createApp();

app.listen(port, () => {
  logger.info('server_started', { port });
});
