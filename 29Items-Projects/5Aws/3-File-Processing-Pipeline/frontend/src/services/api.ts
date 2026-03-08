import axios from 'axios';

// Safely fallback depending on the environment. 
// If in production, and VITE_API_BASE_URL is not provided, use an empty string to use relative origin (same domain)
// to prevent breaking CORS with static hardcoded `localhost` urls.
const fallbackUrl = import.meta.env.DEV ? 'http://localhost:4566/restapis/mock/prod/_user_request_' : '';
const apiBase = import.meta.env.VITE_API_BASE_URL || fallbackUrl;

export const api = axios.create({
    baseURL: apiBase,
    headers: {
        'Content-Type': 'application/json'
    }
});
