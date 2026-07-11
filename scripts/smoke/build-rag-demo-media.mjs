import fs from 'fs';
import path from 'path';
import { spawnSync } from 'child_process';
import { fileURLToPath } from 'url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const screenshotDir = path.join(repoRoot, 'docs', 'screenshots');
const desktopDir = path.join(screenshotDir, 'desktop');
const tempDir = path.join(repoRoot, 'target', 'demo-media');

const slides = [
  ['smartkb-01-upload-document.png', '1/10 文档入库：解析、切片、Embedding，并写入 PostgreSQL pgvector'],
  ['smartkb-02-document-indexed.png', '2/10 知识库索引：展示文件与 chunk 数量，确认文档可检索'],
  ['smartkb-03-document-chunks.png', '3/10 证据检查：查看原文 chunk 与元数据，支持过滤和引用定位'],
  ['smartkb-04-normal-rag-qa.png', '4/10 流式问答：从知识库召回证据并生成中文回答'],
  ['smartkb-05-follow-up-chat.png', '5/10 多轮上下文：使用 Redis ChatMemory 保存 conversationId 状态'],
  ['smartkb-06-advanced-rag.png', '6/10 Advanced RAG：Hybrid Search、BGE 融合重排与 91% 证据置信度'],
  ['smartkb-07-citation-jump.png', '7/10 可解释引用：点击回答引用，定位并高亮支撑答案的原文 chunk'],
  ['smartkb-08-low-confidence-refusal.png', '8/10 安全拒答：证据置信度仅 8%，跳过生成模型，避免无依据回答'],
  ['smartkb-09-rag-quality-eval.png', '9/10 检索评测：对比 Recall@K、Top1、MRR 和引用覆盖率'],
  ['smartkb-10-answer-quality-judge.png', '10/10 答案评估：LLM-as-Judge 衡量忠实度、答案相关性与上下文相关性'],
];

function run(command, args) {
  const result = spawnSync(command, args, { stdio: 'inherit' });
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(' ')} failed with exit code ${result.status}`);
  }
}

function ffmpegFilterPath(value) {
  return value.replace(/\\/g, '/').replace(/:/g, '\\:').replace(/'/g, "\\'");
}

function firstExisting(paths) {
  const found = paths.find((candidate) => fs.existsSync(candidate));
  if (!found) {
    throw new Error(`No usable Chinese font found. Checked: ${paths.join(', ')}`);
  }
  return found;
}

fs.mkdirSync(tempDir, { recursive: true });

const fontFile = firstExisting([
  'C:\\Windows\\Fonts\\msyh.ttc',
  'C:\\Windows\\Fonts\\simhei.ttf',
  'C:\\Windows\\Fonts\\simsun.ttc',
]);

const captionedFrames = slides.map(([fileName, caption], index) => {
  const input = path.join(desktopDir, fileName);
  if (!fs.existsSync(input)) {
    throw new Error(`Missing screenshot: ${input}`);
  }

  const captionFile = path.join(tempDir, `caption-${String(index + 1).padStart(2, '0')}.txt`);
  const output = path.join(tempDir, `captioned-${String(index + 1).padStart(2, '0')}.png`);
  fs.writeFileSync(captionFile, caption, new TextEncoder().encode(caption));

  const filter = [
    'drawbox=x=0:y=ih-98:w=iw:h=98:color=black@0.68:t=fill',
    `drawtext=fontfile='${ffmpegFilterPath(fontFile)}':textfile='${ffmpegFilterPath(captionFile)}':fontcolor=white:fontsize=34:x=(w-text_w)/2:y=h-66`,
  ].join(',');

  run('ffmpeg', ['-y', '-i', input, '-vf', filter, output]);
  return output;
});

const listPath = path.join(tempDir, 'rag-demo-captioned-slides.txt');
const listLines = [];
for (const frame of captionedFrames) {
  listLines.push(`file '${frame.replace(/\\/g, '/').replace(/'/g, "'\\''")}'`);
  listLines.push('duration 2.5');
}
listLines.push(`file '${captionedFrames.at(-1).replace(/\\/g, '/').replace(/'/g, "'\\''")}'`);
fs.writeFileSync(listPath, new TextEncoder().encode(listLines.join('\n')));

const mp4Path = path.join(screenshotDir, 'smartkb-rag-demo.mp4');
const gifPath = path.join(screenshotDir, 'smartkb-rag-demo.gif');
const palettePath = path.join(tempDir, 'smartkb-rag-demo-palette.png');

run('ffmpeg', [
  '-y',
  '-f', 'concat',
  '-safe', '0',
  '-i', listPath,
  '-vf', 'scale=1280:-2,fps=30,format=yuv420p',
  '-c:v', 'libx264',
  '-crf', '20',
  '-movflags', '+faststart',
  mp4Path,
]);

run('ffmpeg', [
  '-y',
  '-i', mp4Path,
  '-vf', 'fps=1,scale=1280:-1:flags=lanczos,palettegen=stats_mode=diff',
  palettePath,
]);

run('ffmpeg', [
  '-y',
  '-i', mp4Path,
  '-i', palettePath,
  '-lavfi', 'fps=1,scale=1280:-1:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=4',
  gifPath,
]);

const outputs = [gifPath, mp4Path].map((filePath) => ({
  file: path.relative(repoRoot, filePath).replace(/\\/g, '/'),
  bytes: fs.statSync(filePath).size,
}));

console.log(JSON.stringify({ ok: true, outputs }, null, 2));
