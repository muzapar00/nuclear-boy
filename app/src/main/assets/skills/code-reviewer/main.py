"""
代码审查 Skill — 扫描项目文件，给出改进建议
触发: 新建项目时自动运行
"""
import os
import re

def run(args=None):
    """扫描当前目录，分析代码文件"""
    project_dir = args.get("project_dir", ".") if args else "."
    lines = []
    lines.append("=" * 45)
    lines.append("🔍 核弹男孩 · 代码审查")
    lines.append("=" * 45)

    code_files = []
    for root, dirs, files in os.walk(project_dir):
        # 跳过隐藏目录和常见忽略目录
        dirs[:] = [d for d in dirs if not d.startswith('.') and d not in ('node_modules', '__pycache__', 'build', '.gradle')]
        for f in files:
            ext = os.path.splitext(f)[1].lower()
            if ext in ('.py', '.kt', '.java', '.js', '.ts', '.rs', '.go', '.swift'):
                code_files.append(os.path.join(root, f))

    if not code_files:
        lines.append("📭 未找到代码文件，项目可能为空")
        print("\n".join(lines))
        return {"output": "\n".join(lines)}

    lines.append(f"📁 找到 {len(code_files)} 个代码文件")
    total_lines = 0
    issues = []

    for filepath in code_files[:10]:  # 只分析前10个文件
        try:
            with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
                content = f.read()
                file_lines = content.count('\n') + 1
                total_lines += file_lines
                ext = os.path.splitext(filepath)[1]

                # 基础检查
                if file_lines > 500:
                    issues.append(f"⚠️ {filepath}: {file_lines}行，建议拆分")
                if 'TODO' in content or 'FIXME' in content:
                    todos = re.findall(r'(TODO|FIXME)[:\s]*(.*)', content)
                    for t in todos[:3]:
                        issues.append(f"📝 {filepath}: {t[0]} — {t[1].strip()[:60]}")
                if ext == '.py' and 'import *' in content:
                    issues.append(f"⚠️ {filepath}: 使用了 'import *'，建议明确导入")
        except:
            pass

    lines.append(f"📊 总代码行数: {total_lines:,}")
    if issues:
        lines.append(f"🐛 发现 {len(issues)} 个问题:")
        for issue in issues[:15]:
            lines.append(f"  {issue}")
    else:
        lines.append("✅ 未发现明显问题！")
    lines.append("=" * 45)

    print("\n".join(lines))
    return {"output": "\n".join(lines)}
