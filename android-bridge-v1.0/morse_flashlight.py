"""
闪光灯摩斯密码 — 用手机闪光灯发送摩斯电码
通过 CameraManager.setTorchMode 控制闪光灯闪烁
"""

from java import jclass
import time


def get_context():
    from com.chaquo.python import Python
    return Python.getPlatform().getApplication()


# 摩斯密码表
MORSE = {
    "A": ".-", "B": "-...", "C": "-.-.", "D": "-..", "E": ".",
    "F": "..-.", "G": "--.", "H": "....", "I": "..", "J": ".---",
    "K": "-.-", "L": ".-..", "M": "--", "N": "-.", "O": "---",
    "P": ".--.", "Q": "--.-", "R": ".-.", "S": "...", "T": "-",
    "U": "..-", "V": "...-", "W": ".--", "X": "-..-", "Y": "-.--",
    "Z": "--..",
    "0": "-----", "1": ".----", "2": "..---", "3": "...--", "4": "....-",
    "5": ".....", "6": "-....", "7": "--...", "8": "---..", "9": "----.",
    " ": "/",  # 词间停顿
}

# 时间参数 (毫秒)
DOT_MS = 200       # 点: 短闪
DASH_MS = 600      # 划: 长闪
SYMBOL_GAP = 200   # 点划之间间隔
CHAR_GAP = 600     # 字符之间间隔
WORD_GAP = 1400    # 单词之间间隔


def encode_morse(text):
    """将文本编码为摩斯符号列表"""
    symbols = []
    chars = [c.upper() for c in text if c.upper() in MORSE]
    for i, char in enumerate(chars):
        code = MORSE[char]
        for j, s in enumerate(code):
            symbols.append(s)
            if j < len(code) - 1:
                symbols.append("_")  # 点划间间隔
        if i < len(chars) - 1:
            if char == " " or chars[i + 1] == " ":
                symbols.append("___")  # 单词间隔
            else:
                symbols.append("__")  # 字符间隔
    return symbols


def get_flashlight():
    """获取闪光灯 ID"""
    ctx = get_context()
    ctx_cls = jclass("android.content.Context")
    cm = ctx.getSystemService(ctx_cls.CAMERA_SERVICE)

    camera_ids = cm.getCameraIdList()
    CameraChars = jclass("android.hardware.camera2.CameraCharacteristics")

    # 优先找后置带闪光灯
    for cid in camera_ids:
        chars = cm.getCameraCharacteristics(cid)
        flash = chars.get(CameraChars.FLASH_INFO_AVAILABLE)
        facing = chars.get(CameraChars.LENS_FACING)
        if flash and facing == 0:  # 后置+闪光灯
            return cm, cid

    # 退而求其次
    for cid in camera_ids:
        chars = cm.getCameraCharacteristics(cid)
        flash = chars.get(CameraChars.FLASH_INFO_AVAILABLE)
        if flash:
            return cm, cid

    return None, None


def flash_morse(text, dot=DOT_MS, dash=DASH_MS):
    """用闪光灯发送摩斯密码"""
    cm, cam_id = get_flashlight()

    if cm is None or cam_id is None:
        print("❌ 没有找到带闪光灯的摄像头")
        return

    print(f"✅ 摄像头 ID={cam_id} 就绪\n")

    # 编码
    symbols = encode_morse(text)
    morse_str = " ".join(s for s in symbols if s not in ("_", "__", "___"))

    print(f"📝 原文: {text}")
    print(f"📡 摩斯: {morse_str}")
    print(f"🔢 符号数: {len(symbols)}")

    # 估算时长
    total = 0
    for s in symbols:
        if s == ".": total += dot + SYMBOL_GAP
        elif s == "-": total += dash + SYMBOL_GAP
        elif s == "_": total += SYMBOL_GAP
        elif s == "__": total += CHAR_GAP
        elif s == "___": total += WORD_GAP
    print(f"⏱️ 预计: {total / 1000:.1f} 秒")
    print()

    # 倒计时
    print("   🚨 ", end="", flush=True)
    for i in range(3, 0, -1):
        print(f"{i}...", end="", flush=True)
        cm.setTorchMode(cam_id, True)
        time.sleep(0.08)
        cm.setTorchMode(cam_id, False)
        time.sleep(0.4)
    print("发射！")
    time.sleep(0.3)

    # 逐符号发送
    print("   ", end="", flush=True)
    for s in symbols:
        if s == ".":
            print("●", end="", flush=True)
            cm.setTorchMode(cam_id, True)
            time.sleep(dot / 1000.0)
            cm.setTorchMode(cam_id, False)
            time.sleep(SYMBOL_GAP / 1000.0)
        elif s == "-":
            print("▬", end="", flush=True)
            cm.setTorchMode(cam_id, True)
            time.sleep(dash / 1000.0)
            cm.setTorchMode(cam_id, False)
            time.sleep(SYMBOL_GAP / 1000.0)
        elif s == "__":
            print(" │ ", end="", flush=True)
            time.sleep((CHAR_GAP - SYMBOL_GAP) / 1000.0)
        elif s == "___":
            print(" ‖ ", end="", flush=True)
            time.sleep((WORD_GAP - SYMBOL_GAP) / 1000.0)

    print()
    print()
    print(f"✅ 发送完成: {text} → {morse_str}")


if __name__ == "__main__":
    # === 修改下面这行来发送不同消息 ===
    MESSAGE = "SOS"
    # =================================

    print("=" * 55)
    print("🔦 闪光灯摩斯密码发射器")
    print("=" * 55)
    flash_morse(MESSAGE)
