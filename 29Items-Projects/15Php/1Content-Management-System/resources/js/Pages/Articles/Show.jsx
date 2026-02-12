import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft, User, Calendar, Tag, Clock } from 'lucide-react';

export default function ArticleShow() {
    const { id } = useParams();
    const [article, setArticle] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        axios.get(`/api/articles/${id}`)
            .then(res => {
                setArticle(res.data.data);
                setLoading(false);
            })
            .catch(err => {
                console.error("Error fetching article:", err);
                setLoading(false);
            });
    }, [id]);

    if (loading) return <div className="flex justify-center p-12"><div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div></div>;
    if (!article) return <div className="p-12 text-center text-gray-500">Article not found.</div>;

    const StatusBadge = ({ status }) => {
        const colors = {
            published: 'bg-green-100 text-green-800',
            draft: 'bg-yellow-100 text-yellow-800',
            archived: 'bg-gray-100 text-gray-800'
        };
        return (
            <span className={`px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wide ${colors[status] || 'bg-gray-100 text-gray-800'}`}>
                {status}
            </span>
        );
    };

    return (
        <div className="max-w-4xl mx-auto">
            <Link to="/articles" className="inline-flex items-center text-gray-500 hover:text-blue-600 mb-6 transition-colors">
                <ArrowLeft size={18} className="mr-2" /> Back to Articles
            </Link>

            <div className="bg-white rounded-xl shadow-lg border border-gray-100 overflow-hidden">
                {/* Header */}
                <div className="p-8 border-b border-gray-100 bg-gray-50">
                    <div className="flex justify-between items-start mb-4">
                        <StatusBadge status={article.status} />
                        <span className="text-xs font-mono text-gray-400 bg-gray-200 px-2 py-1 rounded">v{article.version}.0</span>
                    </div>

                    <h1 className="text-3xl font-extrabold text-gray-900 mb-2">{article.title}</h1>
                    <p className="font-mono text-sm text-blue-600 mb-6">/{article.slug}</p>

                    <div className="flex items-center gap-6 text-sm text-gray-500">
                        <div className="flex items-center gap-2">
                            <User size={16} />
                            <span>{article.author?.name || 'Unknown Author'}</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <Calendar size={16} />
                            <span>{article.published_at ? new Date(article.published_at).toLocaleDateString() : 'Unpublished'}</span>
                        </div>
                        <div className="flex items-center gap-2" title="Last Updated">
                            <Clock size={16} />
                            <span>{new Date(article.updated_at).toLocaleDateString()}</span>
                        </div>
                    </div>
                </div>

                {/* Content */}
                <div className="p-8">
                    {article.excerpt && (
                        <div className="mb-8 p-4 bg-blue-50 border-l-4 border-blue-400 text-blue-900 italic rounded-r">
                            {article.excerpt}
                        </div>
                    )}

                    <div className="prose max-w-none text-gray-800 leading-relaxed whitespace-pre-wrap">
                        {article.content}
                    </div>
                </div>

                {/* Footer / Meta */}
                <div className="px-8 py-6 bg-gray-50 border-t border-gray-100">
                    <div className="flex flex-wrap gap-2">
                        {article.tags && article.tags.length > 0 ? (
                            article.tags.map(tag => (
                                <span key={tag.id} className="inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-gray-200 text-gray-700">
                                    <Tag size={12} className="mr-1" /> {tag.name}
                                </span>
                            ))
                        ) : (
                            <span className="text-xs text-gray-400 italic">No tags assigned</span>
                        )}
                    </div>

                    <div className="mt-4 pt-4 border-t border-gray-200 text-xs text-gray-400 flex justify-between">
                        <span>Database ID: {article.id}</span>
                        <span className="text-orange-500 font-semibold cursor-help" title="Functionality coming in Phase 2">Versioning Implementation Pending 🚧</span>
                    </div>
                </div>
            </div>
        </div>
    );
}
