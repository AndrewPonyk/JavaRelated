import './bootstrap';
import '../css/app.css';

import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';

// Methods & Components
import AdminLayout from './Layouts/AdminLayout';
import PublicLayout from './Layouts/PublicLayout';
import Dashboard from './Pages/Dashboard';
import ArticlesIndex from './Pages/Articles/Index';
import ArticleCreate from './Pages/Articles/Create';
import ArticleShow from './Pages/Articles/Show';
import Home from './Pages/Public/Home';
import ArticleView from './Pages/Public/Article';
import SettingsIndex from './Pages/Settings/Index';

// Mock component for missing routes
const ComingSoon = ({ title }) => (
    <div className="flex flex-col items-center justify-center h-full text-center p-12">
        <div className="text-6xl mb-4">🚧</div>
        <h2 className="text-2xl font-bold text-gray-700">{title || 'Coming Soon'}</h2>
        <p className="text-gray-500 mt-2">This module is under construction.</p>
    </div>
);

import { SettingsProvider } from './Context/SettingsContext';

const App = () => {
    return (
        <SettingsProvider>
            <BrowserRouter>
                <Routes>
                    {/* Public Frontend Routes */}
                    <Route path="/" element={<PublicLayout />}>
                        <Route index element={<Home />} />
                        <Route path="article/:slug" element={<ArticleView />} />
                        <Route path="about" element={<ComingSoon title="About Us" />} />
                        <Route path="contact" element={<ComingSoon title="Contact Us" />} />
                    </Route>

                    {/* Admin/Backend Routes */}
                    <Route path="/admin" element={<AdminLayout />}>
                        <Route index element={<Dashboard />} />
                        <Route path="articles/create" element={<ArticleCreate />} />
                        <Route path="articles/:id" element={<ArticleShow />} />
                        <Route path="articles" element={<ArticlesIndex />} />

                        {/* Placeholders for other menu items */}
                        <Route path="sites" element={<ComingSoon title="Multi-Site Manager" />} />
                        <Route path="backups" element={<ComingSoon title="Backup & Restore" />} />
                        <Route path="settings" element={<SettingsIndex />} />
                    </Route>

                    {/* Fallback */}
                    <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
            </BrowserRouter>
        </SettingsProvider>
    );
};

const container = document.getElementById('root');
if (container) {
    const root = createRoot(container);
    root.render(<App />);
}
