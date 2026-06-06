"""文件整理工具 - 扫描、分类、整理项目文件"""
import os
import json
from collections import defaultdict

def run(action: str = "scan", target_dir: str = ".") -> str:
    target = os.path.abspath(target_dir)
    if not os.path.isdir(target):
        return f"错误: 目录不存在 — {target_dir}"

    files_info = {"总文件数": 0, "分类": defaultdict(list)}

    for root, dirs, files in os.walk(target):
        # 跳过隐藏目录
        dirs[:] = [d for d in dirs if not d.startswith('.')]
        for f in files:
            if f.startswith('.'):
                continue
            path = os.path.join(root, f)
            ext = os.path.splitext(f)[1].lower() or "(无扩展名)"
            size = os.path.getsize(path)
            rel_path = os.path.relpath(path, target)
            files_info["分类"][ext].append({"文件": rel_path, "大小": size})
            files_info["总文件数"] += 1

    if action == "scan":
        report_lines = [f"📁 项目文件扫描: {target_dir}\n"]
        report_lines.append(f"总文件数: {files_info['总文件数']}\n")
        for ext in sorted(files_info["分类"]):
            count = len(files_info["分类"][ext])
            total_size = sum(f["大小"] for f in files_info["分类"][ext])
            report_lines.append(f"  {ext}: {count} 个文件 ({_fmt_size(total_size)})")
        return "\n".join(report_lines)

    elif action == "report":
        json_output = json.dumps({
            "扫描目录": target_dir,
            "总文件数": files_info["总文件数"],
            "分类统计": {ext: len(v) for ext, v in files_info["分类"].items()}
        }, ensure_ascii=False, indent=2)
        return json_output

    return f"✅ 扫描完成，共 {files_info['总文件数']} 个文件"

def _fmt_size(size: int) -> str:
    for unit in ['B', 'KB', 'MB', 'GB']:
        if size < 1024:
            return f"{size:.1f}{unit}"
        size /= 1024
    return f"{size:.1f}TB"
