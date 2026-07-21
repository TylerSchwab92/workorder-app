// Client-side script.

interface WorkOrder {
    id: number;
    title: string;
    description: string | null;
    status: string;
    assignedTo: string | null;
    createdAt: string;
    updatedAt: string;
}

export {}; // needed so the `declare global` block below is allowed

declare global {
    interface Window {
        __API_BASE_URL__: string;
    }
}

const STATUSES = ['OPEN', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED'] as const;
const TERMINAL_STATUSES = new Set(['COMPLETED', 'CANCELLED']);

const API_BASE = window.__API_BASE_URL__;

function qs<T extends HTMLElement>(selector: string): T {
    const el = document.querySelector(selector);
    if (!el) {
        throw new Error(`Element not found: ${selector}`);
    }
    return el as T;
}

function showError(message: string): void {
    const banner = qs<HTMLDivElement>('#error-banner');
    banner.textContent = message;
    banner.hidden = false;
}

function clearError(): void {
    const banner = qs<HTMLDivElement>('#error-banner');
    banner.hidden = true;
    banner.textContent = '';
}

async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
    const res = await fetch(`${API_BASE}${path}`, {
        headers: { 'Content-Type': 'application/json' },
        ...init,
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
        const message = typeof body?.error === 'string' ? body.error : `Request failed (${res.status})`;
        throw new Error(message);
    }
    return body as T;
}

function formatTimestamp(iso: string): string {
    const date = new Date(iso);
    return Number.isNaN(date.getTime()) ? iso : date.toLocaleString();
}

function renderRow(wo: WorkOrder): HTMLTableRowElement {
    const tr = document.createElement('tr');

    const idCell = document.createElement('td');
    idCell.textContent = String(wo.id);
    tr.appendChild(idCell);

    const titleCell = document.createElement('td');
    titleCell.textContent = wo.title;
    if (wo.description) {
        const desc = document.createElement('div');
        desc.className = 'description';
        desc.textContent = wo.description;
        titleCell.appendChild(desc);
    }
    tr.appendChild(titleCell);

    const assignedCell = document.createElement('td');
    const assignedInput = document.createElement('input');
    assignedInput.type = 'text';
    assignedInput.value = wo.assignedTo ?? '';
    assignedInput.placeholder = 'Unassigned';
    assignedInput.maxLength = 100;
    assignedInput.className = 'assigned-to-input';
    assignedInput.dataset.originalValue = wo.assignedTo ?? '';
    assignedInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            assignedInput.blur(); // saves via the blur handler below
        }
    });
    assignedInput.addEventListener('blur', () => {
        const newValue = assignedInput.value.trim();
        const originalValue = assignedInput.dataset.originalValue ?? '';
        if (newValue !== originalValue) {
            void onAssignedToChange(wo.id, newValue, assignedInput);
        }
    });
    assignedCell.appendChild(assignedInput);
    tr.appendChild(assignedCell);

    const statusCell = document.createElement('td');
    const select = document.createElement('select');
    select.setAttribute('aria-label', `Status for work order ${wo.id}`);
    for (const status of STATUSES) {
        const option = document.createElement('option');
        option.value = status;
        option.textContent = status.replace('_', ' ');
        option.selected = status === wo.status;
        select.appendChild(option);
    }

    // disable the control instead of letting the user hit a 409.
    select.disabled = TERMINAL_STATUSES.has(wo.status);
    select.addEventListener('change', () => onStatusChange(wo.id, select.value, select));
    statusCell.appendChild(select);
    tr.appendChild(statusCell);

    const updatedCell = document.createElement('td');
    updatedCell.textContent = formatTimestamp(wo.updatedAt);
    tr.appendChild(updatedCell);

    return tr;
}

async function loadWorkOrders(): Promise<void> {
    clearError();
    const tbody = qs<HTMLTableSectionElement>('#workorders-body');
    try {
        const data = await apiRequest<{ items: WorkOrder[] }>('/api/workorders');
        tbody.innerHTML = '';
        if (data.items.length === 0) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 5;
            td.textContent = 'No work orders yet.';
            td.className = 'empty-state';
            tr.appendChild(td);
            tbody.appendChild(tr);
            return;
        }
        for (const wo of data.items) {
            tbody.appendChild(renderRow(wo));
        }
    } catch (err) {
        showError(err instanceof Error ? err.message : 'Failed to load work orders.');
    }
}
async function onAssignedToChange(id: number, newValue: string, input: HTMLInputElement): Promise<void> {
    clearError();
    input.disabled = true;
    try {
        await apiRequest(`/api/workorders/${id}`, {
            method: 'PATCH',
            body: JSON.stringify({ assignedTo: newValue || null }),
        });
        input.dataset.originalValue = newValue;
    } catch (err) {
        showError(err instanceof Error ? err.message : 'Failed to update assigned to.');
        input.value = input.dataset.originalValue ?? '';
    } finally {
        input.disabled = false;
    }
}

async function onStatusChange(id: number, newStatus: string, select: HTMLSelectElement): Promise<void> {
    clearError();
    const previousValue = select.dataset.previousValue ?? select.value;
    select.disabled = true;
    try {
        await apiRequest(`/api/workorders/${id}`, {
            method: 'PATCH',
            body: JSON.stringify({ status: newStatus }),
        });
        await loadWorkOrders();
    } catch (err) {
        showError(err instanceof Error ? err.message : 'Failed to update status.');
        select.value = previousValue;
        select.disabled = false;
    }
}

async function onCreateSubmit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    clearError();
    const form = event.currentTarget as HTMLFormElement;
    const titleInput = qs<HTMLInputElement>('#title-input');
    const descriptionInput = qs<HTMLTextAreaElement>('#description-input');
    const assignedToInput = qs<HTMLInputElement>('#assignedTo-input');

    const submitButton = qs<HTMLButtonElement>('#create-button');
    submitButton.disabled = true;
    try {
        await apiRequest('/api/workorders', {
            method: 'POST',
            body: JSON.stringify({
                title: titleInput.value,
                description: descriptionInput.value || null,
                assignedTo: assignedToInput.value || null,
            }),
        });
        form.reset();
        await loadWorkOrders();
    } catch (err) {
        showError(err instanceof Error ? err.message : 'Failed to create work order.');
    } finally {
        submitButton.disabled = false;
    }
}

function init(): void {
    qs<HTMLFormElement>('#create-form').addEventListener('submit', (e) => {
        void onCreateSubmit(e as SubmitEvent);
    });
    qs<HTMLButtonElement>('#refresh-button').addEventListener('click', () => {
        void loadWorkOrders();
    });
    void loadWorkOrders();
}

document.addEventListener('DOMContentLoaded', init);
