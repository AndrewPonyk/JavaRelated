import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';
import { Calendar, User, Clock, ArrowRight, Tag, Bookmark } from 'lucide-react';

export default function Home() {
    const [articles, setArticles] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        // Fetch published articles (assuming generic index returns all for now)
        // In a real app, query params ?status=published would be good
        axios.get('/api/articles?status=published')
            .then(res => {
                setArticles(res.data.data);
                setLoading(false);
            })
            .catch(err => {
                console.error("Error fetching articles:", err);
                setLoading(false);
            });
    }, []);

    if (loading) return (
        <div className="flex justify-center items-center h-64">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
        </div>
    );

    return (
        <div>
            {/* Latest Articles */}
            <div className="flex items-end justify-between mb-8 border-b border-gray-100 pb-4 mt-8">
                <h2 className="text-3xl font-bold text-gray-900">
                    Latest Articles
                </h2>
                <Link to="/archive" className="text-blue-600 hover:text-blue-800 text-sm font-medium flex items-center gap-1 transition-colors">
                    View Archive <ArrowRight size={16} />
                </Link>
            </div>

            {articles.length > 0 ? (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
                    {articles.map((article) => (
                        <article key={article.id} className="flex flex-col bg-white rounded-2xl overflow-hidden shadow-sm hover:shadow-xl transition-all duration-300 border border-gray-100 group hover:-translate-y-1">

                            {/* Image Placeholder - since no image field yet, use a gradient */}
                            <div className="h-48 bg-gray-100 w-full relative overflow-hidden group-hover:opacity-90 transition-opacity">
                                <div className={`absolute inset-0 bg-gradient-to-br ${getGradient(article.id)}`}></div>
                                <span className="absolute bottom-3 right-3 bg-white/90 backdrop-blur-sm px-2 py-1 rounded text-xs font-bold text-gray-800 shadow-sm">
                                    ARTICLE
                                </span>
                            </div>

                            <div className="p-6 flex flex-col flex-grow">
                                <div className="flex items-center gap-2 mb-3 text-xs font-medium text-blue-600 uppercase tracking-wider">
                                    {article.category?.name || 'Uncategorized'}
                                    <span className="text-gray-300">•</span>
                                    <span className="text-gray-500 normal-case flex items-center gap-1">
                                        <Clock size={12} />
                                        {/* Simple read time estimation */}
                                        {Math.ceil((article.content?.length || 0) / 1000)} min read
                                    </span>
                                </div>

                                <h3 className="text-xl font-bold text-gray-900 mb-3 group-hover:text-blue-600 transition-colors line-clamp-2 leading-tight">
                                    <Link to={`/article/${article.slug || article.id}`}>
                                        {article.title}
                                    </Link>
                                </h3>

                                <p className="text-gray-600 mb-6 text-sm leading-relaxed line-clamp-3 flex-grow">
                                    {article.excerpt || article.content?.substring(0, 150) + '...'}
                                </p>

                                <div className="mt-auto pt-4 border-t border-gray-100 flex items-center justify-between">
                                    <div className="flex items-center gap-2">
                                        <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-gray-200 to-gray-300 flex items-center justify-center text-gray-600 font-bold text-xs ring-2 ring-white">
                                            {article.author?.name?.charAt(0) || 'U'}
                                        </div>
                                        <div className="flex flex-col">
                                            <span className="text-xs font-semibold text-gray-900">{article.author?.name || 'Unknown'}</span>
                                            <span className="text-xs text-gray-400">
                                                {new Date(article.published_at || article.created_at).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
                                            </span>
                                        </div>
                                    </div>
                                    <button className="text-gray-400 hover:text-blue-600 transition-colors">
                                        <Bookmark size={18} />
                                    </button>
                                </div>
                            </div>
                        </article>
                    ))}
                </div>
            ) : (
                <div className="text-center py-20 bg-gray-50 rounded-2xl border border-dashed border-gray-200">
                    <h3 className="text-xl font-medium text-gray-500 mb-2">No articles published yet</h3>
                    <p className="text-gray-400 mb-6">Check back later for new content!</p>
                </div>
            )}
        </div>
    );
}

// Helper for random-ish gradients based on ID
function getGradient(id) {
    const gradients = [
        'from-blue-400 to-indigo-500',
        'from-emerald-400 to-teal-500',
        'from-orange-400 to-pink-500',
        'from-purple-400 to-fuchsia-500',
        'from-cyan-400 to-blue-500',
        'from-rose-400 to-red-500',
    ];
    return gradients[id % gradients.length];
}
