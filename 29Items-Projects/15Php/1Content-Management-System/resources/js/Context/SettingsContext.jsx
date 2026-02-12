import React, { createContext, useContext, useState, useEffect } from 'react';
import axios from 'axios';

const SettingsContext = createContext();

export const useSettings = () => useContext(SettingsContext);

export const SettingsProvider = ({ children }) => {
    const [settings, setSettings] = useState({
        site_name: window.AppConfig?.name || 'InSight CMS'
    });
    const [loading, setLoading] = useState(true);

    const fetchSettings = async () => {
        try {
            const { data } = await axios.get('/api/settings');
            // Merge defaults with fetched data
            setSettings(prev => ({ ...prev, ...data.data }));
        } catch (error) {
            console.error("Failed to fetch settings:", error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchSettings();
    }, []);

    const updateSettings = async (newSettings) => {
        try {
            const { data } = await axios.post('/api/settings', { settings: newSettings });
            setSettings(prev => ({ ...prev, ...data.data }));
            return { success: true };
        } catch (error) {
            console.error("Failed to update settings:", error);
            return { success: false, error: error.response?.data?.message || 'Update failed' };
        }
    };

    return (
        <SettingsContext.Provider value={{ settings, updateSettings, loading }}>
            {children}
        </SettingsContext.Provider>
    );
};
