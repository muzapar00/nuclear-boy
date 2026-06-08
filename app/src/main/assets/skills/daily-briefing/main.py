"""
早间简报 Skill — 获取天气、系统状态、新闻摘要
触发: App启动时自动运行
"""
import time
from java import jclass

def run(args=None):
    """获取早间简报"""
    lines = []
    lines.append("=" * 45)
    lines.append("☀️ 核弹男孩 · 早间简报")
    lines.append("=" * 45)

    # 1. 时间
    t = time.localtime()
    hour = t.tm_hour
    greeting = "早上好" if hour < 12 else ("下午好" if hour < 18 else "晚上好")
    lines.append(f"🕐 {greeting}！现在是 {t.tm_year}-{t.tm_mon:02d}-{t.tm_mday:02d} {t.tm_hour:02d}:{t.tm_min:02d}")

    # 2. 设备信息
    try:
        B = jclass("android.os.Build")
        V = jclass("android.os.Build$VERSION")
        lines.append(f"📱 {B.BRAND} {B.MODEL} | Android {V.RELEASE} (SDK {V.SDK_INT})")
    except:
        lines.append("📱 设备信息不可用")

    # 3. 电池
    try:
        ctx = jclass("android.app.ActivityThread").currentActivityThread().getApplication()
        bm = ctx.getSystemService(jclass("android.content.Context").BATTERY_SERVICE)
        BM = jclass("android.os.BatteryManager")
        pct = bm.getIntProperty(BM.BATTERY_PROPERTY_CAPACITY)
        status = bm.getIntProperty(BM.BATTERY_PROPERTY_STATUS)
        status_map = {1: "未知", 2: "充电中", 3: "放电中", 4: "未充电", 5: "已充满"}
        lines.append(f"🔋 电量: {pct}% | 状态: {status_map.get(status, '未知')}")
    except:
        lines.append("🔋 电池信息不可用")

    # 4. 提醒
    lines.append("💡 今日待办：打开核弹男孩，开启高效一天！")
    lines.append("=" * 45)

    print("\n".join(lines))
    return {"output": "\n".join(lines)}
