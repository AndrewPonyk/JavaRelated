import { useEffect } from 'react';
import { DocumentEntity } from '../types/document';

interface EventStreamMessage {
  eventType: 'DOCUMENT_CREATED' | 'DOCUMENT_UPDATED' | 'SLA_BREACHED';
  action?: string;
  document: DocumentEntity;
}

export function useEventStream(onUpdate: (msg: EventStreamMessage) => void) {
  useEffect(() => {
    const streamUrl = `${import.meta.env.VITE_API_BASE_URL || '/api/v1'}/events/stream`;
    let eventSource: EventSource | null = null;

    try {
      eventSource = new EventSource(streamUrl);

      eventSource.addEventListener('workflow_update', (event) => {
        try {
          const parsed = JSON.parse(event.data) as EventStreamMessage;
          onUpdate(parsed);
        } catch (err) {
          console.error('Error parsing SSE payload:', err);
        }
      });

      eventSource.onerror = (err) => {
        console.warn('SSE stream disconnected or reconnecting...', err);
      };
    } catch (e) {
      console.warn('SSE not supported or failed to initialize', e);
    }

    return () => {
      if (eventSource) {
        eventSource.close();
      }
    };
  }, [onUpdate]);
}
