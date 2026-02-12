import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Edit2, Eye, Trash2, GitCommit, FileText, CheckCircle, Clock } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function ArticlesIndex() {
    const [articles, setArticles] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        axios.get('/api/articles')
            .then(res => {
                setArticles(res.data.data);
                setLoading(false);
            })
            .catch(err => {
                console.error("Error fetching articles:", err);
                setLoading(false);
            });
    }, []);

    const StatusBadge = ({ status }) => {
        const colors = {
            published: 'bg-green-100 text-green-800 border-green-200',
            draft: 'bg-yellow-100 text-yellow-800 border-yellow-200',
            archived: 'bg-gray-100 text-gray-800 border-gray-200'
        };
        const icons = {
            published: <CheckCircle size={12} className="mr-1" />,
            draft: <Clock size={12} className="mr-1" />,
            archived: <FileText size={12} className="mr-1" />
        };
        return (
            <span className={`px-2 py-1 rounded-full text-xs font-medium border flex items-center w-fit ${colors[status] || colors.draft}`}>
                {icons[status] || icons.draft}
                {status.charAt(0).toUpperCase() + status.slice(1)}
            </span>
        );
    };

    if (loading) return <div className="flex justify-center p-12"><div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div></div>;

    return (
        <div className="bg-white rounded-xl shadow-lg border border-gray-100 overflow-hidden">
            <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-gray-50">
                <h3 className="text-lg font-bold text-gray-800 flex items-center gap-2">
                    <FileText className="text-blue-600" size={20} />
                    Manage Content
                </h3>
                <Link to="/admin/articles/create" className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors shadow-md flex items-center gap-2">
                    <Edit2 size={16} /> New Article
                </Link>
            </div>

            <div className="overflow-x-auto">
                <table className="w-full text-left">
                    <thead>
                        <tr className="bg-gray-50 text-gray-600 text-xs uppercase tracking-wider font-semibold">
                            <th className="px-6 py-4">Title / Slug</th>
                            <th className="px-6 py-4">Author</th>
                            <th className="px-6 py-4">Status</th>
                            <th className="px-6 py-4">Last Updated</th>
                            <th className="px-6 py-4 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                        {articles.map((article) => (
                            <tr key={article.id} className="hover:bg-blue-50 transition-colors group">
                                <td className="px-6 py-4">
                                    <div className="font-semibold text-gray-900 group-hover:text-blue-600 transition-colors">{article.title}</div>
                                    <div className="text-xs text-gray-400 font-mono mt-1">{article.slug}</div>
                                </td>
                                <td className="px-6 py-4">
                                    <div className="flex items-center gap-2">
                                        <div className="w-6 h-6 rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center text-xs font-bold">
                                            {article.author?.name?.charAt(0) || 'U'}
                                        </div>
                                        <span className="text-sm text-gray-600">{article.author?.name || 'Unknown'}</span>
                                    </div>
                                </td>
                                <td className="px-6 py-4">
                                    <StatusBadge status={article.status} />
                                </td>
                                <td className="px-6 py-4 text-sm text-gray-500">
                                    <div className="flex items-center gap-1">
                                        <Clock size={14} />
                                        {new Date(article.updated_at).toLocaleDateString()}
                                    </div>
                                </td>
                                <td className="px-6 py-4 text-right">
                                    <div className="flex justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                        <button className="p-1.5 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors" title="Versioning">
                                            <GitCommit size={18} />
                                        </button>
                                        <Link
                                            to={`/admin/articles/${article.id}`}
                                            className="p-1.5 text-gray-400 hover:text-green-600 hover:bg-green-50 rounded-md transition-colors inline-block"
                                            title="Preview"
                                        >
                                            <Eye size={18} />
                                        </Link>
                                        <button
                                            onClick={() => {
                                                if (confirm('Are you sure you want to delete this article?')) {
                                                    axios.delete(`/api/articles/${article.id}`)
                                                        .then(() => {
                                                            setArticles(prev => prev.filter(a => a.id !== article.id));
                                                        })
                                                        .catch(err => alert('Failed to delete'));
                                                }
                                            }}
                                            className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors"
                                            title="Delete"
                                        >
                                            <Trash2 size={18} />
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                        {articles.length === 0 && (
                            <tr>
                                <td colSpan="5" className="px-6 py-12 text-center text-gray-400">
                                    <div className="flex flex-col items-center gap-2">
                                        <FileText size={48} className="text-gray-200" />
                                        <p>No articles found. Create your first one!</p>
                                    </div>
                                </td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
