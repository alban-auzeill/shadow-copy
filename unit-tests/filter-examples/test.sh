cd "data"
RESULT=$("${SHADOW_COPY_SH}")

[[ -d ".shadow-copy" ]]
[[ -d "$RESULT" ]] || (echo "'$RESULT' directory should exist" && false)

[[ ! -d "$RESULT/tmp" ]]
[[   -d "$RESULT/not-tmp" ]]
[[ ! -d "$RESULT/build/tmp" ]]
[[   -d "$RESULT/build/not-tmp" ]]

[[ ! -f "$RESULT/tmp/f1.txt" ]]
[[   -f "$RESULT/not-a.out" ]]
[[   -f "$RESULT/not-tmp/f1.txt" ]]
[[ ! -f "$RESULT/a.out" ]]
[[   -f "$RESULT/f1.txt" ]]
[[ ! -f "$RESULT/.shadow-copy-filter.sh" ]]
[[ ! -f "$RESULT/f2.json" ]]
[[ ! -f "$RESULT/link1" ]]
[[ ! -f "$RESULT/build/tmp/f1.txt" ]]
[[   -f "$RESULT/build/not-tmp/f1.txt" ]]
[[ ! -f "$RESULT/file42" ]]
