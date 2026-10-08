import sys
from reportlab.graphics.barcode import qrencoder
url = sys.argv[1]
q = qrencoder.QRCode(None, qrencoder.QRErrorCorrectLevel.H)
q.addData(url); q.make()
n = q.getModuleCount(); m = [[q.isDark(r, c) for c in range(n)] for r in range(n)]
quiet = 4; size = n + 2 * quiet
# Rounded-square modules would hurt some scanners; use crisp squares, charcoal on white.
path = ''.join(f'M{c+quiet} {r+quiet}h1v1h-1z' for r in range(n) for c in range(n) if m[r][c])
svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" shape-rendering="crispEdges">'
       f'<rect width="{size}" height="{size}" fill="#FFFFFF"/><path d="{path}" fill="#3A3F45"/></svg>')
open('qr.svg', 'w').write(svg)
print('modules', n, 'version', q.version)
