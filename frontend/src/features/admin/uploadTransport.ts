import { readCsrfToken } from '../../api/client.ts';

export interface UploadRequest {
  /** Same-origin path with its query. */
  url: string;
  file: Blob;
  fileName: string;
  idempotencyKey: string;
  /** Called with the share of the body already sent, from 0 to 1. */
  onProgress?: (sent: number) => void;
}

export interface UploadReply {
  status: number;
  /** The parsed JSON body, if any. */
  body: unknown;
}

function parse(text: string): unknown {
  try {
    return text === '' ? undefined : (JSON.parse(text) as unknown);
  } catch {
    return undefined;
  }
}

/**
 * Sends a photo as multipart with XMLHttpRequest, the only browser API that reports upload progress (T-E1-22). Like
 * the typed client it sends the session cookie and the CSRF token; a lost connection rejects as fetch does.
 * Tests replace {@link uploadTransport.send}.
 */
export const uploadTransport = {
  send(request: UploadRequest): Promise<UploadReply> {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open('POST', request.url);
      xhr.setRequestHeader('Idempotency-Key', request.idempotencyKey);
      xhr.setRequestHeader('Accept', 'application/json, application/problem+json');
      const token = readCsrfToken();
      if (token !== undefined) {
        xhr.setRequestHeader('X-XSRF-TOKEN', token);
      }
      xhr.upload.addEventListener('progress', (event) => {
        if (event.lengthComputable && event.total > 0) {
          request.onProgress?.(event.loaded / event.total);
        }
      });
      xhr.addEventListener('load', () => {
        resolve({ status: xhr.status, body: parse(xhr.responseText) });
      });
      xhr.addEventListener('error', () => {
        reject(new TypeError('Failed to fetch'));
      });
      xhr.addEventListener('abort', () => {
        reject(new TypeError('Failed to fetch'));
      });
      const form = new FormData();
      form.append('file', request.file, request.fileName);
      xhr.send(form);
    });
  },
};
