import fs from 'fs';
import http from 'http';
import net from 'net';
import path from 'path';
import { spawn } from 'child_process';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');
const defaultPage = `file:///${path.resolve(repoRoot, 'src/main/resources/static/index.html').replace(/\\/g, '/')}`;
const pageUrl = process.argv[2] || defaultPage;
const profileDir = path.resolve(repoRoot, 'target', `chrome-cdp-workbench-summary-${process.pid}`);

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

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function evaluate(cdp, expression) {
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

async function waitFor(cdp, expression, timeoutMs = 10000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (await evaluate(cdp, expression)) {
      return;
    }
    await delay(250);
  }
  throw new Error(`Timed out waiting for expression: ${expression}`);
}

function mockFetchExpression() {
  return `(() => {
    window.fetch = async (input) => {
      const url = String(input);
      if (url.includes('/api/agent/projects/intake')) {
        return new Response(JSON.stringify({
          success: true,
          project: {
            name: 'summary-smoke-project',
            rootPath: '/workspace/projects/summary-smoke-project',
            detectedStack: ['Java 21', 'Spring Boot', 'Docker Compose'],
            testCommands: ['mvn test']
          },
          intake: {
            currentGoal: 'Polish display density',
            currentStage: 'Workbench polish',
            takeoverBrief: 'Mocked takeover brief for summary smoke.',
            completed: ['Read project files'],
            unfinished: ['Continue UI polish'],
            risks: [],
            nextStepOnly: 'Inspect summary metrics',
            stackEvidence: ['pom.xml', 'docker-compose.yml'],
            runnableCommands: ['mvn test', 'docker compose up -d'],
            verificationGaps: ['Live Docker rebuild pending'],
            workingTree: {
              isGitRepository: true,
              hasUncommittedChanges: false,
              statusShort: '',
              latestCommits: ['abc123 test commit']
            }
          },
          evidence: [{ type: 'README', path: 'README.md', summary: 'Project overview' }],
          readLog: { readFiles: ['README.md', 'SPEC.md'], commands: ['git status --short'] },
          warnings: []
        }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      if (url.includes('/api/agent/code/search')) {
        return new Response(JSON.stringify({
          rootPath: '/workspace/projects/summary-smoke-project',
          query: 'StaticWorkbenchHtmlTest',
          gitRepository: true,
          matches: [
            { path: 'src/test/java/com/smartkb/StaticWorkbenchHtmlTest.java', lineNumber: 78, line: 'function renderCompactMetric(label, value, tone)' },
            { path: 'src/main/resources/static/index.html', lineNumber: 937, line: 'function renderCompactMetric(label, value, tone)' }
          ],
          skippedFiles: [{ path: 'target/generated.log', reason: 'ignored' }],
          warnings: ['mock warning']
        }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      throw new Error('Unexpected mocked fetch: ' + url);
    };
  })()`;
}

async function runSmoke(cdp) {
  await waitFor(cdp, 'Boolean(document.getElementById("workspaceNavProjectIntake"))');
  const hasSummaryHelper = await evaluate(cdp, 'typeof renderCompactMetric === "function"');
  assert(hasSummaryHelper, 'renderCompactMetric helper was not found on the page');
  await evaluate(cdp, mockFetchExpression());

  await evaluate(cdp, `(() => {
    document.getElementById('workspaceNavProjectIntake').click();
    document.getElementById('projectIntakePath').value = '/workspace/projects/summary-smoke-project';
    document.getElementById('projectIntakeButton').click();
  })()`);
  await waitFor(cdp, `(() => {
    const result = document.getElementById('projectIntakeResult');
    return !result.classList.contains('hidden') && result.textContent.includes('summary-smoke-project');
  })()`);

  const projectSummary = await evaluate(cdp, `(() => {
    const summary = document.querySelector('#projectIntakeResult > div.mb-3.grid');
    return {
      cardCount: summary?.children.length || 0,
      values: [...(summary?.children || [])].map((card) => card.querySelector('p:last-child')?.textContent.trim()),
      visible: !document.getElementById('projectIntakeResult').classList.contains('hidden')
    };
  })()`);
  assert(projectSummary.visible, 'Project Intake result did not become visible');
  assert(projectSummary.cardCount === 4, `Expected 4 Project Intake summary cards: ${JSON.stringify(projectSummary)}`);
  assert(projectSummary.values.join(',') === '3,2,1,0', `Unexpected Project Intake metrics: ${JSON.stringify(projectSummary)}`);

  await evaluate(cdp, `(() => {
    document.getElementById('workspaceNavCodeContext').click();
    document.getElementById('codeContextMode').value = 'search';
    document.getElementById('codeContextQuery').value = 'StaticWorkbenchHtmlTest';
    document.getElementById('codeContextButton').click();
  })()`);
  await waitFor(cdp, `(() => {
    const result = document.getElementById('codeContextResult');
    return !result.classList.contains('hidden') && result.textContent.includes('StaticWorkbenchHtmlTest');
  })()`);

  const codeSummary = await evaluate(cdp, `(() => {
    const summary = document.querySelector('#codeContextResult > div.mb-3.grid');
    return {
      cardCount: summary?.children.length || 0,
      values: [...(summary?.children || [])].map((card) => card.querySelector('p:last-child')?.textContent.trim()),
      visible: !document.getElementById('codeContextResult').classList.contains('hidden')
    };
  })()`);
  assert(codeSummary.visible, 'Code Context result did not become visible');
  assert(codeSummary.cardCount === 4, `Expected 4 Code Context summary cards: ${JSON.stringify(codeSummary)}`);
  assert(codeSummary.values.join(',') === '2,1,1,yes', `Unexpected Code Context metrics: ${JSON.stringify(codeSummary)}`);

  const layout = await evaluate(cdp, `(() => ({
    viewportWidth: window.innerWidth,
    documentScrollWidth: document.documentElement.scrollWidth,
    bodyScrollWidth: document.body.scrollWidth,
    overflow: document.documentElement.scrollWidth > window.innerWidth || document.body.scrollWidth > window.innerWidth
  }))()`);
  assert(!layout.overflow, `Summary smoke caused horizontal overflow: ${JSON.stringify(layout)}`);

  return { pageUrl, projectSummary, codeSummary, layout };
}

async function main() {
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
    '--window-size=390,844',
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
      width: 390,
      height: 844,
      deviceScaleFactor: 2,
      mobile: true,
    });
    await cdp.send('Page.navigate', { url: pageUrl });
    try {
      await cdp.waitEvent('Page.domContentEventFired', 10000);
    } catch (_) {
      // CDN assets are not required for this mocked workbench smoke.
    }

    const result = await runSmoke(cdp);
    console.log(JSON.stringify({ ok: true, ...result }, null, 2));
    cdp.close();
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

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
