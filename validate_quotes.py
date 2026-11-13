import glob
import re

# Compose Multiplatform kaynakları Android usulü \\' ve \\" kaçışlarını ÇÖZMEZ (yalnızca \\n,
# \\t, \\uXXXX ve \\\\ işlenir); kaçışlı bir kesme işareti ekranda ters eğik çizgiyle görünür.
# Bu yüzden kesme işareti ve tırnak kaçışsız yazılmalıdır.
files = glob.glob('shared/src/commonMain/composeResources/values*/strings.xml')
for f in files:
    with open(f, 'r', encoding='utf-8') as file:
        for i, line in enumerate(file.readlines()):
            if '<string' in line or '<item' in line:
                match = re.search(r'>([^<]+)<', line)
                if match and re.search(r"\\['\"@?]", match.group(1)):
                    print(f"{f}:{i+1}: Escaped quote renders a literal backslash: {match.group(1).strip()}")
