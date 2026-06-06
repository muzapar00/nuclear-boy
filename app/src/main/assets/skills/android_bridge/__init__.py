"""
NuclearBoy Android 系统服务桥接 Skill
========================================
通过 Chaquopy Java 桥接调用 Android 系统服务：
振动器、闪光灯、电池、剪贴板、音频、传感器、电话信息等

用法:
    from skills.android_bridge import AndroidBridge
    ab = AndroidBridge()
    
    # 系统信息
    info = ab.get_system_info()
    
    # 振动
    ab.vibrate(200)
    ab.vibrate_pattern("sos")
    ab.haptic_click("double")
    
    # 闪光灯
    ab.flashlight_on()
    ab.flashlight_off()
    ab.flashlight_blink(3)
    
    # 电池
    battery = ab.get_battery_info()
    
    # 剪贴板
    ab.clipboard_copy("Hello!")
    text = ab.clipboard_paste()
    
    # 音频
    audio = ab.get_audio_info()
    
    # 传感器
    sensors = ab.list_sensors()
    
    # 电话
    tel = ab.get_telephony_info()
"""

from skills.android_bridge.core import AndroidBridge

__all__ = ["AndroidBridge"]
__version__ = "1.0.0"
