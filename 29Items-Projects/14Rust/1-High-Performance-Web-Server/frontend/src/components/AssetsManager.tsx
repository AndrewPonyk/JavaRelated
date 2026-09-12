import React, { useState, useEffect } from 'react';

interface Asset {
  id: number;
  url_path: string;
  origin_url: string;
  content_type: string;
  size_bytes: number;
  cache_ttl_seconds: number;
}

export const AssetsManager: React.FC = () => {
  const [assets, setAssets] = useState<Asset[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  
  // Form State
  const [urlPath, setUrlPath] = useState('');
  const [originUrl, setOriginUrl] = useState('');
  const [contentType, setContentType] = useState('application/json');
  const [ttl, setTtl] = useState(3600);

  const fetchAssets = async () => {
    try {
      const res = await fetch('/api/assets');
      if (res.ok) setAssets(await res.json());
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAssets();
  }, []);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);

    if (!urlPath.startsWith('/')) {
        setErrorMsg("Intercept Path must start with a forward slash (/)");
        return;
    }
    if (!originUrl.startsWith('http://') && !originUrl.startsWith('https://')) {
        setErrorMsg("Origin URL must be a valid HTTP or HTTPS URL");
        return;
    }

    try {
      const res = await fetch('/api/assets', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          url_path: urlPath,
          origin_url: originUrl,
          content_type: contentType,
          size_bytes: 0,
          cache_ttl_seconds: ttl
        })
      });
      
      if (!res.ok) {
        const errorData = await res.json();
        throw new Error(errorData.error || 'Failed to add rule');
      }

      fetchAssets();
      setUrlPath('');
      setOriginUrl('');
      setSuccessMsg("Rule successfully added!");
    } catch (e: any) {
      setErrorMsg(e.message);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      const res = await fetch(`/api/assets/${id}`, { method: 'DELETE' });
      if (res.ok) {
          setSuccessMsg("Rule deleted.");
          fetchAssets();
      } else {
          setErrorMsg("Failed to delete rule.");
      }
    } catch (e) {
      console.error(e);
      setErrorMsg("Failed to connect to server.");
    }
  };

  return (
    <div className="assets-manager">
      <h2>Routing Rules</h2>
      
      <form onSubmit={handleAdd} className="add-asset-form">
        <h3>Add New Rule</h3>
        {errorMsg && <div className="error-message" style={{color: 'red', marginBottom: '10px'}}>{errorMsg}</div>}
        {successMsg && <div className="success-message" style={{color: 'green', marginBottom: '10px'}}>{successMsg}</div>}
        
        <div className="form-group">
          <input 
            placeholder="/api/v1/users" 
            value={urlPath} 
            onChange={e => setUrlPath(e.target.value)} 
            required 
          />
          <input 
            placeholder="https://origin-api.com/users" 
            value={originUrl} 
            onChange={e => setOriginUrl(e.target.value)} 
            required 
          />
          <select value={contentType} onChange={e => setContentType(e.target.value)}>
            <option value="application/json">JSON</option>
            <option value="text/html">HTML</option>
            <option value="image/png">PNG</option>
          </select>
          <input 
            type="number" 
            placeholder="TTL (sec)" 
            value={ttl} 
            onChange={e => setTtl(Number(e.target.value))} 
            required
          />
          <button type="submit">Add Rule</button>
        </div>
      </form>

      {loading ? <p>Loading...</p> : (
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Intercept Path</th>
              <th>Origin URL</th>
              <th>TTL (s)</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {assets.map(a => (
              <tr key={a.id}>
                <td>{a.id}</td>
                <td>{a.url_path}</td>
                <td>{a.origin_url}</td>
                <td>{a.cache_ttl_seconds}</td>
                <td>
                  <button className="delete" onClick={() => handleDelete(a.id)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};
