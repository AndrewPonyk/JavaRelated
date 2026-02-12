import React from 'react';

export default function Dashboard() {
    return (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            <div className="bg-white p-6 rounded-xl shadow-lg border border-gray-100 transform transition hover:-translate-y-1 hover:shadow-xl">
                <div className="flex items-center justify-between mb-4">
                    <h3 className="text-gray-500 text-sm font-medium uppercase tracking-wider">Total Articles</h3>
                    <div className="p-2 bg-blue-100 rounded-lg text-blue-600">
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"></path></svg>
                    </div>
                </div>
                <div className="flex items-baseline">
                    <p className="text-3xl font-bold text-gray-900">124</p>
                    <span className="ml-2 text-sm font-medium text-green-600 flex items-center">
                        <svg className="w-3 h-3 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 10l7-7m0 0l7 7m-7-7v18"></path></svg>
                        12%
                    </span>
                </div>
            </div>

            <div className="bg-white p-6 rounded-xl shadow-lg border border-gray-100 transform transition hover:-translate-y-1 hover:shadow-xl">
                <div className="flex items-center justify-between mb-4">
                    <h3 className="text-gray-500 text-sm font-medium uppercase tracking-wider">Active Users</h3>
                    <div className="p-2 bg-purple-100 rounded-lg text-purple-600">
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 005.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"></path></svg>
                    </div>
                </div>
                <div className="flex items-baseline">
                    <p className="text-3xl font-bold text-gray-900">42</p>
                    <span className="ml-2 text-sm font-medium text-green-600 flex items-center">
                        <svg className="w-3 h-3 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 10l7-7m0 0l7 7m-7-7v18"></path></svg>
                        5%
                    </span>
                </div>
            </div>

            <div className="bg-white p-6 rounded-xl shadow-lg border border-gray-100 transform transition hover:-translate-y-1 hover:shadow-xl">
                <div className="flex items-center justify-between mb-4">
                    <h3 className="text-gray-500 text-sm font-medium uppercase tracking-wider">Pending Revisions</h3>
                    <div className="p-2 bg-yellow-100 rounded-lg text-yellow-600">
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                    </div>
                </div>
                <div className="flex items-baseline">
                    <p className="text-3xl font-bold text-gray-900">8</p>
                    <span className="ml-2 text-sm font-medium text-red-600 flex items-center">
                        -2
                    </span>
                </div>
            </div>

            <div className="bg-white p-6 rounded-xl shadow-lg border border-gray-100 transform transition hover:-translate-y-1 hover:shadow-xl">
                <div className="flex items-center justify-between mb-4">
                    <h3 className="text-gray-500 text-sm font-medium uppercase tracking-wider">System Health</h3>
                    <div className="p-2 bg-green-100 rounded-lg text-green-600">
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                    </div>
                </div>
                <div className="flex items-baseline">
                    <p className="text-3xl font-bold text-green-600">Healthy</p>
                    <span className="ml-2 text-xs text-gray-400">Up 99.9%</span>
                </div>
            </div>
        </div>
    );
}
