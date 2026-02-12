import './bootstrap';
import '../css/app.css';

import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';

// Layouts & Pages
import AdminLayout from './Layouts/AdminLayout';
import Dashboard from './Pages/Dashboard';
import ArticlesIndex from './Pages/Articles/Index';
import ArticleCreate from './Pages/Articles/Create';
import ArticleShow from './Pages/Articles/Show';

// Mock component for missing routes
const ComingSoon = ({ title }) => (
    <div className="flex flex-col items-center justify-center h-full text-center p-12">
        <div className="text-6xl mb-4">🚧</div>
        <h2 className="text-2xl font-bold text-gray-700">{title || 'Coming Soon'}</h2>
        <p className="text-gray-500 mt-2">This module is under construction.</p>
    </div>
);

const App = () => {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<AdminLayout />}>
                    <Route index element={<Dashboard />} />
                    <Route path="articles/create" element={<ArticleCreate />} />
                    <Route path="articles/:id" element={<ArticleShow />} />
                    <Route path="articles" element={<ArticlesIndex />} />

                    {/* Placeholders for other menu items */}
                    <Route path="sites" element={<ComingSoon title="Multi-Site Manager" />} />
                    <Route path="backups" element={<ComingSoon title="Backup & Restore" />} />
                    <Route path="settings" element={<ComingSoon title="Workflow Settings" />} />

                    {/* Fallback */}
                    <Route path="*" element={<Navigate to="/" replace />} />
                </Route>
            </Routes>
        </BrowserRouter>
    );
};

const container = document.getElementById('root');
if (container) {
    const root = createRoot(container);
    root.render(<App />);
}
