import glob
import re

files = glob.glob('shared/src/commonMain/composeResources/values*/strings.xml')
for f in files:
    with open(f, 'r') as file:
        lines = file.readlines()
        for i, line in enumerate(lines):
            if '<string' in line:
                # Find the text between > and <
                match = re.search(r'>([^<]+)<', line)
                if match:
                    text = match.group(1)
                    # Check for unescaped single quote
                    # Negative lookbehind: not preceded by backslash
                    unescaped_quotes = re.findall(r"(?<!\\)'", text)
                    if unescaped_quotes:
                        print(f"{f}:{i+1}: Unescaped quote in: {text.strip()}")
