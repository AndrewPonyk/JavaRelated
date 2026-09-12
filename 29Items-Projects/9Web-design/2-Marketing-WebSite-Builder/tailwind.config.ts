import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      colors: {
        ink: "#17202a",
        signal: "#2563eb",
        mint: "#10b981",
        coral: "#f97316"
      }
    }
  },
  plugins: []
};

export default config;

