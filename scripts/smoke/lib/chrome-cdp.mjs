import fs from 'fs';
import http from 'http';
import net from 'net';
import path from 'path';
import { spawn } from 'child_process';
import { fileURLToPath } from 'url';

export const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');

export function defaultWorkbenchPage() {
  return `file:///${path.resolve(repoRoot, 'src/main/resources/static/index.html').replace(/\\/g, '/')}`;
}

function findChrome() {
  const candidates = [
    process.env.CHROME_PATH,
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/usr/bin/google-chrome',
    '/usr/bin/google-chrome-stable',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser',
  ].filter(Boolean);

  const chromePath = candidates.find((candidate) => fs.existsSync(candidate));
  if (!chromePath) {
    throw new Error('Chrome executable was not found. Set CHROME_PATH to run this smoke test.');
  }
  return chromePath;
}

function getFreePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      server.close(() => resolve(address.port));
    });
  });
}

function requestJson(port, route) {
  return new Promise((resolve, reject) => {
    const req = http.get({ host: '127.0.0.1', port, path: route }, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (chunk) => {
        body += chunk;
      });
      res.on('end', () => {
        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(new Error(`GET ${route} failed: ${res.statusCode} ${body}`));
          return;
        }
        resolve(JSON.parse(body));
      });
    });
    req.on('error', reject);
  });
}

async function waitForDevTools(port) {
  const deadline = Date.now() + 15000;
  let lastError;
  while (Date.now() < deadline) {
    try {
      return await requestJson(port, '/json/list');
    } catch (error) {
      lastError = error;
      await delay(250);
    }
  }
  throw lastError || new Error('Chrome DevTools endpoint did not start');
}

function connect(wsUrl) {
  let nextId = 1;
  const pending = new Map();
  const events = [];
  const ws = new WebSocket(wsUrl);

  ws.onmessage = (message) => {
    const data = JSON.parse(message.data);
    if (data.id && pending.has(data.id)) {
      const { resolve, reject } = pending.get(data.id);
      pending.delete(data.id);
      if (data.error) {
        reject(new Error(`${data.error.message}: ${data.error.data || ''}`));
      } else {
        resolve(data.result || {});
      }
      return;
    }
    if (data.method) {
      events.push(data);
    }
  };

  return new Promise((resolve, reject) => {
    ws.onerror = reject;
    ws.onopen = () => {
      const send = (method, params = {}) => new Promise((resolveCommand, rejectCommand) => {
        const id = nextId++;
        pending.set(id, { resolve: resolveCommand, reject: rejectCommand });
        ws.send(JSON.stringify({ id, method, params }));
      });

      const waitEvent = (method, timeoutMs = 10000) => new Promise((resolveEvent, rejectEvent) => {
        const existing = events.findIndex((event) => event.method === method);
        if (existing >= 0) {
          resolveEvent(events.splice(existing, 1)[0]);
          return;
        }
        const started = Date.now();
        const timer = setInterval(() => {
          const index = events.findIndex((event) => event.method === method);
          if (index >= 0) {
            clearInterval(timer);
            resolveEvent(events.splice(index, 1)[0]);
            return;
          }
          if (Date.now() - started > timeoutMs) {
            clearInterval(timer);
            rejectEvent(new Error(`Timed out waiting for ${method}`));
          }
        }, 50);
      });

      resolve({ send, waitEvent, close: () => ws.close() });
    };
  });
}

export function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

export function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function evaluate(cdp, expression) {
  const result = await cdp.send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
  });
  if (result.exceptionDetails) {
    const details = result.exceptionDetails.exception?.description || result.exceptionDetails.text;
    throw new Error(details || 'Runtime evaluation failed');
  }
  return result.result.value;
}

export async function waitFor(cdp, expression, timeoutMs = 10000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (await evaluate(cdp, expression)) {
      return;
    }
    await delay(250);
  }
  throw new Error(`Timed out waiting for expression: ${expression}`);
}

export async function runChromeSmoke(pageUrl, options, runSmoke) {
  const width = options.width || 390;
  const height = options.height || 844;
  const deviceScaleFactor = options.deviceScaleFactor || 2;
  const mobile = options.mobile !== false;
  const profileDir = path.resolve(repoRoot, 'target', `chrome-cdp-${options.profileName || 'smoke'}-${process.pid}`);
  fs.mkdirSync(profileDir, { recursive: true });

  const port = await getFreePort();
  const chrome = spawn(findChrome(), [
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--allow-file-access-from-files',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${profileDir}`,
    `--window-size=${width},${height}`,
    'about:blank',
  ], { stdio: ['ignore', 'pipe', 'pipe'] });

  try {
    const tabs = await waitForDevTools(port);
    const pageTab = tabs.find((tab) => tab.type === 'page') || tabs[0];
    assert(pageTab?.webSocketDebuggerUrl, 'No debuggable Chrome page found');

    const cdp = await connect(pageTab.webSocketDebuggerUrl);
    await cdp.send('Page.enable');
    await cdp.send('Runtime.enable');
    await cdp.send('Emulation.setDeviceMetricsOverride', {
      width,
      height,
      deviceScaleFactor,
      mobile,
    });
    await cdp.send('Page.navigate', { url: pageUrl });
    try {
      await cdp.waitEvent('Page.domContentEventFired', 10000);
    } catch (_) {
      // CDN assets are not required for mocked workbench smoke tests.
    }

    try {
      return await runSmoke(cdp);
    } finally {
      cdp.close();
    }
  } finally {
    if (!chrome.killed) {
      chrome.kill();
    }
    if (chrome.exitCode === null) {
      await new Promise((resolve) => chrome.once('exit', resolve));
    }
    for (let attempt = 0; attempt < 5; attempt++) {
      try {
        fs.rmSync(profileDir, { recursive: true, force: true });
        break;
      } catch (error) {
        if (attempt === 4) {
          console.warn(`Could not remove temporary Chrome profile: ${error.message}`);
          break;
        }
        await delay(300);
      }
    }
  }
}
