import os
import glob
import re

files = glob.glob('shared/src/commonMain/composeResources/values*/strings.xml')
for f in files:
    with open(f, 'r') as file:
        content = file.read()
        if not content.strip().endswith('</resources>'):
            print(f"{f}: Missing </resources>")
        # Check for unescaped '&' (not followed by amp;, lt;, gt;, etc.)
        # and unescaped quotes if they are inside text.
        bad_amps = re.findall(r'&(?![a-zA-Z0-9#]+;)', content)
        if bad_amps:
            print(f"{f}: Has unescaped ampersands: {bad_amps}")
