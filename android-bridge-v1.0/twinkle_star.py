"""
小星星 — 震动演奏版 v2 (节奏修正)
=====================================
修复：
  1. 四分音符 vs 二分音符 时长区分（节奏是灵魂！）
  2. 音符间隔拉长到 100ms（分得清每个音）
  3. 乐句间 400ms 呼吸停顿
  4. 脉冲数加倍（4/8对），音高更明显

通过 vibrate(pattern, -1) 一次性播放完整旋律
"""

from java import jclass
import time


def get_context():
    from com.chaquo.python import Python
    return Python.getPlatform().getApplication()


def build_melody():
    """
    小星星旋律 — 震动波形

    音高编码（脉冲密度）：
      C (do)  : ON=75ms / OFF=50ms → 慢速深沉嗡嗡
      D (re)  : ON=60ms / OFF=45ms
      E (mi)  : ON=50ms / OFF=38ms
      F (fa)  : ON=40ms / OFF=32ms
      G (sol) : ON=32ms / OFF=25ms
      A (la)  : ON=22ms / OFF=18ms → 快速尖锐哒哒

    节奏编码（脉冲对数）：
      四分音符 (q): 4对脉冲 → ~500ms
      二分音符 (h): 8对脉冲 → ~1000ms

    间隔：
      音符间: 100ms
      乐句间: 400ms
    """

    pitch_map = {
        "C": (75, 50),
        "D": (60, 45),
        "E": (50, 38),
        "F": (40, 32),
        "G": (32, 25),
        "A": (22, 18),
    }

    PULSES_Q = 4    # 四分音符脉冲对数
    PULSES_H = 8    # 二分音符脉冲对数
    NOTE_GAP = 100  # 音符间休止 (ms)
    PHRASE_GAP = 400  # 乐句间休止 (ms)

    # 小星星旋律 — (音名, 时值)   q=四分之一  h=二分之一（拉长）
    # 每行一句，最后音符都是二分音符
    melody = [
        # 一闪一闪亮晶晶
        ("C","q"), ("C","q"), ("G","q"), ("G","q"), ("A","q"), ("A","q"), ("G","h"),
        # 满天都是小星星
        ("F","q"), ("F","q"), ("E","q"), ("E","q"), ("D","q"), ("D","q"), ("C","h"),
        # 高高挂在天空中
        ("G","q"), ("G","q"), ("F","q"), ("F","q"), ("E","q"), ("E","q"), ("D","h"),
        # 好像许多小眼睛
        ("G","q"), ("G","q"), ("F","q"), ("F","q"), ("E","q"), ("E","q"), ("D","h"),
        # 一闪一闪亮晶晶
        ("C","q"), ("C","q"), ("G","q"), ("G","q"), ("A","q"), ("A","q"), ("G","h"),
        # 满天都是小星星
        ("F","q"), ("F","q"), ("E","q"), ("E","q"), ("D","q"), ("D","q"), ("C","h"),
    ]

    # 乐句分界点（每句7个音符）
    phrase_end_indices = {7, 14, 21, 28, 35}  # 下标7,14,21,28,35是二分音符所在

    pattern = [0]  # 起始延迟 = 0

    for i, (note, dur) in enumerate(melody):
        on_ms, off_ms = pitch_map[note]
        num_pulses = PULSES_Q if dur == "q" else PULSES_H

        for _ in range(num_pulses):
            pattern.append(on_ms)
            pattern.append(off_ms)

        # 休止符
        if i < len(melody) - 1:
            # 下一音符是乐句开头？用长休止
            if (i + 1) in phrase_end_indices:
                # 当前是二分音符（句末），后面跟乐句休止
                pass  # gap will be added below
            pattern.append(NOTE_GAP)

        # 乐句之间加额外呼吸停顿
        if i in phrase_end_indices and i < len(melody) - 1:
            # 把刚才加的 NOTE_GAP 换成 PHRASE_GAP
            pattern[-1] = PHRASE_GAP

    total_ms = sum(pattern)
    total_sec = total_ms / 1000.0

    print(f"🎵 小星星 — 震动演奏版 v2")
    print(f"   音符总数: {len(melody)} (四分×30 + 二分×12)")
    print(f"   脉冲编码: C=75ms→A=22ms (低沉→尖锐)")
    print(f"   节奏划分: 四分=~500ms / 二分=~1000ms")
    print(f"   波形长度: {len(pattern)} 元素")
    print(f"   预计时长: {total_sec:.1f} 秒")
    print()

    return pattern, total_sec


def play():
    ctx = get_context()
    ctx_cls = jclass("android.content.Context")
    vib = ctx.getSystemService(ctx_cls.VIBRATOR_SERVICE)

    if not vib or not vib.hasVibrator():
        print("❌ 振动器不可用")
        return

    print("=" * 55)
    print("🌟 小星星 — 震动演奏版 v2")
    print("=" * 55)
    print("✅ 振动器就绪")

    # 倒计时
    print("\n   ⏰ 3...", end=" ")
    vib.vibrate(80)
    time.sleep(0.6)
    print("2...", end=" ")
    vib.vibrate(80)
    time.sleep(0.6)
    print("1... 🎶")
    vib.vibrate(80)
    time.sleep(0.6)

    # 构建旋律
    pattern, total_sec = build_melody()

    # 播放（一次性提交，非阻塞）
    print("   🎶 演奏中...")
    vib.vibrate(pattern, -1)

    # 等待播放完成
    time.sleep(total_sec + 0.5)

    print("   ✅ 演奏完成！")
    print()
    print("💡 听感指引：")
    print("   慢嗡嗡(do)→快哒哒(la)→音高上升")
    print("   每句末尾明显拉长 ~1秒")
    print("   句间 0.4秒停顿呼吸")
    print()
    print("   现在能听出《小星星》了吗？🌟")


if __name__ == "__main__":
    play()
