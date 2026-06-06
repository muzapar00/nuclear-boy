"""
android-bridge Skill — 一键演示 / CLI 入口
用法:
    python skills/android-bridge/run_all.py          # 综合测试
    python skills/android-bridge/run_all.py info     # 系统信息
    python skills/android-bridge/run_all.py battery  # 电池
    python skills/android-bridge/run_all.py vibrate  # 振动演示
    python skills/android-bridge/run_all.py flash    # 闪光灯闪烁
    python skills/android-bridge/run_all.py sensors  # 传感器列表
    python skills/android-bridge/run_all.py full     # 完整诊断报告
"""

import sys
from skills.android_bridge import AndroidBridge


def main():
    ab = AndroidBridge()

    cmd = sys.argv[1] if len(sys.argv) > 1 else "all"

    if cmd == "info":
        print(ab.print_system_info())

    elif cmd == "full":
        ab.full_report()

    elif cmd == "battery":
        print(ab.print_battery())

    elif cmd == "vibrate":
        print("📳 振动器演示...")
        print(f"  硬件: {'✅' if ab.has_vibrator else '❌'}")
        print(f"  振幅控制: {'✅' if ab.has_amplitude_control else '❌'}")
        if ab.has_vibrator:
            ab.vibrate(200)
            import time
            time.sleep(0.4)
            ab.haptic_click("double")
            time.sleep(0.4)
            ab.haptic_click("heavy")
        print("  ✅ 完成")

    elif cmd == "flash":
        print("🔦 闪光灯演示...")
        print(f"  硬件: {'✅' if ab.has_flashlight else '❌'}")
        print(f"  CAMERA 权限: {'✅' if ab.has_camera_permission else '❌'}")
        if ab.has_flashlight:
            ab.flashlight_blink(3)
        print("  ✅ 完成")

    elif cmd == "clipboard":
        result = ab.clipboard_test()
        print(f"📋 剪贴板: {'✅ 正常' if result['success'] else '⚠️ 异常'}")
        print(f"  写入: {result['written']}")
        print(f"  读出: {result['read']}")

    elif cmd == "audio":
        print(ab.print_audio())

    elif cmd == "sensors":
        print(ab.print_common_sensors())
        print(f"\n(共 {len(ab.list_sensors())} 个传感器，用 'python run_all.py sensors-all' 查看全部)")

    elif cmd == "sensors-all":
        print(ab.print_sensors())

    elif cmd == "telephony":
        print(ab.print_telephony())

    elif cmd == "cameras":
        cameras = ab.get_camera_list()
        print(f"📷 摄像头列表 ({len(cameras)} 个):")
        for c in cameras:
            flash = "🔦" if c["has_flash"] else "  "
            print(f"  {flash} ID={c['id']} | {c['facing']}")

    else:  # "all" — 综合测试
        ab.run_all_tests()


if __name__ == "__main__":
    main()
