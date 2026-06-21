import fs from 'fs';
import path from 'path';
import {
  assert,
  defaultWorkbenchPage,
  delay,
  evaluate,
  repoRoot,
  runChromeSmoke,
  waitFor,
} from './lib/chrome-cdp.mjs';

const pageUrl = process.argv[2] || defaultWorkbenchPage();
const screenshotDir = path.resolve(repoRoot, 'docs/screenshots/desktop');

const screenshots = {
  upload: 'smartkb-01-workbench-overview.png',
  uploaded: 'smartkb-02-advanced-rag.png',
  detail: 'smartkb-03-project-intake.png',
  qa: 'smartkb-04-agent-task.png',
  followup: 'smartkb-05-memory.png',
  advanced: 'smartkb-06-code-context.png',
  citation: 'smartkb-07-eval-report.png',
};

function ragMockExpression() {
  return `(() => {
    const documents = [
      { fileName: 'advanced-rag-demo.md', chunkCount: 12, description: 'Advanced RAG demo corpus' },
      { fileName: 'virtual-threads-guide.md', chunkCount: 6, description: 'Java 21 IO notes' }
    ];

    const documentDetail = {
      fileName: 'advanced-rag-demo.md',
      chunks: [
        {
          id: 'chunk-01',
          content: 'SmartKB 会读取 Markdown、TXT、PDF 和 DOCX 文档，按 UTF-8 解析文本，然后切分成可检索的知识片段。'
        },
        {
          id: 'chunk-02',
          content: '每个片段会通过本地 Ollama 的 nomic-embed-text 生成向量，并写入 PostgreSQL pgvector，用于后续相似度召回。'
        },
        {
          id: 'chunk-03',
          content: '普通对话模式使用 Redis ChatMemory 保存 conversationId 对应的上下文，刷新页面或服务重启后仍可继续追问。'
        },
        {
          id: 'chunk-07',
          content: 'Advanced RAG 会先改写用户问题，把简短或模糊的提问扩展成更适合检索的表达，从而命中文档中的关键章节。'
        },
        {
          id: 'chunk-09',
          content: 'Hybrid Search 将 pgvector 语义召回与关键词证据结合，在调用模型前完成文档过滤、候选片段筛选和重排序。'
        },
        {
          id: 'chunk-11',
          content: '引用片段让最终回答可追溯：用户可以展开引用，并跳转到支撑该回答的具体原文 chunk。'
        }
      ]
    };

    window.fetch = async (input) => {
      const url = String(input);
      if (url.includes('/api/documents/') && !url.endsWith('/api/documents')) {
        return new Response(JSON.stringify({ success: true, document: documentDetail }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        });
      }
      if (url.includes('/api/documents')) {
        return new Response(JSON.stringify({ success: true, documents }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        });
      }
      if (url.includes('/api/chat/memory/')) {
        return new Response(JSON.stringify({ success: true }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        });
      }
      throw new Error('Unexpected screenshot fetch: ' + url);
    };

    window.__ragDemo = { documents, documentDetail };
  })()`;
}

async function capture(cdp, key) {
  await delay(300);
  const layout = await evaluate(cdp, `(() => ({
    width: window.innerWidth,
    height: window.innerHeight,
    styled: getComputedStyle(document.querySelector('.app-shell')).display === 'flex',
    overflowX: document.documentElement.scrollWidth > window.innerWidth || document.body.scrollWidth > window.innerWidth
  }))()`);
  assert(layout.width === 1440 && layout.height === 900, `Unexpected viewport before ${key}: ${JSON.stringify(layout)}`);
  assert(layout.styled, `Tailwind layout is not active before ${key}: ${JSON.stringify(layout)}`);
  assert(!layout.overflowX, `Horizontal overflow before ${key}: ${JSON.stringify(layout)}`);

  const result = await cdp.send('Page.captureScreenshot', {
    format: 'png',
    fromSurface: true,
    captureBeyondViewport: false,
  });
  const output = path.join(screenshotDir, screenshots[key]);
  fs.mkdirSync(screenshotDir, { recursive: true });
  fs.writeFileSync(output, Buffer.from(result.data, 'base64'));
  return output;
}

async function initPage(cdp) {
  await waitFor(cdp, `Boolean(document.getElementById('workspaceNavChat'))`);
  await waitFor(cdp, `getComputedStyle(document.querySelector('.app-shell')).display === 'flex'`, 30000);
  await evaluate(cdp, ragMockExpression());
  await evaluate(cdp, `(() => {
    const style = document.createElement('style');
    style.textContent = '.fa-solid::before, .fa::before { content: "" !important; } .fa-solid, .fa { width: 0.75rem; }';
    document.head.appendChild(style);
    localStorage.clear();
    openWorkspacePanel('chat');
    setRagMode('conversation');
    conversationId = 'rag-demo-20260621';
    localStorage.setItem(conversationIdKey, conversationId);
    updateConversationStatus();
  })()`);
}

async function showUploadStep(cdp) {
  await evaluate(cdp, `(() => {
    const docList = document.getElementById('documentList');
    docList.innerHTML = '';
    const uploadingDiv = document.createElement('div');
    uploadingDiv.className = 'p-3 bg-blue-50 border border-blue-200 rounded-lg';
    uploadingDiv.innerHTML = \`
      <div class="flex items-center gap-2">
        <i class="fa-solid fa-spinner fa-spin text-blue-600"></i>
        <div class="flex-1">
          <div class="font-medium text-blue-900 text-sm">advanced-rag-demo.md</div>
          <div class="text-xs text-blue-600 mt-1">正在上传并生成向量...</div>
        </div>
      </div>
    \`;
    docList.prepend(uploadingDiv);
    document.getElementById('chatContainer').innerHTML = \`
      <div class="mx-auto mt-14 max-w-2xl rounded-2xl border border-blue-100 bg-white p-6 shadow-sm">
        <p class="text-xs font-semibold uppercase tracking-wide text-blue-600">步骤 1 / 上传</p>
        <h3 class="mt-2 text-xl font-semibold text-gray-900">上传知识文档</h3>
        <p class="mt-3 text-sm leading-6 text-gray-600">
          演示从上传 <strong>advanced-rag-demo.md</strong> 开始。SmartKB 会解析中文 Markdown，切分知识片段，生成 Embedding，并把向量写入 PostgreSQL pgvector。
        </p>
      </div>
    \`;
  })()`);
}

async function showUploadedList(cdp) {
  await evaluate(cdp, `(() => {
    loadDocuments();
    document.getElementById('chatContainer').innerHTML = \`
      <div class="mx-auto mt-14 max-w-2xl rounded-2xl border border-emerald-100 bg-white p-6 shadow-sm">
        <p class="text-xs font-semibold uppercase tracking-wide text-emerald-600">步骤 2 / 入库</p>
        <h3 class="mt-2 text-xl font-semibold text-gray-900">文档已切片并进入检索层</h3>
        <p class="mt-3 text-sm leading-6 text-gray-600">
          左侧文档列表已经显示上传后的文件名和 chunk 数量，说明文档已经从上传流程进入可检索的知识库。
        </p>
      </div>
    \`;
  })()`);
  await waitFor(cdp, `document.getElementById('documentList').textContent.includes('advanced-rag-demo.md')`);
}

async function showDocumentDetailStep(cdp) {
  await evaluate(cdp, `showDocumentDetail('advanced-rag-demo.md')`);
  await waitFor(cdp, `document.getElementById('documentDetailBody').textContent.includes('pgvector')`);
}

async function showConversationQuestion(cdp) {
  await evaluate(cdp, `(() => {
    closeDocumentDetail();
    openWorkspacePanel('chat');
    setRagMode('conversation');
    const chat = document.getElementById('chatContainer');
    chat.innerHTML = '';
    addMessage('上传文档后 SmartKB 做了哪些处理？', true);
    addMessage(
      'SmartKB 会解析上传文件，将文本切分成知识片段，通过 Ollama 生成 Embedding，并把向量写入 PostgreSQL pgvector。后续 RAG 问答会从这些片段中召回证据。',
      false,
      ['advanced-rag-demo.md'],
      { retrievedCount: 4, metrics: { totalMs: 1420, retrievalMs: 88, generationMs: 1210 } }
    );
  })()`);
}

async function showConversationFollowup(cdp) {
  await evaluate(cdp, `(() => {
    openWorkspacePanel('chat');
    setRagMode('conversation');
    addMessage('它会记住我上一轮问的是上传流程吗？', true);
    addMessage(
      '会。当前 conversationId 背后由 Redis ChatMemory 保存上下文，因此追问可以直接沿用上一轮关于“上传流程”的问题，不需要重新说明完整背景。',
      false,
      ['Redis ChatMemory', 'advanced-rag-demo.md'],
      { retrievedCount: 3, metrics: { totalMs: 1260, retrievalMs: 75, generationMs: 1080 } }
    );
  })()`);
}

async function showAdvancedRagStep(cdp) {
  await evaluate(cdp, `(() => {
    openWorkspacePanel('chat');
    setRagMode('advanced');
    setAdvancedFilter('advanced-rag-demo.md');
    const chat = document.getElementById('chatContainer');
    chat.innerHTML = '';
    addMessage('查询改写和引用片段为什么能提升 RAG 可信度？', true);
    addMessage(
      '查询改写会把简短问题扩展成更适合检索的表达，Hybrid Search 再结合语义召回和关键词证据。引用片段让用户可以回到原文 chunk 检查答案依据，因此能提升可解释性和可信度。',
      false,
      ['advanced-rag-demo.md'],
      {
        retrievedCount: 5,
        rewrittenQuery: 'Advanced RAG 查询改写 引用片段 可信度 可追溯',
        metrics: { totalMs: 2130, rewriteMs: 180, retrievalMs: 96, filterMs: 32, rerankMs: 55, generationMs: 1767 },
        stages: [
          { message: '改写问题以提升召回命中率', details: { rewrittenQuery: 'Advanced RAG 查询改写 引用片段 可信度 可追溯', durationMs: 180 } },
          { message: '结合 pgvector 与关键词进行 Hybrid Search', details: { candidateCount: 16, retrievedCount: 5, durationMs: 96 } },
          { message: '过滤并重排序候选片段', details: { referenceCount: 2, durationMs: 87 } },
          { message: '基于引用片段生成回答', details: { totalMs: 2130 } }
        ],
        references: [
          { fileName: 'advanced-rag-demo.md', chunkId: 'chunk-07', preview: 'Advanced RAG 会先改写用户问题，把简短或模糊的提问扩展成更适合检索的表达。' },
          { fileName: 'advanced-rag-demo.md', chunkId: 'chunk-11', preview: '引用片段让最终回答可追溯，用户可以跳转到支撑回答的具体原文 chunk。' }
        ]
      }
    );
  })()`);
  await waitFor(cdp, `document.getElementById('chatContainer').textContent.includes('改写问题以提升召回命中率')`);
}

async function showCitationJumpStep(cdp) {
  await evaluate(cdp, `(() => {
    const summary = document.querySelector('#chatContainer details summary');
    if (summary) summary.click();
  })()`);
  await waitFor(cdp, `Boolean(document.querySelector('[data-reference-chunk-id="chunk-11"]'))`);
  await evaluate(cdp, `(() => {
    const reference = document.querySelector('[data-reference-chunk-id="chunk-11"]');
    reference.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
    showDocumentDetail('advanced-rag-demo.md', 'chunk-11');
  })()`);
  await waitFor(cdp, `(() => {
    const overlay = document.getElementById('documentDetailOverlay');
    const target = document.querySelector('[data-document-chunk-id="chunk-11"]');
    return !overlay.classList.contains('hidden') && Boolean(target) && target.textContent.includes('引用片段');
  })()`);
  await delay(500);
}

async function run(cdp) {
  await initPage(cdp);
  const outputs = [];

  await showUploadStep(cdp);
  outputs.push(await capture(cdp, 'upload'));

  await showUploadedList(cdp);
  outputs.push(await capture(cdp, 'uploaded'));

  await showDocumentDetailStep(cdp);
  outputs.push(await capture(cdp, 'detail'));

  await showConversationQuestion(cdp);
  outputs.push(await capture(cdp, 'qa'));

  await showConversationFollowup(cdp);
  outputs.push(await capture(cdp, 'followup'));

  await showAdvancedRagStep(cdp);
  outputs.push(await capture(cdp, 'advanced'));

  await showCitationJumpStep(cdp);
  outputs.push(await capture(cdp, 'citation'));

  return outputs;
}

runChromeSmoke(pageUrl, {
  profileName: 'workbench-rag-screenshots',
  width: 1440,
  height: 900,
  deviceScaleFactor: 1,
  mobile: false,
}, run)
  .then((outputs) => {
    console.log(JSON.stringify({ ok: true, outputs }, null, 2));
  })
  .catch((error) => {
    console.error(error.stack || error.message);
    process.exit(1);
  });
