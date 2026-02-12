import React, { useState, useEffect } from 'react';

// RecommendationWidget Component
// Fetches ML-based content recommendations for the current user/article context.
const RecommendationWidget = ({ articleId }) => {
    const [recommendations, setRecommendations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const fetchRecommendations = async () => {
            try {
                // In a real app, this URL would come from a config or prop
                // endpoint: /api/v1/articles/{id}/recommendations
                const response = await fetch(`/api/v1/articles/${articleId}/recommendations`, {
                    headers: {
                        'Accept': 'application/json',
                        // 'Authorization': `Bearer ${token}` // If auth needed
                    }
                });

                if (!response.ok) {
                    throw new Error(`Error: ${response.status}`);
                }

                const data = await response.json();
                setRecommendations(data.data || []);
            } catch (err) {
                console.error("Failed to load recommendations", err);
                setError("Could not load recommendations.");
            } finally {
                setLoading(false);
            }
        };

        if (articleId) {
            fetchRecommendations();
        }
    }, [articleId]);

    if (loading) {
        return (
            <div className="p-4 border rounded shadow-sm animate-pulse bg-gray-50">
                <div className="h-4 bg-gray-200 rounded w-1/3 mb-4"></div>
                <div className="space-y-3">
                    <div className="h-20 bg-gray-200 rounded"></div>
                    <div className="h-20 bg-gray-200 rounded"></div>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="p-4 border border-red-200 rounded bg-red-50 text-red-600">
                <p>{error}</p>
                <button 
                    onClick={() => window.location.reload()} 
                    className="mt-2 text-sm underline hover:text-red-800"
                >
                    Retry
                </button>
            </div>
        );
    }

    if (recommendations.length === 0) {
        return null; // Don't show anything if no recommendations
    }

    return (
        <div className="bg-white rounded-lg shadow-sm overflow-hidden">
            <h3 className="text-lg font-semibold text-gray-800 p-4 border-b">
                Recommended for You
            </h3>
            <ul className="divide-y divide-gray-100">
                {recommendations.map((item) => (
                    <li key={item.id} className="p-4 hover:bg-gray-50 transition duration-150">
                        <a href={`/articles/${item.slug}`} className="group block">
                            <h4 className="text-md font-medium text-blue-600 group-hover:text-blue-800 mb-1">
                                {item.title}
                            </h4>
                            <p className="text-sm text-gray-500 line-clamp-2">
                                {item.excerpt}
                            </p>
                            <div className="mt-2 flex items-center text-xs text-gray-400">
                                <span className="mr-2">{new Date(item.published_at).toLocaleDateString()}</span>
                                <span>• {item.author.name}</span>
                            </div>
                        </a>
                    </li>
                ))}
            </ul>
        </div>
    );
};

export default RecommendationWidget;
