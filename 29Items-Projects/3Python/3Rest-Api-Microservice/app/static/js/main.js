/**
 * Customer Management Dashboard - Main JavaScript
 *
 * |su:57) FRONTEND JAVASCRIPT - Vanilla JS dashboard that calls REST API endpoints.
 *         Demonstrates fetch API, DOM manipulation, and async/await patterns.
 *
 * Provides complete functionality for:
 * - Loading and displaying statistics
 * - Customer CRUD operations
 * - Search functionality
 * - Pagination
 * - Sentiment analysis
 */


// |su:58) API BASE URL - All API calls go through this prefix
const API_BASE = '/api';

// Application State
const state = {
    currentPage: 1,
    perPage: 10,
    searchQuery: '',
    searchField: 'all',
    totalPages: 1
};

/**
 * Initialize the application when DOM is ready
 */
document.addEventListener('DOMContentLoaded', () => {
    initializeApp();
});

/**
 * Initialize the application
 */
function initializeApp() {
    // Load initial data
    loadStats();
    loadCustomers();

    // Set up event listeners
    setupEventListeners();

    console.log('Customer Management Dashboard initialized');
}

/**
 * Set up all event listeners
 */
function setupEventListeners() {
    // Search form
    const searchForm = document.getElementById('search-form');
    if (searchForm) {
        searchForm.addEventListener('submit', handleSearch);
    }

    // Search input - live search on Enter
    const searchInput = document.getElementById('search-query');
    if (searchInput) {
        searchInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                handleSearch(e);
            }
        });
    }

    // Add customer form
    const addForm = document.getElementById('add-customer-form');
    if (addForm) {
        addForm.addEventListener('submit', handleAddCustomer);
    }

    // Clear form when modal is closed
    const addModal = document.getElementById('addCustomerModal');
    if (addModal) {
        addModal.addEventListener('hidden.bs.modal', () => {
            document.getElementById('add-customer-form').reset();
        });
    }
}

// ============================================
// Statistics Functions
// ============================================

/**
 * Load dashboard statistics from the API
 */
// |su:59) FETCH API PATTERN - async/await for clean HTTP calls, try/catch for error handling
async function loadStats() {
    try {
        // |su:60) API CALL - fetch() returns Promise, await waits for response
        const response = await fetch(`${API_BASE}/analytics/summary`);

        if (!response.ok) {
            throw new Error('Failed to load stats');
        }

        // |su:61) JSON PARSING - response.json() also returns Promise, needs await
        const result = await response.json();

        if (result.data) {
            updateStatsDisplay(result.data);
        }
    } catch (error) {
        console.error('Failed to load stats:', error);
        // Show default values on error
        updateStatsDisplay({
            total_customers: '-',
            by_status: {},
            sentiment: {}
        });
    }
}

/**
 * Update the statistics display cards
 */
function updateStatsDisplay(data) {
    const totalEl = document.getElementById('total-customers');
    const activeEl = document.getElementById('active-customers');
    const leadEl = document.getElementById('lead-customers');
    const sentimentEl = document.getElementById('avg-sentiment');

    if (totalEl) totalEl.textContent = data.total_customers || 0;
    if (activeEl) activeEl.textContent = data.by_status?.active || 0;
    if (leadEl) leadEl.textContent = data.by_status?.lead || 0;

    if (sentimentEl) {
        const avgSentiment = data.sentiment?.average;
        sentimentEl.textContent = avgSentiment ? avgSentiment.toFixed(2) : 'N/A';
    }
}

// ============================================
// Customer List Functions
// ============================================

/**
 * Load customers from the API
 */
async function loadCustomers(page = 1) {
    state.currentPage = page;
    const tbody = document.getElementById('customers-tbody');

    if (!tbody) return;

    // Show loading state
    tbody.innerHTML = `
        <tr>
            <td colspan="6" class="text-center py-4">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
            </td>
        </tr>
    `;

    try {
        let url;

        if (state.searchQuery) {
            url = `${API_BASE}/search/?q=${encodeURIComponent(state.searchQuery)}&field=${state.searchField}&page=${page}&per_page=${state.perPage}`;
        } else {
            url = `${API_BASE}/customers/?page=${page}&per_page=${state.perPage}`;
        }

        const response = await fetch(url);

        if (!response.ok) {
            throw new Error('Failed to load customers');
        }

        const result = await response.json();

        if (result.data && result.data.length > 0) {
            renderCustomers(result.data);
            renderPagination(result.pagination);
        } else {
            renderEmptyState();
        }
    } catch (error) {
        console.error('Failed to load customers:', error);
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="text-center text-danger py-4">
                    <i class="bi bi-exclamation-triangle fs-3 mb-2 d-block"></i>
                    Failed to load customers. Please try again.
                </td>
            </tr>
        `;
    }
}

/**
 * Render customers table
 */
function renderCustomers(customers) {
    const tbody = document.getElementById('customers-tbody');

    if (!tbody) return;

    tbody.innerHTML = customers.map(customer => `
        <tr class="fade-in" data-customer-id="${customer.id}">
            <td>
                <div class="fw-medium">${escapeHtml(customer.name)}</div>
                ${customer.phone ? `<small class="text-muted">${escapeHtml(customer.phone)}</small>` : ''}
            </td>
            <td>
                <a href="mailto:${escapeHtml(customer.email)}" class="text-decoration-none">
                    ${escapeHtml(customer.email)}
                </a>
            </td>
            <td>${escapeHtml(customer.company || '-')}</td>
            <td>
                <span class="badge badge-status-${customer.status}">
                    ${capitalizeFirst(customer.status)}
                </span>
            </td>
            <td>
                ${renderSentiment(customer.sentiment_score)}
            </td>
            <td>
                <div class="btn-group btn-group-sm">
                    <button class="btn btn-outline-primary btn-action"
                            onclick="viewCustomer(${customer.id})"
                            title="View Details">
                        <i class="bi bi-eye"></i>
                    </button>
                    <button class="btn btn-outline-secondary btn-action"
                            onclick="editCustomer(${customer.id})"
                            title="Edit">
                        <i class="bi bi-pencil"></i>
                    </button>
                    <button class="btn btn-outline-info btn-action"
                            onclick="analyzeCustomer(${customer.id})"
                            title="Analyze Sentiment">
                        <i class="bi bi-chat-dots"></i>
                    </button>
                    <button class="btn btn-outline-danger btn-action"
                            onclick="deleteCustomer(${customer.id}, '${escapeHtml(customer.name)}')"
                            title="Delete">
                        <i class="bi bi-trash"></i>
                    </button>
                </div>
            </td>
        </tr>
    `).join('');
}

/**
 * Render empty state when no customers found
 */
function renderEmptyState() {
    const tbody = document.getElementById('customers-tbody');
    const nav = document.getElementById('pagination-nav');

    if (tbody) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6">
                    <div class="empty-state">
                        <i class="bi bi-inbox"></i>
                        <h5>No customers found</h5>
                        <p class="text-muted mb-3">
                            ${state.searchQuery
                                ? 'Try adjusting your search criteria'
                                : 'Get started by adding your first customer'}
                        </p>
                        ${!state.searchQuery ? `
                            <button class="btn btn-primary" data-bs-toggle="modal" data-bs-target="#addCustomerModal">
                                <i class="bi bi-plus-lg me-1"></i>Add Customer
                            </button>
                        ` : ''}
                    </div>
                </td>
            </tr>
        `;
    }

    if (nav) {
        nav.classList.add('d-none');
    }
}

/**
 * Render sentiment indicator
 */
function renderSentiment(score) {
    if (score === null || score === undefined) {
        return '<span class="text-muted small">Not analyzed</span>';
    }

    const percentage = Math.round(score * 100);
    let colorClass = 'bg-secondary';
    let label = 'Neutral';

    if (score >= 0.7) {
        colorClass = 'bg-success';
        label = 'Positive';
    } else if (score >= 0.4) {
        colorClass = 'bg-warning';
        label = 'Neutral';
    } else {
        colorClass = 'bg-danger';
        label = 'Negative';
    }

    return `
        <div class="d-flex align-items-center gap-2">
            <div class="sentiment-bar flex-grow-1" title="${label} (${percentage}%)">
                <div class="sentiment-bar-fill ${colorClass}" style="width: ${percentage}%"></div>
            </div>
            <small class="text-muted" style="min-width: 35px">${percentage}%</small>
        </div>
    `;
}

/**
 * Render pagination controls
 */
function renderPagination(pagination) {
    const nav = document.getElementById('pagination-nav');
    const container = document.getElementById('pagination');

    if (!nav || !container) return;

    state.totalPages = pagination.pages;

    if (pagination.pages <= 1) {
        nav.classList.add('d-none');
        return;
    }

    nav.classList.remove('d-none');

    let html = '';

    // Previous button
    html += `
        <li class="page-item ${pagination.page === 1 ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="loadCustomers(${pagination.page - 1}); return false;">
                <i class="bi bi-chevron-left"></i>
            </a>
        </li>
    `;

    // Page numbers
    const startPage = Math.max(1, pagination.page - 2);
    const endPage = Math.min(pagination.pages, pagination.page + 2);

    if (startPage > 1) {
        html += `<li class="page-item"><a class="page-link" href="#" onclick="loadCustomers(1); return false;">1</a></li>`;
        if (startPage > 2) {
            html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
    }

    for (let i = startPage; i <= endPage; i++) {
        html += `
            <li class="page-item ${pagination.page === i ? 'active' : ''}">
                <a class="page-link" href="#" onclick="loadCustomers(${i}); return false;">${i}</a>
            </li>
        `;
    }

    if (endPage < pagination.pages) {
        if (endPage < pagination.pages - 1) {
            html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
        }
        html += `<li class="page-item"><a class="page-link" href="#" onclick="loadCustomers(${pagination.pages}); return false;">${pagination.pages}</a></li>`;
    }

    // Next button
    html += `
        <li class="page-item ${pagination.page === pagination.pages ? 'disabled' : ''}">
            <a class="page-link" href="#" onclick="loadCustomers(${pagination.page + 1}); return false;">
                <i class="bi bi-chevron-right"></i>
            </a>
        </li>
    `;

    container.innerHTML = html;
}

// ============================================
// Search Functions
// ============================================

/**
 * Handle search form submission
 */
function handleSearch(event) {
    event.preventDefault();

    const queryInput = document.getElementById('search-query');
    const fieldSelect = document.getElementById('search-field');

    state.searchQuery = queryInput ? queryInput.value.trim() : '';
    state.searchField = fieldSelect ? fieldSelect.value : 'all';
    state.currentPage = 1;

    loadCustomers(1);
}

/**
 * Clear search and reload all customers
 */
function clearSearch() {
    const queryInput = document.getElementById('search-query');
    if (queryInput) queryInput.value = '';

    state.searchQuery = '';
    state.currentPage = 1;

    loadCustomers(1);
}

// ============================================
// Customer CRUD Functions
// ============================================

/**
 * Handle add customer form submission
 */
async function handleAddCustomer(event) {
    event.preventDefault();

    const form = event.target;
    const submitBtn = form.querySelector('button[type="submit"]');
    const formData = new FormData(form);

    // Build data object
    const data = {};
    formData.forEach((value, key) => {
        if (value.trim()) {
            data[key] = value.trim();
        }
    });

    // Disable submit button
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Adding...';

    try {
        const response = await fetch(`${API_BASE}/customers/`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(data)
        });

        const result = await response.json();

        if (response.ok) {
            // Close modal
            const modal = bootstrap.Modal.getInstance(document.getElementById('addCustomerModal'));
            if (modal) modal.hide();

            // Reset form
            form.reset();

            // Reload data
            loadCustomers(1);
            loadStats();

            showToast('Customer added successfully!', 'success');
        } else {
            const errorMsg = result.error?.message || 'Failed to add customer';
            const details = result.error?.details;

            if (details) {
                const detailsStr = Object.entries(details)
                    .map(([field, msg]) => `${field}: ${msg}`)
                    .join(', ');
                showToast(`${errorMsg}: ${detailsStr}`, 'danger');
            } else {
                showToast(errorMsg, 'danger');
            }
        }
    } catch (error) {
        console.error('Failed to add customer:', error);
        showToast('Failed to add customer. Please try again.', 'danger');
    } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = 'Add Customer';
    }
}

/**
 * View customer details
 */
async function viewCustomer(id) {
    try {
        const response = await fetch(`${API_BASE}/customers/${id}`);
        const result = await response.json();

        if (response.ok) {
            const customer = result.data;
            alert(`Customer Details:\n\nName: ${customer.name}\nEmail: ${customer.email}\nPhone: ${customer.phone || 'N/A'}\nCompany: ${customer.company || 'N/A'}\nStatus: ${customer.status}\nNotes: ${customer.notes || 'N/A'}\nSentiment: ${customer.sentiment_score ? (customer.sentiment_score * 100).toFixed(0) + '%' : 'Not analyzed'}`);
        } else {
            showToast('Failed to load customer details', 'danger');
        }
    } catch (error) {
        console.error('Failed to view customer:', error);
        showToast('Failed to load customer details', 'danger');
    }
}

/**
 * Edit customer (opens modal with pre-filled data)
 */
async function editCustomer(id) {
    try {
        const response = await fetch(`${API_BASE}/customers/${id}`);
        const result = await response.json();

        if (response.ok) {
            const customer = result.data;

            // For now, use prompt-based editing
            const newName = prompt('Enter new name:', customer.name);
            if (newName === null) return;

            const newEmail = prompt('Enter new email:', customer.email);
            if (newEmail === null) return;

            const newStatus = prompt('Enter status (active/inactive/lead):', customer.status);
            if (newStatus === null) return;

            // Update customer
            const updateResponse = await fetch(`${API_BASE}/customers/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    name: newName,
                    email: newEmail,
                    status: newStatus
                })
            });

            if (updateResponse.ok) {
                loadCustomers(state.currentPage);
                loadStats();
                showToast('Customer updated successfully!', 'success');
            } else {
                const error = await updateResponse.json();
                showToast(error.error?.message || 'Failed to update customer', 'danger');
            }
        }
    } catch (error) {
        console.error('Failed to edit customer:', error);
        showToast('Failed to edit customer', 'danger');
    }
}

/**
 * Delete customer with confirmation
 */
async function deleteCustomer(id, name) {
    if (!confirm(`Are you sure you want to delete "${name}"?\n\nThis action cannot be undone.`)) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/customers/${id}`, {
            method: 'DELETE'
        });

        if (response.ok) {
            loadCustomers(state.currentPage);
            loadStats();
            showToast('Customer deleted successfully', 'success');
        } else {
            showToast('Failed to delete customer', 'danger');
        }
    } catch (error) {
        console.error('Failed to delete customer:', error);
        showToast('Failed to delete customer', 'danger');
    }
}

/**
 * Analyze customer sentiment
 */
async function analyzeCustomer(id) {
    showToast('Analyzing sentiment...', 'info');

    try {
        const response = await fetch(`${API_BASE}/nlp/customer/${id}/analyze`, {
            method: 'POST'
        });

        const result = await response.json();

        if (response.ok) {
            if (result.data.analyzed) {
                loadCustomers(state.currentPage);
                loadStats();
                showToast(`Sentiment analysis complete: ${(result.data.updated_score * 100).toFixed(0)}%`, 'success');
            } else {
                showToast(result.data.message || 'No notes to analyze', 'warning');
            }
        } else {
            showToast(result.error?.message || 'Analysis failed', 'danger');
        }
    } catch (error) {
        console.error('Failed to analyze customer:', error);
        showToast('Sentiment analysis failed. NLP service may not be available.', 'warning');
    }
}

// ============================================
// Utility Functions
// ============================================

/**
 * Show toast notification
 */
function showToast(message, type = 'info') {
    // Create toast container if it doesn't exist
    let container = document.querySelector('.toast-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container position-fixed top-0 end-0 p-3';
        container.style.zIndex = '1050';
        document.body.appendChild(container);
    }

    // Map type to Bootstrap colors
    const typeMap = {
        'success': 'bg-success',
        'danger': 'bg-danger',
        'warning': 'bg-warning text-dark',
        'info': 'bg-info text-dark'
    };

    const bgClass = typeMap[type] || 'bg-secondary';

    const toastId = 'toast-' + Date.now();
    const toast = document.createElement('div');
    toast.id = toastId;
    toast.className = `toast align-items-center ${bgClass} text-white border-0`;
    toast.setAttribute('role', 'alert');
    toast.innerHTML = `
        <div class="d-flex">
            <div class="toast-body">${escapeHtml(message)}</div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
        </div>
    `;

    container.appendChild(toast);

    const bsToast = new bootstrap.Toast(toast, {
        autohide: true,
        delay: type === 'danger' ? 5000 : 3000
    });
    bsToast.show();

    toast.addEventListener('hidden.bs.toast', () => toast.remove());
}

/**
 * Escape HTML to prevent XSS
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Capitalize first letter
 */
function capitalizeFirst(str) {
    if (!str) return '';
    return str.charAt(0).toUpperCase() + str.slice(1);
}

// Export functions for global access (onclick handlers)
window.loadCustomers = loadCustomers;
window.viewCustomer = viewCustomer;
window.editCustomer = editCustomer;
window.deleteCustomer = deleteCustomer;
window.analyzeCustomer = analyzeCustomer;
window.clearSearch = clearSearch;
