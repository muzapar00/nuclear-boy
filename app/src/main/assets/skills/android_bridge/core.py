"""
Android 系统服务桥接 - 核心模块
提供 Context 获取、系统信息、权限检查、硬件特性探测
"""

from java import jclass
import time

# ============================================================
#  基础工具
# ============================================================

def _get_context():
    """获取 Android Context"""
    from com.chaquo.python import Python
    return Python.getPlatform().getApplication()


def _get_activity():
    """尝试获取当前 Activity（可能为 None）"""
    from com.chaquo.python import Python
    try:
        return Python.getPlatform().getActivity()
    except:
        return None


# ============================================================
#  AndroidBridge 主类
# ============================================================

class AndroidBridge:
    """Android 系统服务统一桥接接口"""

    def __init__(self):
        self._ctx = _get_context()
        self._ctx_cls = jclass("android.content.Context")
        self._Build = jclass("android.os.Build")
        self._VERSION = jclass("android.os.Build$VERSION")
        self._pm_cls = jclass("android.content.pm.PackageManager")

        # 缓存的服务
        self._services = {}

    # ---------- 内部方法 ----------

    def _get_service(self, name):
        """获取系统服务（带缓存）"""
        if name not in self._services:
            const_val = getattr(self._ctx_cls, name)
            self._services[name] = self._ctx.getSystemService(const_val)
        return self._services[name]

    @property
    def vibrator(self):
        return self._get_service("VIBRATOR_SERVICE")

    @property
    def camera_manager(self):
        return self._get_service("CAMERA_SERVICE")

    @property
    def clipboard_manager(self):
        return self._get_service("CLIPBOARD_SERVICE")

    @property
    def audio_manager(self):
        return self._get_service("AUDIO_SERVICE")

    @property
    def sensor_manager(self):
        return self._get_service("SENSOR_SERVICE")

    @property
    def telephony_manager(self):
        return self._get_service("TELEPHONY_SERVICE")

    @property
    def battery_manager(self):
        return self._get_service("BATTERY_SERVICE")

    @property
    def power_manager(self):
        return self._get_service("POWER_SERVICE")

    @property
    def wifi_manager(self):
        return self._get_service("WIFI_SERVICE")

    @property
    def bluetooth_manager(self):
        return self._get_service("BLUETOOTH_SERVICE")

    @property
    def notification_manager(self):
        return self._get_service("NOTIFICATION_SERVICE")

    @property
    def location_manager(self):
        return self._get_service("LOCATION_SERVICE")

    @property
    def connectivity_manager(self):
        return self._get_service("CONNECTIVITY_SERVICE")

    @property
    def alarm_manager(self):
        return self._get_service("ALARM_SERVICE")

    @property
    def storage_manager(self):
        return self._get_service("STORAGE_SERVICE")

    @property
    def display_manager(self):
        return self._get_service("DISPLAY_SERVICE")

    @property
    def input_method_manager(self):
        return self._get_service("INPUT_METHOD_SERVICE")

    @property
    def window_manager(self):
        return self._get_service("WINDOW_SERVICE")

    # ================================================================
    #  1. 系统信息
    # ================================================================

    def get_system_info(self):
        """获取设备和系统版本信息"""
        info = {}

        # Build 静态字段
        build_fields = [
            "BRAND", "MANUFACTURER", "MODEL", "DEVICE", "PRODUCT",
            "HARDWARE", "BOARD", "FINGERPRINT", "TYPE", "TAGS"
        ]
        for field in build_fields:
            try:
                info[field.lower()] = getattr(self._Build, field, "N/A")
            except:
                info[field.lower()] = "N/A"

        # VERSION 字段
        version_fields = ["SDK_INT", "RELEASE", "CODENAME", "INCREMENTAL", "PREVIEW_SDK_INT"]
        for field in version_fields:
            try:
                info[f"version_{field.lower()}"] = getattr(self._VERSION, field, "N/A")
            except:
                info[f"version_{field.lower()}"] = "N/A"

        # Context 类名
        try:
            info["context_class"] = str(self._ctx.getClass().getName())
        except:
            info["context_class"] = "N/A"

        return info

    def print_system_info(self):
        """打印格式化的系统信息"""
        info = self.get_system_info()
        lines = [
            "=" * 55,
            "📱  Android 系统信息",
            "=" * 55,
            "",
            "📋 设备信息:",
            f"  品牌:     {info['brand']}",
            f"  制造商:   {info['manufacturer']}",
            f"  型号:     {info['model']}",
            f"  设备名:   {info['device']}",
            f"  产品:     {info['product']}",
            f"  硬件:     {info['hardware']}",
            f"  主板:     {info['board']}",
            f"  指纹:     {info['fingerprint']}",
            f"  类型:     {info['type']}",
            "",
            "🤖 系统版本:",
            f"  Android:  {info['version_release']}",
            f"  SDK:      {info['version_sdk_int']}",
            f"  代号:     {info['version_codename']}",
            f"  增量:     {info['version_incremental']}",
            f"  Context:  {info['context_class']}",
        ]
        return "\n".join(lines)

    # ================================================================
    #  2. 权限检查
    # ================================================================

    def check_permission(self, permission):
        """检查单个权限是否已授予"""
        try:
            return self._ctx.checkSelfPermission(permission) == self._pm_cls.PERMISSION_GRANTED
        except:
            return None

    def check_permissions(self, permissions=None):
        """批量检查权限状态"""
        if permissions is None:
            permissions = [
                "android.permission.VIBRATE",
                "android.permission.CAMERA",
                "android.permission.FLASHLIGHT",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.BLUETOOTH",
                "android.permission.INTERNET",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_PHONE_STATE",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.FOREGROUND_SERVICE",
            ]

        result = {}
        for perm in permissions:
            try:
                granted = self._ctx.checkSelfPermission(perm) == self._pm_cls.PERMISSION_GRANTED
                result[perm] = granted
            except Exception as e:
                result[perm] = f"err: {e}"
        return result

    def print_permissions(self):
        """打印权限状态"""
        perms = self.check_permissions()
        lines = ["=" * 55, "🔐 权限状态", "=" * 55]
        for perm, granted in perms.items():
            icon = "✅" if granted is True else ("❌" if granted is False else "⚠️")
            short = perm.split(".")[-1]
            lines.append(f"  {icon} {short}")
        return "\n".join(lines)

    # ================================================================
    #  3. 硬件特性
    # ================================================================

    def check_hardware(self, features=None):
        """检查硬件特性是否支持"""
        if features is None:
            features = [
                "android.hardware.camera",
                "android.hardware.camera.flash",
                "android.hardware.camera.front",
                "android.hardware.vibrator",
                "android.hardware.bluetooth",
                "android.hardware.wifi",
                "android.hardware.sensor.gyroscope",
                "android.hardware.sensor.accelerometer",
                "android.hardware.sensor.proximity",
                "android.hardware.sensor.light",
                "android.hardware.sensor.stepcounter",
                "android.hardware.fingerprint",
                "android.hardware.nfc",
                "android.hardware.location.gps",
                "android.hardware.usb.host",
            ]

        pm = self._ctx.getPackageManager()
        result = {}
        for feat in features:
            try:
                result[feat] = pm.hasSystemFeature(feat)
            except:
                result[feat] = "unknown"
        return result

    def print_hardware(self):
        """打印硬件特性"""
        hw = self.check_hardware()
        lines = ["=" * 55, "🔧 硬件特性", "=" * 55]
        for feat, has in hw.items():
            icon = "✅" if has is True else ("❌" if has is False else "⚠️")
            short = feat.split(".")[-1]
            lines.append(f"  {icon} {short}")
        return "\n".join(lines)

    # ================================================================
    #  4. 系统服务探测
    # ================================================================

    def probe_services(self):
        """探测可用系统服务"""
        svc_map = {
            "vibrator": "VIBRATOR_SERVICE",
            "camera": "CAMERA_SERVICE",
            "alarm": "ALARM_SERVICE",
            "power": "POWER_SERVICE",
            "wifi": "WIFI_SERVICE",
            "bluetooth": "BLUETOOTH_SERVICE",
            "sensor": "SENSOR_SERVICE",
            "location": "LOCATION_SERVICE",
            "audio": "AUDIO_SERVICE",
            "notification": "NOTIFICATION_SERVICE",
            "clipboard": "CLIPBOARD_SERVICE",
            "telephony": "TELEPHONY_SERVICE",
            "connectivity": "CONNECTIVITY_SERVICE",
            "input_method": "INPUT_METHOD_SERVICE",
            "window": "WINDOW_SERVICE",
            "layout_inflater": "LAYOUT_INFLATER_SERVICE",
            "media_router": "MEDIA_ROUTER_SERVICE",
            "battery": "BATTERY_SERVICE",
            "storage": "STORAGE_SERVICE",
            "display": "DISPLAY_SERVICE",
        }
        result = {}
        for name, const_name in svc_map.items():
            try:
                const_val = getattr(self._ctx_cls, const_name)
                svc = self._ctx.getSystemService(const_val)
                result[name] = str(svc.getClass().getName()) if svc else "null"
            except Exception as e:
                result[name] = f"⚠️ {e}"
        return result

    def print_services(self):
        """打印服务探测结果"""
        services = self.probe_services()
        lines = ["=" * 55, "🔌 系统服务探测", "=" * 55]
        for name, status in services.items():
            lines.append(f"  {name:<20} {'✅' if not status.startswith('⚠️') else '⚠️'} {status}")
        return "\n".join(lines)

    # ================================================================
    #  5. 振动器
    # ================================================================

    @property
    def has_vibrator(self):
        """检查是否有振动器硬件"""
        try:
            vib = self.vibrator
            return vib.hasVibrator() if vib else False
        except:
            return False

    @property
    def has_amplitude_control(self):
        """是否支持振幅控制 (Android 8+)"""
        if self._VERSION.SDK_INT >= 26:
            try:
                return self.vibrator.hasAmplitudeControl()
            except:
                return False
        return False

    def vibrate(self, duration_ms=200, amplitude=None):
        """
        短振动
        - duration_ms: 振动时长（毫秒）
        - amplitude: 振幅 1-255，None 使用默认
        """
        vib = self.vibrator
        if not vib:
            return False

        VibrationEffect = jclass("android.os.VibrationEffect")

        if self._VERSION.SDK_INT >= 26 and amplitude is not None:
            amp = max(1, min(255, int(amplitude)))
        else:
            amp = VibrationEffect.DEFAULT_AMPLITUDE

        try:
            effect = VibrationEffect.createOneShot(max(1, int(duration_ms)), amp)
            vib.vibrate(effect)
            return True
        except:
            # 回退：旧 API
            try:
                vib.vibrate(int(duration_ms))
                return True
            except Exception as e:
                print(f"⚠️ 振动失败: {e}")
                return False

    def vibrate_pattern(self, name="sos", repeat=False):
        """
        模式振动
        - name: "sos" / "double" / "triple" / "long_short" 或自定义 pattern 列表
        - repeat: True 则循环（用 cancel() 停止）
        """
        patterns = {
            "sos":       [0, 100, 100, 100, 100, 100, 300, 300, 100, 300, 100, 300, 300, 100, 100, 100, 100, 100],
            "double":    [0, 100, 200, 100],
            "triple":    [0, 80, 100, 80, 100, 80],
            "long_short":[0, 400, 300, 100],
            "heartbeat": [0, 80, 120, 80, 400],
        }

        if isinstance(name, list):
            pattern = name
        else:
            pattern = patterns.get(name, patterns["sos"])

        vib = self.vibrator
        if not vib:
            return False

        rep = 0 if repeat else -1

        try:
            VibrationEffect = jclass("android.os.VibrationEffect")
            effect = VibrationEffect.createWaveform(pattern, rep)
            vib.vibrate(effect)
            return True
        except:
            try:
                vib.vibrate(pattern, rep)
                return True
            except Exception as e:
                print(f"⚠️ 模式振动失败: {e}")
                return False

    def haptic_click(self, style="click"):
        """
        触感反馈（短振感）
        style: "tick" / "click" / "double" / "heavy"
        """
        styles = {"tick": 0, "click": 1, "double": 2, "heavy": 5}

        if style not in styles:
            raise ValueError(f"未知触感: {style}，可选: {list(styles.keys())}")

        vib = self.vibrator
        if not vib:
            return False

        try:
            VibrationEffect = jclass("android.os.VibrationEffect")
            effect = VibrationEffect.createPredefined(styles[style])
            vib.vibrate(effect)
            return True
        except Exception as e:
            print(f"⚠️ 触感反馈失败: {e}")
            return False

    def vibrate_cancel(self):
        """取消振动"""
        try:
            self.vibrator.cancel()
            return True
        except:
            return False

    # ================================================================
    #  6. 闪光灯
    # ================================================================

    def _find_flash_camera(self):
        """找到有闪光灯的摄像头 ID"""
        try:
            cm = self.camera_manager
            camera_ids = cm.getCameraIdList()
            CameraCharacteristics = jclass("android.hardware.camera2.CameraCharacteristics")

            # 优先后置
            for cid in camera_ids:
                chars = cm.getCameraCharacteristics(cid)
                facing = chars.get(CameraCharacteristics.LENS_FACING)
                flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                if facing == 0 and flash:  # 后置 + 有灯
                    return cid

            # 次选任意有灯的
            for cid in camera_ids:
                chars = cm.getCameraCharacteristics(cid)
                flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                if flash:
                    return cid

            # 最后随便选一个
            return camera_ids[0] if camera_ids else None
        except Exception as e:
            print(f"⚠️ 查找闪光灯摄像头失败: {e}")
            return None

    @property
    def has_flashlight(self):
        """是否有闪光灯硬件"""
        hw = self.check_hardware(["android.hardware.camera.flash"])
        return hw.get("android.hardware.camera.flash", False)

    @property
    def has_camera_permission(self):
        """是否有 CAMERA 权限"""
        return self.check_permission("android.permission.CAMERA") is True

    def flashlight_on(self):
        """打开闪光灯"""
        if not self.has_flashlight:
            print("⚠️ 设备无闪光灯")
            return False

        cid = self._find_flash_camera()
        if not cid:
            print("⚠️ 找不到可用摄像头")
            return False

        try:
            self.camera_manager.setTorchMode(cid, True)
            return True
        except Exception as e:
            print(f"⚠️ 闪光灯开启失败: {e}")
            return False

    def flashlight_off(self):
        """关闭闪光灯"""
        cid = self._find_flash_camera()
        if not cid:
            return False

        try:
            self.camera_manager.setTorchMode(cid, False)
            return True
        except Exception as e:
            print(f"⚠️ 闪光灯关闭失败: {e}")
            return False

    def flashlight_blink(self, times=3, on_ms=300, off_ms=200):
        """
        闪光灯闪烁
        - times: 闪烁次数
        - on_ms: 每次亮起时长（毫秒）
        - off_ms: 每次熄灭时长（毫秒）
        """
        if not self.has_flashlight:
            print("⚠️ 设备无闪光灯")
            return False

        cid = self._find_flash_camera()
        if not cid:
            return False

        try:
            for i in range(times):
                self.camera_manager.setTorchMode(cid, True)
                time.sleep(on_ms / 1000.0)
                self.camera_manager.setTorchMode(cid, False)
                if i < times - 1:
                    time.sleep(off_ms / 1000.0)
            return True
        except Exception as e:
            print(f"⚠️ 闪烁失败: {e}")
            # 尝试关闭
            try:
                self.camera_manager.setTorchMode(cid, False)
            except:
                pass
            return False

    def get_camera_list(self):
        """获取摄像头列表及其信息"""
        try:
            cm = self.camera_manager
            camera_ids = cm.getCameraIdList()
            CameraCharacteristics = jclass("android.hardware.camera2.CameraCharacteristics")

            cameras = []
            facing_map = {0: "后置", 1: "前置", 2: "外置"}

            for cid in camera_ids:
                try:
                    chars = cm.getCameraCharacteristics(cid)
                    facing = chars.get(CameraCharacteristics.LENS_FACING)
                    flash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                    cameras.append({
                        "id": cid,
                        "facing": facing_map.get(facing, f"未知({facing})"),
                        "has_flash": flash,
                    })
                except:
                    cameras.append({"id": cid, "facing": "?", "has_flash": False})

            return cameras
        except Exception as e:
            print(f"⚠️ 获取摄像头列表失败: {e}")
            return []

    # ================================================================
    #  7. 电池信息
    # ================================================================

    def get_battery_info(self):
        """获取电池详细信息"""
        info = {}

        # 方法1: BatteryManager.getIntProperty (Android 8+)
        bm = self.battery_manager
        if bm and self._VERSION.SDK_INT >= 26:
            BatteryManager = jclass("android.os.BatteryManager")
            props = {
                "capacity": "BATTERY_PROPERTY_CAPACITY",
                "charge_counter": "BATTERY_PROPERTY_CHARGE_COUNTER",
                "current_now": "BATTERY_PROPERTY_CURRENT_NOW",
                "status": "BATTERY_PROPERTY_STATUS",
            }
            for key, prop_id in props.items():
                try:
                    val = bm.getIntProperty(getattr(BatteryManager, prop_id))
                    if key == "charge_counter" and val != -2147483648:
                        info[key] = val / 1000.0  # → mAh
                    else:
                        info[key] = val
                except:
                    info[key] = None

        # 方法2: sticky Intent
        try:
            intent = self._ctx.registerReceiver(
                None,
                jclass("android.content.IntentFilter")("android.intent.action.BATTERY_CHANGED")
            )
            if intent:
                info["level"] = intent.getIntExtra("level", -1)
                info["scale"] = intent.getIntExtra("scale", 100)
                info["percent"] = round(info["level"] / info["scale"] * 100, 1)
                info["temperature"] = intent.getIntExtra("temperature", 0) / 10.0
                info["voltage"] = intent.getIntExtra("voltage", 0) / 1000.0
                plugged = intent.getIntExtra("plugged", 0)
                info["plugged"] = {0: "未插电", 1: "AC", 2: "USB", 4: "无线"}.get(plugged, f"未知({plugged})")
        except:
            pass

        return info

    def print_battery(self):
        """打印电池信息"""
        b = self.get_battery_info()
        lines = ["=" * 50, "🔋 电池信息", "=" * 50]
        if "percent" in b:
            lines.append(f"  电量: {b['percent']}%")
        if "charge_counter" in b and b["charge_counter"]:
            lines.append(f"  容量: {b['charge_counter']:.1f} mAh")
        if "temperature" in b:
            lines.append(f"  温度: {b['temperature']}°C")
        if "voltage" in b:
            lines.append(f"  电压: {b['voltage']}V")
        if "plugged" in b:
            lines.append(f"  充电: {b['plugged']}")
        return "\n".join(lines)

    # ================================================================
    #  8. 剪贴板
    # ================================================================

    def clipboard_copy(self, text, label="NuclearBoy"):
        """复制文本到剪贴板"""
        try:
            ClipData = jclass("android.content.ClipData")
            clip = ClipData.newPlainText(label, str(text))
            self.clipboard_manager.setPrimaryClip(clip)
            return True
        except Exception as e:
            print(f"⚠️ 剪贴板写入失败: {e}")
            return False

    def clipboard_paste(self):
        """从剪贴板读取文本"""
        try:
            primary = self.clipboard_manager.getPrimaryClip()
            if primary and primary.getItemCount() > 0:
                return str(primary.getItemAt(0).getText())
            return None
        except Exception as e:
            print(f"⚠️ 剪贴板读取失败: {e}")
            return None

    def clipboard_test(self):
        """剪贴板读写测试"""
        ts = time.strftime("%H:%M:%S")
        test_text = f"☢️ NuclearBoy 桥接测试 @ {ts}"
        ok = self.clipboard_copy(test_text)
        read = self.clipboard_paste()
        return {
            "written": test_text,
            "read": read,
            "match": read == test_text,
            "success": ok and read == test_text,
        }

    # ================================================================
    #  9. 音频管理器
    # ================================================================

    def get_audio_info(self):
        """获取音频管理器信息"""
        am = self.audio_manager
        if not am:
            return {}

        streams = {0: "语音通话", 1: "系统", 2: "铃声", 3: "音乐",
                    4: "闹钟", 5: "通知", 6: "蓝牙", 8: "DTMF"}

        volumes = {}
        for sid, sname in streams.items():
            try:
                vol = am.getStreamVolume(sid)
                max_vol = am.getStreamMaxVolume(sid)
                volumes[sname] = {"current": vol, "max": max_vol}
            except:
                pass

        ringer_map = {0: "静音", 1: "振动", 2: "正常"}
        try:
            ringer = am.getRingerMode()
            ringer_str = ringer_map.get(ringer, str(ringer))
        except:
            ringer_str = "未知"

        return {
            "volumes": volumes,
            "ringer_mode": ringer_str,
        }

    def print_audio(self):
        """打印音频信息"""
        info = self.get_audio_info()
        lines = ["=" * 50, "🔊 音频管理器", "=" * 50]
        if "volumes" in info:
            lines.append("  当前音量:")
            for name, v in info["volumes"].items():
                lines.append(f"    {name}: {v['current']}/{v['max']}")
        if "ringer_mode" in info:
            lines.append(f"  铃声模式: {info['ringer_mode']}")
        return "\n".join(lines)

    # ================================================================
    #  10. 传感器
    # ================================================================

    def list_sensors(self):
        """列出所有传感器"""
        sm = self.sensor_manager
        if not sm:
            return []

        sensors = []
        sensor_list = sm.getSensorList(-1)  # TYPE_ALL

        for s in sensor_list:
            sensors.append({
                "name": s.getName(),
                "vendor": s.getVendor(),
                "type": s.getType(),
                "power_mA": s.getPower(),
                "resolution": s.getResolution(),
                "max_range": s.getMaximumRange(),
                "min_delay_us": s.getMinDelay(),
            })

        return sensors

    def print_sensors(self):
        """打印传感器列表"""
        sensors = self.list_sensors()
        lines = ["=" * 50, f"📡 传感器列表 (共 {len(sensors)} 个)", "=" * 50]
        for s in sensors:
            lines.append(f"  📡 {s['name']}")
            lines.append(f"     类型={s['type']} | 厂商={s['vendor']} | 功耗={s['power_mA']}mA")
            lines.append(f"     量程={s['max_range']} | 分辨率={s['resolution']} | 最快={s['min_delay_us']}μs")
            lines.append("")
        return "\n".join(lines)

    def check_common_sensors(self):
        """检查常用传感器是否可用"""
        targets = {
            1: "加速度计", 4: "陀螺仪", 5: "光线",
            8: "距离", 11: "旋转向量", 19: "计步器",
            21: "心率", 9: "重力",
        }
        sm = self.sensor_manager
        result = {}
        for typ, name in targets.items():
            try:
                sensor = sm.getDefaultSensor(typ)
                result[name] = sensor.getName() if sensor else None
            except:
                result[name] = None
        return result

    def print_common_sensors(self):
        """打印常用传感器"""
        cs = self.check_common_sensors()
        lines = ["📊 常用传感器:"]
        for name, sensor_name in cs.items():
            icon = "✅" if sensor_name else "❌"
            detail = f" ({sensor_name})" if sensor_name else ""
            lines.append(f"  {icon} {name}{detail}")
        return "\n".join(lines)

    # ================================================================
    #  11. 电话信息
    # ================================================================

    def get_telephony_info(self):
        """获取电话/网络信息（不涉及隐私）"""
        tm = self.telephony_manager
        if not tm:
            return {}

        network_types = {
            0: "未知", 1: "GPRS", 2: "EDGE", 3: "UMTS",
            4: "CDMA", 5: "EVDO_0", 6: "EVDO_A", 7: "1xRTT",
            8: "HSDPA", 9: "HSUPA", 10: "HSPA", 11: "IDEN",
            12: "EVDO_B", 13: "LTE", 14: "EHRPD", 15: "HSPAP",
            16: "GSM", 17: "TD_SCDMA", 18: "IWLAN", 19: "LTE_CA",
            20: "NR(5G)",
        }

        info = {}
        queries = [
            ("network_operator", "getNetworkOperatorName"),
            ("sim_operator", "getSimOperatorName"),
            ("network_type", "getNetworkType"),
            ("data_network_type", "getDataNetworkType"),
            ("voice_network_type", "getVoiceNetworkType"),
            ("is_roaming", "isNetworkRoaming"),
        ]

        for key, method in queries:
            try:
                val = getattr(tm, method)()
                if isinstance(val, bool):
                    val = "是" if val else "否"
                if key.endswith("_type") and isinstance(val, int):
                    val = network_types.get(val, f"类型{val}")
                info[key] = val
            except:
                info[key] = "N/A"

        return info

    def print_telephony(self):
        """打印电话信息"""
        t = self.get_telephony_info()
        if not t:
            return "❌ 无 TelephonyManager"

        labels = {
            "network_operator": "网络运营商",
            "sim_operator": "SIM运营商",
            "network_type": "网络类型",
            "data_network_type": "数据网络类型",
            "voice_network_type": "语音网络类型",
            "is_roaming": "漫游状态",
        }

        lines = ["=" * 50, "📞 电话信息", "=" * 50]
        for key, label in labels.items():
            lines.append(f"  {label}: {t.get(key, 'N/A')}")
        return "\n".join(lines)

    # ================================================================
    #  12. 综合测试
    # ================================================================

    def run_all_tests(self):
        """运行所有非侵入性测试并打印报告"""
        print("=" * 55)
        print("🧪 NuclearBoy 系统服务综合测试")
        info = self.get_system_info()
        print(f"设备: {info['brand']} {info['model']} | Android {info['version_release']} (SDK {info['version_sdk_int']})")
        print("=" * 55)

        # 电池
        print(self.print_battery())

        # 振动
        print("\n" + "=" * 50)
        print("📳 振动器快速演示")
        print("=" * 50)
        if self.has_vibrator:
            print("  🟢 短振 (100ms)...")
            self.vibrate(100)
            time.sleep(0.3)
            print("  🔵 双击感...")
            self.haptic_click("double")
            time.sleep(0.4)
            print("  🟡 重击感...")
            self.haptic_click("heavy")
            print("  ✅ 完成")
        else:
            print("  ❌ 无振动器")

        # 闪光灯
        print("\n" + "=" * 50)
        print("🔦 闪光灯快速闪烁")
        print("=" * 50)
        if self.has_flashlight:
            print("  闪烁 3 次...")
            self.flashlight_blink(3)
            print("  ✅ 完成")
        else:
            print("  ❌ 无闪光灯")

        # 剪贴板
        print("\n" + "=" * 50)
        print("📋 剪贴板读写测试")
        print("=" * 50)
        result = self.clipboard_test()
        print(f"  📤 写入: {result['written']}")
        print(f"  📥 读出: {result['read']}")
        print(f"  {'✅ 读写一致' if result['match'] else '⚠️ 不一致'}")

        # 音频
        print(self.print_audio())

        # 传感器
        print(self.print_common_sensors())

        # 电话
        print(self.print_telephony())

        print("\n" + "=" * 55)
        print("✅ 全部测试完成")
        print("=" * 55)

    # ================================================================
    #  13. 完整诊断报告
    # ================================================================

    def full_report(self):
        """打印完整诊断报告"""
        print(self.print_system_info())
        print()
        print(self.print_services())
        print()
        print(self.print_permissions())
        print()
        print(self.print_hardware())


# ============================================================
#  便捷函数（无需实例化）
# ============================================================

_bridge = None


def _get_bridge():
    global _bridge
    if _bridge is None:
        _bridge = AndroidBridge()
    return _bridge


def vibrate(ms=200):
    """快速振动"""
    return _get_bridge().vibrate(ms)


def flashlight(on=True):
    """快速开关闪光灯"""
    ab = _get_bridge()
    return ab.flashlight_on() if on else ab.flashlight_off()


def clipboard_copy(text):
    """快速复制"""
    return _get_bridge().clipboard_copy(text)


def clipboard_paste():
    """快速粘贴"""
    return _get_bridge().clipboard_paste()


def get_info():
    """快速获取系统信息"""
    return _get_bridge().get_system_info()
