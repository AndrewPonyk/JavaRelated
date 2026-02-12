import React, { useState, useEffect } from 'react';
import { useSettings } from '../../Context/SettingsContext';
import { Save } from 'lucide-react';

export default function Settings() {
    const { settings, updateSettings } = useSettings();
    const [formData, setFormData] = useState({
        site_name: '',
    });
    const [isSaving, setIsSaving] = useState(false);
    const [message, setMessage] = useState(null);

    useEffect(() => {
        if (settings) {
            setFormData({
                site_name: settings.site_name || ''
            });
        }
    }, [settings]);

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsSaving(true);
        setMessage(null);

        const result = await updateSettings(formData);

        if (result.success) {
            setMessage({ type: 'success', text: 'Settings updated successfully!' });
        } else {
            setMessage({ type: 'error', text: result.error });
        }
        setIsSaving(false);
    };

    return (
        <div className="max-w-2xl bg-white rounded-xl shadow-sm border border-gray-100 p-8">
            <h2 className="text-2xl font-bold text-gray-800 mb-6">Workflow Settings</h2>

            {message && (
                <div className={`p-4 mb-6 rounded-lg ${message.type === 'success' ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'}`}>
                    {message.text}
                </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-6">
                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">
                        Site Name
                    </label>
                    <input
                        type="text"
                        name="site_name"
                        value={formData.site_name}
                        onChange={handleChange}
                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all"
                        placeholder="Enter site name"
                    />
                    <p className="text-xs text-gray-500 mt-1">
                        This name will appear in the header and footer of your public site.
                    </p>
                </div>

                <div className="pt-4 border-t border-gray-100">
                    <button
                        type="submit"
                        disabled={isSaving}
                        className="flex items-center gap-2 bg-blue-600 text-white px-6 py-2.5 rounded-lg font-medium hover:bg-blue-700 transition-colors disabled:opacity-50"
                    >
                        <Save size={18} />
                        {isSaving ? 'Saving...' : 'Save Changes'}
                    </button>
                </div>
            </form>
        </div>
    );
}
