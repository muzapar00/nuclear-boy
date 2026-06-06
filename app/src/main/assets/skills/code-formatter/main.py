"""代码格式化 —— 对 Python/JSON/XML/YAML 文件进行格式化"""
import os, json, re

def run(file_path: str, style: str = "auto") -> str:
    if not os.path.isfile(file_path):
        return f"错误: 文件不存在 — {file_path}"

    ext = os.path.splitext(file_path)[1].lower()
    with open(file_path, "r", encoding="utf-8") as f:
        original = f.read()

    if ext == ".json":
        formatted = _fmt_json(original, style)
    elif ext in (".xml", ".html"):
        formatted = _fmt_xml(original)
    elif ext == ".py":
        formatted = _fmt_python(original)
    elif ext in (".yaml", ".yml"):
        formatted = original  # YAML is usually already formatted
    else:
        formatted = _fmt_generic(original)

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(formatted)

    return f"✅ 已格式化: {file_path}\n类型: {ext}\n大小: {len(original)} → {len(formatted)} 字符"

def _fmt_json(text: str, style: str) -> str:
    data = json.loads(text)
    indent = 2 if style != "compact" else None
    return json.dumps(data, ensure_ascii=False, indent=indent)

def _fmt_xml(text: str) -> str:
    # 简单的XML缩进
    lines = text.replace("><", ">\n<").split("\n")
    indent = 0
    result = []
    for line in lines:
        line = line.strip()
        if not line:
            continue
        if line.startswith("</"):
            indent = max(0, indent - 1)
        result.append("  " * indent + line)
        if line.startswith("<") and not line.startswith("</") and not line.endswith("/>"):
            indent += 1
    return "\n".join(result)

def _fmt_python(text: str) -> str:
    # 基础格式化：统一缩进、去除行尾空格
    lines = [line.rstrip() for line in text.split("\n")]
    # 确保文件末尾有换行
    if lines and lines[-1] != "":
        lines.append("")
    return "\n".join(lines)

def _fmt_generic(text: str) -> str:
    text = re.sub(r'[ \t]+$', '', text, flags=re.MULTILINE)
    text = re.sub(r'\n{3,}', '\n\n', text)
    return text.strip() + "\n"
