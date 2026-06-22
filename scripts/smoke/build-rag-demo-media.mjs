import fs from 'fs';
import path from 'path';
import { spawnSync } from 'child_process';
import { fileURLToPath } from 'url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const screenshotDir = path.join(repoRoot, 'docs', 'screenshots');
const desktopDir = path.join(screenshotDir, 'desktop');
const tempDir = path.join(repoRoot, 'target', 'demo-media');

const slides = [
  ['smartkb-01-upload-document.png', '1/8 上传知识文档：解析中文文档，准备切片并写入向量库'],
  ['smartkb-02-document-indexed.png', '2/8 文档入库：左侧展示文件与 chunk 数量，知识库已可检索'],
  ['smartkb-03-document-chunks.png', '3/8 查看详情：检查入库 chunk 内容，确认检索证据来自原文'],
  ['smartkb-04-normal-rag-qa.png', '4/8 普通问答：基于 pgvector 召回片段，生成中文回答'],
  ['smartkb-05-follow-up-chat.png', '5/8 多轮追问：同一 conversationId 读取 Redis ChatMemory 上下文'],
  ['smartkb-06-advanced-rag.png', '6/8 Advanced RAG：展示查询改写、召回、过滤、重排和生成阶段'],
  ['smartkb-07-citation-jump.png', '7/8 引用跳转：点击引用片段，定位并高亮原文 chunk'],
  ['smartkb-08-rag-quality-eval.png', '8/8 检索评测：固定中文问题集展示 Recall@K、Top1、MRR 和引用覆盖'],
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
