"""
Skill Creator — 在项目中快速创建新的 Skill 模板。

用法: skill_skill-creator skill_name=my-tool description="我的工具" language=python

输出: 在 .agent/skills/<skill_name>/ 下创建完整的 skill 文件结构
"""
import os
import sys

def run(skill_name: str, description: str, language: str = "python") -> str:
    # 确保 skills 目录存在
    skills_dir = os.path.join(".agent", "skills", skill_name)
    os.makedirs(skills_dir, exist_ok=True)

    # 生成 skill.yaml
    yaml_content = f"""name: {skill_name}
version: 0.1.0
description: "{description}"
author: "user"
entry_point: "main:run"

permissions:
  filesystem:
    read: [workspace/**]
    write: [workspace/**]
  network:
    allowed: false
  packages:
    allowed: []
  shell:
    allowed: false

parameters:
  - name: input
    type: string
    description: "输入参数"
    required: true
"""
    yaml_path = os.path.join(skills_dir, "skill.yaml")
    with open(yaml_path, "w", encoding="utf-8") as f:
        f.write(yaml_content)
    print(f"[OK] 已创建 skill.yaml → {yaml_path}")

    # 生成入口脚本
    if language == "python":
        main_content = '''"""
{description}
"""
import os, sys, json

def run(input: str = "") -> str:
    print(f"[{skill_name}] 开始执行...")
    print(f"输入: {input}")
    # TODO: 在这里编写你的核心逻辑
    print(f"[{skill_name}] 完成!")
    return json.dumps({{"status": "ok", "result": "done"}}, ensure_ascii=False)
'''.replace("{skill_name}", skill_name).replace("{description}", description)
    else:
        main_content = f"# {skill_name} — {description}\n# Language: {language}\ndef run(input=''):\n    print('Hello from {skill_name}!')\n    return 'done'\n"

    main_path = os.path.join(skills_dir, "main.py")
    with open(main_path, "w", encoding="utf-8") as f:
        f.write(main_content)
    print(f"[OK] 已创建 main.py → {main_path}")

    return f"""✨ Skill 「{skill_name}」创建成功！

📁 文件结构:
  .agent/skills/{skill_name}/
  ├── skill.yaml   —— 元数据声明
  └── main.py      —— 入口脚本

🔧 已自动注册为工具: skill_{skill_name}
📝 现在可以使用 skill_{skill_name} 来调用它！

💡 下一步:
  - 修改 main.py 中的 run() 函数实现你的功能
  - 修改 skill.yaml 调整权限和参数
  - 让 AI 帮你编写核心逻辑
"""
