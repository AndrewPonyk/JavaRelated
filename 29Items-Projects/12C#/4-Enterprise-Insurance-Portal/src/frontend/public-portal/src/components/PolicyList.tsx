import { useState, useEffect } from 'react';
import axios from 'axios';

interface Policy {
  id: string;
  policyNumber: string;
  premiumAmount: number;
  status: string;
  customerId: string;
}

const PolicyList: React.FC = () => {
  const [policies, setPolicies] = useState<Policy[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Form states
  const [customerName, setCustomerName] = useState('');
  const [customerEmail, setCustomerEmail] = useState('');
  const [newCustomerId, setNewCustomerId] = useState('');
  const [planType, setPlanType] = useState('Standard');
  const [initialPremium, setInitialPremium] = useState(100);

  const fetchPolicies = async () => {
    try {
      setLoading(true);
      const response = await axios.get('http://localhost:5162/api/policies');
      setPolicies(response.data.items);
      setError(null);
    } catch (err: any) {
      setError(err.message || 'An unexpected error occurred.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPolicies();
  }, []);

  const bindPolicy = async (id: string) => {
    try {
      await axios.post(`http://localhost:5162/api/policies/${id}/bind`);
      fetchPolicies();
    } catch (err: any) {
      alert(`Failed to bind policy: ${err.message}`);
    }
  };

  const deletePolicy = async (id: string) => {
    try {
      await axios.delete(`http://localhost:5162/api/policies/${id}`);
      fetchPolicies();
    } catch (err: any) {
      alert(`Failed to delete policy: ${err.message}`);
    }
  };

  const handleCreateCustomer = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await axios.post('http://localhost:5162/api/customers', { name: customerName, email: customerEmail });
      setNewCustomerId(res.data.id);
      alert(`Customer created successfully! ID: ${res.data.id}`);
    } catch (err: any) {
      alert(`Failed to create customer: ${err.message}`);
    }
  };

  const handleCreatePolicy = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCustomerId) {
      alert('Please create a customer first or provide a Customer ID');
      return;
    }
    try {
      await axios.post('http://localhost:5162/api/policies', { customerId: newCustomerId, planType, initialPremium });
      fetchPolicies();
    } catch (err: any) {
      alert(`Failed to create policy: ${err.message}`);
    }
  };

  if (loading) return <div>Loading policies...</div>;
  if (error) return <div className="error-message">Error: {error}. Please ensure the backend is running.</div>;

  return (
    <div className="policy-list-container" style={{ padding: '20px', fontFamily: 'system-ui' }}>
      <h2>Create Customer</h2>
      <form onSubmit={handleCreateCustomer} style={{ marginBottom: '20px', padding: '15px', background: '#f9f9f9', borderRadius: '8px' }}>
        <input type="text" placeholder="Name" value={customerName} onChange={e => setCustomerName(e.target.value)} required style={{ marginRight: '10px' }} />
        <input type="email" placeholder="Email" value={customerEmail} onChange={e => setCustomerEmail(e.target.value)} required style={{ marginRight: '10px' }} />
        <button type="submit" style={{ padding: '5px 15px', backgroundColor: '#28a745', color: 'white', border: 'none', borderRadius: '4px' }}>Create Customer</button>
      </form>

      <h2>Create Policy</h2>
      <form onSubmit={handleCreatePolicy} style={{ marginBottom: '20px', padding: '15px', background: '#f9f9f9', borderRadius: '8px' }}>
        <input type="text" placeholder="Customer ID" value={newCustomerId} onChange={e => setNewCustomerId(e.target.value)} required style={{ marginRight: '10px' }} />
        <select value={planType} onChange={e => setPlanType(e.target.value)} style={{ marginRight: '10px' }}>
          <option value="Standard">Standard</option>
          <option value="Premium">Premium</option>
        </select>
        <input type="number" placeholder="Premium" value={initialPremium} onChange={e => setInitialPremium(Number(e.target.value))} required style={{ marginRight: '10px' }} />
        <button type="submit" style={{ padding: '5px 15px', backgroundColor: '#007bff', color: 'white', border: 'none', borderRadius: '4px' }}>Create Policy</button>
      </form>

      <h2>Your Policies</h2>
      {policies.length === 0 ? (
        <p>No policies found.</p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: '20px' }}>
          <thead>
            <tr style={{ backgroundColor: '#f0f0f0', textAlign: 'left' }}>
              <th style={{ padding: '10px', borderBottom: '2px solid #ddd' }}>Policy Number</th>
              <th style={{ padding: '10px', borderBottom: '2px solid #ddd' }}>Premium</th>
              <th style={{ padding: '10px', borderBottom: '2px solid #ddd' }}>Status</th>
              <th style={{ padding: '10px', borderBottom: '2px solid #ddd' }}>Action</th>
            </tr>
          </thead>
          <tbody>
            {policies.map(policy => (
              <tr key={policy.id} style={{ borderBottom: '1px solid #ddd' }}>
                <td style={{ padding: '10px' }}>{policy.policyNumber}</td>
                <td style={{ padding: '10px' }}>${policy.premiumAmount.toFixed(2)}</td>
                <td style={{ padding: '10px' }}>
                  <span style={{ 
                    padding: '5px 10px', 
                    borderRadius: '15px', 
                    backgroundColor: policy.status === 'Bound' ? '#d4edda' : '#fff3cd',
                    color: policy.status === 'Bound' ? '#155724' : '#856404'
                  }}>
                    {policy.status}
                  </span>
                </td>
                <td style={{ padding: '10px' }}>
                  {policy.status !== 'Bound' && (
                    <button 
                      onClick={() => bindPolicy(policy.id)}
                      style={{ padding: '5px 15px', backgroundColor: '#007bff', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer', marginRight: '5px' }}
                    >
                      Bind
                    </button>
                  )}
                  <button 
                    onClick={() => deletePolicy(policy.id)}
                    style={{ padding: '5px 15px', backgroundColor: '#dc3545', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
                  >
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
};

export default PolicyList;
