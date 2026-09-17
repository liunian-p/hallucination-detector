const file = document.getElementById('file');
const detect = document.getElementById('detect');
const status = document.getElementById('status');
const error = document.getElementById('error');
const results = document.getElementById('results');
const rows = document.getElementById('rows');
const summary = document.getElementById('summary');
const download = document.getElementById('download');
const typeNames = {POLICY:'政策与流程', PROMOTION:'优惠编造', PRODUCT:'产品参数', CAPABILITY:'能力越界', INFORMATION:'信息编造', SAFETY:'安全误导', OMISSION:'关键信息遗漏'};
const severityNames = {NONE:'无', LOW:'低', MEDIUM:'中', HIGH:'高', CRITICAL:'严重'};
let replies = [];
let detections = [];

file.addEventListener('change', () => {
  detect.disabled = !file.files.length;
  results.hidden = true;
  error.hidden = true;
  status.textContent = file.files.length ? '文件已选择' : '请选择 JSON 文件';
});

detect.addEventListener('click', async () => {
  try {
    replies = await readReplies();
    detect.disabled = true;
    error.hidden = true;
    status.textContent = '正在检测...';
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 210000);
    let response;
    try {
      response = await fetch('/api/detect', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(replies),
        signal: controller.signal
      });
    } finally {
      clearTimeout(timeout);
    }
    const body = await response.text();
    let data;
    try {
      data = body ? JSON.parse(body) : null;
    } catch {
      throw new Error(`服务返回了无法解析的响应（HTTP ${response.status}）`);
    }
    if (!response.ok) throw new Error(data?.error || `检测失败（HTTP ${response.status}）`);
    if (!Array.isArray(data)) throw new Error('服务返回格式错误：检测结果不是数组');
    detections = data;
    render(detections);
    const hallucinations = detections.filter(item => item.is_hallucination).length;
    const summaryText = `共 ${detections.length} 条：疑似幻觉 ${hallucinations} 条，未检出 ${detections.length - hallucinations} 条`;
    status.textContent = `检测完成，${summaryText}`;
    summary.textContent = `检测结果（${summaryText}）`;
  } catch (exception) {
    error.textContent = exception.name === 'AbortError'
      ? '模型调用超时，请减少上传条数、稍后重试或检查网络。'
      : exception.message || '检测失败';
    error.hidden = false;
    status.textContent = '检测未完成';
  } finally {
    detect.disabled = !file.files.length;
  }
});

async function readReplies() {
  const selected = file.files[0];
  if (!selected) throw new Error('请选择 JSON 文件');
  if (selected.size > 1024 * 1024) throw new Error('文件不能超过 1 MB');
  let value;
  try { value = JSON.parse((await selected.text()).replace(/^\uFEFF/, '')); }
  catch { throw new Error('文件不是有效 JSON'); }
  if (!Array.isArray(value) || !value.length) throw new Error('JSON 必须是非空数组');
  return value;
}

function render(detections) {
  const source = new Map(replies.map(reply => [reply.id, reply]));
  rows.replaceChildren();
  for (const detection of detections) {
    const reply = source.get(detection.id);
    const types = [...new Set(detection.findings.map(finding => typeNames[finding.type]))].join('、') || '—';
    const reason = detection.findings.map(finding => finding.reason).join('；') || detection.explanation;
    const row = document.createElement('tr');
    for (const value of [detection.id, reply.user_question, detection.is_hallucination ? '疑似幻觉' : '未检出', types, severityNames[detection.severity], reason]) {
      const cell = document.createElement('td');
      cell.textContent = value;
      row.append(cell);
    }
    rows.append(row);
  }
  results.hidden = false;
}

download.addEventListener('click', () => {
  const exported = detections.map(detection => ({
    id: detection.id,
    is_hallucination: detection.is_hallucination,
    hallucination_type: detection.findings.length ? (typeNames[detection.findings[0].type] || detection.findings[0].type) : null,
    detail: detection.findings.map(finding => finding.reason).join('；') || detection.explanation
  }));
  const url = URL.createObjectURL(new Blob([JSON.stringify(exported, null, 2)], {type: 'application/json;charset=utf-8'}));
  const link = document.createElement('a');
  link.href = url;
  link.download = 'hallucination-detections.json';
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
});
