import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import axios from 'axios';
import { Calendar, User, Clock, ArrowLeft, Share2, Printer, Tag } from 'lucide-react';

export default function ArticleView() {
    const { slug } = useParams();
    const [article, setArticle] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        // Need to ensure API can fetch by slug. 
        // If current API only supports ID, we might have issues if slug is passed.
        // Assuming for now user might use ID in URL if slug resolution isn't set up, 
        // OR we'll try to fetch list and filter client side as fallback if no direct slug endpoint exists yet.

        axios.get(`/api/articles/${slug}`)
            .then(res => {
                setArticle(res.data.data);
                setLoading(false);
            })
            .catch(err => {
                // If 404, maybe it's because we passed a slug but API expects ID.
                // Fallback: Fetch all and find by slug (inefficient but works for small demo)
                console.warn("Direct fetch failed, trying fallback search...");
                axios.get('/api/articles')
                    .then(res => {
                        const found = res.data.data.find(a => a.slug === slug || a.id.toString() === slug);
                        if (found) {
                            setArticle(found);
                        }
                        setLoading(false);
                    })
            });
    }, [slug]);

    if (loading) return (
        <div className="flex justify-center items-center h-screen">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
        </div>
    );

    if (!article) return (
        <div className="text-center py-24 px-4">
            <div className="bg-gray-100 rounded-full w-24 h-24 flex items-center justify-center mx-auto mb-6">
                <span className="text-4xl">🤷‍♂️</span>
            </div>
            <h1 className="text-3xl font-bold text-gray-800 mb-4">Article Not Found</h1>
            <p className="text-gray-600 mb-8 max-w-md mx-auto">We couldn't find the article you're looking for. It might have been removed or the link is incorrect.</p>
            <Link to="/" className="inline-flex items-center gap-2 bg-blue-600 text-white px-6 py-3 rounded-lg hover:bg-blue-700 transition-colors">
                <ArrowLeft size={16} /> Return to Homepage
            </Link>
        </div>
    );

    return (
        <article className="max-w-4xl mx-auto bg-white rounded-3xl shadow-xl overflow-hidden border border-gray-100 mt-8 mb-16">
            {/* Header / Featured Image Area */}
            <div className="bg-gradient-to-r from-gray-900 via-slate-800 to-gray-900 text-white p-8 md:p-16 relative overflow-hidden">
                <div className="absolute inset-0 opacity-10 bg-[url('https://www.transparenttextures.com/patterns/cubes.png')] mix-blend-overlay"></div>

                {/* Back Button */}
                <div className="relative z-10 mb-8">
                    <Link to="/" className="inline-flex items-center text-gray-400 hover:text-white transition-colors text-sm font-medium uppercase tracking-wide group">
                        <ArrowLeft size={16} className="mr-2 group-hover:-translate-x-1 transition-transform" /> Back to Articles
                    </Link>
                </div>

                <div className="relative z-10">
                    <div className="flex flex-wrap gap-2 mb-4">
                        <span className="px-3 py-1 bg-blue-500/20 backdrop-blur-sm border border-blue-400/30 text-blue-100 rounded-full text-xs font-semibold uppercase tracking-wider">
                            {article.category?.name || 'Featured'}
                        </span>
                        {article.status === 'draft' && (
                            <span className="px-3 py-1 bg-yellow-500/20 backdrop-blur-sm border border-yellow-400/30 text-yellow-100 rounded-full text-xs font-semibold uppercase tracking-wider">
                                Draft Preview
                            </span>
                        )}
                    </div>

                    <h1 className="text-3xl md:text-5xl font-extrabold leading-tight mb-8 tracking-tight">
                        {article.title}
                    </h1>

                    <div className="flex flex-wrap items-center gap-x-8 gap-y-4 text-sm text-gray-300 font-medium font-mono border-t border-white/10 pt-6">
                        <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-blue-400 to-indigo-500 flex items-center justify-center text-white font-bold text-xs ring-2 ring-white/20">
                                {article.author?.name?.charAt(0) || 'A'}
                            </div>
                            <span className="text-white font-semibold">{article.author?.name || 'Unknown Author'}</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <Calendar size={16} className="text-blue-400" />
                            <span>{new Date(article.published_at || article.created_at).toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' })}</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <Clock size={16} className="text-blue-400" />
                            <span>{Math.ceil((article.content?.length || 0) / 1000)} min read</span>
                        </div>
                    </div>
                </div>
            </div>

            {/* Content Body */}
            <div className="p-8 md:p-12 lg:p-16">
                <div className="flex justify-between items-start mb-8 pb-8 border-b border-gray-100">
                    <div className="flex items-center gap-2 text-gray-500 text-sm">
                        <Tag size={16} />
                        {article.tags?.length > 0 ? (
                            <div className="flex gap-2">
                                {article.tags.map(tag => (
                                    <span key={tag.id} className="hover:text-blue-600 cursor-pointer transition-colors">#{tag.name}</span>
                                ))}
                            </div>
                        ) : (
                            <span>No tags</span>
                        )}
                    </div>
                    <div className="flex gap-4">
                        <button className="text-gray-400 hover:text-blue-600 transition-colors p-2 hover:bg-blue-50 rounded-full" title="Share">
                            <Share2 size={20} />
                        </button>
                        <button className="text-gray-400 hover:text-blue-600 transition-colors p-2 hover:bg-blue-50 rounded-full" title="Print">
                            <Printer size={20} />
                        </button>
                    </div>
                </div>

                {article.excerpt && (
                    <div className="text-xl md:text-2xl font-serif text-gray-600 italic mb-10 leading-relaxed border-l-4 border-blue-500 pl-6 py-2">
                        {article.excerpt}
                    </div>
                )}

                <div className="prose prose-lg prose-blue max-w-none text-gray-800 leading-8 font-serif">
                    {/* 
                        Ideally used a Markdown parser like react-markdown 
                        For now just rendering text with line breaks 
                     */}
                    {article.content?.split('\n').map((paragraph, idx) => (
                        paragraph.trim() !== '' ?
                            <p key={idx} className="mb-6 indent-0">{paragraph}</p>
                            : null
                    ))}
                </div>
            </div>

            {/* Author Bio / Footer */}
            <div className="bg-gray-50 border-t border-gray-100 p-8 md:p-12">
                <div className="flex flex-col md:flex-row items-center md:items-start gap-6 text-center md:text-left">
                    <div className="w-20 h-20 rounded-full bg-gradient-to-br from-blue-100 to-indigo-200 flex items-center justify-center text-blue-600 font-bold text-3xl shadow-inner border-4 border-white">
                        {article.author?.name?.charAt(0) || 'A'}
                    </div>
                    <div className="flex-1">
                        <h3 className="text-xl font-bold text-gray-900 mb-2">About the Author</h3>
                        <p className="text-gray-600 leading-relaxed">
                            {article.author?.bio || `Written by ${article.author?.name || 'a contributor'}. Passionate about sharing knowledge and insights on the latest trends and technologies.`}
                        </p>
                        <div className="mt-4 flex gap-3 justify-center md:justify-start">
                            <button className="text-sm font-semibold text-blue-600 hover:text-blue-800">View Profile</button>
                            <span className="text-gray-300">|</span>
                            <button className="text-sm font-semibold text-blue-600 hover:text-blue-800">More Articles</button>
                        </div>
                    </div>
                </div>
            </div>
        </article>
    );
}
