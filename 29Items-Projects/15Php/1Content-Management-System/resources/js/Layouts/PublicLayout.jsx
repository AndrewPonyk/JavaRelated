import React, { useState } from 'react';
import { NavLink, Link, Outlet } from 'react-router-dom';
import { Menu, X, Globe, LogIn } from 'lucide-react';
import { useSettings } from '../Context/SettingsContext';

const PublicLayout = () => {
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    const { settings } = useSettings();
    const appName = settings?.site_name || window.AppConfig?.name || 'InSight CMS';

    return (
        <div className="flex flex-col min-h-screen bg-white font-sans text-gray-900">
            {/* Header */}
            <header className="border-b border-gray-100 sticky top-0 bg-white/80 backdrop-blur-md z-50">
                <div className="container mx-auto px-4 max-w-6xl">
                    <div className="flex justify-between items-center h-16">
                        {/* Logo */}
                        <Link to="/" className="flex items-center space-x-2 text-blue-600 font-bold text-xl hover:text-blue-700 transition-colors">
                            <Globe className="h-6 w-6" />
                            <span>{appName}</span>
                        </Link>

                        {/* Desktop Navigation */}
                        <nav className="hidden md:flex space-x-8">
                            <NavLink to="/" className={({ isActive }) =>
                                `text-sm font-medium transition-colors ${isActive ? 'text-blue-600' : 'text-gray-500 hover:text-gray-900'}`
                            }>
                                Home
                            </NavLink>
                            <NavLink to="/about" className={({ isActive }) =>
                                `text-sm font-medium transition-colors ${isActive ? 'text-blue-600' : 'text-gray-500 hover:text-gray-900'}`
                            }>
                                About
                            </NavLink>
                            <NavLink to="/contact" className={({ isActive }) =>
                                `text-sm font-medium transition-colors ${isActive ? 'text-blue-600' : 'text-gray-500 hover:text-gray-900'}`
                            }>
                                Contact
                            </NavLink>
                        </nav>

                        {/* Actions */}
                        <div className="hidden md:flex items-center space-x-4">
                            <Link to="/admin" className="text-sm font-medium text-gray-500 hover:text-blue-600 flex items-center gap-1 transition-colors">
                                <LogIn size={16} /> Admin
                            </Link>
                        </div>

                        {/* Mobile Menu Button */}
                        <button
                            className="md:hidden p-2 rounded-md text-gray-400 hover:text-gray-500 hover:bg-gray-100"
                            onClick={() => setIsMenuOpen(!isMenuOpen)}
                        >
                            {isMenuOpen ? <X size={24} /> : <Menu size={24} />}
                        </button>
                    </div>
                </div>

                {/* Mobile Menu */}
                {isMenuOpen && (
                    <div className="md:hidden border-t border-gray-100 bg-white">
                        <div className="px-2 pt-2 pb-3 space-y-1 sm:px-3">
                            <Link to="/" className="block px-3 py-2 rounded-md text-base font-medium text-gray-900 hover:bg-gray-50" onClick={() => setIsMenuOpen(false)}>Home</Link>
                            <Link to="/about" className="block px-3 py-2 rounded-md text-base font-medium text-gray-500 hover:text-gray-900 hover:bg-gray-50" onClick={() => setIsMenuOpen(false)}>About</Link>
                            <Link to="/contact" className="block px-3 py-2 rounded-md text-base font-medium text-gray-500 hover:text-gray-900 hover:bg-gray-50" onClick={() => setIsMenuOpen(false)}>Contact</Link>
                            <Link to="/admin" className="block px-3 py-2 rounded-md text-base font-medium text-blue-600 hover:bg-blue-50" onClick={() => setIsMenuOpen(false)}>Admin Login</Link>
                        </div>
                    </div>
                )}
            </header>

            {/* Main Content */}
            <main className="flex-grow">
                <div className="container mx-auto px-4 py-8 max-w-6xl">
                    <Outlet />
                </div>
            </main>

            {/* Footer */}
            <footer className="bg-gray-50 border-t border-gray-100">
                <div className="container mx-auto px-4 py-12 max-w-6xl">
                    <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
                        <div className="col-span-1 md:col-span-2">
                            <div className="flex items-center space-x-2 text-blue-600 font-bold text-xl mb-4">
                                <Globe className="h-6 w-6" />
                                <span>{appName}</span>
                            </div>
                            <p className="text-gray-500 text-sm leading-relaxed max-w-xs">
                                A modern content platform delivering insightful articles and stories. Built with Laravel & React.
                            </p>
                        </div>

                        <div>
                            <h3 className="text-sm font-semibold text-gray-900 tracking-wider uppercase mb-4">Content</h3>
                            <ul className="space-y-3">
                                <li><Link to="/" className="text-sm text-gray-500 hover:text-blue-600 transition-colors">Latest Articles</Link></li>
                                <li><Link to="/popular" className="text-sm text-gray-500 hover:text-blue-600 transition-colors">Popular</Link></li>
                                <li><Link to="/categories" className="text-sm text-gray-500 hover:text-blue-600 transition-colors">Categories</Link></li>
                            </ul>
                        </div>

                        <div>
                            <h3 className="text-sm font-semibold text-gray-900 tracking-wider uppercase mb-4">Company</h3>
                            <ul className="space-y-3">
                                <li><Link to="/about" className="text-sm text-gray-500 hover:text-blue-600 transition-colors">About Us</Link></li>
                                <li><Link to="/contact" className="text-sm text-gray-500 hover:text-blue-600 transition-colors">Contact</Link></li>
                                <li><Link to="/privacy" className="text-sm text-gray-500 hover:text-blue-600 transition-colors">Privacy Policy</Link></li>
                            </ul>
                        </div>
                    </div>

                    <div className="border-t border-gray-200 mt-12 pt-8 flex flex-col md:flex-row justify-between items-center">
                        <p className="text-sm text-gray-400">&copy; {new Date().getFullYear()} {appName}. All rights reserved.</p>
                        <div className="flex space-x-6 mt-4 md:mt-0">
                            {/* Social icons placeholder */}
                        </div>
                    </div>
                </div>
            </footer>
        </div>
    );
};

export default PublicLayout;
