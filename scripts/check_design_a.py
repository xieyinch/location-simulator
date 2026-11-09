"""Exercise the Android home and panels on the emulator and emit review screenshots."""
import base64
import subprocess
import time
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.check_output(['adb', *args], stderr=subprocess.STDOUT)


def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/window.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/window.xml'))


def find(root, **attributes):
    for node in root.iter('node'):
        if all(node.attrib.get(key) == value for key, value in attributes.items()):
            return node
    raise AssertionError('Missing UI element: ' + repr(attributes))


def tap(node):
    import re
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
    assert x2 > x1 and y2 > y1, node.attrib
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))


def capture(label):
    data=adb('exec-out','screencap','-p')
    print(label+'='+base64.b64encode(data).decode(), flush=True)


adb('shell','wm','size','360x800')
adb('shell','wm','density','160')
adb('shell','pm','grant','io.github.xieyinch.locationsimulator','android.permission.ACCESS_FINE_LOCATION')
adb('shell','pm','grant','io.github.xieyinch.locationsimulator','android.permission.ACCESS_COARSE_LOCATION')
adb('shell','appops','set','io.github.xieyinch.locationsimulator','android:mock_location','allow')
adb('shell','am','start','-W','-n','io.github.xieyinch.locationsimulator/com.zcshou.gogogo.FreeMapActivity')
root=tree()
for label in ('路线','收藏','摇杆','设置'):
    find(root, **{'content-desc':label})
search=find(root, **{'content-desc':'搜索地点或地址'})
assert search.attrib.get('class')=='android.widget.EditText', search.attrib
tap(search);adb('shell','input','text','test')
root=tree();find(root,text='test');find(root,**{'content-desc':'执行地点搜索'})
adb('shell','input','keyevent','4')
root=tree()
find(root, text='开始模拟')
capture('DESIGN_HOME')
tap(find(root, **{'content-desc':'路线'}))
root=tree();find(root,text='路线工具');find(root,text='开始路线模拟');capture('DESIGN_ROUTE')
tap(find(root,text='关闭'))
root=tree();tap(find(root,**{'content-desc':'设置'}))
root=tree();find(root,text='手动输入坐标');tap(find(root,text='手动输入坐标'))
root=tree();fields=[n for n in root.iter('node') if n.attrib.get('class')=='android.widget.EditText']
assert len(fields)==2
for index,value in enumerate(['34.230000','108.940000']):
    root=tree();fields=[n for n in root.iter('node') if n.attrib.get('class')=='android.widget.EditText']
    tap(fields[index]);adb('shell','input','text',value)
root=tree()
try:
    confirm=find(root,text='选择')
except AssertionError:
    adb('shell','input','keyevent','4');root=tree();confirm=find(root,text='选择')
tap(confirm)
root=tree();find(root,text='已选位置');find(root,text='开始模拟')
tap(find(root,text='开始模拟'))
for _ in range(20):
    time.sleep(.5);root=tree()
    try:
        find(root,text='停止模拟');find(root,text='切换到此处');break
    except AssertionError:
        continue
else: raise AssertionError('Simulation state did not become active')
capture('DESIGN_ACTIVE')
tap(find(root,text='停止模拟'))
print('DESIGN_UI_CHECKS_PASSED',flush=True)
