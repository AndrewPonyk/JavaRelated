import 'express-async-errors';
declare const app: import("express-serve-static-core").Express;
declare function startServer(): Promise<import("http").Server<typeof import("http").IncomingMessage, typeof import("http").ServerResponse>>;
export { app, startServer };
export default app;
//# sourceMappingURL=server.d.ts.map